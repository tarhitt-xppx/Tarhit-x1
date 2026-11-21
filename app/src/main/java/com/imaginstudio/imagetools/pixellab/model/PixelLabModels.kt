package com.imaginstudio.imagetools.pixellab.model

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

enum class StudioTab { THEMES, TEXT, SHAPES, BG, EFFECTS }

enum class GradientDirection { LINEAR_V, LINEAR_H, LINEAR_DIAG, RADIAL }

enum class FillType { COLOR, GRADIENT }

enum class BoundedShapeLabel(val label: String) {
    RECT("Rectangle"),
    OVAL("Circle / Oval"),
    TRI1("Triangle"),
    TRI2("Right Triangle"),
    DIAMOND("Diamond"),
    PENT("Pentagon"),
    HEX("Hexagon"),
    OCT("Octagon"),
    STAR("Star"),
    HEART("Heart"),
    LIGHTNING("Lightning"),
    DROP("Droplet"),
    ARC("Half Arc"),
    ARROW("Arrow"),
    DARROW("Double Arrow"),
    VARROW("Vertical Arrow"),
    BUBBLE1("Speech Bubble"),
    BUBBLE2("Round Bubble"),
    BUBBLE3("Thought Bubble"),
    EXIT("Ribbon / Tag"),
    POST("Signpost"),
    CPOST("Curved Sign")
}

enum class ArrowHeadStyle { NONE, TRIANGLE, STEALTH, CIRCLE, DIAMOND }

enum class BackgroundType { TRANSPARENT, COLOR, GRADIENT, IMAGE }

data class GradientStop(val offset: Float, val color: Int)

data class GradientFill(
    val type: GradientDirection = GradientDirection.LINEAR_V,
    val angle: Float = 180f,
    val stops: List<GradientStop> = listOf(
        GradientStop(0f, Color.parseColor("#99DAFF")),
        GradientStop(1f, Color.parseColor("#008080"))
    )
)

data class ShadowConfig(
    val enabled: Boolean = false,
    val color: Int = Color.argb(120, 0, 0, 0),
    val blur: Float = 12f,
    val offsetX: Float = 0f,
    val offsetY: Float = 6f,
    val outerGlow: Boolean = false
)

data class StrokeConfig(
    val enabled: Boolean = false,
    val color: Int = Color.BLACK,
    val width: Float = 6f
)

data class EmbossConfig(
    val enabled: Boolean = false,
    val lightAngle: Int = 45,
    val intensity: Int = 50,
    val ambient: Int = 40,
    val hardness: Int = 50,
    val bevel: Int = 20
)

data class ThreeDConfig(
    val enabled: Boolean = false,
    val depth: Int = 14,
    val darken: Int = 45,
    val angle: Int = 135,
    val colorAuto: Boolean = true,
    val color: Int = Color.parseColor("#1E293B")
)

data class TextBackgroundConfig(
    val enabled: Boolean = false,
    val fillType: FillType = FillType.COLOR,
    val color: Int = Color.parseColor("#0079FF"),
    val gradient: GradientFill = GradientFill(),
    val padLeft: Float = 24f,
    val padRight: Float = 24f,
    val padTop: Float = 12f,
    val padBottom: Float = 12f,
    val radius: Float = 14f
)

data class ReflectionConfig(
    val enabled: Boolean = false,
    val offset: Float = 8f,
    val opacity: Int = 35
)

data class DrawPoint(val x: Float, val y: Float)

data class BezierAnchor(
    val x: Float,
    val y: Float,
    val ctrlInX: Float = x,
    val ctrlInY: Float = y,
    val ctrlOutX: Float = x,
    val ctrlOutY: Float = y
)

data class DrawingStroke(
    val points: List<DrawPoint>,
    val color: Int,
    val width: Float,
    val blur: Float = 0f,
    val dashed: Boolean = false,
    val neon: Boolean = false,
    val fill: Boolean = false,
    val eraser: Boolean = false
)

sealed class CanvasLayer {
    abstract val id: String
    abstract val name: String
    abstract val x: Float
    abstract val y: Float
    abstract val width: Float
    abstract val height: Float
    abstract val rotation: Float
    abstract val rotX: Float
    abstract val rotY: Float
    abstract val opacity: Int
    abstract val locked: Boolean
    abstract val hidden: Boolean

    abstract fun copyBase(
        id: String = this.id,
        name: String = this.name,
        x: Float = this.x,
        y: Float = this.y,
        width: Float = this.width,
        height: Float = this.height,
        rotation: Float = this.rotation,
        rotX: Float = this.rotX,
        rotY: Float = this.rotY,
        opacity: Int = this.opacity,
        locked: Boolean = this.locked,
        hidden: Boolean = this.hidden
    ): CanvasLayer
}

data class TextLayer(
    override val id: String,
    override val name: String,
    override val x: Float,
    override val y: Float,
    override val width: Float = 480f,
    override val height: Float = 140f,
    override val rotation: Float = 0f,
    override val rotX: Float = 0f,
    override val rotY: Float = 0f,
    override val opacity: Int = 100,
    override val locked: Boolean = false,
    override val hidden: Boolean = false,
    val text: String = "New Text",
    val fontName: String = "Sans Bold",
    val fontSize: Float = 96f,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val align: Int = 1, // 0=left, 1=center, 2=right
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 1.15f,
    val curve: Int = 0, // -100..100
    val paddingLeft: Float = 12f,
    val paddingRight: Float = 12f,
    val fillType: FillType = FillType.COLOR,
    val color: Int = Color.WHITE,
    val gradient: GradientFill = GradientFill(),
    val textureUri: String? = null,
    val stroke: StrokeConfig = StrokeConfig(),
    val shadow: ShadowConfig = ShadowConfig(enabled = true),
    val innerShadow: ShadowConfig = ShadowConfig(enabled = false, color = Color.BLACK, blur = 8f, offsetX = 3f, offsetY = 3f),
    val emboss: EmbossConfig = EmbossConfig(),
    val background: TextBackgroundConfig = TextBackgroundConfig(),
    val threeD: ThreeDConfig = ThreeDConfig(),
    val reflection: ReflectionConfig = ReflectionConfig(),
    val maskEnabled: Boolean = false,
    val maskInvert: Boolean = false,
    val maskPoints: List<DrawPoint> = emptyList(),
    val perspectiveEnabled: Boolean = false,
    val perspectivePoints: List<DrawPoint> = listOf(
        DrawPoint(0f, 0f), DrawPoint(1f, 0f), DrawPoint(1f, 1f), DrawPoint(0f, 1f)
    )
) : CanvasLayer() {
    override fun copyBase(
        id: String, name: String, x: Float, y: Float, width: Float, height: Float,
        rotation: Float, rotX: Float, rotY: Float, opacity: Int, locked: Boolean, hidden: Boolean
    ) = copy(id = id, name = name, x = x, y = y, width = width, height = height,
        rotation = rotation, rotX = rotX, rotY = rotY, opacity = opacity, locked = locked, hidden = hidden)
}

