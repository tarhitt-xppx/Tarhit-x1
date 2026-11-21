package com.imaginstudio.imagetools.pixellab.model

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class SavedTextStyle(
    val id: String,
    val fontName: String = "Sans Bold",
    val bold: Boolean = true,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val letterSpacing: Float = 0f,
    val fillType: FillType = FillType.COLOR,
    val color: Int = Color.WHITE,
    val gradient: GradientFill = GradientFill(),
    val stroke: StrokeConfig = StrokeConfig(),
    val shadow: ShadowConfig = ShadowConfig(enabled = false),
    val innerShadow: ShadowConfig = ShadowConfig(enabled = false),
    val emboss: EmbossConfig = EmbossConfig(),
    val background: TextBackgroundConfig = TextBackgroundConfig(),
    val threeD: ThreeDConfig = ThreeDConfig(),
    val reflection: ReflectionConfig = ReflectionConfig()
) {
    fun applyTo(layer: TextLayer): TextLayer = layer.copy(
        fontName = fontName,
        bold = bold,
        italic = italic,
        underline = underline,
        strikethrough = strikethrough,
        letterSpacing = letterSpacing,
        fillType = fillType,
        color = color,
        gradient = gradient,
        textureUri = null,
        stroke = stroke,
        shadow = shadow,
        innerShadow = innerShadow,
        emboss = emboss,
        background = background,
        threeD = threeD,
        reflection = reflection
    )

    companion object {
        fun fromTextLayer(layer: TextLayer, id: String = "sty_${System.currentTimeMillis()}"): SavedTextStyle {
            return SavedTextStyle(
                id = id,
                fontName = layer.fontName,
                bold = layer.bold,
                italic = layer.italic,
                underline = layer.underline,
                strikethrough = layer.strikethrough,
                letterSpacing = layer.letterSpacing,
                fillType = layer.fillType,
                color = layer.color,
                gradient = layer.gradient,
                stroke = layer.stroke,
                shadow = layer.shadow,
                innerShadow = layer.innerShadow,
                emboss = layer.emboss,
                background = layer.background,
                threeD = layer.threeD,
                reflection = layer.reflection
            )
        }
    }
}

object JsonPresetCodec {
    private const val PREFS_NAME = "pixellab_presets"
    private const val KEY_CUSTOM_COLORS = "pl_colors"
    private const val KEY_CUSTOM_GRADS = "pl_grads"
    private const val FILE_MY_STYLES = "my_styles.json"

