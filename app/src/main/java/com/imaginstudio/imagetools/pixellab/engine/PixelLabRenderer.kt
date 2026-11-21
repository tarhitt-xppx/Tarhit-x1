package com.imaginstudio.imagetools.pixellab.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.EmbossMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.util.LruCache
import com.imaginstudio.imagetools.pixellab.model.AppDefaults
import com.imaginstudio.imagetools.pixellab.model.ArrowLayer
import com.imaginstudio.imagetools.pixellab.model.BackgroundType
import com.imaginstudio.imagetools.pixellab.model.BezierLayer
import com.imaginstudio.imagetools.pixellab.model.CanvasLayer
import com.imaginstudio.imagetools.pixellab.model.DrawPoint
import com.imaginstudio.imagetools.pixellab.model.DrawingLayer
import com.imaginstudio.imagetools.pixellab.model.DrawingStroke
import com.imaginstudio.imagetools.pixellab.model.EffectsState
import com.imaginstudio.imagetools.pixellab.model.FillType
import com.imaginstudio.imagetools.pixellab.model.ImageLayer
import com.imaginstudio.imagetools.pixellab.model.ProjectState
import com.imaginstudio.imagetools.pixellab.model.ShadowConfig
import com.imaginstudio.imagetools.pixellab.model.ShapeLayer
import com.imaginstudio.imagetools.pixellab.model.TextLayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

object PixelLabRenderer {

    private val maxCacheKb = ((Runtime.getRuntime().maxMemory() / 1024L) / 8L).toInt().coerceIn(8192, 49152)
    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val chromaCache = LruCache<String, Bitmap>(6)
    private val camera = Camera()
    private val matrix3D = Matrix()

    fun clearCache() {
        bitmapCache.evictAll()
        chromaCache.evictAll()
    }

    private fun openUriStream(context: Context, uriString: String): java.io.InputStream? {
        return when {
            uriString.startsWith("asset://") -> context.assets.open(uriString.removePrefix("asset://"))
            uriString.startsWith("/") -> java.io.FileInputStream(uriString)
            else -> context.contentResolver.openInputStream(Uri.parse(uriString))
        }
    }