data class ShapeLayer(
    override val id: String,
    override val name: String,
    override val x: Float,
    override val y: Float,
    override val width: Float = 320f,
    override val height: Float = 320f,
    override val rotation: Float = 0f,
    override val rotX: Float = 0f,
    override val rotY: Float = 0f,
    override val opacity: Int = 100,
    override val locked: Boolean = false,
    override val hidden: Boolean = false,
    val shapeLabel: BoundedShapeLabel = BoundedShapeLabel.RECT,
    val cornerRadius: Int = 0,
    val cornerRadii: List<Float>? = null,
    val cornerEnabled: List<Boolean> = listOf(true, true, true, true),
    val cornerLocked: Boolean = true,
    val fillType: FillType = FillType.COLOR,
    val color: Int = Color.WHITE,
    val gradient: GradientFill = GradientFill(),
    val textureUri: String? = null,
    val stroke: StrokeConfig = StrokeConfig(),
    val shadow: ShadowConfig = ShadowConfig(),
    val innerShadow: ShadowConfig = ShadowConfig(enabled = false, color = Color.BLACK, blur = 8f, offsetX = 3f, offsetY = 3f),
    val emboss: EmbossConfig = EmbossConfig(),
    val threeD: ThreeDConfig = ThreeDConfig(),
    val maskEnabled: Boolean = false,
    val maskInvert: Boolean = false,
    val maskPoints: List<DrawPoint> = emptyList(),
    val perspectiveEnabled: Boolean = false,
    val perspectivePoints: List<DrawPoint> = listOf(
        DrawPoint(0f, 0f), DrawPoint(1f, 0f), DrawPoint(1f, 1f), DrawPoint(0f, 1f)
    )
) : CanvasLayer() {
    override fun copyBase(
        id: String, name: String, x: Float, y: Float, width: Float, height: Float,
        rotation: Float, rotX: Float, rotY: Float, opacity: Int, locked: Boolean, hidden: Boolean
    ) = copy(id = id, name = name, x = x, y = y, width = width, height = height,
        rotation = rotation, rotX = rotX, rotY = rotY, opacity = opacity, locked = locked, hidden = hidden)
}

data class BezierLayer(
    override val id: String,
    override val name: String,
    override val x: Float,
    override val y: Float,
    override val width: Float = 640f,
    override val height: Float = 640f,
    override val rotation: Float = 0f,
    override val rotX: Float = 0f,
    override val rotY: Float = 0f,
    override val opacity: Int = 100,
    override val locked: Boolean = false,
    override val hidden: Boolean = false,
    val anchors: List<BezierAnchor> = emptyList(),
    val closed: Boolean = false,
    val fillEnabled: Boolean = false,
    val fillType: FillType = FillType.COLOR,
    val color: Int = Color.WHITE,
    val gradient: GradientFill = GradientFill(),
    val stroke: StrokeConfig = StrokeConfig(enabled = true, color = Color.WHITE, width = 8f),
    val shadow: ShadowConfig = ShadowConfig()
) : CanvasLayer() {
    override fun copyBase(
        id: String, name: String, x: Float, y: Float, width: Float, height: Float,
        rotation: Float, rotX: Float, rotY: Float, opacity: Int, locked: Boolean, hidden: Boolean
    ) = copy(id = id, name = name, x = x, y = y, width = width, height = height,
        rotation = rotation, rotX = rotX, rotY = rotY, opacity = opacity, locked = locked, hidden = hidden)
}

data class ArrowLayer(
    override val id: String,
    override val name: String,
    override val x: Float,
    override val y: Float,
    override val width: Float = 520f,
    override val height: Float = 120f,
    override val rotation: Float = 0f,
    override val rotX: Float = 0f,
    override val rotY: Float = 0f,
    override val opacity: Int = 100,
    override val locked: Boolean = false,
    override val hidden: Boolean = false,
    val startStyle: ArrowHeadStyle = ArrowHeadStyle.NONE,
    val endStyle: ArrowHeadStyle = ArrowHeadStyle.TRIANGLE,
    val headSize: Float = 48f,
    val stemWidth: Float = 18f,
    val fillType: FillType = FillType.COLOR,
    val color: Int = Color.WHITE,
    val gradient: GradientFill = GradientFill(),
    val stroke: StrokeConfig = StrokeConfig(),
    val shadow: ShadowConfig = ShadowConfig()
) : CanvasLayer() {
    override fun copyBase(
        id: String, name: String, x: Float, y: Float, width: Float, height: Float,
        rotation: Float, rotX: Float, rotY: Float, opacity: Int, locked: Boolean, hidden: Boolean
    ) = copy(id = id, name = name, x = x, y = y, width = width, height = height,
        rotation = rotation, rotX = rotX, rotY = rotY, opacity = opacity, locked = locked, hidden = hidden)
}