    val referenceDefaultStyles: List<SavedTextStyle> by lazy {
        listOf(
            SavedTextStyle(
                id = "def_1",
                fontName = "Sans Bold",
                bold = true,
                fillType = FillType.COLOR,
                color = Color.WHITE,
                stroke = StrokeConfig(enabled = true, color = Color.BLACK, width = 5f),
                shadow = ShadowConfig(enabled = true, color = Color.BLACK, blur = 1f, offsetX = 6f, offsetY = 6f)
            ),
            SavedTextStyle(
                id = "def_2",
                fontName = "Serif Bold",
                bold = true,
                fillType = FillType.GRADIENT,
                gradient = linearGrad(GradientDirection.LINEAR_V, "#F2A3BD", "#FDEEF3"),
                shadow = ShadowConfig(enabled = true, color = Color.argb(140, 0, 0, 0), blur = 10f, offsetX = 0f, offsetY = 5f)
            ),
            SavedTextStyle(
                id = "def_3",
                fontName = "Serif Bold",
                bold = true,
                fillType = FillType.COLOR,
                color = Color.parseColor("#BDBDBD"),
                stroke = StrokeConfig(enabled = true, color = Color.parseColor("#3A3A3A"), width = 4f),
                shadow = ShadowConfig(enabled = true, color = Color.argb(130, 0, 0, 0), blur = 4f, offsetX = 3f, offsetY = 4f)
            ),
            SavedTextStyle(
                id = "def_4",
                fontName = "Sans Condensed",
                bold = true,
                fillType = FillType.GRADIENT,
                gradient = GradientFill(
                    GradientDirection.LINEAR_V, 90f,
                    listOf(
                        GradientStop(0f, Color.parseColor("#0AA4F0")),
                        GradientStop(0.46f, Color.parseColor("#0AA4F0")),
                        GradientStop(0.56f, Color.parseColor("#DFF3E8")),
                        GradientStop(1f, Color.parseColor("#BFE3D3"))
                    )
                ),
                shadow = ShadowConfig(enabled = true, color = Color.argb(115, 0, 0, 0), blur = 8f, offsetX = 0f, offsetY = 5f)
            ),
            SavedTextStyle(
                id = "def_5",
                fontName = "Serif Bold",
                bold = true,
                fillType = FillType.GRADIENT,
                gradient = linearGrad(GradientDirection.LINEAR_V, "#9FD3EE", "#E6F4FB"),
                stroke = StrokeConfig(enabled = true, color = Color.parseColor("#E8707E"), width = 3.5f),
                shadow = ShadowConfig(enabled = true, color = Color.argb(180, 217, 79, 98), blur = 14f, offsetX = 0f, offsetY = 3f)
            ),
            SavedTextStyle(
                id = "def_6",
                fontName = "Serif Regular",
                bold = false,
                fillType = FillType.COLOR,
                color = Color.WHITE,
                background = TextBackgroundConfig(enabled = true, color = Color.parseColor("#222222"), radius = 8f, padLeft = 18f, padRight = 18f)
            ),
            SavedTextStyle(
                id = "def_7",
                fontName = "Serif Bold",
                bold = true,
                fillType = FillType.GRADIENT,
                gradient = linearGrad(GradientDirection.LINEAR_V, "#FF5D4D", "#D8392E"),
                stroke = StrokeConfig(enabled = true, color = Color.parseColor("#F3D4CC"), width = 3f),
                shadow = ShadowConfig(enabled = true, color = Color.argb(180, 0, 0, 0), blur = 7f, offsetX = 3f, offsetY = 6f)
            ),
            SavedTextStyle(
                id = "def_8",
                fontName = "Serif Bold",
                bold = true,
                italic = true,
                fillType = FillType.GRADIENT,
                gradient = GradientFill(
                    GradientDirection.LINEAR_V, 90f,
                    listOf(
                        GradientStop(0f, Color.parseColor("#7D7D7D")),
                        GradientStop(0.5f, Color.parseColor("#CFCFCF")),
                        GradientStop(1f, Color.parseColor("#9A9A9A"))
                    )
                ),
                shadow = ShadowConfig(enabled = true, color = Color.argb(130, 0, 0, 0), blur = 8f, offsetX = 0f, offsetY = 5f)
            ),
            SavedTextStyle(
                id = "def_9",
                fontName = "Sans Condensed",
                bold = true,
                fillType = FillType.COLOR,
                color = Color.WHITE,
                stroke = StrokeConfig(enabled = true, color = Color.BLACK, width = 4.5f),
                shadow = ShadowConfig(enabled = true, color = Color.argb(150, 0, 0, 0), blur = 10f, offsetX = 0f, offsetY = 4f)
            ),
            SavedTextStyle(
                id = "def_10",
                fontName = "Sans Bold",
                bold = true,
                letterSpacing = 0.08f,
                fillType = FillType.GRADIENT,
                gradient = linearGrad(GradientDirection.LINEAR_V, "#C4A393", "#ECD6CB"),
                stroke = StrokeConfig(enabled = true, color = Color.parseColor("#F1E4DD"), width = 2.5f),
                shadow = ShadowConfig(enabled = true, color = Color.argb(130, 0, 0, 0), blur = 10f, offsetX = 0f, offsetY = 5f)
            )
        )
    }

    private fun linearGrad(dir: GradientDirection, startHex: String, endHex: String): GradientFill {
        return GradientFill(
            type = dir,
            angle = if (dir == GradientDirection.LINEAR_H) 90f else 180f,
            stops = listOf(GradientStop(0f, Color.parseColor(startHex)), GradientStop(1f, Color.parseColor(endHex)))
        )
    }

    fun colorToHex(color: Int): String {
        val a = Color.alpha(color)
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return if (a >= 255) {
            String.format(Locale.US, "#%02x%02x%02x", r, g, b)
        } else {
            String.format(Locale.US, "#%02x%02x%02x%02x", r, g, b, a)
        }
    }

