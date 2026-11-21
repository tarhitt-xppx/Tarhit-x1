package com.imaginstudio.imagetools.pixellab.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.imaginstudio.imagetools.pixellab.engine.PixelLabRenderer
import com.imaginstudio.imagetools.pixellab.model.BezierAnchor
import com.imaginstudio.imagetools.pixellab.model.BezierLayer
import com.imaginstudio.imagetools.pixellab.model.CanvasLayer
import com.imaginstudio.imagetools.pixellab.model.DrawPoint
import com.imaginstudio.imagetools.pixellab.model.DrawingLayer
import com.imaginstudio.imagetools.pixellab.model.DrawingStroke
import com.imaginstudio.imagetools.pixellab.model.GridState
import com.imaginstudio.imagetools.pixellab.model.ImageLayer
import com.imaginstudio.imagetools.pixellab.model.ProjectState
import com.imaginstudio.imagetools.pixellab.model.ShapeLayer
import com.imaginstudio.imagetools.pixellab.model.TextLayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class CanvasStageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class BrushConfig(
        var color: Int = Color.parseColor("#0079FF"),
        var width: Float = 16f,
        var blur: Float = 0f,
        var dashed: Boolean = false,
        var neon: Boolean = false,
        var fill: Boolean = false,
        var eraser: Boolean = false
    )

    enum class StageToolMode {
        NORMAL,
        DRAW,
        PERSPECTIVE,
        MASK,
        BEZIER,
        BITMAP_ERASER,
        EYEDROPPER
    }

    var project: ProjectState = ProjectState(id = "init")
        set(value) {
            field = value
            invalidate()
        }

    var grid: GridState = GridState()
        set(value) {
            field = value
            invalidate()
        }

    var selectedLayerId: String? = null
        set(value) {
            field = value
            invalidate()
        }

    var zoomModeEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var toolMode: StageToolMode = StageToolMode.NORMAL
        set(value) {
            field = value
            if (value != StageToolMode.DRAW) {
                activeDrawPoints.clear()
            }
            if (value != StageToolMode.EYEDROPPER) {
                eyedropperBitmap?.recycle()
                eyedropperBitmap = null
            }
            invalidate()
        }

    var bezierActionMode: Int = 0 // 0 = Add Point, 1 = Move/Drag Point
    var bitmapEraserWidth: Float = 28f

    val brushConfig = BrushConfig()
    private val pendingStrokes = mutableListOf<DrawingStroke>()
    private val activeDrawPoints = mutableListOf<DrawPoint>()
    private val activeEraserPoints = mutableListOf<DrawPoint>()

    private var activePerspectiveIndex = -1
    private var activeVertexIndex = -1
    private var eyedropperX = -1f
    private var eyedropperY = -1f
    private var eyedropperColor = Color.WHITE
    private var eyedropperBitmap: Bitmap? = null

    var onLayerSelected: ((String?) -> Unit)? = null
    var onBeforeTransform: (() -> Unit)? = null
    var onLayerTransform: ((CanvasLayer) -> Unit)? = null
    var onTransformEnd: (() -> Unit)? = null
    var onDoubleTapText: ((TextLayer) -> Unit)? = null
    var onDrawStrokeCountChanged: ((Int) -> Unit)? = null
    var onEyedropperColor: ((Int) -> Unit)? = null

    private var userZoom = 1f
    private var panX = 0f
    private var panY = 0f

    private var baseScale = 1f
    private var stageLeft = 0f
    private var stageTop = 0f

    private enum class DragMode { NONE, MOVE, RESIZE, ROTATE, PAN }

    private var dragMode = DragMode.NONE
    private var dragSnapshotSent = false
    private var touchStartProjX = 0f
    private var touchStartProjY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var origLayerX = 0f
    private var origLayerY = 0f
    private var origLayerW = 0f
    private var origLayerH = 0f
    private var origRot = 0f

    private val checkerPaint: Paint by lazy {
        val tile = 48
        val half = tile / 2
        val bmp = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val light = Paint().apply { color = Color.parseColor("#E5E7EB") }
        val dark = Paint().apply { color = Color.parseColor("#CBD5E1") }
        c.drawRect(0f, 0f, half.toFloat(), half.toFloat(), light)
        c.drawRect(half.toFloat(), 0f, tile.toFloat(), half.toFloat(), dark)
        c.drawRect(0f, half.toFloat(), half.toFloat(), tile.toFloat(), dark)
        c.drawRect(half.toFloat(), half.toFloat(), tile.toFloat(), tile.toFloat(), light)
        Paint().apply {
            shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }
    private val eyedropperFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyedropperRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val stageBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#475569")
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(190, 56, 189, 248)
        pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }
    private val selStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#0079FF")
        pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
    }
    private val overlayWirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#F59E0B")
    }
    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = Color.parseColor("#0079FF")
    }
    private val handleWarnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = Color.parseColor("#F59E0B")
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!zoomModeEnabled) return false
            userZoom = (userZoom * detector.scaleFactor).coerceIn(0.4f, 6f)
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (zoomModeEnabled) {
                resetZoom()
                return true
            }
            if (toolMode != StageToolMode.NORMAL) return false
            val (px, py) = viewToProject(e.x, e.y)
            val hit = hitTestLayer(px, py)
            if (hit is TextLayer && !hit.locked) {
                onDoubleTapText?.invoke(hit)
                return true
            }
            return false
        }
    })

    fun resetZoom() {
        userZoom = 1f
        panX = 0f
        panY = 0f
        invalidate()
    }

    fun undoPendingStroke() {
        if (pendingStrokes.isNotEmpty()) {
            pendingStrokes.removeAt(pendingStrokes.lastIndex)
            onDrawStrokeCountChanged?.invoke(pendingStrokes.size)
            invalidate()
        }
    }

    fun clearPendingDrawing() {
        pendingStrokes.clear()
        activeDrawPoints.clear()
        onDrawStrokeCountChanged?.invoke(0)
        invalidate()
    }

    fun finishDrawingLayer(): DrawingLayer? {
        if (pendingStrokes.isEmpty()) return null
        val layer = DrawingLayer(
            id = "draw_${System.currentTimeMillis()}",
            name = "Drawing",
            x = project.width / 2f,
            y = project.height / 2f,
            width = project.width.toFloat(),
            height = project.height.toFloat(),
            sourceWidth = project.width.toFloat(),
            sourceHeight = project.height.toFloat(),
            strokes = pendingStrokes.toList()
        )
        pendingStrokes.clear()
        activeDrawPoints.clear()
        onDrawStrokeCountChanged?.invoke(0)
        invalidate()
        return layer
    }

    private fun updateStageMetrics() {
        val vw = width.toFloat().coerceAtLeast(1f)
        val vh = height.toFloat().coerceAtLeast(1f)
        val pad = 32f
        val fitScale = min(
            (vw - pad * 2f) / project.width.toFloat().coerceAtLeast(1f),
            (vh - pad * 2f) / project.height.toFloat().coerceAtLeast(1f)
        )
        baseScale = fitScale * userZoom
        val stageW = project.width * baseScale
        val stageH = project.height * baseScale
        stageLeft = (vw - stageW) / 2f + panX
        stageTop = (vh - stageH) / 2f + panY
    }

    private fun viewToProject(vx: Float, vy: Float): Pair<Float, Float> {
        updateStageMetrics()
        val px = (vx - stageLeft) / baseScale
        val py = (vy - stageTop) / baseScale
        return Pair(px, py)
    }

    private fun projectToLayerNormalized(layer: CanvasLayer, px: Float, py: Float): DrawPoint {
        val dx = px - layer.x
        val dy = py - layer.y
        val rad = -layer.rotation * PI / 180.0
        val lx = (dx * cos(rad) - dy * sin(rad)).toFloat()
        val ly = (dx * sin(rad) + dy * cos(rad)).toFloat()
        val nx = (lx / layer.width.coerceAtLeast(1f)) + 0.5f
        val ny = (ly / layer.height.coerceAtLeast(1f)) + 0.5f
        return DrawPoint(nx, ny)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateStageMetrics()

        val stageW = project.width * baseScale
        val stageH = project.height * baseScale

        canvas.save()
        canvas.translate(stageLeft, stageTop)
        if (project.background.type == com.imaginstudio.imagetools.pixellab.model.BackgroundType.TRANSPARENT ||
            project.background.circular
        ) {
            canvas.drawRect(0f, 0f, stageW, stageH, checkerPaint)
        }
        canvas.drawRect(0f, 0f, stageW, stageH, stageBorderPaint)

        canvas.save()
        canvas.scale(baseScale, baseScale)
        PixelLabRenderer.renderProject(context, canvas, project)

        if (toolMode == StageToolMode.DRAW && (pendingStrokes.isNotEmpty() || activeDrawPoints.isNotEmpty())) {
            val saveL = canvas.saveLayer(0f, 0f, project.width.toFloat(), project.height.toFloat(), null)
            for (s in pendingStrokes) {
                PixelLabRenderer.drawStroke(canvas, s)
            }
            if (activeDrawPoints.isNotEmpty()) {
                PixelLabRenderer.drawStroke(
                    canvas,
                    DrawingStroke(
                        points = activeDrawPoints,
                        color = brushConfig.color,
                        width = brushConfig.width,
                        blur = brushConfig.blur,
                        dashed = brushConfig.dashed,
                        neon = brushConfig.neon,
                        fill = brushConfig.fill,
                        eraser = brushConfig.eraser
                    )
                )
            }
            canvas.restoreToCount(saveL)
        }

        if (grid.enabled) {
            val pw = project.width.toFloat()
            val ph = project.height.toFloat()
            for (hFrac in grid.hGuides) {
                val gy = hFrac * ph
                canvas.drawLine(0f, gy, pw, gy, gridPaint)
            }
            for (vFrac in grid.vGuides) {
                val gx = vFrac * pw
                canvas.drawLine(gx, 0f, gx, ph, gridPaint)
            }
        }

        val selected = project.layers.firstOrNull { it.id == selectedLayerId && !it.hidden }
        val invScale = (1f / baseScale).coerceAtLeast(0.5f)
        if (selected != null) {
            when (toolMode) {
                StageToolMode.NORMAL -> drawSelectionOverlay(canvas, selected, invScale)
                StageToolMode.PERSPECTIVE -> drawPerspectiveOverlay(canvas, selected, invScale)
                StageToolMode.MASK -> drawMaskOverlay(canvas, selected, invScale)
                StageToolMode.BEZIER -> if (selected is BezierLayer) drawBezierOverlay(canvas, selected, invScale)
                StageToolMode.BITMAP_ERASER -> drawSelectionOverlay(canvas, selected, invScale)
                else -> Unit
            }
        }

        if (toolMode == StageToolMode.EYEDROPPER && eyedropperX >= 0f && eyedropperY >= 0f) {
            val r = 24f * invScale
            eyedropperFillPaint.color = eyedropperColor
            eyedropperRingPaint.strokeWidth = 4f * invScale
            canvas.drawCircle(eyedropperX, eyedropperY - 56f * invScale, r * 1.4f, eyedropperFillPaint)
            canvas.drawCircle(eyedropperX, eyedropperY - 56f * invScale, r * 1.4f, eyedropperRingPaint)
            canvas.drawCircle(eyedropperX, eyedropperY, r * 0.5f, handleBorderPaint)
        }

        canvas.restore()
        canvas.restore()
    }

    private fun drawSelectionOverlay(canvas: Canvas, layer: CanvasLayer, invScale: Float) {
        canvas.save()
        canvas.translate(layer.x, layer.y)
        canvas.rotate(layer.rotation)
        val hw = layer.width / 2f + 10f
        val hh = layer.height / 2f + 10f
        selStrokePaint.strokeWidth = 2.5f * invScale
        handleBorderPaint.strokeWidth = 3f * invScale

        canvas.drawRect(-hw, -hh, hw, hh, selStrokePaint)

        val handleRadius = 14f * invScale
        canvas.drawCircle(hw, hh, handleRadius, handleFillPaint)
        canvas.drawCircle(hw, hh, handleRadius, handleBorderPaint)

        val rotStemY = hh + 38f * invScale
        canvas.drawLine(0f, hh, 0f, rotStemY, selStrokePaint)
        canvas.drawCircle(0f, rotStemY, handleRadius * 0.9f, handleFillPaint)
        canvas.drawCircle(0f, rotStemY, handleRadius * 0.9f, handleBorderPaint)
        canvas.restore()
    }

    private fun drawPerspectiveOverlay(canvas: Canvas, layer: CanvasLayer, invScale: Float) {
        val pts = when (layer) {
            is TextLayer -> layer.perspectivePoints
            is ShapeLayer -> layer.perspectivePoints
            is ImageLayer -> layer.perspectivePoints
            else -> return
        }
        if (pts.size != 4) return
        canvas.save()
        canvas.translate(layer.x, layer.y)
        canvas.rotate(layer.rotation)
        overlayWirePaint.strokeWidth = 3f * invScale
        handleWarnPaint.strokeWidth = 3.5f * invScale

        val p = Path()
        val coords = pts.map { DrawPoint((it.x - 0.5f) * layer.width, (it.y - 0.5f) * layer.height) }
        p.moveTo(coords[0].x, coords[0].y)
        for (i in 1..3) p.lineTo(coords[i].x, coords[i].y)
        p.close()
        canvas.drawPath(p, overlayWirePaint)

        val hr = 16f * invScale
        for (c in coords) {
            canvas.drawCircle(c.x, c.y, hr, handleFillPaint)
            canvas.drawCircle(c.x, c.y, hr, handleWarnPaint)
        }
        canvas.restore()
    }

    private fun drawMaskOverlay(canvas: Canvas, layer: CanvasLayer, invScale: Float) {
        val pts = when (layer) {
            is TextLayer -> layer.maskPoints
            is ShapeLayer -> layer.maskPoints
            is ImageLayer -> layer.maskPoints
            else -> return
        }
        canvas.save()
        canvas.translate(layer.x, layer.y)
        canvas.rotate(layer.rotation)
        overlayWirePaint.strokeWidth = 3f * invScale
        handleWarnPaint.strokeWidth = 3f * invScale
        if (pts.size >= 2) {
            val p = Path()
            p.moveTo((pts[0].x - 0.5f) * layer.width, (pts[0].y - 0.5f) * layer.height)
            for (i in 1 until pts.size) {
                p.lineTo((pts[i].x - 0.5f) * layer.width, (pts[i].y - 0.5f) * layer.height)
            }
            if (pts.size >= 3) p.close()
            canvas.drawPath(p, overlayWirePaint)
        }
        val hr = 14f * invScale
        for (pt in pts) {
            val lx = (pt.x - 0.5f) * layer.width
            val ly = (pt.y - 0.5f) * layer.height
            canvas.drawCircle(lx, ly, hr, handleFillPaint)
            canvas.drawCircle(lx, ly, hr, handleWarnPaint)
        }
        canvas.restore()
    }

    private fun drawBezierOverlay(canvas: Canvas, layer: BezierLayer, invScale: Float) {
        canvas.save()
        canvas.translate(layer.x, layer.y)
        canvas.rotate(layer.rotation)
        overlayWirePaint.strokeWidth = 2.5f * invScale
        handleWarnPaint.strokeWidth = 3f * invScale
        val hr = 14f * invScale
        for (anc in layer.anchors) {
            val ax = (anc.x - 0.5f) * layer.width
            val ay = (anc.y - 0.5f) * layer.height
            val ix = (anc.ctrlInX - 0.5f) * layer.width
            val iy = (anc.ctrlInY - 0.5f) * layer.height
            val ox = (anc.ctrlOutX - 0.5f) * layer.width
            val oy = (anc.ctrlOutY - 0.5f) * layer.height
            canvas.drawLine(ix, iy, ox, oy, overlayWirePaint)
            canvas.drawCircle(ix, iy, hr * 0.65f, handleFillPaint)
            canvas.drawCircle(ox, oy, hr * 0.65f, handleFillPaint)
            canvas.drawCircle(ax, ay, hr, handleFillPaint)
            canvas.drawCircle(ax, ay, hr, handleWarnPaint)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (zoomModeEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragMode = DragMode.PAN
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragMode == DragMode.PAN && !scaleDetector.isInProgress) {
                        panX += event.x - lastTouchX
                        panY += event.y - lastTouchY
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragMode = DragMode.NONE
            }
            return true
        }

        val (px, py) = viewToProject(event.x, event.y)

        when (toolMode) {
            StageToolMode.DRAW -> {
                handleFreehandDraw(event, px, py)
                return true
            }
            StageToolMode.PERSPECTIVE -> {
                handlePerspectiveTouch(event, px, py)
                return true
            }
            StageToolMode.MASK -> {
                handleMaskTouch(event, px, py)
                return true
            }
            StageToolMode.BEZIER -> {
                handleBezierTouch(event, px, py)
                return true
            }
            StageToolMode.BITMAP_ERASER -> {
                handleBitmapEraserTouch(event, px, py)
                return true
            }
            StageToolMode.EYEDROPPER -> {
                eyedropperX = px
                eyedropperY = py
                if (event.actionMasked == MotionEvent.ACTION_DOWN || eyedropperBitmap == null || eyedropperBitmap?.isRecycled == true) {
                    eyedropperBitmap?.recycle()
                    val sw = (project.width * 0.25f).toInt().coerceIn(64, 320)
                    val sh = (project.height * 0.25f).toInt().coerceIn(64, 320)
                    eyedropperBitmap = PixelLabRenderer.exportToBitmap(context, project, sw, sh, includeBackground = true)
                }
                eyedropperBitmap?.let { snap ->
                    val ix = ((px / project.width.coerceAtLeast(1)) * snap.width).toInt().coerceIn(0, snap.width - 1)
                    val iy = ((py / project.height.coerceAtLeast(1)) * snap.height).toInt().coerceIn(0, snap.height - 1)
                    eyedropperColor = snap.getPixel(ix, iy)
                    onEyedropperColor?.invoke(eyedropperColor)
                }
                invalidate()
                return true
            }
            StageToolMode.NORMAL -> Unit
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragSnapshotSent = false
                touchStartProjX = px
                touchStartProjY = py

                val sel = project.layers.firstOrNull { it.id == selectedLayerId && !it.hidden }
                if (sel != null && !sel.locked) {
                    val handleHit = checkHandleHit(sel, px, py)
                    if (handleHit != DragMode.NONE) {
                        dragMode = handleHit
                        origLayerX = sel.x
                        origLayerY = sel.y
                        origLayerW = sel.width
                        origLayerH = sel.height
                        origRot = sel.rotation
                        return true
                    }
                }

                val hit = hitTestLayer(px, py)
                if (hit != null) {
                    onLayerSelected?.invoke(hit.id)
                    if (!hit.locked) {
                        dragMode = DragMode.MOVE
                        origLayerX = hit.x
                        origLayerY = hit.y
                    } else {
                        dragMode = DragMode.NONE
                    }
                } else {
                    onLayerSelected?.invoke(null)
                    dragMode = DragMode.NONE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val sel = project.layers.firstOrNull { it.id == selectedLayerId && !it.hidden && !it.locked }
                    ?: return true
                if (dragMode == DragMode.NONE) return true

                if (!dragSnapshotSent) {
                    dragSnapshotSent = true
                    onBeforeTransform?.invoke()
                }

                when (dragMode) {
                    DragMode.MOVE -> {
                        var nx = origLayerX + (px - touchStartProjX)
                        var ny = origLayerY + (py - touchStartProjY)
                        if (grid.enabled && grid.snap) {
                            nx = snapToGuides(nx, project.width.toFloat(), grid.vGuides)
                            ny = snapToGuides(ny, project.height.toFloat(), grid.hGuides)
                        }
                        onLayerTransform?.invoke(sel.copyBase(x = nx, y = ny))
                    }
                    DragMode.RESIZE -> {
                        val dx = px - sel.x
                        val dy = py - sel.y
                        val rad = -sel.rotation * PI / 180.0
                        val lx = abs((dx * cos(rad) - dy * sin(rad)).toFloat())
                        val ly = abs((dx * sin(rad) + dy * cos(rad)).toFloat())
                        val nw = (lx * 2f).coerceAtLeast(48f)
                        val nh = (ly * 2f).coerceAtLeast(36f)
                        onLayerTransform?.invoke(sel.copyBase(width = nw, height = nh))
                    }
                    DragMode.ROTATE -> {
                        val angleRad = atan2(py - sel.y, px - sel.x)
                        var deg = (angleRad * 180.0 / PI).toFloat() - 90f
                        while (deg > 180f) deg -= 360f
                        while (deg < -180f) deg += 360f
                        deg = snapAngle(deg)
                        onLayerTransform?.invoke(sel.copyBase(rotation = deg))
                    }
                    else -> Unit
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragSnapshotSent) {
                    onTransformEnd?.invoke()
                }
                dragMode = DragMode.NONE
                dragSnapshotSent = false
            }
        }
        return true
    }

    private fun handleFreehandDraw(event: MotionEvent, px: Float, py: Float) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeDrawPoints.clear()
                activeDrawPoints.add(DrawPoint(px, py))
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                activeDrawPoints.add(DrawPoint(px, py))
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeDrawPoints.isNotEmpty()) {
                    pendingStrokes.add(
                        DrawingStroke(
                            points = activeDrawPoints.toList(),
                            color = brushConfig.color,
                            width = brushConfig.width,
                            blur = brushConfig.blur,
                            dashed = brushConfig.dashed,
                            neon = brushConfig.neon,
                            fill = brushConfig.fill,
                            eraser = brushConfig.eraser
                        )
                    )
                    activeDrawPoints.clear()
                    onDrawStrokeCountChanged?.invoke(pendingStrokes.size)
                    invalidate()
                }
            }
        }
    }

    private fun handlePerspectiveTouch(event: MotionEvent, px: Float, py: Float) {
        val sel = project.layers.firstOrNull { it.id == selectedLayerId } ?: return
        val pts = when (sel) {
            is TextLayer -> sel.perspectivePoints
            is ShapeLayer -> sel.perspectivePoints
            is ImageLayer -> sel.perspectivePoints
            else -> return
        }
        if (pts.size != 4) return
        val np = projectToLayerNormalized(sel, px, py)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePerspectiveIndex = pts.indices.minByOrNull { idx ->
                    hypot(pts[idx].x - np.x, pts[idx].y - np.y)
                } ?: -1
                if (activePerspectiveIndex >= 0) {
                    val dist = hypot((pts[activePerspectiveIndex].x - np.x) * sel.width, (pts[activePerspectiveIndex].y - np.y) * sel.height)
                    if (dist > max(64f / baseScale, 48f)) activePerspectiveIndex = -1
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePerspectiveIndex in 0..3) {
                    val updatedPts = pts.toMutableList()
                    updatedPts[activePerspectiveIndex] = DrawPoint(np.x.coerceIn(-0.6f, 1.6f), np.y.coerceIn(-0.6f, 1.6f))
                    val updated = when (sel) {
                        is TextLayer -> sel.copy(perspectiveEnabled = true, perspectivePoints = updatedPts)
                        is ShapeLayer -> sel.copy(perspectiveEnabled = true, perspectivePoints = updatedPts)
                        is ImageLayer -> sel.copy(perspectiveEnabled = true, perspectivePoints = updatedPts)
                        else -> sel
                    }
                    onLayerTransform?.invoke(updated)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> activePerspectiveIndex = -1
        }
    }

    private fun handleMaskTouch(event: MotionEvent, px: Float, py: Float) {
        val sel = project.layers.firstOrNull { it.id == selectedLayerId } ?: return
        val pts = when (sel) {
            is TextLayer -> sel.maskPoints
            is ShapeLayer -> sel.maskPoints
            is ImageLayer -> sel.maskPoints
            else -> return
        }
        val np = projectToLayerNormalized(sel, px, py)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hitIdx = pts.indexOfFirst {
                    hypot((it.x - np.x) * sel.width, (it.y - np.y) * sel.height) < max(42f / baseScale, 32f)
                }
                if (hitIdx >= 0) {
                    activeVertexIndex = hitIdx
                } else {
                    val nextPts = pts + np
                    val updated = when (sel) {
                        is TextLayer -> sel.copy(maskEnabled = nextPts.size >= 3, maskPoints = nextPts)
                        is ShapeLayer -> sel.copy(maskEnabled = nextPts.size >= 3, maskPoints = nextPts)
                        is ImageLayer -> sel.copy(maskEnabled = nextPts.size >= 3, maskPoints = nextPts)
                        else -> sel
                    }
                    onLayerTransform?.invoke(updated)
                    activeVertexIndex = nextPts.lastIndex
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeVertexIndex in pts.indices) {
                    val nextPts = pts.toMutableList()
                    nextPts[activeVertexIndex] = np
                    val updated = when (sel) {
                        is TextLayer -> sel.copy(maskPoints = nextPts)
                        is ShapeLayer -> sel.copy(maskPoints = nextPts)
                        is ImageLayer -> sel.copy(maskPoints = nextPts)
                        else -> sel
                    }
                    onLayerTransform?.invoke(updated)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> activeVertexIndex = -1
        }
    }

    private fun handleBezierTouch(event: MotionEvent, px: Float, py: Float) {
        val sel = project.layers.firstOrNull { it.id == selectedLayerId } as? BezierLayer ?: return
        val np = projectToLayerNormalized(sel, px, py)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hitIdx = sel.anchors.indexOfFirst {
                    hypot((it.x - np.x) * sel.width, (it.y - np.y) * sel.height) < max(44f / baseScale, 32f)
                }
                if (hitIdx >= 0) {
                    activeVertexIndex = hitIdx
                } else if (bezierActionMode == 0) {
                    val prev = sel.anchors.lastOrNull()
                    val offset = 0.08f
                    val newAnchor = if (prev != null) {
                        val dx = (np.x - prev.x) * 0.25f
                        val dy = (np.y - prev.y) * 0.25f
                        BezierAnchor(np.x, np.y, np.x - dx, np.y - dy, np.x + dx, np.y + dy)
                    } else {
                        BezierAnchor(np.x, np.y, np.x - offset, np.y, np.x + offset, np.y)
                    }
                    onLayerTransform?.invoke(sel.copy(anchors = sel.anchors + newAnchor))
                    activeVertexIndex = sel.anchors.size
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeVertexIndex in sel.anchors.indices) {
                    val list = sel.anchors.toMutableList()
                    val old = list[activeVertexIndex]
                    val dx = np.x - old.x
                    val dy = np.y - old.y
                    list[activeVertexIndex] = old.copy(
                        x = np.x,
                        y = np.y,
                        ctrlInX = old.ctrlInX + dx,
                        ctrlInY = old.ctrlInY + dy,
                        ctrlOutX = old.ctrlOutX + dx,
                        ctrlOutY = old.ctrlOutY + dy
                    )
                    onLayerTransform?.invoke(sel.copy(anchors = list))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> activeVertexIndex = -1
        }
    }

    private fun handleBitmapEraserTouch(event: MotionEvent, px: Float, py: Float) {
        val sel = project.layers.firstOrNull { it.id == selectedLayerId } as? ImageLayer ?: return
        val np = projectToLayerNormalized(sel, px, py)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeEraserPoints.clear()
                activeEraserPoints.add(np)
            }
            MotionEvent.ACTION_MOVE -> {
                activeEraserPoints.add(np)
                val previewStroke = DrawingStroke(
                    points = activeEraserPoints.toList(),
                    color = Color.TRANSPARENT,
                    width = bitmapEraserWidth,
                    eraser = true
                )
                onLayerTransform?.invoke(sel.copy(eraserStrokes = sel.eraserStrokes.dropLast(if (activeEraserPoints.size > 2) 1 else 0) + previewStroke))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeEraserPoints.clear()
            }
        }
    }

    private fun snapAngle(deg: Float): Float {
        val snapTargets = floatArrayOf(0f, 90f, -90f, 180f, -180f)
        for (target in snapTargets) {
            if (abs(deg - target) <= 4f) return target
        }
        return deg
    }

    private fun snapToGuides(coord: Float, total: Float, guides: List<Float>): Float {
        val threshold = total * 0.02f
        if (abs(coord - total / 2f) < threshold) return total / 2f
        for (g in guides) {
            val pos = g * total
            if (abs(coord - pos) < threshold) return pos
        }
        return coord
    }

    private fun checkHandleHit(layer: CanvasLayer, px: Float, py: Float): DragMode {
        val dx = px - layer.x
        val dy = py - layer.y
        val rad = -layer.rotation * PI / 180.0
        val lx = (dx * cos(rad) - dy * sin(rad)).toFloat()
        val ly = (dx * sin(rad) + dy * cos(rad)).toFloat()

        val hw = layer.width / 2f + 10f
        val hh = layer.height / 2f + 10f
        val hitR = (28f / baseScale).coerceAtLeast(24f)

        if (hypot(lx - hw, ly - hh) <= hitR) return DragMode.RESIZE
        val rotY = hh + (38f / baseScale).coerceAtLeast(24f)
        if (hypot(lx, ly - rotY) <= hitR) return DragMode.ROTATE

        return DragMode.NONE
    }

    private fun hitTestLayer(px: Float, py: Float): CanvasLayer? {
        for (i in project.layers.indices.reversed()) {
            val l = project.layers[i]
            if (l.hidden) continue
            val dx = px - l.x
            val dy = py - l.y
            val rad = -l.rotation * PI / 180.0
            val lx = (dx * cos(rad) - dy * sin(rad)).toFloat()
            val ly = (dx * sin(rad) + dy * cos(rad)).toFloat()
            val pad = max(14f / baseScale, 10f)
            if (abs(lx) <= l.width / 2f + pad && abs(ly) <= l.height / 2f + pad) {
                return l
            }
        }
        return null
    }
}
