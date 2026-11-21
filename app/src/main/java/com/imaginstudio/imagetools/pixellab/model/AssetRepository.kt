package com.imaginstudio.imagetools.pixellab.model

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import org.json.JSONObject
import java.util.Locale

data class FontEntry(
    val displayName: String,
    val fontKey: String,
    val category: String,
    val assetPath: String? = null,
    val systemTypeface: Typeface? = null
)

data class AssetImageItem(
    val title: String,
    val category: String,
    val thumbUri: String,
    val fullUri: String
)

data class AssetPreset(
    val id: String,
    val name: String,
    val thumbUri: String?,
    val project: ProjectState
)

data class AssetTextStyle(
    val id: String,
    val name: String,
    val thumbUri: String?,
    val template: TextLayer
)

object AssetRepository {

    private val typefaceCache = HashMap<String, Typeface>()
    private var cachedFonts: List<FontEntry>? = null
    private var cachedBackgrounds: List<AssetImageItem>? = null
    private var cachedPresets: List<AssetPreset>? = null
    private var cachedTextStyles: List<AssetTextStyle>? = null

    fun loadFonts(context: Context): List<FontEntry> {
        cachedFonts?.let { return it }
        val result = ArrayList<FontEntry>()

        val categoriesText = runCatching {
            context.assets.open("fonts/list").bufferedReader().use { it.readText().trim() }
        }.getOrNull()

        val categories = categoriesText
            ?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: listOf("Basic", "Poster", "Fancy", "non-Latin")

        for (cat in categories) {
            val files = runCatching { context.assets.list("fonts/$cat")?.toList().orEmpty() }.getOrDefault(emptyList())
            for (fn in files.sorted()) {
                val lower = fn.lowercase(Locale.ROOT)
                if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) continue
                val assetPath = "fonts/$cat/$fn"
                val cleanName = fn.substringBeforeLast(".")
                    .replace("_", " ")
                    .replace("-", " ")
                    .trim()
                result.add(
                    FontEntry(
                        displayName = cleanName.ifBlank { fn },
                        fontKey = assetPath,
                        category = cat,
                        assetPath = assetPath
                    )
                )
            }
        }

        for ((name, tf) in AppDefaults.fontMap) {
            result.add(
                FontEntry(
                    displayName = name,
                    fontKey = name,
                    category = "System",
                    assetPath = null,
                    systemTypeface = tf
                )
            )
        }