    fun parseHexColor(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().removePrefix("#")
        return runCatching {
            when (s.length) {
                3 -> {
                    val r = "${s[0]}${s[0]}".toInt(16)
                    val g = "${s[1]}${s[1]}".toInt(16)
                    val b = "${s[2]}${s[2]}".toInt(16)
                    Color.rgb(r, g, b)
                }
                6 -> Color.parseColor("#$s")
                8 -> {
                    // Support #RRGGBBAA (CSS from reference HTML) and #AARRGGBB gracefully
                    // In CSS / reference HTML, 8-char hex is #RRGGBBAA
                    val r = s.substring(0, 2).toInt(16)
                    val g = s.substring(2, 4).toInt(16)
                    val b = s.substring(4, 6).toInt(16)
                    val a = s.substring(6, 8).toInt(16)
                    Color.argb(a, r, g, b)
                }
                else -> null
            }
        }.getOrNull()
    }

    fun gradToWebJson(g: GradientFill): JSONObject {
        val obj = JSONObject()
        obj.put("type", if (g.type == GradientDirection.RADIAL) "radial" else "linear")
        val angle = when (g.type) {
            GradientDirection.LINEAR_H -> 90.0
            GradientDirection.LINEAR_V -> 180.0
            GradientDirection.LINEAR_DIAG -> 135.0
            GradientDirection.RADIAL -> 0.0
        }
        obj.put("angle", if (g.angle > 0f) g.angle.toDouble() else angle)
        val stops = JSONArray()
        for (st in g.stops) {
            stops.put(JSONObject().apply {
                put("pos", (st.offset.coerceIn(0f, 1f) * 100f).toInt())
                put("color", colorToHex(st.color))
            })
        }
        obj.put("stops", stops)
        return obj
    }