data class DrawingLayer(
    override val id: String,
    override val name: String,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotation: Float = 0f,
    override val rotX: Float = 0f,
    override val rotY: Float = 0f,
    override val opacity: Int = 100,
    override val locked: Boolean = false,
    override val hidden: Boolean = false,
    val sourceWidth: Float,
    val sourceHeight: Float,
    val strokes: List<DrawingStroke>,
    val shadow: ShadowConfig = ShadowConfig()
) : CanvasLayer() {
    override fun copyBase(
        id: String, name: String, x: Float, y: Float, width: Float, height: Float,
        rotation: Float, rotX: Float, rotY: Float, opacity: Int, locked: Boolean, hidden: Boolean
    ) = copy(id = id, name = name, x = x, y = y, width = width, height = height,
        rotation = rotation, rotX = rotX, rotY = rotY, opacity = opacity, locked = locked, hidden = hidden)
}

data class ImageLayer(
    override val id: String,
    override val name: String,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotation: Float = 0f,
    override val rotX: Float = 0f,
    override val rotY: Float = 0f,
    override val opacity: Int = 100,
    override val locked: Boolean = false,
    override val hidden: Boolean = false,
    val uriString: String = "",
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val circularCrop: Boolean = false,
    val cornerRadii: List<Float> = listOf(0f, 0f, 0f, 0f),
    val cornerEnabled: List<Boolean> = listOf(true, true, true, true),
    val cornerLocked: Boolean = true,
    val colorOverlayEnabled: Boolean = false,
    val colorOverlayFillType: FillType = FillType.COLOR,
    val colorOverlay: Int = Color.parseColor("#0079FF"),
    val colorOverlayGradient: GradientFill = GradientFill(),
    val removeColorEnabled: Boolean = false,
    val removeColorTarget: Int = Color.WHITE,
    val removeColorTolerance: Int = 25,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val hue: Int = 0,
    val stroke: StrokeConfig = StrokeConfig(),
    val shadow: ShadowConfig = ShadowConfig(),
    val innerShadow: ShadowConfig = ShadowConfig(enabled = false, color = Color.BLACK, blur = 8f, offsetX = 3f, offsetY = 3f),
    val eraserStrokes: List<DrawingStroke> = emptyList(),
    val maskEnabled: Boolean = false,
    val maskInvert: Boolean = false,
    val maskPoints: List<DrawPoint> = emptyList(),
    val perspectiveEnabled: Boolean = false,
    val perspectivePoints: List<DrawPoint> = listOf(
        DrawPoint(0f, 0f), DrawPoint(1f, 0f), DrawPoint(1f, 1f), DrawPoint(0f, 1f)
    )
) : CanvasLayer() {
    override fun copyBase(
        id: String, name: String, x: Float, y: Float, width: Float, height: Float,
        rotation: Float, rotX: Float, rotY: Float, opacity: Int, locked: Boolean, hidden: Boolean
    ) = copy(id = id, name = name, x = x, y = y, width = width, height = height,
        rotation = rotation, rotX = rotX, rotY = rotY, opacity = opacity, locked = locked, hidden = hidden)
}

data class BackgroundState(
    val type: BackgroundType = BackgroundType.GRADIENT,
    val color: Int = Color.parseColor("#95A5A6"),
    val gradient: GradientFill = GradientFill(
        type = GradientDirection.RADIAL,
        stops = listOf(
            GradientStop(0f, Color.parseColor("#B8C6DB")),
            GradientStop(1f, Color.parseColor("#6B7B8C"))
        )
    ),
    val imageUri: String? = null,
    val imageRotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val circular: Boolean = false
)

data class EffectsState(
    val vignetteEnabled: Boolean = true,
    val vignetteColor: Int = Color.BLACK,
    val vignetteAlpha: Int = 110,
    val noiseEnabled: Boolean = false,
    val noiseAlpha: Int = 50,
    val noiseScale: Int = 7,
    val stripesEnabled: Boolean = false,
    val stripesAlpha: Int = 90,
    val stripesRepeat: Int = 10,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val hue: Int = 0,
    val saturation: Int = 0
)

data class GridState(
    val enabled: Boolean = false,
    val snap: Boolean = true,
    val rows: Int = 3,
    val cols: Int = 3,
    val hGuides: List<Float> = listOf(0.25f, 0.5f, 0.75f),
    val vGuides: List<Float> = listOf(0.25f, 0.5f, 0.75f)
)