        cachedFonts = result
        return result
    }

    fun resolveTypeface(context: Context?, fontKeyOrName: String): Typeface {
        AppDefaults.fontMap[fontKeyOrName]?.let { return it }
        if (context == null) return Typeface.SANS_SERIF

        typefaceCache[fontKeyOrName]?.let { return it }

        // Direct asset path (e.g. "fonts/Poster/Blackout Midnight.ttf")
        if (fontKeyOrName.startsWith("fonts/")) {
            val loaded = runCatching { Typeface.createFromAsset(context.assets, fontKeyOrName) }.getOrNull()
            if (loaded != null) {
                typefaceCache[fontKeyOrName] = loaded
                return loaded
            }
        }

        // Match by display name or file name
        val match = loadFonts(context).firstOrNull {
            it.fontKey.equals(fontKeyOrName, ignoreCase = true) ||
                it.displayName.equals(fontKeyOrName, ignoreCase = true)
        }
        if (match?.assetPath != null) {
            val loaded = runCatching { Typeface.createFromAsset(context.assets, match.assetPath) }.getOrNull()
            if (loaded != null) {
                typefaceCache[fontKeyOrName] = loaded
                return loaded
            }
        }
        if (match?.systemTypeface != null) {
            typefaceCache[fontKeyOrName] = match.systemTypeface
            return match.systemTypeface
        }

        typefaceCache[fontKeyOrName] = Typeface.SANS_SERIF
        return Typeface.SANS_SERIF
    }

    fun loadBackgrounds(context: Context): List<AssetImageItem> {
        cachedBackgrounds?.let { return it }
        val items = ArrayList<AssetImageItem>()
        val folders = runCatching { context.assets.list("bg")?.toList().orEmpty() }.getOrDefault(emptyList())
        for (folder in folders.sorted()) {
            val files = runCatching { context.assets.list("bg/$folder")?.toList().orEmpty() }.getOrDefault(emptyList())
            val fullFile = files.firstOrNull { it.startsWith("1.") }
                ?: files.firstOrNull { !it.startsWith("thumb") }
                ?: continue
            val thumbFile = files.firstOrNull { it.startsWith("thumb") } ?: fullFile
            items.add(
                AssetImageItem(
                    title = cleanAssetTitle(folder),
                    category = "bg",
                    thumbUri = "asset://bg/$folder/$thumbFile",
                    fullUri = "asset://bg/$folder/$fullFile"
                )
            )
        }
        cachedBackgrounds = items
        return items
    }

    fun loadQuotes(context: Context, langCode: String): Map<String, List<String>> {
        val out = LinkedHashMap<String, List<String>>()
        val files = runCatching { context.assets.list("quotes/$langCode")?.toList().orEmpty() }.getOrDefault(emptyList())
        for (fn in files.sorted()) {
            val quotesList = runCatching {
                val raw = context.assets.open("quotes/$langCode/$fn").bufferedReader().use { it.readText() }
                val arr = JSONObject(raw).optJSONArray("quotes") ?: return@runCatching emptyList<String>()
                List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
            }.getOrDefault(emptyList())
            if (quotesList.isNotEmpty()) {
                out[cleanAssetTitle(fn)] = quotesList
            }
        }
        if (out.isEmpty()) {
            return if (langCode == "fr") AppDefaults.quotesFr else AppDefaults.quotesEn
        }
        return out
    }

    fun loadPresets(context: Context): List<AssetPreset> {
        cachedPresets?.let { return it }
        val list = ArrayList<AssetPreset>()
        val folders = runCatching { context.assets.list("presets")?.toList().orEmpty() }.getOrDefault(emptyList())
        for (folder in folders.sorted()) {
            val dir = "presets/$folder"
            val files = runCatching { context.assets.list(dir)?.toList().orEmpty() }.getOrDefault(emptyList())
            if (!files.contains("data.plab")) continue
            val thumb = if (files.contains("thumb")) "asset://$dir/thumb" else null

            val parsed = runCatching {
                val raw = context.assets.open("$dir/data.plab").bufferedReader().use { it.readText() }
                parsePlabProject(context, folder, JSONObject(raw))
            }.getOrNull() ?: continue

            list.add(
                AssetPreset(
                    id = folder,
                    name = cleanAssetTitle(folder),
                    thumbUri = thumb,
                    project = parsed
                )
            )
        }
        cachedPresets = list
        return list
    }

    fun loadTextStyles(context: Context): List<AssetTextStyle> {
        cachedTextStyles?.let { return it }
        val list = ArrayList<AssetTextStyle>()
        val folders = runCatching { context.assets.list("textstyles")?.toList().orEmpty() }.getOrDefault(emptyList())
        for (folder in folders.sorted()) {
            val dir = "textstyles/$folder"
            val files = runCatching { context.assets.list(dir)?.toList().orEmpty() }.getOrDefault(emptyList())
            if (!files.contains("data.plab")) continue
            val thumb = if (files.contains("thumb")) "asset://$dir/thumb" else null

            val parsed = runCatching {
                val raw = context.assets.open("$dir/data.plab").bufferedReader().use { it.readText() }
                parsePlabTextLayer(context, "style_$folder", JSONObject(raw), 1080, 1080)
            }.getOrNull() ?: continue

            list.add(
                AssetTextStyle(
                    id = folder,
                    name = "Style $folder",
                    thumbUri = thumb,
                    template = parsed
                )
            )
        }
        cachedTextStyles = list
        return list
    }

    private fun parsePlabProject(context: Context, id: String, json: JSONObject): ProjectState {
        val rawW = json.optInt("bgWidth", 1080)
        val rawH = json.optInt("bgHeight", 1080)
        val w = if (rawW < 64) 1080 else rawW.coerceIn(240, 2560)
        val h = if (rawH < 64) 1080 else rawH.coerceIn(240, 2560)

        val bgTypeInt = json.optInt("bgType", 2)
        val bgPhoto = json.optString("bgPhotoPath", "")
        val bgType = when (bgTypeInt) {
            1 -> BackgroundType.COLOR
            2 -> BackgroundType.GRADIENT
            3 -> if (bgPhoto.isNotBlank()) BackgroundType.IMAGE else BackgroundType.GRADIENT
            4 -> BackgroundType.TRANSPARENT
            else -> BackgroundType.GRADIENT
        }
        val gradType = if (json.optInt("bgGradientType", 1) == 2) GradientDirection.RADIAL else GradientDirection.LINEAR_V
        val gStart = json.optInt("bgGradientStart", Color.parseColor("#B8C6DB"))
        val gEnd = json.optInt("bgGradientEnd", Color.parseColor("#6B7B8C"))
        val bgGrad = parseV2Gradient(json.optString("bgGradientV2", "")) ?: GradientFill(
            type = gradType,
            stops = listOf(GradientStop(0f, gStart), GradientStop(1f, gEnd))
        )

        val bg = BackgroundState(
            type = bgType,
            color = json.optInt("bgColor", Color.WHITE),
            gradient = bgGrad,
            imageUri = if (bgPhoto.isNotBlank()) "asset://$bgPhoto" else null,
            imageRotation = json.optInt("bgRotation", 0)
        )

        val fx = EffectsState(
            vignetteEnabled = json.optBoolean("effectVignetteEnabled", false),
            vignetteColor = json.optInt("effectVignetteColor", Color.BLACK),
            vignetteAlpha = json.optInt("effectVignetteAlpha", 170),
            noiseEnabled = json.optBoolean("effectNoiseEnabled", false),
            noiseAlpha = json.optInt("effectNoiseAlpha", 50),
            noiseScale = json.optInt("effectNoiseVal2", 7),
            stripesEnabled = json.optBoolean("effectStripesEnabled", false),
            stripesAlpha = json.optInt("effectStripesAlpha", 90),
            stripesRepeat = json.optInt("effectStripesVal2", 10),
            brightness = json.optInt("effectBrightnessVal", 0),
            contrast = json.optInt("effectContrastVal", 0),
            hue = json.optInt("effectHueVal", 0),
            saturation = json.optInt("effectSaturationVal", 0)
        )

        val layers = ArrayList<CanvasLayer>()
        val ids = json.optJSONArray("objectsIds")
        val bundle = json.optJSONObject("objectsBundle")
        if (ids != null && bundle != null) {
            for (i in 0 until ids.length()) {
                val oid = ids.optString(i)
                val obj = bundle.optJSONObject(oid) ?: continue
                if (oid.startsWith("text")) {
                    layers.add(parsePlabTextLayer(context, oid, obj, w, h))
                }
            }
        }

        return ProjectState(
            id = "preset_$id",
            name = cleanAssetTitle(id),
            width = w,
            height = h,
            background = bg,
            effects = fx,
            layers = layers
        )
    }

    private fun parsePlabTextLayer(
        context: Context,
        id: String,
        obj: JSONObject,
        targetW: Int,
        targetH: Int
    ): TextLayer {
        val text = obj.optString("textTextString", "New Text").ifBlank { "New Text" }
        val oldW = obj.optDouble("shapeOldContainerWidth", 790.0).toFloat().coerceAtLeast(1f)
        val oldH = obj.optDouble("shapeOldContainerHeight", 790.0).toFloat().coerceAtLeast(1f)
        val scaleFactor = minOf(targetW / oldW, targetH / oldH)

        val fontObj = obj.optJSONObject("textTextFont")?.optJSONObject("interval1")
        val fontPath = fontObj?.optString("textsIntervalsFont", "").orEmpty().ifBlank { "Sans Bold" }

        val colorObj = obj.optJSONObject("textTextColor")?.optJSONObject("interval1")
        val textColor = colorObj?.optInt("textsIntervalsColor", Color.WHITE) ?: Color.WHITE

        val fillInt = obj.optInt("textTextFillType", 1)
        val fillType = if (fillInt == 2) FillType.GRADIENT else FillType.COLOR

        val gDir = if (obj.optInt("textTextGradientType", 1) == 2) GradientDirection.RADIAL else GradientDirection.LINEAR_V
        val textGrad = parseV2Gradient(obj.optString("textTextGradientV2", "")) ?: GradientFill(
            type = gDir,
            stops = listOf(
                GradientStop(0f, obj.optInt("textTextGradientStartColor", -3355444)),
                GradientStop(1f, obj.optInt("textTextGradientEndColor", -12303292))
            )
        )

        val rawSize = obj.optString("textTextSize", "79.0").toFloatOrNull() ?: 79f
        val fontSize = (rawSize * scaleFactor).coerceIn(28f, 260f)

        val rawAlign = obj.optInt("textAlignment", 17)
        val align = when {
            rawAlign == 3 -> 0
            rawAlign == 5 -> 2
            else -> 1
        }

        val isBold = (obj.optJSONObject("textBold")?.length() ?: 0) > 0
        val isItalic = (obj.optJSONObject("textItalic")?.length() ?: 0) > 0
        val isUnder = (obj.optJSONObject("textUnder")?.length() ?: 0) > 0

        val strokeEnabled = obj.optBoolean("textStrokeEnabled", false)
        val strokeWidth = (obj.optString("textStrokeWidth", "4.0").toFloatOrNull() ?: 4f) * scaleFactor
        val strokeColor = obj.optInt("textStrokeColor", Color.BLACK)

        val outerEnabled = obj.optBoolean("textOuterEnabled", false)
        val outerColor = obj.optInt("textOuterColor", Color.argb(140, 0, 0, 0))
        val outerRadius = (obj.optString("textOuterRadius", "10.0").toFloatOrNull() ?: 10f) * scaleFactor
        val outerDx = obj.optInt("textOuterDx", 0).toFloat() * scaleFactor
        val outerDy = obj.optInt("textOuterDy", 0).toFloat() * scaleFactor

        val innerEnabled = obj.optBoolean("textInnerEnabled", false)
        val innerColor = obj.optInt("textInnerColor", Color.BLACK)
        val innerRadius = (obj.optString("textInnerRadius", "3.0").toFloatOrNull() ?: 3f) * scaleFactor
        val innerDx = (obj.optString("textInnerDx", "0.0").toFloatOrNull() ?: 0f) * scaleFactor
        val innerDy = (obj.optString("textInnerDy", "0.0").toFloatOrNull() ?: 0f) * scaleFactor

        val embossEnabled = obj.optBoolean("textEmbossEnabled", false)
        val threeDEnabled = obj.optBoolean("textThreeDEnabled", false)
        val reflectionEnabled = obj.optBoolean("textReflectionEnabled", false)

        val texPath = obj.optString("textTextureImagePath", "")
        val texUri = if (texPath.isNotBlank() && !texPath.startsWith("/")) "asset://$texPath" else null

        val layer = TextLayer(
            id = id,
            name = text.take(18),
            x = targetW / 2f,
            y = targetH / 2f,
            rotation = obj.optString("textRotation", "0.0").toFloatOrNull() ?: 0f,
            rotX = obj.optDouble("textRotationX", 0.0).toFloat(),
            rotY = obj.optDouble("textRotationY", 0.0).toFloat(),
            opacity = obj.optInt("textOpacity", 100),
            text = text,
            fontName = fontPath,
            fontSize = fontSize,
            bold = isBold,
            italic = isItalic,
            underline = isUnder,
            align = align,
            letterSpacing = (obj.optDouble("textLetterSpacing", 0.0).toFloat() / 100f).coerceIn(-0.1f, 0.8f),
            curve = obj.optInt("textTextCurve", 0),
            fillType = fillType,
            color = textColor,
            gradient = textGrad,
            textureUri = texUri,
            stroke = StrokeConfig(enabled = strokeEnabled, color = strokeColor, width = strokeWidth.coerceIn(1f, 32f)),
            shadow = ShadowConfig(
                enabled = outerEnabled,
                color = outerColor,
                blur = outerRadius.coerceIn(0f, 50f),
                offsetX = outerDx,
                offsetY = outerDy,
                outerGlow = obj.optBoolean("textOuterGlowEnabled", false)
            ),
            innerShadow = ShadowConfig(
                enabled = innerEnabled,
                color = innerColor,
                blur = innerRadius.coerceIn(0f, 40f),
                offsetX = innerDx,
                offsetY = innerDy
            ),
            emboss = EmbossConfig(
                enabled = embossEnabled,
                lightAngle = obj.optInt("textEmbossLightAngle", 90),
                intensity = obj.optInt("textEmbossIntensity", 60),
                ambient = obj.optInt("textEmbossAmbient", 50),
                hardness = obj.optInt("textEmbossHardness", 40),
                bevel = obj.optInt("textEmbossBevel", 30)
            ),
            background = TextBackgroundConfig(
                enabled = obj.optBoolean("textBgEnabled", false),
                fillType = if (obj.optInt("textBgFillType", 1) == 2) FillType.GRADIENT else FillType.COLOR,
                color = obj.optInt("textBgColor", Color.parseColor("#0079FF")),
                padLeft = obj.optDouble("textBgPaddingLeft", 12.0).toFloat(),
                padRight = obj.optDouble("textBgPaddingRight", 12.0).toFloat(),
                padTop = obj.optDouble("textBgPaddingTop", 8.0).toFloat(),
                padBottom = obj.optDouble("textBgPaddingBottom", 8.0).toFloat(),
                radius = obj.optDouble("textBgBorderRadius", 0.0).toFloat()
            ),
            threeD = ThreeDConfig(
                enabled = threeDEnabled,
                depth = obj.optInt("textThreeDDepth", 25).coerceIn(1, 45),
                darken = obj.optInt("textThreeDDepthDarken", 30),
                angle = 135,
                colorAuto = obj.optBoolean("textThreeDDepthColorAutomatic", true),
                color = obj.optInt("textThreeDDepthColorFill", Color.parseColor("#1E293B"))
            ),
            reflection = ReflectionConfig(
                enabled = reflectionEnabled,
                offset = obj.optDouble("textReflectionOffset", 0.0).toFloat(),
                opacity = 40
            )
        )

        val (mw, mh) = com.imaginstudio.imagetools.pixellab.engine.PixelLabRenderer.measureTextBounds(layer, context)
        return layer.copy(width = mw, height = mh)
    }

    private fun parseV2Gradient(raw: String): GradientFill? {
        if (!raw.startsWith("v2;")) return null
        val parts = raw.split(";")
        if (parts.size < 5) return null
        val typeInt = parts[1].toIntOrNull() ?: 1
        val colors = parts[3].split("/").mapNotNull { it.toIntOrNull() }
        val positions = parts[4].split("/").mapNotNull { it.toFloatOrNull() }
        if (colors.size < 2) return null
        val stops = colors.mapIndexed { idx, col ->
            GradientStop(positions.getOrElse(idx) { idx.toFloat() / (colors.size - 1) }, col)
        }
        val dir = when (typeInt) {
            2 -> GradientDirection.LINEAR_H
            4, 5, 6 -> GradientDirection.RADIAL
            else -> GradientDirection.LINEAR_V
        }
        return GradientFill(type = dir, angle = 180f, stops = stops)
    }

    private fun cleanAssetTitle(raw: String): String {
        val stripped = raw.replace(Regex("^[0-9z]+\\."), "")
        return stripped.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}