    fun parseWebOrNativeGrad(obj: JSONObject?): GradientFill? {
        if (obj == null) return null
        // Check native format first if stops use "o" & numeric "c"
        val stopsArr = obj.optJSONArray("stops") ?: return null
        val stops = mutableListOf<GradientStop>()
        for (i in 0 until stopsArr.length()) {
            val sj = stopsArr.optJSONObject(i) ?: continue
            val offset = if (sj.has("pos")) {
                (sj.optDouble("pos", 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
            } else {
                sj.optDouble("o", 0.0).toFloat().coerceIn(0f, 1f)
            }
            val colorVal = when {
                sj.has("color") && sj.opt("color") is String -> parseHexColor(sj.optString("color"))
                sj.has("color") && sj.opt("color") is Int -> sj.optInt("color")
                sj.has("c") && sj.opt("c") is Int -> sj.optInt("c")
                sj.has("c") && sj.opt("c") is String -> parseHexColor(sj.optString("c"))
                else -> null
            } ?: continue
            stops.add(GradientStop(offset, colorVal))
        }
        if (stops.size < 2) return null

        val typeStr = obj.optString("type", "linear").lowercase(Locale.ROOT)
        val angle = obj.optDouble("angle", 180.0).toFloat()
        val dir = when {
            typeStr == "radial" -> GradientDirection.RADIAL
            typeStr.contains("linear_h") -> GradientDirection.LINEAR_H
            typeStr.contains("linear_diag") -> GradientDirection.LINEAR_DIAG
            typeStr.contains("linear_v") -> GradientDirection.LINEAR_V
            else -> {
                val norm = ((angle % 360f) + 360f) % 360f
                when {
                    norm in 65f..115f || norm in 245f..295f -> GradientDirection.LINEAR_H
                    norm in 20f..64f || norm in 116f..160f || norm in 200f..244f || norm in 296f..340f -> GradientDirection.LINEAR_DIAG
                    else -> GradientDirection.LINEAR_V
                }
            }
        }
        return GradientFill(type = dir, angle = angle, stops = stops.sortedBy { it.offset })
    }

    // ---- Custom Colors & Gradients Persistence & JSON ----

    fun loadCustomColors(context: Context): List<Int> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_COLORS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                when (val v = arr.opt(i)) {
                    is Int -> v
                    is String -> parseHexColor(v)
                    else -> null
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    fun saveCustomColor(context: Context, color: Int) {
        val current = loadCustomColors(context).toMutableList()
        if (!current.contains(color) && !AppDefaults.defaultColors.contains(color)) {
            current.add(color)
            val arr = JSONArray().apply { current.forEach { put(colorToHex(it)) } }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_CUSTOM_COLORS, arr.toString()).apply()
        }
    }

    fun loadCustomGradients(context: Context): List<GradientFill> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_GRADS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> parseWebOrNativeGrad(arr.optJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    fun saveCustomGradient(context: Context, gradient: GradientFill) {
        val current = loadCustomGradients(context).toMutableList()
        val key = gradToWebJson(gradient).toString()
        val exists = (AppDefaults.defaultGradients + current).any { gradToWebJson(it).toString() == key }
        if (!exists) {
            current.add(gradient)
            val arr = JSONArray().apply { current.forEach { put(gradToWebJson(it)) } }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_CUSTOM_GRADS, arr.toString()).apply()
        }
    }

    fun exportColorsAndGradientsJson(
        context: Context,
        activeFillColor: Int?,
        activeGradient: GradientFill?
    ): String {
        val colorsOut = loadCustomColors(context).toMutableList()
        if (activeFillColor != null && !colorsOut.contains(activeFillColor)) {
            colorsOut.add(activeFillColor)
        }
        val gradsOut = loadCustomGradients(context).toMutableList()
        if (activeGradient != null) {
            val gStr = gradToWebJson(activeGradient).toString()
            if (gradsOut.none { gradToWebJson(it).toString() == gStr }) {
                gradsOut.add(activeGradient)
            }
        }
        if (gradsOut.isEmpty() && colorsOut.isEmpty()) {
            gradsOut.addAll(AppDefaults.defaultGradients.take(4))
        }

        val root = JSONObject().apply {
            put("app", "pixellab")
            put("type", "gradients")
            put("version", 1)
            put("colors", JSONArray().apply { colorsOut.forEach { put(colorToHex(it)) } })
            put("gradients", JSONArray().apply { gradsOut.forEach { put(gradToWebJson(it)) } })
        }
        return root.toString(2)
    }

    data class ImportedPalette(val colors: List<Int>, val gradients: List<GradientFill>)

    fun importColorsAndGradientsJson(context: Context, rawJson: String): ImportedPalette {
        val trimmed = rawJson.trim()
        val importedColors = mutableListOf<Int>()
        val importedGrads = mutableListOf<GradientFill>()

        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                when (val item = arr.opt(i)) {
                    is String -> parseHexColor(item)?.let { importedColors.add(it) }
                    is JSONObject -> {
                        val candidate = if (item.has("g") && !item.has("stops")) item.optJSONObject("g") else item
                        parseWebOrNativeGrad(candidate)?.let { importedGrads.add(it) }
                    }
                }
            }
        } else {
            val obj = JSONObject(trimmed)
            obj.optJSONArray("colors")?.let { cArr ->
                for (i in 0 until cArr.length()) {
                    when (val item = cArr.opt(i)) {
                        is String -> parseHexColor(item)?.let { importedColors.add(it) }
                        is Int -> importedColors.add(item)
                    }
                }
            }
            val gArr = obj.optJSONArray("gradients")
            if (gArr != null) {
                for (i in 0 until gArr.length()) {
                    val item = gArr.optJSONObject(i) ?: continue
                    val candidate = if (item.has("g") && !item.has("stops")) item.optJSONObject("g") else item
                    parseWebOrNativeGrad(candidate)?.let { importedGrads.add(it) }
                }
            } else if (obj.has("g")) {
                parseWebOrNativeGrad(obj.optJSONObject("g"))?.let { importedGrads.add(it) }
            } else if (obj.has("stops")) {
                parseWebOrNativeGrad(obj)?.let { importedGrads.add(it) }
            }
        }

        importedColors.forEach { saveCustomColor(context, it) }
        importedGrads.forEach { saveCustomGradient(context, it) }
        return ImportedPalette(importedColors, importedGrads)
    }