data class ProjectState(
    val id: String,
    val name: String = "Untitled Design",
    val width: Int = 1280,
    val height: Int = 1280,
    val background: BackgroundState = BackgroundState(),
    val effects: EffectsState = EffectsState(),
    val layers: List<CanvasLayer> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("width", width)
        obj.put("height", height)
        obj.put("updatedAt", updatedAt)
        obj.put("bgType", background.type.name)
        obj.put("bgColor", background.color)
        obj.put("bgImageUri", background.imageUri ?: "")
        obj.put("bgRot", background.imageRotation)
        obj.put("bgFlipH", background.flipH)
        obj.put("bgFlipV", background.flipV)
        obj.put("bgCircular", background.circular)
        obj.put("bgGrad", gradToJson(background.gradient))

        val fx = JSONObject().apply {
            put("vEn", effects.vignetteEnabled)
            put("vCol", effects.vignetteColor)
            put("vAlp", effects.vignetteAlpha)
            put("nEn", effects.noiseEnabled)
            put("nAlp", effects.noiseAlpha)
            put("nScl", effects.noiseScale)
            put("sEn", effects.stripesEnabled)
            put("sAlp", effects.stripesAlpha)
            put("sRep", effects.stripesRepeat)
            put("br", effects.brightness)
            put("co", effects.contrast)
            put("hu", effects.hue)
            put("sa", effects.saturation)
        }
        obj.put("effects", fx)

        val arr = JSONArray()
        for (l in layers) {
            val lj = JSONObject()
            lj.put("id", l.id)
            lj.put("name", l.name)
            lj.put("x", l.x.toDouble())
            lj.put("y", l.y.toDouble())
            lj.put("w", l.width.toDouble())
            lj.put("h", l.height.toDouble())
            lj.put("rot", l.rotation.toDouble())
            lj.put("rotX", l.rotX.toDouble())
            lj.put("rotY", l.rotY.toDouble())
            lj.put("op", l.opacity)
            lj.put("locked", l.locked)
            lj.put("hidden", l.hidden)
            when (l) {
                is TextLayer -> {
                    lj.put("kind", "text")
                    lj.put("text", l.text)
                    lj.put("font", l.fontName)
                    lj.put("size", l.fontSize.toDouble())
                    lj.put("bold", l.bold)
                    lj.put("italic", l.italic)
                    lj.put("under", l.underline)
                    lj.put("strike", l.strikethrough)
                    lj.put("align", l.align)
                    lj.put("lSpace", l.letterSpacing.toDouble())
                    lj.put("lineSpace", l.lineSpacing.toDouble())
                    lj.put("curve", l.curve)
                    lj.put("padL", l.paddingLeft.toDouble())
                    lj.put("padR", l.paddingRight.toDouble())
                    lj.put("fillType", l.fillType.name)
                    lj.put("color", l.color)
                    lj.put("grad", gradToJson(l.gradient))
                    lj.put("tex", l.textureUri ?: "")
                    lj.put("stEn", l.stroke.enabled)
                    lj.put("stCol", l.stroke.color)
                    lj.put("stW", l.stroke.width.toDouble())
                    lj.put("shEn", l.shadow.enabled)
                    lj.put("shCol", l.shadow.color)
                    lj.put("shBlur", l.shadow.blur.toDouble())
                    lj.put("shDx", l.shadow.offsetX.toDouble())
                    lj.put("shDy", l.shadow.offsetY.toDouble())
                    lj.put("ishEn", l.innerShadow.enabled)
                    lj.put("ishCol", l.innerShadow.color)
                    lj.put("ishBlur", l.innerShadow.blur.toDouble())
                    lj.put("ishDx", l.innerShadow.offsetX.toDouble())
                    lj.put("ishDy", l.innerShadow.offsetY.toDouble())
                    lj.put("emEn", l.emboss.enabled)
                    lj.put("emAng", l.emboss.lightAngle)
                    lj.put("emBev", l.emboss.bevel)
                    lj.put("bgEn", l.background.enabled)
                    lj.put("bgCol", l.background.color)
                    lj.put("bgRad", l.background.radius.toDouble())
                    lj.put("bgPadL", l.background.padLeft.toDouble())
                    lj.put("bgPadR", l.background.padRight.toDouble())
                    lj.put("tdEn", l.threeD.enabled)
                    lj.put("tdDepth", l.threeD.depth)
                    lj.put("tdAngle", l.threeD.angle)
                    lj.put("tdDarken", l.threeD.darken)
                    lj.put("tdAuto", l.threeD.colorAuto)
                    lj.put("tdColor", l.threeD.color)
                    lj.put("rfEn", l.reflection.enabled)
                    lj.put("rfOff", l.reflection.offset.toDouble())
                    lj.put("rfOp", l.reflection.opacity)
                    lj.put("mEn", l.maskEnabled)
                    lj.put("mInv", l.maskInvert)
                    lj.put("mPts", pointsToJson(l.maskPoints))
                    lj.put("pEn", l.perspectiveEnabled)
                    lj.put("pPts", pointsToJson(l.perspectivePoints))
                }
                is ShapeLayer -> {
                    lj.put("kind", "shape")
                    lj.put("shape", l.shapeLabel.name)
                    lj.put("cr", l.cornerRadius)
                    l.cornerRadii?.let { radii -> lj.put("crList", floatsToJson(radii)) }
                    lj.put("crEn", boolsToJson(l.cornerEnabled))
                    lj.put("crLock", l.cornerLocked)
                    lj.put("fillType", l.fillType.name)
                    lj.put("color", l.color)
                    lj.put("grad", gradToJson(l.gradient))
                    lj.put("tex", l.textureUri ?: "")
                    lj.put("stEn", l.stroke.enabled)
                    lj.put("stCol", l.stroke.color)
                    lj.put("stW", l.stroke.width.toDouble())
                    lj.put("shEn", l.shadow.enabled)
                    lj.put("shCol", l.shadow.color)
                    lj.put("shBlur", l.shadow.blur.toDouble())
                    lj.put("shDx", l.shadow.offsetX.toDouble())
                    lj.put("shDy", l.shadow.offsetY.toDouble())
                    lj.put("ishEn", l.innerShadow.enabled)
                    lj.put("ishCol", l.innerShadow.color)
                    lj.put("ishBlur", l.innerShadow.blur.toDouble())
                    lj.put("ishDx", l.innerShadow.offsetX.toDouble())
                    lj.put("ishDy", l.innerShadow.offsetY.toDouble())
                    lj.put("emEn", l.emboss.enabled)
                    lj.put("emAng", l.emboss.lightAngle)
                    lj.put("emBev", l.emboss.bevel)
                    lj.put("tdEn", l.threeD.enabled)
                    lj.put("tdDepth", l.threeD.depth)
                    lj.put("tdAngle", l.threeD.angle)
                    lj.put("tdDarken", l.threeD.darken)
                    lj.put("mEn", l.maskEnabled)
                    lj.put("mInv", l.maskInvert)
                    lj.put("mPts", pointsToJson(l.maskPoints))
                    lj.put("pEn", l.perspectiveEnabled)
                    lj.put("pPts", pointsToJson(l.perspectivePoints))
                }
                is BezierLayer -> {
                    lj.put("kind", "bezier")
                    lj.put("closed", l.closed)
                    lj.put("fillEn", l.fillEnabled)
                    lj.put("fillType", l.fillType.name)
                    lj.put("color", l.color)
                    lj.put("grad", gradToJson(l.gradient))
                    lj.put("stEn", l.stroke.enabled)
                    lj.put("stCol", l.stroke.color)
                    lj.put("stW", l.stroke.width.toDouble())
                    lj.put("shEn", l.shadow.enabled)
                    lj.put("shCol", l.shadow.color)
                    lj.put("shBlur", l.shadow.blur.toDouble())
                    val ancArr = JSONArray()
                    for (a in l.anchors) {
                        ancArr.put(JSONObject().apply {
                            put("x", a.x.toDouble()); put("y", a.y.toDouble())
                            put("ix", a.ctrlInX.toDouble()); put("iy", a.ctrlInY.toDouble())
                            put("ox", a.ctrlOutX.toDouble()); put("oy", a.ctrlOutY.toDouble())
                        })
                    }
                    lj.put("anchors", ancArr)
                }
                is ArrowLayer -> {
                    lj.put("kind", "arrow")
                    lj.put("start", l.startStyle.name)
                    lj.put("end", l.endStyle.name)
                    lj.put("head", l.headSize.toDouble())
                    lj.put("stem", l.stemWidth.toDouble())
                    lj.put("fillType", l.fillType.name)
                    lj.put("color", l.color)
                    lj.put("grad", gradToJson(l.gradient))
                    lj.put("stEn", l.stroke.enabled)
                    lj.put("stCol", l.stroke.color)
                    lj.put("stW", l.stroke.width.toDouble())
                    lj.put("shEn", l.shadow.enabled)
                    lj.put("shCol", l.shadow.color)
                    lj.put("shBlur", l.shadow.blur.toDouble())
                }
                is ImageLayer -> {
                    lj.put("kind", "image")
                    lj.put("uri", l.uriString)
                    lj.put("flipH", l.flipH)
                    lj.put("flipV", l.flipV)
                    lj.put("circ", l.circularCrop)
                    lj.put("crList", floatsToJson(l.cornerRadii))
                    lj.put("crEn", boolsToJson(l.cornerEnabled))
                    lj.put("crLock", l.cornerLocked)
                    lj.put("coEn", l.colorOverlayEnabled)
                    lj.put("coFillType", l.colorOverlayFillType.name)
                    lj.put("coCol", l.colorOverlay)
                    lj.put("coGrad", gradToJson(l.colorOverlayGradient))
                    lj.put("rcEn", l.removeColorEnabled)
                    lj.put("rcTarget", l.removeColorTarget)
                    lj.put("rcTol", l.removeColorTolerance)
                    lj.put("br", l.brightness)
                    lj.put("ct", l.contrast)
                    lj.put("sa", l.saturation)
                    lj.put("hu", l.hue)
                    lj.put("stEn", l.stroke.enabled)
                    lj.put("stCol", l.stroke.color)
                    lj.put("stW", l.stroke.width.toDouble())
                    lj.put("shEn", l.shadow.enabled)
                    lj.put("shCol", l.shadow.color)
                    lj.put("shBlur", l.shadow.blur.toDouble())
                    lj.put("ishEn", l.innerShadow.enabled)
                    lj.put("ishCol", l.innerShadow.color)
                    lj.put("ishBlur", l.innerShadow.blur.toDouble())
                    lj.put("erStrokes", strokesToJson(l.eraserStrokes))
                    lj.put("mEn", l.maskEnabled)
                    lj.put("mInv", l.maskInvert)
                    lj.put("mPts", pointsToJson(l.maskPoints))
                    lj.put("pEn", l.perspectiveEnabled)
                    lj.put("pPts", pointsToJson(l.perspectivePoints))
                }
                is DrawingLayer -> {
                    lj.put("kind", "drawing")
                    lj.put("srcW", l.sourceWidth.toDouble())
                    lj.put("srcH", l.sourceHeight.toDouble())
                    lj.put("strokes", strokesToJson(l.strokes))
                }
            }
            arr.put(lj)
        }
        obj.put("layers", arr)
        return obj
    }

    companion object {
        private fun pointsToJson(pts: List<DrawPoint>): JSONArray {
            val arr = JSONArray()
            for (pt in pts) {
                arr.put(JSONObject().apply {
                    put("x", pt.x.toDouble())
                    put("y", pt.y.toDouble())
                })
            }
            return arr
        }

        private fun jsonToPoints(arr: JSONArray?): List<DrawPoint> {
            if (arr == null) return emptyList()
            val out = mutableListOf<DrawPoint>()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                out.add(DrawPoint(p.optDouble("x", 0.0).toFloat(), p.optDouble("y", 0.0).toFloat()))
            }
            return out
        }

        private fun strokesToJson(strokes: List<DrawingStroke>): JSONArray {
            val strokesArr = JSONArray()
            for (st in strokes) {
                val sj = JSONObject()
                sj.put("col", st.color)
                sj.put("w", st.width.toDouble())
                sj.put("bl", st.blur.toDouble())
                sj.put("da", st.dashed)
                sj.put("ne", st.neon)
                sj.put("fi", st.fill)
                sj.put("er", st.eraser)
                sj.put("pts", pointsToJson(st.points))
                strokesArr.put(sj)
            }
            return strokesArr
        }

        private fun jsonToStrokes(strokesArr: JSONArray?): List<DrawingStroke> {
            if (strokesArr == null) return emptyList()
            val strokes = mutableListOf<DrawingStroke>()
            for (sIdx in 0 until strokesArr.length()) {
                val sj = strokesArr.getJSONObject(sIdx)
                strokes.add(
                    DrawingStroke(
                        points = jsonToPoints(sj.optJSONArray("pts")),
                        color = sj.optInt("col", Color.BLACK),
                        width = sj.optDouble("w", 12.0).toFloat(),
                        blur = sj.optDouble("bl", 0.0).toFloat(),
                        dashed = sj.optBoolean("da", false),
                        neon = sj.optBoolean("ne", false),
                        fill = sj.optBoolean("fi", false),
                        eraser = sj.optBoolean("er", false)
                    )
                )
            }
            return strokes
        }

        private fun defaultPerspective(): List<DrawPoint> = listOf(
            DrawPoint(0f, 0f), DrawPoint(1f, 0f), DrawPoint(1f, 1f), DrawPoint(0f, 1f)
        )

        private fun floatsToJson(vals: List<Float>): JSONArray = JSONArray().apply {
            for (v in vals) put(v.toDouble())
        }

        private fun jsonToFloats(arr: JSONArray?, expectedSize: Int): List<Float>? {
            if (arr == null || arr.length() < expectedSize) return null
            return List(expectedSize) { i -> arr.optDouble(i, 0.0).toFloat() }
        }

        private fun boolsToJson(vals: List<Boolean>): JSONArray = JSONArray().apply {
            for (v in vals) put(v)
        }

        private fun jsonToBools(arr: JSONArray?, expectedSize: Int): List<Boolean> {
            if (arr == null || arr.length() < expectedSize) return List(expectedSize) { true }
            return List(expectedSize) { i -> arr.optBoolean(i, true) }
        }

        internal fun gradToJson(g: GradientFill): JSONObject {
            val j = JSONObject()
            j.put("type", g.type.name)
            j.put("angle", g.angle.toDouble())
            val s = JSONArray()
            for (st in g.stops) {
                s.put(JSONObject().apply {
                    put("o", st.offset.toDouble())
                    put("c", st.color)
                })
            }
            j.put("stops", s)
            return j
        }

        internal fun jsonToGrad(j: JSONObject?): GradientFill {
            if (j == null) return GradientFill()
            val t = runCatching { GradientDirection.valueOf(j.optString("type", "LINEAR_V")) }.getOrDefault(GradientDirection.LINEAR_V)
            val ang = j.optDouble("angle", 180.0).toFloat()
            val stopsArr = j.optJSONArray("stops")
            val stops = mutableListOf<GradientStop>()
            if (stopsArr != null) {
                for (i in 0 until stopsArr.length()) {
                    val sj = stopsArr.getJSONObject(i)
                    stops.add(GradientStop(sj.optDouble("o", 0.0).toFloat(), sj.optInt("c", Color.BLACK)))
                }
            }
            return GradientFill(t, ang, if (stops.size >= 2) stops else GradientFill().stops)
        }

        fun fromJson(obj: JSONObject): ProjectState {
            val bgType = runCatching { BackgroundType.valueOf(obj.optString("bgType", "GRADIENT")) }.getOrDefault(BackgroundType.GRADIENT)
            val bg = BackgroundState(
                type = bgType,
                color = obj.optInt("bgColor", Color.GRAY),
                gradient = jsonToGrad(obj.optJSONObject("bgGrad")),
                imageUri = obj.optString("bgImageUri").ifEmpty { null },
                imageRotation = obj.optInt("bgRot", 0),
                flipH = obj.optBoolean("bgFlipH", false),
                flipV = obj.optBoolean("bgFlipV", false),
                circular = obj.optBoolean("bgCircular", false)
            )
            val fxJ = obj.optJSONObject("effects")
            val fx = if (fxJ != null) EffectsState(
                vignetteEnabled = fxJ.optBoolean("vEn", false),
                vignetteColor = fxJ.optInt("vCol", Color.BLACK),
                vignetteAlpha = fxJ.optInt("vAlp", 110),
                noiseEnabled = fxJ.optBoolean("nEn", false),
                noiseAlpha = fxJ.optInt("nAlp", 50),
                noiseScale = fxJ.optInt("nScl", 7),
                stripesEnabled = fxJ.optBoolean("sEn", false),
                stripesAlpha = fxJ.optInt("sAlp", 90),
                stripesRepeat = fxJ.optInt("sRep", 10),
                brightness = fxJ.optInt("br", 0),
                contrast = fxJ.optInt("co", 0),
                hue = fxJ.optInt("hu", 0),
                saturation = fxJ.optInt("sa", 0)
            ) else EffectsState()

            val layers = mutableListOf<CanvasLayer>()
            val arr = obj.optJSONArray("layers") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val lj = arr.getJSONObject(i)
                val id = lj.optString("id", "layer_$i")
                val name = lj.optString("name", "Layer")
                val x = lj.optDouble("x", 640.0).toFloat()
                val y = lj.optDouble("y", 640.0).toFloat()
                val w = lj.optDouble("w", 320.0).toFloat()
                val h = lj.optDouble("h", 320.0).toFloat()
                val rot = lj.optDouble("rot", 0.0).toFloat()
                val rotX = lj.optDouble("rotX", 0.0).toFloat()
                val rotY = lj.optDouble("rotY", 0.0).toFloat()
                val op = lj.optInt("op", 100)
                val locked = lj.optBoolean("locked", false)
                val hidden = lj.optBoolean("hidden", false)

                when (lj.optString("kind")) {
                    "text" -> {
                        val pPts = jsonToPoints(lj.optJSONArray("pPts"))
                        layers.add(
                            TextLayer(
                                id = id, name = name, x = x, y = y, width = w, height = h,
                                rotation = rot, rotX = rotX, rotY = rotY, opacity = op, locked = locked, hidden = hidden,
                                text = lj.optString("text", "New Text"),
                                fontName = lj.optString("font", "Sans Bold"),
                                fontSize = lj.optDouble("size", 96.0).toFloat(),
                                bold = lj.optBoolean("bold", true),
                                italic = lj.optBoolean("italic", false),
                                underline = lj.optBoolean("under", false),
                                strikethrough = lj.optBoolean("strike", false),
                                align = lj.optInt("align", 1),
                                letterSpacing = lj.optDouble("lSpace", 0.0).toFloat(),
                                lineSpacing = lj.optDouble("lineSpace", 1.15).toFloat(),
                                curve = lj.optInt("curve", 0),
                                paddingLeft = lj.optDouble("padL", 12.0).toFloat(),
                                paddingRight = lj.optDouble("padR", 12.0).toFloat(),
                                fillType = runCatching { FillType.valueOf(lj.optString("fillType", "COLOR")) }.getOrDefault(FillType.COLOR),
                                color = lj.optInt("color", Color.WHITE),
                                gradient = jsonToGrad(lj.optJSONObject("grad")),
                                textureUri = lj.optString("tex").ifEmpty { null },
                                stroke = StrokeConfig(lj.optBoolean("stEn", false), lj.optInt("stCol", Color.BLACK), lj.optDouble("stW", 6.0).toFloat()),
                                shadow = ShadowConfig(lj.optBoolean("shEn", true), lj.optInt("shCol", Color.argb(120, 0, 0, 0)), lj.optDouble("shBlur", 12.0).toFloat(), lj.optDouble("shDx", 0.0).toFloat(), lj.optDouble("shDy", 6.0).toFloat()),
                                innerShadow = ShadowConfig(lj.optBoolean("ishEn", false), lj.optInt("ishCol", Color.BLACK), lj.optDouble("ishBlur", 8.0).toFloat(), lj.optDouble("ishDx", 3.0).toFloat(), lj.optDouble("ishDy", 3.0).toFloat()),
                                emboss = EmbossConfig(enabled = lj.optBoolean("emEn", false), lightAngle = lj.optInt("emAng", 45), bevel = lj.optInt("emBev", 20)),
                                background = TextBackgroundConfig(
                                    enabled = lj.optBoolean("bgEn", false),
                                    color = lj.optInt("bgCol", Color.parseColor("#0079FF")),
                                    radius = lj.optDouble("bgRad", 14.0).toFloat(),
                                    padLeft = lj.optDouble("bgPadL", 24.0).toFloat(),
                                    padRight = lj.optDouble("bgPadR", 24.0).toFloat()
                                ),
                                threeD = ThreeDConfig(lj.optBoolean("tdEn", false), lj.optInt("tdDepth", 14), lj.optInt("tdDarken", 45), lj.optInt("tdAngle", 135), lj.optBoolean("tdAuto", true), lj.optInt("tdColor", Color.DKGRAY)),
                                reflection = ReflectionConfig(lj.optBoolean("rfEn", false), lj.optDouble("rfOff", 8.0).toFloat(), lj.optInt("rfOp", 35)),
                                maskEnabled = lj.optBoolean("mEn", false),
                                maskInvert = lj.optBoolean("mInv", false),
                                maskPoints = jsonToPoints(lj.optJSONArray("mPts")),
                                perspectiveEnabled = lj.optBoolean("pEn", false),
                                perspectivePoints = if (pPts.size == 4) pPts else defaultPerspective()
                            )
                        )
                    }
                    "shape" -> {
                        val pPts = jsonToPoints(lj.optJSONArray("pPts"))
                        layers.add(
                            ShapeLayer(
                                id = id, name = name, x = x, y = y, width = w, height = h,
                                rotation = rot, rotX = rotX, rotY = rotY, opacity = op, locked = locked, hidden = hidden,
                                shapeLabel = runCatching { BoundedShapeLabel.valueOf(lj.optString("shape", "RECT")) }.getOrDefault(BoundedShapeLabel.RECT),
                                cornerRadius = lj.optInt("cr", 0),
                                cornerRadii = jsonToFloats(lj.optJSONArray("crList"), 4),
                                cornerEnabled = jsonToBools(lj.optJSONArray("crEn"), 4),
                                cornerLocked = lj.optBoolean("crLock", true),
                                fillType = runCatching { FillType.valueOf(lj.optString("fillType", "COLOR")) }.getOrDefault(FillType.COLOR),
                                color = lj.optInt("color", Color.WHITE),
                                gradient = jsonToGrad(lj.optJSONObject("grad")),
                                textureUri = lj.optString("tex").ifEmpty { null },
                                stroke = StrokeConfig(lj.optBoolean("stEn", false), lj.optInt("stCol", Color.BLACK), lj.optDouble("stW", 6.0).toFloat()),
                                shadow = ShadowConfig(lj.optBoolean("shEn", false), lj.optInt("shCol", Color.BLACK), lj.optDouble("shBlur", 12.0).toFloat(), lj.optDouble("shDx", 0.0).toFloat(), lj.optDouble("shDy", 6.0).toFloat()),
                                innerShadow = ShadowConfig(lj.optBoolean("ishEn", false), lj.optInt("ishCol", Color.BLACK), lj.optDouble("ishBlur", 8.0).toFloat(), lj.optDouble("ishDx", 3.0).toFloat(), lj.optDouble("ishDy", 3.0).toFloat()),
                                emboss = EmbossConfig(enabled = lj.optBoolean("emEn", false), lightAngle = lj.optInt("emAng", 45), bevel = lj.optInt("emBev", 20)),
                                threeD = ThreeDConfig(lj.optBoolean("tdEn", false), lj.optInt("tdDepth", 14), lj.optInt("tdDarken", 45), lj.optInt("tdAngle", 135)),
                                maskEnabled = lj.optBoolean("mEn", false),
                                maskInvert = lj.optBoolean("mInv", false),
                                maskPoints = jsonToPoints(lj.optJSONArray("mPts")),
                                perspectiveEnabled = lj.optBoolean("pEn", false),
                                perspectivePoints = if (pPts.size == 4) pPts else defaultPerspective()
                            )
                        )
                    }
                    "bezier" -> {
                        val ancArr = lj.optJSONArray("anchors") ?: JSONArray()
                        val anchors = mutableListOf<BezierAnchor>()
                        for (aIdx in 0 until ancArr.length()) {
                            val aj = ancArr.getJSONObject(aIdx)
                            val ax = aj.optDouble("x", 0.0).toFloat()
                            val ay = aj.optDouble("y", 0.0).toFloat()
                            anchors.add(
                                BezierAnchor(
                                    x = ax, y = ay,
                                    ctrlInX = aj.optDouble("ix", ax.toDouble()).toFloat(),
                                    ctrlInY = aj.optDouble("iy", ay.toDouble()).toFloat(),
                                    ctrlOutX = aj.optDouble("ox", ax.toDouble()).toFloat(),
                                    ctrlOutY = aj.optDouble("oy", ay.toDouble()).toFloat()
                                )
                            )
                        }
                        layers.add(
                            BezierLayer(
                                id = id, name = name, x = x, y = y, width = w, height = h,
                                rotation = rot, rotX = rotX, rotY = rotY, opacity = op, locked = locked, hidden = hidden,
                                anchors = anchors,
                                closed = lj.optBoolean("closed", false),
                                fillEnabled = lj.optBoolean("fillEn", false),
                                fillType = runCatching { FillType.valueOf(lj.optString("fillType", "COLOR")) }.getOrDefault(FillType.COLOR),
                                color = lj.optInt("color", Color.WHITE),
                                gradient = jsonToGrad(lj.optJSONObject("grad")),
                                stroke = StrokeConfig(lj.optBoolean("stEn", true), lj.optInt("stCol", Color.WHITE), lj.optDouble("stW", 8.0).toFloat()),
                                shadow = ShadowConfig(lj.optBoolean("shEn", false), lj.optInt("shCol", Color.BLACK), lj.optDouble("shBlur", 12.0).toFloat())
                            )
                        )
                    }
                    "arrow" -> layers.add(
                        ArrowLayer(
                            id = id, name = name, x = x, y = y, width = w, height = h,
                            rotation = rot, rotX = rotX, rotY = rotY, opacity = op, locked = locked, hidden = hidden,
                            startStyle = runCatching { ArrowHeadStyle.valueOf(lj.optString("start", "NONE")) }.getOrDefault(ArrowHeadStyle.NONE),
                            endStyle = runCatching { ArrowHeadStyle.valueOf(lj.optString("end", "TRIANGLE")) }.getOrDefault(ArrowHeadStyle.TRIANGLE),
                            headSize = lj.optDouble("head", 48.0).toFloat(),
                            stemWidth = lj.optDouble("stem", 18.0).toFloat(),
                            fillType = runCatching { FillType.valueOf(lj.optString("fillType", "COLOR")) }.getOrDefault(FillType.COLOR),
                            color = lj.optInt("color", Color.WHITE),
                            gradient = jsonToGrad(lj.optJSONObject("grad")),
                            stroke = StrokeConfig(lj.optBoolean("stEn", false), lj.optInt("stCol", Color.BLACK), lj.optDouble("stW", 6.0).toFloat()),
                            shadow = ShadowConfig(lj.optBoolean("shEn", false), lj.optInt("shCol", Color.BLACK), lj.optDouble("shBlur", 12.0).toFloat())
                        )
                    )
                    "image" -> {
                        val pPts = jsonToPoints(lj.optJSONArray("pPts"))
                        layers.add(
                            ImageLayer(
                                id = id, name = name, x = x, y = y, width = w, height = h,
                                rotation = rot, rotX = rotX, rotY = rotY, opacity = op, locked = locked, hidden = hidden,
                                uriString = lj.optString("uri", ""),
                                flipH = lj.optBoolean("flipH", false),
                                flipV = lj.optBoolean("flipV", false),
                                circularCrop = lj.optBoolean("circ", false),
                                cornerRadii = jsonToFloats(lj.optJSONArray("crList"), 4) ?: listOf(0f, 0f, 0f, 0f),
                                cornerEnabled = jsonToBools(lj.optJSONArray("crEn"), 4),
                                cornerLocked = lj.optBoolean("crLock", true),
                                colorOverlayEnabled = lj.optBoolean("coEn", false),
                                colorOverlayFillType = runCatching { FillType.valueOf(lj.optString("coFillType", "COLOR")) }.getOrDefault(FillType.COLOR),
                                colorOverlay = lj.optInt("coCol", Color.parseColor("#0079FF")),
                                colorOverlayGradient = jsonToGrad(lj.optJSONObject("coGrad")),
                                removeColorEnabled = lj.optBoolean("rcEn", false),
                                removeColorTarget = lj.optInt("rcTarget", Color.WHITE),
                                removeColorTolerance = lj.optInt("rcTol", 25),
                                brightness = lj.optInt("br", 0),
                                contrast = lj.optInt("ct", 0),
                                saturation = lj.optInt("sa", 0),
                                hue = lj.optInt("hu", 0),
                                stroke = StrokeConfig(lj.optBoolean("stEn", false), lj.optInt("stCol", Color.BLACK), lj.optDouble("stW", 6.0).toFloat()),
                                shadow = ShadowConfig(lj.optBoolean("shEn", false), lj.optInt("shCol", Color.BLACK), lj.optDouble("shBlur", 12.0).toFloat()),
                                innerShadow = ShadowConfig(lj.optBoolean("ishEn", false), lj.optInt("ishCol", Color.BLACK), lj.optDouble("ishBlur", 8.0).toFloat()),
                                eraserStrokes = jsonToStrokes(lj.optJSONArray("erStrokes")),
                                maskEnabled = lj.optBoolean("mEn", false),
                                maskInvert = lj.optBoolean("mInv", false),
                                maskPoints = jsonToPoints(lj.optJSONArray("mPts")),
                                perspectiveEnabled = lj.optBoolean("pEn", false),
                                perspectivePoints = if (pPts.size == 4) pPts else defaultPerspective()
                            )
                        )
                    }
                    "drawing" -> layers.add(
                        DrawingLayer(
                            id = id, name = name, x = x, y = y, width = w, height = h,
                            rotation = rot, rotX = rotX, rotY = rotY, opacity = op, locked = locked, hidden = hidden,
                            sourceWidth = lj.optDouble("srcW", w.toDouble()).toFloat(),
                            sourceHeight = lj.optDouble("srcH", h.toDouble()).toFloat(),
                            strokes = jsonToStrokes(lj.optJSONArray("strokes"))
                        )
                    )
                }
            }

            return ProjectState(
                id = obj.optString("id", "proj"),
                name = obj.optString("name", "Project"),
                width = obj.optInt("width", 1280),
                height = obj.optInt("height", 1280),
                background = bg,
                effects = fx,
                layers = layers,
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}