    private fun calculateInSampleSize(rawW: Int, rawH: Int, maxDim: Int = 2048): Int {
        var sample = 1
        var w = rawW
        var h = rawH
        while (w > maxDim || h > maxDim) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    fun loadBitmap(context: Context, uriString: String): Bitmap? {
        if (uriString.isBlank()) return null
        bitmapCache.get(uriString)?.let { if (!it.isRecycled) return it }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openUriStream(context, uriString)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                calculateInSampleSize(bounds.outWidth, bounds.outHeight, 2048)
            } else 1
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sample
            }
            openUriStream(context, uriString)?.use { input ->
                val bmp = BitmapFactory.decodeStream(input, null, opts)
                if (bmp != null) {
                    bitmapCache.put(uriString, bmp)
                }
                bmp
            }
        }.getOrNull()
    }

    fun measureTextBounds(layer: TextLayer, context: Context? = null): Pair<Float, Float> {
        val paint = buildTextPaint(context, layer, alphaOverride = 255)
        val lines = layer.text.split("\n").ifEmpty { listOf(" ") }
        var maxW = 0f
        for (line in lines) {
            maxW = max(maxW, paint.measureText(line.ifEmpty { " " }))
        }
        val fm = paint.fontMetrics
        val lineH = (fm.descent - fm.ascent) * layer.lineSpacing
        val totalH = lineH * lines.size + 16f
        val totalW = maxW + layer.paddingLeft + layer.paddingRight + 24f
        return Pair(totalW.coerceAtLeast(80f), totalH.coerceAtLeast(60f))
    }

    fun exportToBitmap(
        context: Context,
        project: ProjectState,
        outWidth: Int,
        outHeight: Int,
        includeBackground: Boolean = true
    ): Bitmap {
        val out = Bitmap.createBitmap(outWidth.coerceAtLeast(1), outHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val sx = outWidth.toFloat() / project.width.toFloat().coerceAtLeast(1f)
        val sy = outHeight.toFloat() / project.height.toFloat().coerceAtLeast(1f)
        canvas.save()
        canvas.scale(sx, sy)
        renderProject(context, canvas, project, skipTransparentBg = !includeBackground)
        canvas.restore()
        return out
    }

    fun renderProject(
        context: Context,
        canvas: Canvas,
        project: ProjectState,
        skipTransparentBg: Boolean = false
    ) {
        val w = project.width.toFloat()
        val h = project.height.toFloat()

        canvas.save()
        canvas.clipRect(0f, 0f, w, h)

        // 1. Background
        if (!skipTransparentBg) {
            renderBackground(context, canvas, project, w, h)
        }

        // 2. Background Effects: Stripes & Noise
        if (project.effects.stripesEnabled) {
            renderStripes(context, canvas, w, h, project.effects)
        }
        if (project.effects.noiseEnabled) {
            renderNoise(context, canvas, w, h, project.effects)
        }

        // 3. Layers
        for (layer in project.layers) {
            if (!layer.hidden) {
                renderLayer(context, canvas, layer)
            }
        }

        // 4. Foreground Vignette
        if (project.effects.vignetteEnabled) {
            renderVignette(canvas, w, h, project.effects)
        }

        canvas.restore()
    }

    fun renderBackground(
        context: Context,
        canvas: Canvas,
        project: ProjectState,
        w: Float,
        h: Float
    ) {
        val bg = project.background
        if (bg.type == BackgroundType.TRANSPARENT) return

        val fxFilter = buildColorMatrixFilter(
            project.effects.brightness,
            project.effects.contrast,
            project.effects.saturation,
            project.effects.hue
        )

        val saveCount = if (bg.circular) {
            val c = canvas.save()
            val clipPath = Path().apply {
                addOval(RectF(0f, 0f, w, h), Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            c
        } else -1

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = fxFilter
        }

        when (bg.type) {
            BackgroundType.COLOR -> {
                paint.color = bg.color
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            BackgroundType.GRADIENT -> {
                paint.shader = ShapePathBuilder.createShader(bg.gradient, 0f, 0f, w, h)
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            BackgroundType.IMAGE -> {
                val bmp = bg.imageUri?.let { loadBitmap(context, it) }
                if (bmp != null) {
                    canvas.save()
                    canvas.translate(w / 2f, h / 2f)
                    if (bg.imageRotation != 0) canvas.rotate(bg.imageRotation.toFloat())
                    canvas.scale(if (bg.flipH) -1f else 1f, if (bg.flipV) -1f else 1f)
                    val isSwapped = bg.imageRotation % 180 != 0
                    val targetW = if (isSwapped) h else w
                    val targetH = if (isSwapped) w else h
                    val scale = max(targetW / bmp.width, targetH / bmp.height)
                    val drawW = bmp.width * scale
                    val drawH = bmp.height * scale
                    val dst = RectF(-drawW / 2f, -drawH / 2f, drawW / 2f, drawH / 2f)
                    canvas.drawBitmap(bmp, null, dst, paint)
                    canvas.restore()
                } else {
                    paint.color = bg.color
                    canvas.drawRect(0f, 0f, w, h, paint)
                }
            }
            BackgroundType.TRANSPARENT -> Unit
        }

        if (saveCount >= 0) canvas.restoreToCount(saveCount)
    }

    private fun renderStripes(context: Context, canvas: Canvas, w: Float, h: Float, fx: EffectsState) {
        val assetStripe = loadBitmap(context, "asset://effects/stripe.png")
        if (assetStripe != null) {
            val scale = (h / (fx.stripesRepeat.coerceIn(2, 40) * assetStripe.height.coerceAtLeast(1) * 0.4f)).coerceIn(0.25f, 6f)
            val mat = Matrix().apply { setScale(scale, scale) }
            val shader = BitmapShader(assetStripe, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                setLocalMatrix(mat)
            }
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.shader = shader
                alpha = fx.stripesAlpha.coerceIn(0, 255)
            }
            canvas.drawRect(0f, 0f, w, h, p)
            return
        }
        val count = fx.stripesRepeat.coerceIn(2, 40)
        val stripeH = h / (count * 2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(fx.stripesAlpha.coerceIn(0, 255), 255, 255, 255)
        }
        var y = 0f
        while (y < h) {
            canvas.drawRect(0f, y, w, y + stripeH, paint)
            y += stripeH * 2f
        }
    }

    private fun renderNoise(context: Context, canvas: Canvas, w: Float, h: Float, fx: EffectsState) {
        val assetNoise = loadBitmap(context, "asset://effects/noise.png")
        if (assetNoise != null) {
            val scale = (fx.noiseScale.coerceIn(2, 24) / 7f).coerceIn(0.4f, 4f)
            val mat = Matrix().apply { setScale(scale, scale) }
            val shader = BitmapShader(assetNoise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                setLocalMatrix(mat)
            }
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.shader = shader
                alpha = fx.noiseAlpha.coerceIn(0, 255)
            }
            canvas.drawRect(0f, 0f, w, h, p)
            return
        }
        val step = (fx.noiseScale.coerceIn(2, 24) * 3).toFloat()
        val paint = Paint().apply { strokeWidth = step * 0.7f }
        val rng = Random(42)
        val alpha = fx.noiseAlpha.coerceIn(0, 255)
        var y = 0f
        while (y < h) {
            var x = 0f
            while (x < w) {
                val lum = if (rng.nextBoolean()) 255 else 0
                if (rng.nextFloat() > 0.45f) {
                    paint.color = Color.argb((alpha * 0.55f).toInt().coerceIn(0, 255), lum, lum, lum)
                    canvas.drawPoint(x, y, paint)
                }
                x += step
            }
            y += step
        }
    }

    private fun renderVignette(canvas: Canvas, w: Float, h: Float, fx: EffectsState) {
        val cx = w / 2f
        val cy = h / 2f
        val radius = max(w, h) * 0.72f
        val edgeColor = Color.argb(
            fx.vignetteAlpha.coerceIn(0, 255),
            Color.red(fx.vignetteColor),
            Color.green(fx.vignetteColor),
            Color.blue(fx.vignetteColor)
        )
        val shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, edgeColor),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawRect(0f, 0f, w, h, p)
    }

    fun samplePixelAt(context: Context, project: ProjectState, px: Float, py: Float): Int {
        val sampleScale = 0.25f
        val sw = (project.width * sampleScale).toInt().coerceIn(64, 512)
        val sh = (project.height * sampleScale).toInt().coerceIn(64, 512)
        val bmp = exportToBitmap(context, project, sw, sh, includeBackground = true)
        val ix = ((px / project.width.coerceAtLeast(1)) * sw).toInt().coerceIn(0, sw - 1)
        val iy = ((py / project.height.coerceAtLeast(1)) * sh).toInt().coerceIn(0, sh - 1)
        val pixel = bmp.getPixel(ix, iy)
        bmp.recycle()
        return pixel
    }

    fun renderLayer(context: Context, canvas: Canvas, layer: CanvasLayer) {
        canvas.save()
        canvas.translate(layer.x, layer.y)

        if (layer.rotX != 0f || layer.rotY != 0f) {
            camera.save()
            camera.rotateX(layer.rotX)
            camera.rotateY(layer.rotY)
            camera.getMatrix(matrix3D)
            camera.restore()
            canvas.concat(matrix3D)
        }
        if (layer.rotation != 0f) {
            canvas.rotate(layer.rotation)
        }

        val perspective = when (layer) {
            is TextLayer -> if (layer.perspectiveEnabled && layer.perspectivePoints.size == 4) layer.perspectivePoints else null
            is ShapeLayer -> if (layer.perspectiveEnabled && layer.perspectivePoints.size == 4) layer.perspectivePoints else null
            is ImageLayer -> if (layer.perspectiveEnabled && layer.perspectivePoints.size == 4) layer.perspectivePoints else null
            else -> null
        }
        if (perspective != null) {
            val hw = layer.width / 2f
            val hh = layer.height / 2f
            val src = floatArrayOf(-hw, -hh, hw, -hh, hw, hh, -hw, hh)
            val dst = floatArrayOf(
                (perspective[0].x - 0.5f) * layer.width, (perspective[0].y - 0.5f) * layer.height,
                (perspective[1].x - 0.5f) * layer.width, (perspective[1].y - 0.5f) * layer.height,
                (perspective[2].x - 0.5f) * layer.width, (perspective[2].y - 0.5f) * layer.height,
                (perspective[3].x - 0.5f) * layer.width, (perspective[3].y - 0.5f) * layer.height
            )
            val pMat = Matrix()
            if (pMat.setPolyToPoly(src, 0, dst, 0, 4)) {
                canvas.concat(pMat)
            }
        }

        applyLayerMaskIfNeeded(canvas, layer)

        val alphaFactor = (layer.opacity.coerceIn(0, 100) / 100f)

        when (layer) {
            is TextLayer -> renderTextLayer(context, canvas, layer, alphaFactor)
            is ShapeLayer -> renderShapeLayer(context, canvas, layer, alphaFactor)
            is BezierLayer -> renderBezierLayer(canvas, layer, alphaFactor)
            is ArrowLayer -> renderArrowLayer(canvas, layer, alphaFactor)
            is DrawingLayer -> renderDrawingLayer(canvas, layer, alphaFactor)
            is ImageLayer -> renderImageLayer(context, canvas, layer, alphaFactor)
        }

        canvas.restore()
    }

    private fun applyLayerMaskIfNeeded(canvas: Canvas, layer: CanvasLayer) {
        val (enabled, invert, pts) = when (layer) {
            is TextLayer -> Triple(layer.maskEnabled, layer.maskInvert, layer.maskPoints)
            is ShapeLayer -> Triple(layer.maskEnabled, layer.maskInvert, layer.maskPoints)
            is ImageLayer -> Triple(layer.maskEnabled, layer.maskInvert, layer.maskPoints)
            else -> return
        }
        if (!enabled || pts.size < 3) return
        val maskPath = Path()
        val hw = layer.width / 2f
        val hh = layer.height / 2f
        maskPath.moveTo((pts[0].x - 0.5f) * layer.width, (pts[0].y - 0.5f) * layer.height)
        for (i in 1 until pts.size) {
            maskPath.lineTo((pts[i].x - 0.5f) * layer.width, (pts[i].y - 0.5f) * layer.height)
        }
        maskPath.close()
        if (invert) {
            val outer = Path().apply {
                addRect(-hw * 3f, -hh * 3f, hw * 3f, hh * 3f, Path.Direction.CW)
                op(maskPath, Path.Op.DIFFERENCE)
            }
            canvas.clipPath(outer)
        } else {
            canvas.clipPath(maskPath)
        }
    }

    private fun renderTextLayer(context: Context, canvas: Canvas, layer: TextLayer, alphaFactor: Float) {
        val lines = layer.text.split("\n").ifEmpty { listOf(" ") }
        val mainAlpha = (255 * alphaFactor).toInt().coerceIn(0, 255)
        val basePaint = buildTextPaint(context, layer, mainAlpha)
        val fm = basePaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * layer.lineSpacing
        val totalTextH = lineHeight * lines.size
        val maxLineW = lines.maxOf { basePaint.measureText(it.ifEmpty { " " }) }

        // Optional Background box
        if (layer.background.enabled) {
            val bg = layer.background
            val bgRect = RectF(
                -maxLineW / 2f - bg.padLeft,
                -totalTextH / 2f - bg.padTop,
                maxLineW / 2f + bg.padRight,
                totalTextH / 2f + bg.padBottom
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = mainAlpha
                if (bg.fillType == FillType.GRADIENT) {
                    shader = ShapePathBuilder.createShader(bg.gradient, bgRect.left, bgRect.top, bgRect.width(), bgRect.height())
                } else {
                    color = bg.color
                }
            }
            canvas.drawRoundRect(bgRect, bg.radius, bg.radius, bgPaint)
        }

        // Curved text path (non-zero curve)
        if (layer.curve != 0) {
            val flatText = layer.text.replace("\n", " ")
            val radius = (1800f / abs(layer.curve).coerceAtLeast(1)).coerceIn(80f, 1800f)
            val path = Path()
            val arcRect = if (layer.curve > 0) {
                RectF(-radius, -radius * 0.1f, radius, radius * 1.9f)
            } else {
                RectF(-radius, -radius * 1.9f, radius, radius * 0.1f)
            }
            val sweep = if (layer.curve > 0) 170f else -170f
            val start = if (layer.curve > 0) -90f - sweep / 2f else 90f - sweep / 2f
            path.addArc(arcRect, start, sweep)
            basePaint.textAlign = Paint.Align.CENTER
            applyTextFill(context, basePaint, layer, -maxLineW / 2f, -totalTextH / 2f, maxLineW, totalTextH, mainAlpha)
            if (layer.stroke.enabled) {
                val sp = Paint(basePaint).apply {
                    shader = null
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = layer.stroke.width
                    color = multiplyAlpha(layer.stroke.color, alphaFactor)
                }
                canvas.drawTextOnPath(flatText, path, 0f, 0f, sp)
            }
            canvas.drawTextOnPath(flatText, path, 0f, 0f, basePaint)
            return
        }

        val startY = -totalTextH / 2f - fm.ascent

        fun drawLinesWithPaint(p: Paint, dx: Float = 0f, dy: Float = 0f) {
            for (idx in lines.indices) {
                val line = lines[idx]
                val xPos = when (layer.align) {
                    0 -> -maxLineW / 2f
                    2 -> maxLineW / 2f
                    else -> 0f
                }
                p.textAlign = when (layer.align) {
                    0 -> Paint.Align.LEFT
                    2 -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }
                canvas.drawText(line, xPos + dx, startY + idx * lineHeight + dy, p)
            }
        }

        // Reflection
        if (layer.reflection.enabled) {
            canvas.save()
            canvas.translate(0f, totalTextH + layer.reflection.offset)
            canvas.scale(1f, -0.55f)
            val reflAlpha = ((layer.reflection.opacity / 100f) * mainAlpha).toInt().coerceIn(0, 255)
            val rp = buildTextPaint(context, layer, reflAlpha)
            applyTextFill(context, rp, layer, -maxLineW / 2f, -totalTextH / 2f, maxLineW, totalTextH, reflAlpha)
            drawLinesWithPaint(rp)
            canvas.restore()
        }

        // Outer Shadow / Glow
        if (layer.shadow.enabled) {
            val sp = buildTextPaint(context, layer, mainAlpha).apply {
                color = multiplyAlpha(layer.shadow.color, alphaFactor)
                shader = null
                if (layer.shadow.blur > 0.5f) {
                    maskFilter = BlurMaskFilter(layer.shadow.blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                }
            }
            drawLinesWithPaint(sp, layer.shadow.offsetX, layer.shadow.offsetY)
        }

        // 3D Extrusion
        if (layer.threeD.enabled && layer.threeD.depth > 0) {
            val rad = layer.threeD.angle * PI / 180.0
            val ux = cos(rad).toFloat()
            val uy = sin(rad).toFloat()
            val baseExtrudeColor = if (layer.threeD.colorAuto) {
                darkenColor(layer.color, layer.threeD.darken / 100f)
            } else {
                layer.threeD.color
            }
            val tdPaint = buildTextPaint(context, layer, mainAlpha).apply {
                color = multiplyAlpha(baseExtrudeColor, alphaFactor)
                shader = null
            }
            for (step in layer.threeD.depth downTo 1) {
                drawLinesWithPaint(tdPaint, ux * step * 0.9f, uy * step * 0.9f)
            }
        }

        // Stroke
        if (layer.stroke.enabled && layer.stroke.width > 0f) {
            val strokePaint = buildTextPaint(context, layer, mainAlpha).apply {
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                strokeWidth = layer.stroke.width
                color = multiplyAlpha(layer.stroke.color, alphaFactor)
                shader = null
            }
            drawLinesWithPaint(strokePaint)
        }

        // Main Fill + optional Emboss + optional Inner Shadow
        applyTextFill(context, basePaint, layer, -maxLineW / 2f, -totalTextH / 2f, maxLineW, totalTextH, mainAlpha)
        if (layer.emboss.enabled) {
            val rad = layer.emboss.lightAngle * PI.toFloat() / 180f
            basePaint.maskFilter = EmbossMaskFilter(
                floatArrayOf(cos(rad), sin(rad), 0.5f),
                (layer.emboss.ambient / 100f).coerceIn(0.05f, 1f),
                (layer.emboss.hardness / 10f).coerceIn(1f, 20f),
                (layer.emboss.bevel / 8f).coerceIn(1f, 12f)
            )
        }
        if (layer.innerShadow.enabled) {
            val pad = maxLineW + 40f
            val padH = totalTextH + 40f
            val saveL = canvas.saveLayer(-pad, -padH, pad, padH, null)
            drawLinesWithPaint(basePaint)
            val isPaint = buildTextPaint(context, layer, mainAlpha).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                color = multiplyAlpha(layer.innerShadow.color, alphaFactor)
                shader = null
                if (layer.innerShadow.blur > 0.5f) {
                    maskFilter = BlurMaskFilter(layer.innerShadow.blur.coerceAtLeast(1f), BlurMaskFilter.Blur.INNER)
                }
            }
            drawLinesWithPaint(isPaint, layer.innerShadow.offsetX, layer.innerShadow.offsetY)
            canvas.restoreToCount(saveL)
        } else {
            drawLinesWithPaint(basePaint)
        }
    }

    private fun buildTextPaint(context: Context?, layer: TextLayer, alphaOverride: Int): TextPaint {
        val baseTf = com.imaginstudio.imagetools.pixellab.model.AssetRepository.resolveTypeface(context, layer.fontName)
        val styleBits = when {
            layer.bold && layer.italic -> Typeface.BOLD_ITALIC
            layer.bold -> Typeface.BOLD
            layer.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val tf = if (styleBits == Typeface.NORMAL) baseTf else Typeface.create(baseTf, styleBits)
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = layer.fontSize
            isUnderlineText = layer.underline
            isStrikeThruText = layer.strikethrough
            letterSpacing = layer.letterSpacing
            color = layer.color
            alpha = alphaOverride
        }
    }

    private fun applyTextFill(
        context: Context,
        paint: Paint,
        layer: TextLayer,
        left: Float,
        top: Float,
        w: Float,
        h: Float,
        alphaOverride: Int
    ) {
        val texBmp = layer.textureUri?.let { loadBitmap(context, it) }
        if (texBmp != null) {
            paint.shader = BitmapShader(texBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            paint.alpha = alphaOverride
        } else if (layer.fillType == FillType.GRADIENT) {
            paint.shader = ShapePathBuilder.createShader(layer.gradient, left, top, w, h)
            paint.alpha = alphaOverride
        } else {
            paint.shader = null
            paint.color = layer.color
            paint.alpha = alphaOverride
        }
    }

    private fun renderShapeLayer(context: Context, canvas: Canvas, layer: ShapeLayer, alphaFactor: Float) {
        val hw = layer.width / 2f
        val hh = layer.height / 2f
        val radiiList = layer.cornerRadii
        val path = if (layer.shapeLabel == com.imaginstudio.imagetools.pixellab.model.BoundedShapeLabel.RECT &&
            radiiList != null && radiiList.size == 4
        ) {
            val maxR = min(layer.width, layer.height) / 2f
            val r0 = if (layer.cornerEnabled.getOrElse(0) { true }) radiiList[0].coerceIn(0f, maxR) else 0f
            val r1 = if (layer.cornerEnabled.getOrElse(1) { true }) radiiList[1].coerceIn(0f, maxR) else 0f
            val r2 = if (layer.cornerEnabled.getOrElse(2) { true }) radiiList[2].coerceIn(0f, maxR) else 0f
            val r3 = if (layer.cornerEnabled.getOrElse(3) { true }) radiiList[3].coerceIn(0f, maxR) else 0f
            if (r0 > 0.5f || r1 > 0.5f || r2 > 0.5f || r3 > 0.5f) {
                Path().apply {
                    addRoundRect(
                        RectF(-hw, -hh, hw, hh),
                        floatArrayOf(r0, r0, r1, r1, r2, r2, r3, r3),
                        Path.Direction.CW
                    )
                }
            } else {
                ShapePathBuilder.buildBoundedShapePath(layer.shapeLabel, layer.width, layer.height, layer.cornerRadius)
            }
        } else {
            ShapePathBuilder.buildBoundedShapePath(layer.shapeLabel, layer.width, layer.height, layer.cornerRadius)
        }
        val mainAlpha = (255 * alphaFactor).toInt().coerceIn(0, 255)

        // Shadow
        if (layer.shadow.enabled) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = multiplyAlpha(layer.shadow.color, alphaFactor)
                if (layer.shadow.blur > 0.5f) {
                    maskFilter = BlurMaskFilter(layer.shadow.blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                }
            }
            canvas.save()
            canvas.translate(layer.shadow.offsetX, layer.shadow.offsetY)
            canvas.drawPath(path, sp)
            canvas.restore()
        }

        // 3D Extrusion
        if (layer.threeD.enabled && layer.threeD.depth > 0) {
            val rad = layer.threeD.angle * PI / 180.0
            val ux = cos(rad).toFloat()
            val uy = sin(rad).toFloat()
            val extrudeCol = if (layer.threeD.colorAuto) darkenColor(layer.color, layer.threeD.darken / 100f) else layer.threeD.color
            val tdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = multiplyAlpha(extrudeCol, alphaFactor)
            }
            for (step in layer.threeD.depth downTo 1) {
                canvas.save()
                canvas.translate(ux * step * 0.9f, uy * step * 0.9f)
                canvas.drawPath(path, tdPaint)
                canvas.restore()
            }
        }

        // Main Fill (+ Texture + Emboss + Inner Shadow)
        val texBmp = layer.textureUri?.let { loadBitmap(context, it) }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            if (texBmp != null) {
                shader = BitmapShader(texBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                alpha = mainAlpha
            } else if (layer.fillType == FillType.GRADIENT) {
                shader = ShapePathBuilder.createShader(layer.gradient, -hw, -hh, layer.width, layer.height)
                alpha = mainAlpha
            } else {
                color = layer.color
                alpha = mainAlpha
            }
            if (layer.emboss.enabled) {
                val rad = layer.emboss.lightAngle * PI.toFloat() / 180f
                maskFilter = EmbossMaskFilter(
                    floatArrayOf(cos(rad), sin(rad), 0.5f),
                    (layer.emboss.ambient / 100f).coerceIn(0.05f, 1f),
                    (layer.emboss.hardness / 10f).coerceIn(1f, 20f),
                    (layer.emboss.bevel / 8f).coerceIn(1f, 12f)
                )
            }
        }
        canvas.drawPath(path, fillPaint)

        if (layer.innerShadow.enabled) {
            drawPathInnerShadow(canvas, path, layer.innerShadow, alphaFactor)
        }

        // Stroke
        if (layer.stroke.enabled && layer.stroke.width > 0f) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeWidth = layer.stroke.width
                color = multiplyAlpha(layer.stroke.color, alphaFactor)
            }
            canvas.drawPath(path, sp)
        }
    }

    private fun drawPathInnerShadow(canvas: Canvas, path: Path, shadow: ShadowConfig, alphaFactor: Float) {
        canvas.save()
        canvas.clipPath(path)
        val isp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (shadow.blur * 1.4f).coerceAtLeast(8f)
            color = multiplyAlpha(shadow.color, alphaFactor)
            if (shadow.blur > 0.5f) {
                maskFilter = BlurMaskFilter(shadow.blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.translate(shadow.offsetX, shadow.offsetY)
        canvas.drawPath(path, isp)
        canvas.restore()
    }

    private fun renderBezierLayer(canvas: Canvas, layer: BezierLayer, alphaFactor: Float) {
        if (layer.anchors.size < 2) return
        val path = buildBezierPath(layer.anchors, layer.closed, layer.width, layer.height)
        val hw = layer.width / 2f
        val hh = layer.height / 2f
        val mainAlpha = (255 * alphaFactor).toInt().coerceIn(0, 255)

        if (layer.shadow.enabled) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = if (layer.fillEnabled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                strokeWidth = layer.stroke.width
                color = multiplyAlpha(layer.shadow.color, alphaFactor)
                if (layer.shadow.blur > 0.5f) {
                    maskFilter = BlurMaskFilter(layer.shadow.blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                }
            }
            canvas.save()
            canvas.translate(layer.shadow.offsetX, layer.shadow.offsetY)
            canvas.drawPath(path, sp)
            canvas.restore()
        }

        if (layer.fillEnabled) {
            val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                if (layer.fillType == FillType.GRADIENT) {
                    shader = ShapePathBuilder.createShader(layer.gradient, -hw, -hh, layer.width, layer.height)
                    alpha = mainAlpha
                } else {
                    color = layer.color
                    alpha = mainAlpha
                }
            }
            canvas.drawPath(path, fp)
        }

        if (layer.stroke.enabled && layer.stroke.width > 0f) {
            val stp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = layer.stroke.width
                color = multiplyAlpha(layer.stroke.color, alphaFactor)
            }
            canvas.drawPath(path, stp)
        }
    }

    fun buildBezierPath(anchors: List<com.imaginstudio.imagetools.pixellab.model.BezierAnchor>, closed: Boolean, w: Float, h: Float): Path {
        val path = Path()
        if (anchors.isEmpty()) return path
        fun toX(nx: Float) = (nx - 0.5f) * w
        fun toY(ny: Float) = (ny - 0.5f) * h
        path.moveTo(toX(anchors[0].x), toY(anchors[0].y))
        for (i in 1 until anchors.size) {
            val prev = anchors[i - 1]
            val cur = anchors[i]
            path.cubicTo(
                toX(prev.ctrlOutX), toY(prev.ctrlOutY),
                toX(cur.ctrlInX), toY(cur.ctrlInY),
                toX(cur.x), toY(cur.y)
            )
        }
        if (closed && anchors.size >= 2) {
            val last = anchors.last()
            val first = anchors.first()
            path.cubicTo(
                toX(last.ctrlOutX), toY(last.ctrlOutY),
                toX(first.ctrlInX), toY(first.ctrlInY),
                toX(first.x), toY(first.y)
            )
            path.close()
        }
        return path
    }

    private fun renderArrowLayer(canvas: Canvas, layer: ArrowLayer, alphaFactor: Float) {
        val mainAlpha = (255 * alphaFactor).toInt().coerceIn(0, 255)
        if (layer.shadow.enabled) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = multiplyAlpha(layer.shadow.color, alphaFactor)
                if (layer.shadow.blur > 0.5f) {
                    maskFilter = BlurMaskFilter(layer.shadow.blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                }
            }
            canvas.save()
            canvas.translate(layer.shadow.offsetX, layer.shadow.offsetY)
            ShapePathBuilder.drawArrow(
                canvas, shadowPaint, null, layer.width, layer.stemWidth, layer.headSize,
                layer.startStyle, layer.endStyle
            )
            canvas.restore()
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            if (layer.fillType == FillType.GRADIENT) {
                shader = ShapePathBuilder.createShader(layer.gradient, -layer.width / 2f, -layer.height / 2f, layer.width, layer.height)
                alpha = mainAlpha
            } else {
                color = layer.color
                alpha = mainAlpha
            }
        }
        val strokePaint = if (layer.stroke.enabled && layer.stroke.width > 0f) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = layer.stroke.width
                strokeJoin = Paint.Join.ROUND
                color = multiplyAlpha(layer.stroke.color, alphaFactor)
            }
        } else null

        ShapePathBuilder.drawArrow(
            canvas, fillPaint, strokePaint, layer.width, layer.stemWidth, layer.headSize,
            layer.startStyle, layer.endStyle
        )
    }

    private fun renderDrawingLayer(canvas: Canvas, layer: DrawingLayer, alphaFactor: Float) {
        val hw = layer.width / 2f
        val hh = layer.height / 2f
        val sx = layer.width / layer.sourceWidth.coerceAtLeast(1f)
        val sy = layer.height / layer.sourceHeight.coerceAtLeast(1f)

        val saveLayer = canvas.saveLayer(-hw - 30f, -hh - 30f, hw + 30f, hh + 30f, null)
        canvas.translate(-hw, -hh)
        canvas.scale(sx, sy)
        for (stroke in layer.strokes) {
            drawStroke(canvas, stroke, alphaFactor)
        }
        canvas.restoreToCount(saveLayer)
    }

    fun drawStroke(canvas: Canvas, stroke: DrawingStroke, alphaFactor: Float = 1f) {
        if (stroke.points.isEmpty()) return
        val path = Path()
        path.moveTo(stroke.points[0].x, stroke.points[0].y)
        for (i in 1 until stroke.points.size) {
            val prev = stroke.points[i - 1]
            val curr = stroke.points[i]
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            path.quadTo(prev.x, prev.y, midX, midY)
        }
        if (stroke.points.size > 1) {
            val last = stroke.points.last()
            path.lineTo(last.x, last.y)
        }
        if (stroke.fill) path.close()

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = stroke.width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = if (stroke.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            if (stroke.eraser) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            } else {
                color = multiplyAlpha(stroke.color, alphaFactor)
                if (stroke.dashed) {
                    pathEffect = DashPathEffect(floatArrayOf(stroke.width * 2.2f, stroke.width * 1.5f), 0f)
                }
                if (stroke.neon) {
                    maskFilter = BlurMaskFilter((stroke.blur + 8f).coerceAtLeast(6f), BlurMaskFilter.Blur.SOLID)
                } else if (stroke.blur > 0.5f) {
                    maskFilter = BlurMaskFilter(stroke.blur, BlurMaskFilter.Blur.NORMAL)
                }
            }
        }
        canvas.drawPath(path, p)
    }

    private fun renderImageLayer(context: Context, canvas: Canvas, layer: ImageLayer, alphaFactor: Float) {
        val hw = layer.width / 2f
        val hh = layer.height / 2f
        val mainAlpha = (255 * alphaFactor).toInt().coerceIn(0, 255)

        var bmp = loadBitmap(context, layer.uriString) ?: return
        if (layer.removeColorEnabled) {
            val key = "${layer.uriString}_${layer.removeColorTarget}_${layer.removeColorTolerance}"
            val cached = chromaCache.get(key)
            bmp = if (cached != null && !cached.isRecycled) {
                cached
            } else {
                applyChromaKey(bmp, layer.removeColorTarget, layer.removeColorTolerance).also {
                    chromaCache.put(key, it)
                }
            }
        }

        val rect = RectF(-hw, -hh, hw, hh)
        val maxR = min(layer.width, layer.height) / 2f
        val r0 = if (layer.cornerEnabled.getOrElse(0) { true }) layer.cornerRadii.getOrElse(0) { 0f }.coerceIn(0f, maxR) else 0f
        val r1 = if (layer.cornerEnabled.getOrElse(1) { true }) layer.cornerRadii.getOrElse(1) { 0f }.coerceIn(0f, maxR) else 0f
        val r2 = if (layer.cornerEnabled.getOrElse(2) { true }) layer.cornerRadii.getOrElse(2) { 0f }.coerceIn(0f, maxR) else 0f
        val r3 = if (layer.cornerEnabled.getOrElse(3) { true }) layer.cornerRadii.getOrElse(3) { 0f }.coerceIn(0f, maxR) else 0f
        val hasCornerRadius = r0 > 0.5f || r1 > 0.5f || r2 > 0.5f || r3 > 0.5f

        val shapePath = Path().apply {
            when {
                layer.circularCrop -> addOval(rect, Path.Direction.CW)
                hasCornerRadius -> addRoundRect(
                    rect,
                    floatArrayOf(r0, r0, r1, r1, r2, r2, r3, r3),
                    Path.Direction.CW
                )
                else -> addRect(rect, Path.Direction.CW)
            }
        }

        if (layer.shadow.enabled) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = multiplyAlpha(layer.shadow.color, alphaFactor)
                if (layer.shadow.blur > 0.5f) {
                    maskFilter = BlurMaskFilter(layer.shadow.blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                }
            }
            canvas.save()
            canvas.translate(layer.shadow.offsetX, layer.shadow.offsetY)
            canvas.drawPath(shapePath, sp)
            canvas.restore()
        }

        val isGradientTint = layer.colorOverlayEnabled && layer.colorOverlayFillType == FillType.GRADIENT
        val needOffscreen = layer.eraserStrokes.isNotEmpty() || isGradientTint
        val offscreenSave = if (needOffscreen) {
            canvas.saveLayer(-hw - 20f, -hh - 20f, hw + 20f, hh + 20f, null)
        } else {
            canvas.save()
        }

        if (layer.circularCrop || hasCornerRadius) {
            canvas.clipPath(shapePath)
        }

        canvas.save()
        canvas.scale(if (layer.flipH) -1f else 1f, if (layer.flipV) -1f else 1f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = mainAlpha
            colorFilter = if (layer.colorOverlayEnabled && layer.colorOverlayFillType == FillType.COLOR) {
                PorterDuffColorFilter(layer.colorOverlay, PorterDuff.Mode.SRC_ATOP)
            } else {
                buildColorMatrixFilter(layer.brightness, layer.contrast, layer.saturation, layer.hue)
            }
        }
        canvas.drawBitmap(bmp, null, rect, paint)
        canvas.restore()

        if (isGradientTint) {
            val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = ShapePathBuilder.createShader(layer.colorOverlayGradient, -hw, -hh, layer.width, layer.height)
                alpha = mainAlpha
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
            canvas.drawRect(rect, gp)
        }

        if (layer.eraserStrokes.isNotEmpty()) {
            for (es in layer.eraserStrokes) {
                val mappedPts = es.points.map { pt -> DrawPoint((pt.x - 0.5f) * layer.width, (pt.y - 0.5f) * layer.height) }
                drawStroke(canvas, es.copy(points = mappedPts, eraser = true), 1f)
            }
        }

        if (layer.innerShadow.enabled) {
            drawPathInnerShadow(canvas, shapePath, layer.innerShadow, alphaFactor)
        }

        canvas.restoreToCount(offscreenSave)

        if (layer.stroke.enabled && layer.stroke.width > 0f) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeWidth = layer.stroke.width
                color = multiplyAlpha(layer.stroke.color, alphaFactor)
            }
            canvas.drawPath(shapePath, sp)
        }
    }

    private fun applyChromaKey(src: Bitmap, targetColor: Int, tolerancePct: Int): Bitmap {
        val maxDist = (tolerancePct.coerceIn(1, 100) / 100f) * 255f * 1.732f
        val tr = Color.red(targetColor)
        val tg = Color.green(targetColor)
        val tb = Color.blue(targetColor)

        val scaled = if (src.width > 900 || src.height > 900) {
            val ratio = min(900f / src.width, 900f / src.height)
            Bitmap.createScaledBitmap(src, (src.width * ratio).toInt().coerceAtLeast(1), (src.height * ratio).toInt().coerceAtLeast(1), true)
        } else {
            src.copy(Bitmap.Config.ARGB_8888, true)
        }
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = Color.alpha(c)
            if (a == 0) continue
            val dr = Color.red(c) - tr
            val dg = Color.green(c) - tg
            val db = Color.blue(c) - tb
            val dist = kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat())
            if (dist < maxDist) {
                pixels[i] = Color.argb(0, Color.red(c), Color.green(c), Color.blue(c))
            }
        }
        val out = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        if (scaled !== src && !scaled.isRecycled) {
            scaled.recycle()
        }
        return out
    }

    fun buildColorMatrixFilter(brightness: Int, contrast: Int, saturation: Int, hue: Int): ColorMatrixColorFilter? {
        if (brightness == 0 && contrast == 0 && saturation == 0 && hue == 0) return null
        val cm = ColorMatrix()

        if (saturation != 0) {
            val satMat = ColorMatrix()
            satMat.setSaturation((1f + saturation / 100f).coerceIn(0f, 3f))
            cm.postConcat(satMat)
        }

        if (contrast != 0 || brightness != 0) {
            val c = (1f + contrast / 100f).coerceIn(0.2f, 3f)
            val b = brightness * 2.55f
            val translate = (-0.5f * c + 0.5f) * 255f + b
            val cbMat = ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, translate,
                    0f, c, 0f, 0f, translate,
                    0f, 0f, c, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(cbMat)
        }

        if (hue != 0) {
            val rad = hue * PI.toFloat() / 180f
            val cosVal = cos(rad)
            val sinVal = sin(rad)
            val lumR = 0.213f
            val lumG = 0.715f
            val lumB = 0.072f
            val hueMat = ColorMatrix(
                floatArrayOf(
                    lumR + cosVal * (1f - lumR) + sinVal * (-lumR), lumG + cosVal * (-lumG) + sinVal * (-lumG), lumB + cosVal * (-lumB) + sinVal * (1f - lumB), 0f, 0f,
                    lumR + cosVal * (-lumR) + sinVal * 0.143f, lumG + cosVal * (1f - lumG) + sinVal * 0.140f, lumB + cosVal * (-lumB) + sinVal * (-0.283f), 0f, 0f,
                    lumR + cosVal * (-lumR) + sinVal * (-(1f - lumR)), lumG + cosVal * (-lumG) + sinVal * lumG, lumB + cosVal * (1f - lumB) + sinVal * lumB, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(hueMat)
        }

        return ColorMatrixColorFilter(cm)
    }

    private fun multiplyAlpha(color: Int, factor: Float): Int {
        val a = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun darkenColor(color: Int, amount: Float): Int {
        val f = (1f - amount).coerceIn(0.15f, 1f)
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) * f).toInt(),
            (Color.green(color) * f).toInt(),
            (Color.blue(color) * f).toInt()
        )
    }
}