    // ---- Saved Text Styles ("My Styles") Persistence & JSON ----

    fun loadMyStyles(context: Context): List<SavedTextStyle> {
        val file = File(context.filesDir, FILE_MY_STYLES)
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONObject(file.readText()).optJSONArray("styles") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i -> parseStyleJson(arr.optJSONObject(i), "sty_$i") }
        }.getOrDefault(emptyList())
    }

    fun saveMyStyles(context: Context, styles: List<SavedTextStyle>) {
        val root = JSONObject().apply {
            put("app", "pixellab")
            put("type", "text-styles")
            put("version", 1)
            put("styles", JSONArray().apply { styles.forEach { put(styleToJson(it)) } })
        }
        File(context.filesDir, FILE_MY_STYLES).writeText(root.toString(2))
    }

    fun appendMyStyle(context: Context, style: SavedTextStyle): List<SavedTextStyle> {
        val updated = loadMyStyles(context) + style
        saveMyStyles(context, updated)
        return updated
    }

    fun exportStylesJson(styles: List<SavedTextStyle>): String {
        val root = JSONObject().apply {
            put("app", "pixellab")
            put("type", "text-styles")
            put("version", 1)
            put("styles", JSONArray().apply { styles.forEach { put(styleToJson(it)) } })
        }
        return root.toString(2)
    }

    fun importStylesJson(rawJson: String): List<SavedTextStyle> {
        val trimmed = rawJson.trim()
        val out = mutableListOf<SavedTextStyle>()
        val baseId = System.currentTimeMillis()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                parseStyleJson(arr.optJSONObject(i), "imp_${baseId}_$i")?.let { out.add(it) }
            }
        } else {
            val obj = JSONObject(trimmed)
            val arr = obj.optJSONArray("styles")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    parseStyleJson(arr.optJSONObject(i), "imp_${baseId}_$i")?.let { out.add(it) }
                }
            } else {
                parseStyleJson(obj, "imp_${baseId}_0")?.let { out.add(it) }
            }
        }
        return out
    }

    private fun styleToJson(s: SavedTextStyle): JSONObject {
        val obj = JSONObject()
        obj.put("id", s.id)
        // Reference HTML compatible fields
        obj.put("font", mapFontToShort(s.fontName))
        obj.put("fontKey", s.fontName)
        obj.put("bold", if (s.bold) 1 else 0)
        obj.put("italic", if (s.italic) 1 else 0)
        obj.put("underline", if (s.underline) 1 else 0)
        obj.put("strike", if (s.strikethrough) 1 else 0)
        obj.put("ls", (s.letterSpacing * 100f).toDouble())

        val fillJson = JSONObject()
        if (s.fillType == FillType.GRADIENT) {
            fillJson.put("kind", "gradient")
            fillJson.put("g", gradToWebJson(s.gradient))
        } else {
            fillJson.put("kind", "color")
            fillJson.put("color", colorToHex(s.color))
        }
        obj.put("fill", fillJson)

        if (s.stroke.enabled) {
            obj.put("ol", JSONObject().apply {
                put("w", (s.stroke.width / 100f).coerceIn(0.01f, 0.35f).toDouble())
                put("widthPx", s.stroke.width.toDouble())
                put("color", colorToHex(s.stroke.color))
            })
        }
        if (s.shadow.enabled) {
            obj.put("sh", JSONObject().apply {
                put("on", 1)
                put("em", 1)
                put("dx", (s.shadow.offsetX / 100f).toDouble())
                put("dy", (s.shadow.offsetY / 100f).toDouble())
                put("b", (s.shadow.blur / 100f).toDouble())
                put("blurPx", s.shadow.blur.toDouble())
                put("dxPx", s.shadow.offsetX.toDouble())
                put("dyPx", s.shadow.offsetY.toDouble())
                put("a", (Color.alpha(s.shadow.color) / 255f).toDouble())
                put("color", colorToHex(Color.rgb(Color.red(s.shadow.color), Color.green(s.shadow.color), Color.blue(s.shadow.color))))
                put("glow", s.shadow.outerGlow)
            })
        }
        if (s.background.enabled) {
            obj.put("bx", JSONObject().apply {
                put("color", colorToHex(s.background.color))
                put("pad", 0.1)
                put("padL", s.background.padLeft.toDouble())
                put("padR", s.background.padRight.toDouble())
                put("radius", s.background.radius.toDouble())
            })
        }
        // Extended native PixelLab properties
        obj.put("plab", JSONObject().apply {
            put("ishEn", s.innerShadow.enabled)
            put("ishCol", s.innerShadow.color)
            put("ishBlur", s.innerShadow.blur.toDouble())
            put("ishDx", s.innerShadow.offsetX.toDouble())
            put("ishDy", s.innerShadow.offsetY.toDouble())
            put("emEn", s.emboss.enabled)
            put("emAng", s.emboss.lightAngle)
            put("emBev", s.emboss.bevel)
            put("tdEn", s.threeD.enabled)
            put("tdDepth", s.threeD.depth)
            put("tdDark", s.threeD.darken)
            put("tdAng", s.threeD.angle)
            put("tdAuto", s.threeD.colorAuto)
            put("tdCol", s.threeD.color)
            put("rfEn", s.reflection.enabled)
            put("rfOff", s.reflection.offset.toDouble())
            put("rfOp", s.reflection.opacity)
        })
        return obj
    }

    private fun parseStyleJson(obj: JSONObject?, fallbackId: String): SavedTextStyle? {
        if (obj == null) return null
        val fillObj = obj.optJSONObject("fill") ?: return null
        val fillKind = fillObj.optString("kind", "color")
        val fillType: FillType
        val color: Int
        val gradient: GradientFill
        if (fillKind == "gradient") {
            val g = parseWebOrNativeGrad(fillObj.optJSONObject("g")) ?: GradientFill()
            fillType = FillType.GRADIENT
            gradient = g
            color = g.stops.firstOrNull()?.color ?: Color.WHITE
        } else {
            fillType = FillType.COLOR
            color = parseHexColor(fillObj.optString("color")) ?: fillObj.optInt("color", Color.WHITE)
            gradient = GradientFill()
        }

        val fontRaw = obj.optString("fontKey").ifBlank { obj.optString("font", "Sans") }
        val fontName = mapShortToFont(fontRaw)
        val bold = obj.optInt("bold", 0) != 0 || obj.optBoolean("bold", false)
        val italic = obj.optInt("italic", 0) != 0 || obj.optBoolean("italic", false)
        val underline = obj.optInt("underline", 0) != 0 || obj.optBoolean("underline", false)
        val strike = obj.optInt("strike", 0) != 0 || obj.optBoolean("strike", false)
        val lsRaw = obj.optDouble("ls", 0.0).toFloat()
        val letterSpacing = if (kotlin.math.abs(lsRaw) > 1f) (lsRaw / 100f).coerceIn(-0.1f, 0.8f) else lsRaw

        val ol = obj.optJSONObject("ol")
        val stroke = if (ol != null) {
            val wPx = if (ol.has("widthPx")) ol.optDouble("widthPx", 4.0).toFloat()
            else (ol.optDouble("w", 0.04) * 100.0).toFloat().coerceIn(1f, 32f)
            val sCol = parseHexColor(ol.optString("color")) ?: Color.BLACK
            StrokeConfig(enabled = wPx > 0f, color = sCol, width = wPx)
        } else StrokeConfig()

        val sh = obj.optJSONObject("sh")
        val shadow = if (sh != null && (sh.optInt("on", 0) != 0 || sh.optBoolean("on", false))) {
            val baseCol = parseHexColor(sh.optString("color")) ?: Color.BLACK
            val aFrac = sh.optDouble("a", 0.55).toFloat().coerceIn(0f, 1f)
            val sCol = Color.argb((aFrac * 255).toInt(), Color.red(baseCol), Color.green(baseCol), Color.blue(baseCol))
            val em = sh.optInt("em", 0) != 0
            val blur = if (sh.has("blurPx")) sh.optDouble("blurPx", 8.0).toFloat()
            else if (em) (sh.optDouble("b", 0.06) * 100.0).toFloat().coerceIn(1f, 45f)
            else sh.optDouble("b", 8.0).toFloat().coerceIn(1f, 45f)
            val dx = if (sh.has("dxPx")) sh.optDouble("dxPx", 0.0).toFloat()
            else if (em) (sh.optDouble("dx", 0.0) * 100.0).toFloat() else 0f
            val dy = if (sh.has("dyPx")) sh.optDouble("dyPx", 4.0).toFloat()
            else if (em) (sh.optDouble("dy", 0.04) * 100.0).toFloat() else (blur / 3f)
            ShadowConfig(enabled = true, color = sCol, blur = blur, offsetX = dx, offsetY = dy, outerGlow = sh.optBoolean("glow", false))
        } else ShadowConfig(enabled = false)

        val bx = obj.optJSONObject("bx")
        val background = if (bx != null) {
            val bgCol = parseHexColor(bx.optString("color")) ?: Color.parseColor("#222222")
            val padL = bx.optDouble("padL", 18.0).toFloat()
            val padR = bx.optDouble("padR", 18.0).toFloat()
            val rad = bx.optDouble("radius", 10.0).toFloat()
            TextBackgroundConfig(enabled = true, color = bgCol, padLeft = padL, padRight = padR, radius = rad)
        } else TextBackgroundConfig()

        val plab = obj.optJSONObject("plab")
        val innerShadow = if (plab != null) {
            ShadowConfig(
                enabled = plab.optBoolean("ishEn", false),
                color = plab.optInt("ishCol", Color.BLACK),
                blur = plab.optDouble("ishBlur", 8.0).toFloat(),
                offsetX = plab.optDouble("ishDx", 3.0).toFloat(),
                offsetY = plab.optDouble("ishDy", 3.0).toFloat()
            )
        } else ShadowConfig(enabled = false)

        val emboss = if (plab != null) {
            EmbossConfig(
                enabled = plab.optBoolean("emEn", false),
                lightAngle = plab.optInt("emAng", 45),
                bevel = plab.optInt("emBev", 20)
            )
        } else EmbossConfig()

        val threeD = if (plab != null) {
            ThreeDConfig(
                enabled = plab.optBoolean("tdEn", false),
                depth = plab.optInt("tdDepth", 14),
                darken = plab.optInt("tdDark", 45),
                angle = plab.optInt("tdAng", 135),
                colorAuto = plab.optBoolean("tdAuto", true),
                color = plab.optInt("tdCol", Color.DKGRAY)
            )
        } else ThreeDConfig()

        val reflection = if (plab != null) {
            ReflectionConfig(
                enabled = plab.optBoolean("rfEn", false),
                offset = plab.optDouble("rfOff", 8.0).toFloat(),
                opacity = plab.optInt("rfOp", 35)
            )
        } else ReflectionConfig()

        return SavedTextStyle(
            id = obj.optString("id").ifBlank { fallbackId },
            fontName = fontName,
            bold = bold,
            italic = italic,
            underline = underline,
            strikethrough = strike,
            letterSpacing = letterSpacing,
            fillType = fillType,
            color = color,
            gradient = gradient,
            stroke = stroke,
            shadow = shadow,
            innerShadow = innerShadow,
            emboss = emboss,
            background = background,
            threeD = threeD,
            reflection = reflection
        )
    }

    private fun mapFontToShort(fontKey: String): String {
        val lower = fontKey.lowercase(Locale.ROOT)
        return when {
            lower.contains("serif") && !lower.contains("sans") -> "Serif"
            lower.contains("mono") -> "Mono"
            lower.contains("cursive") || lower.contains("script") -> "Script"
            lower.contains("condensed") || lower.contains("impact") || lower.contains("black") -> "Impact"
            else -> "Sans"
        }
    }

    private fun mapShortToFont(raw: String): String {
        return when (raw) {
            "Sans" -> "Sans Bold"
            "Serif" -> "Serif Bold"
            "Mono" -> "Monospace"
            "Impact", "Black" -> "Sans Condensed"
            "Script" -> "Cursive / Script"
            "Rounded" -> "Casual"
            "Slab" -> "Serif Bold"
            else -> raw
        }
    }
}
