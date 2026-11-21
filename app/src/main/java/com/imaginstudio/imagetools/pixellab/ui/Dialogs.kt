package com.imaginstudio.imagetools.pixellab.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.imaginstudio.imagetools.pixellab.R
import com.imaginstudio.imagetools.pixellab.engine.PixelLabRenderer
import com.imaginstudio.imagetools.pixellab.model.AppDefaults
import com.imaginstudio.imagetools.pixellab.model.AssetRepository
import com.imaginstudio.imagetools.pixellab.model.AssetTextStyle
import com.imaginstudio.imagetools.pixellab.model.BackgroundType
import com.imaginstudio.imagetools.pixellab.model.EffectsState
import com.imaginstudio.imagetools.pixellab.model.GradientDirection
import com.imaginstudio.imagetools.pixellab.model.GradientFill
import com.imaginstudio.imagetools.pixellab.model.GradientStop
import com.imaginstudio.imagetools.pixellab.model.JsonPresetCodec
import com.imaginstudio.imagetools.pixellab.model.ProjectState
import com.imaginstudio.imagetools.pixellab.model.SavedTextStyle
import com.imaginstudio.imagetools.pixellab.model.TextLayer
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object Dialogs {

    fun showColorPickerDialog(
        context: Context,
        initialColor: Int,
        onConfirm: (Int) -> Unit
    ) {
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        var alpha = Color.alpha(initialColor)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 24, 36, 12)
        }

        val preview = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 110)
        }
        val hexEt = EditText(context).apply {
            hint = "#AARRGGBB or #RRGGBB"
        }

        fun currentColor(): Int = Color.HSVToColor(alpha, hsv)
        fun refreshPreview(updateHex: Boolean = true) {
            val col = currentColor()
            preview.setBackgroundColor(col)
            if (updateHex) {
                hexEt.setText(String.format("#%08X", col))
            }
        }
        refreshPreview()
        root.addView(preview)
        root.addView(hexEt)

        fun addChannel(label: String, maxVal: Int, initVal: Int, onSeek: (Int) -> Unit) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 4)
            }
            row.addView(TextView(context).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(150, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            val sb = SeekBar(context).apply {
                max = maxVal
                progress = initVal.coerceIn(0, maxVal)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) {
                            onSeek(p)
                            refreshPreview()
                        }
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) = Unit
                    override fun onStopTrackingTouch(s: SeekBar?) = Unit
                })
            }
            row.addView(sb)
            root.addView(row)
        }

        addChannel("Hue", 360, hsv[0].roundToInt()) { hsv[0] = it.toFloat() }
        addChannel("Sat", 100, (hsv[1] * 100f).roundToInt()) { hsv[1] = it / 100f }
        addChannel("Value", 100, (hsv[2] * 100f).roundToInt()) { hsv[2] = it / 100f }
        addChannel("Alpha", 255, alpha) { alpha = it }

        AlertDialog.Builder(context)
            .setTitle("Color Picker")
            .setView(root)
            .setPositiveButton(R.string.ok) { _, _ ->
                val typed = hexEt.text.toString().trim()
                val parsed = runCatching { Color.parseColor(typed) }.getOrNull()
                onConfirm(parsed ?: currentColor())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showTextEditorDialog(
        context: Context,
        initialText: String,
        onConfirm: (String) -> Unit
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 8)
        }
        val edit = EditText(context).apply {
            setText(initialText)
            minLines = 3
            maxLines = 6
            gravity = Gravity.TOP or Gravity.START
            setSelection(text.length)
        }
        val toolsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
        }
        val btnCase = Button(context).apply {
            text = "TT"
            setOnClickListener {
                val cur = edit.text.toString()
                edit.setText(if (cur == cur.uppercase()) cur.lowercase() else cur.uppercase())
                edit.setSelection(edit.text.length)
            }
        }
        val btnClear = Button(context).apply {
            text = "Clear"
            setOnClickListener { edit.setText("") }
        }
        toolsRow.addView(btnCase)
        toolsRow.addView(btnClear)
        container.addView(edit)
        container.addView(toolsRow)

        AlertDialog.Builder(context)
            .setTitle("Edit Text")
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                onConfirm(edit.text.toString().ifBlank { "New Text" })
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showFontPickerDialog(
        context: Context,
        currentFont: String,
        sampleText: String,
        onSelect: (String) -> Unit
    ) {
        val allFonts = com.imaginstudio.imagetools.pixellab.model.AssetRepository.loadFonts(context)
        val categories = listOf("All") + allFonts.map { it.category }.distinct()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        val catScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val catRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 900)
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }
        var dialog: AlertDialog? = null
        val previewLabel = sampleText.replace("\n", " ").take(24).ifBlank { "Abcdefg 123" }

        fun renderCategory(cat: String) {
            list.removeAllViews()
            val filtered = if (cat == "All") allFonts else allFonts.filter { it.category == cat }
            for (entry in filtered) {
                val selected = entry.fontKey == currentFont || entry.displayName == currentFont
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(20, 16, 20, 16)
                    setBackgroundColor(if (selected) Color.parseColor("#E0F2FE") else Color.WHITE)
                }
                val title = TextView(context).apply {
                    text = "${entry.displayName} (${entry.category})"
                    textSize = 11f
                    setTextColor(Color.parseColor("#64748B"))
                }
                val preview = TextView(context).apply {
                    text = previewLabel
                    textSize = 20f
                    typeface = com.imaginstudio.imagetools.pixellab.model.AssetRepository.resolveTypeface(context, entry.fontKey)
                    setTextColor(Color.parseColor("#0F172A"))
                }
                row.addView(title)
                row.addView(preview)
                row.setOnClickListener {
                    onSelect(entry.fontKey)
                    dialog?.dismiss()
                }
                list.addView(row)
                list.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                    setBackgroundColor(Color.parseColor("#E2E8F0"))
                })
            }
        }

        for (cat in categories) {
            val btn = Button(context).apply {
                text = cat
                textSize = 11f
                isAllCaps = false
                setOnClickListener { renderCategory(cat) }
            }
            catRow.addView(btn)
        }
        catScroll.addView(catRow)
        scroll.addView(list)
        root.addView(catScroll)
        root.addView(scroll)
        renderCategory("All")

        dialog = AlertDialog.Builder(context)
            .setTitle("Fonts (${allFonts.size})")
            .setView(root)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showGradientMakerDialog(
        context: Context,
        initial: GradientFill,
        onConfirm: (GradientFill) -> Unit
    ) {
        var dir = initial.type
        var c1 = initial.stops.firstOrNull()?.color ?: Color.parseColor("#99DAFF")
        var c2 = initial.stops.lastOrNull()?.color ?: Color.parseColor("#008080")

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 24, 36, 12)
        }

        val preview = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 140)
        }
        fun refreshPreview() {
            val gd = GradientDrawable(
                when (dir) {
                    GradientDirection.LINEAR_H -> GradientDrawable.Orientation.LEFT_RIGHT
                    GradientDirection.LINEAR_DIAG -> GradientDrawable.Orientation.TL_BR
                    else -> GradientDrawable.Orientation.TOP_BOTTOM
                },
                intArrayOf(c1, c2)
            )
            gd.cornerRadius = 16f
            preview.background = gd
        }
        refreshPreview()
        root.addView(preview)

        val dirLabel = TextView(context).apply {
            text = "Direction"
            setPadding(0, 16, 0, 6)
        }
        val dirSpinner = Spinner(context).apply {
            val dirs = GradientDirection.entries
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, dirs.map { it.name })
            setSelection(dirs.indexOf(dir).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    dir = dirs[pos]
                    refreshPreview()
                }
                override fun onNothingSelected(p: AdapterView<*>?) = Unit
            }
        }
        root.addView(dirLabel)
        root.addView(dirSpinner)

        fun buildColorRow(label: String, onPick: (Int) -> Unit): View {
            val wrap = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 14, 0, 0)
            }
            wrap.addView(TextView(context).apply { text = label })
            val hs = HorizontalScrollView(context)
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in AppDefaults.defaultColors) {
                val swatch = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(68, 68).apply { setMargins(6, 8, 6, 8) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(c)
                        setStroke(3, Color.parseColor("#CBD5E1"))
                    }
                    setOnClickListener {
                        onPick(c)
                        refreshPreview()
                    }
                }
                row.addView(swatch)
            }
            hs.addView(row)
            wrap.addView(hs)
            return wrap
        }

        root.addView(buildColorRow("Start Color") { c1 = it })
        root.addView(buildColorRow("End Color") { c2 = it })

        AlertDialog.Builder(context)
            .setTitle("Gradient Maker")
            .setView(root)
            .setPositiveButton(R.string.ok) { _, _ ->
                onConfirm(
                    GradientFill(
                        type = dir,
                        angle = if (dir == GradientDirection.LINEAR_DIAG) 135f else 180f,
                        stops = listOf(GradientStop(0f, c1), GradientStop(1f, c2))
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showImageSizeDialog(
        context: Context,
        currentW: Int,
        currentH: Int,
        onConfirm: (Int, Int) -> Unit
    ) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 24, 36, 12)
        }
        val etW = EditText(context).apply {
            hint = "Width"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentW.toString())
        }
        val etH = EditText(context).apply {
            hint = "Height"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentH.toString())
        }
        val spinner = Spinner(context).apply {
            val arr = context.resources.getStringArray(R.array.aspect_preset)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arr)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    val dims = when (pos) {
                        1 -> 1280 to 1280
                        2 -> 1024 to 1024
                        3 -> 2560 to 1440
                        4 -> 1280 to 720
                        5 -> 851 to 315
                        6 -> 2120 to 1192
                        7 -> 1500 to 500
                        else -> null
                    }
                    if (dims != null) {
                        etW.setText(dims.first.toString())
                        etH.setText(dims.second.toString())
                    }
                }
                override fun onNothingSelected(p: AdapterView<*>?) = Unit
            }
        }

        root.addView(TextView(context).apply { text = "Preset" })
        root.addView(spinner)
        root.addView(TextView(context).apply { text = "Width (px)"; setPadding(0, 12, 0, 0) })
        root.addView(etW)
        root.addView(TextView(context).apply { text = "Height (px)"; setPadding(0, 12, 0, 0) })
        root.addView(etH)

        AlertDialog.Builder(context)
            .setTitle("Image Size")
            .setView(root)
            .setPositiveButton(R.string.ok) { _, _ ->
                val nw = etW.text.toString().toIntOrNull()?.coerceIn(120, 4096) ?: currentW
                val nh = etH.text.toString().toIntOrNull()?.coerceIn(120, 4096) ?: currentH
                onConfirm(nw, nh)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showExportDialog(
        context: Context,
        project: ProjectState
    ) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 24, 36, 12)
        }

        val formatSpinner = Spinner(context).apply {
            val formats = context.resources.getStringArray(R.array.image_format)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, formats)
            setSelection(if (project.background.type == BackgroundType.TRANSPARENT) 1 else 0)
        }

        val etW = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(project.width.toString())
        }
        val etH = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(project.height.toString())
        }

        val aspect = project.width.toFloat() / project.height.toFloat().coerceAtLeast(1f)
        val qualitySpinner = Spinner(context).apply {
            val presets = context.resources.getStringArray(R.array.quality_presets)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, presets)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    val targetBase = when (pos) {
                        0 -> project.width
                        2 -> AppDefaults.DIM_PRESET_LOW
                        3 -> AppDefaults.DIM_PRESET_DEFAULT
                        4 -> AppDefaults.DIM_PRESET_HIGH
                        5 -> AppDefaults.DIM_PRESET_VERY_HIGH
                        6 -> AppDefaults.DIM_PRESET_ULTRA
                        else -> null
                    }
                    if (targetBase != null) {
                        val w = if (pos == 0) project.width else targetBase
                        val h = if (pos == 0) project.height else (targetBase / aspect).roundToInt().coerceAtLeast(64)
                        etW.setText(w.toString())
                        etH.setText(h.toString())
                    }
                }
                override fun onNothingSelected(p: AdapterView<*>?) = Unit
            }
        }

        root.addView(TextView(context).apply { text = "Image Format" })
        root.addView(formatSpinner)
        root.addView(TextView(context).apply { text = "Quality / Dimensions"; setPadding(0, 12, 0, 0) })
        root.addView(qualitySpinner)
        root.addView(TextView(context).apply { text = "Width (px)"; setPadding(0, 10, 0, 0) })
        root.addView(etW)
        root.addView(TextView(context).apply { text = "Height (px)"; setPadding(0, 8, 0, 0) })
        root.addView(etH)

        fun renderOutput(): Pair<Bitmap, Boolean> {
            val isPng = formatSpinner.selectedItemPosition == 1
            val w = etW.text.toString().toIntOrNull()?.coerceIn(64, 4096) ?: project.width
            val h = etH.text.toString().toIntOrNull()?.coerceIn(64, 4096) ?: project.height
            val bmp = PixelLabRenderer.exportToBitmap(context, project, w, h, includeBackground = true)
            if (!isPng) {
                // Flatten onto white if background was transparent
                if (project.background.type == BackgroundType.TRANSPARENT) {
                    val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(flat)
                    c.drawColor(Color.WHITE)
                    c.drawBitmap(bmp, 0f, 0f, null)
                    bmp.recycle()
                    return flat to false
                }
            }
            return bmp to isPng
        }

        AlertDialog.Builder(context)
            .setTitle("Export Image")
            .setView(root)
            .setPositiveButton("Save to Gallery") { _, _ ->
                runCatching {
                    val (bmp, isPng) = renderOutput()
                    val ok = saveBitmapToGallery(context, bmp, isPng)
                    bmp.recycle()
                    Toast.makeText(
                        context,
                        if (ok) "Saved to Pictures/PixelLab" else "Failed to save image",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure {
                    Toast.makeText(context, "Image too large for device memory", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Share") { _, _ ->
                runCatching {
                    val (bmp, isPng) = renderOutput()
                    shareBitmap(context, bmp, isPng)
                    bmp.recycle()
                }.onFailure {
                    Toast.makeText(context, "Unable to export image", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveBitmapToGallery(context: Context, bmp: Bitmap, isPng: Boolean): Boolean {
        val ext = if (isPng) "png" else "jpg"
        val mime = if (isPng) "image/png" else "image/jpeg"
        val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val fileName = "PixelLab_${System.currentTimeMillis()}.$ext"

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelLab")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(format, 95, out)
                }
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "PixelLab"
                ).apply { mkdirs() }
                val outFile = File(dir, fileName)
                FileOutputStream(outFile).use { out ->
                    bmp.compress(format, 95, out)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun shareBitmap(context: Context, bmp: Bitmap, isPng: Boolean) {
        runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val ext = if (isPng) "png" else "jpg"
            val file = File(dir, "share_${System.currentTimeMillis()}.$ext")
            FileOutputStream(file).use { out ->
                bmp.compress(if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, out)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (isPng) "image/png" else "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Design"))
        }.onFailure {
            Toast.makeText(context, "Unable to share image", Toast.LENGTH_SHORT).show()
        }
    }

    fun showSaveProjectDialog(context: Context, project: ProjectState, onSaved: (String) -> Unit) {
        val et = EditText(context).apply {
            setText(project.name)
            setSelection(text.length)
        }
        val wrap = LinearLayout(context).apply {
            setPadding(40, 20, 40, 8)
            addView(et)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.save_project)
            .setView(wrap)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = et.text.toString().trim().ifBlank { "Untitled Design" }
                val updated = project.copy(name = name, updatedAt = System.currentTimeMillis())
                val dir = File(context.filesDir, "projects").apply { mkdirs() }
                val safeName = name.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".plab"
                File(dir, safeName).writeText(updated.toJson().toString())
                onSaved(name)
                Toast.makeText(context, "Project saved: $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showProjectsDialog(context: Context, onLoadProject: (ProjectState) -> Unit) {
        val dir = File(context.filesDir, "projects").apply { mkdirs() }
        val files = dir.listFiles { f -> f.extension == "plab" }?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (files.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle(R.string.my_projects)
                .setMessage("No saved .plab projects yet. Use Save -> Save as Project first.")
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        val names = files.map { it.nameWithoutExtension }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.my_projects)
            .setItems(names) { _, which ->
                val f = files[which]
                runCatching {
                    val json = JSONObject(f.readText())
                    onLoadProject(ProjectState.fromJson(json))
                }.onFailure {
                    Toast.makeText(context, "Failed to open project", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showConfirmDialog(
        context: Context,
        title: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        val dlg = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .show()
        val teal = Color.parseColor("#009688")
        dlg.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(teal)
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(teal)
    }

    fun showStylesDialog(
        context: Context,
        project: ProjectState,
        onApplySavedStyle: (SavedTextStyle) -> Unit,
        onApplyAssetStyle: (AssetTextStyle) -> Unit,
        onRequestImportStyles: (onImported: (List<SavedTextStyle>) -> Unit) -> Unit,
        onRequestExportStyles: (List<SavedTextStyle>) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        fun dpPx(v: Int) = (v * dp).roundToInt()

        var activeTab = 0 // 0 = DEFAULT, 1 = MY STYLES
        val selectedIds = LinkedHashSet<String>()

        // Pre-render live background snapshot once for fast tile previews
        val tileW = 320
        val tileH = 128
        val cachedBgBmp = Bitmap.createBitmap(tileW, tileH, Bitmap.Config.ARGB_8888)
        val bgCanvas = android.graphics.Canvas(cachedBgBmp)
        if (project.background.type == BackgroundType.TRANSPARENT) {
            bgCanvas.drawColor(Color.parseColor("#E5E7EB"))
        } else {
            PixelLabRenderer.renderBackground(context, bgCanvas, project, tileW.toFloat(), tileH.toFloat())
        }

        fun renderSampleBitmap(sampleLayer: TextLayer): Bitmap {
            val bmp = cachedBgBmp.copy(Bitmap.Config.ARGB_8888, true)
            val c = android.graphics.Canvas(bmp)
            val previewProj = ProjectState(
                id = "sty_preview",
                width = tileW,
                height = tileH,
                effects = EffectsState(vignetteEnabled = false),
                layers = listOf(sampleLayer)
            )
            PixelLabRenderer.renderProject(context, c, previewProj, skipTransparentBg = true)
            return bmp
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Tabs row: DEFAULT | MY STYLES
        val tabsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpPx(48))
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }

        fun buildTabView(label: String): Pair<LinearLayout, View> {
            val tab = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }
            val tv = TextView(context).apply {
                text = label
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#475569"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            val line = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpPx(3))
                setBackgroundColor(Color.parseColor("#2FB2E5"))
            }
            tab.addView(tv)
            tab.addView(line)
            return tab to line
        }

        val (tabDefault, lineDefault) = buildTabView("DEFAULT")
        val (tabMine, lineMine) = buildTabView("MY STYLES")
        tabsRow.addView(tabDefault)
        tabsRow.addView(tabMine)
        root.addView(tabsRow)

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpPx(1))
            setBackgroundColor(Color.parseColor("#E2E8F0"))
        })

        // Long-press selection action bar (shown only when items are selected in MY STYLES)
        val selectionBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#E0F2FE"))
            setPadding(dpPx(12), dpPx(6), dpPx(12), dpPx(6))
            visibility = View.GONE
        }
        val tvSelectionCount = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0369A1"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnSelectAll = TextView(context).apply {
            text = "ALL"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#009688"))
            setPadding(dpPx(10), dpPx(4), dpPx(10), dpPx(4))
        }
        val btnDeleteSelected = TextView(context).apply {
            text = "DELETE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E11D48"))
            setPadding(dpPx(10), dpPx(4), dpPx(10), dpPx(4))
        }
        val btnClearSelection = TextView(context).apply {
            text = "CANCEL"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#475569"))
            setPadding(dpPx(10), dpPx(4), dpPx(4), dpPx(4))
        }
        selectionBar.addView(tvSelectionCount)
        selectionBar.addView(btnSelectAll)
        selectionBar.addView(btnDeleteSelected)
        selectionBar.addView(btnClearSelection)
        root.addView(selectionBar)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpPx(320))
        }
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpPx(12), dpPx(12), dpPx(12), dpPx(12))
        }
        scroll.addView(grid)
        root.addView(scroll)

        // Footer bar: IMPORT | EXPORT | OK
        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpPx(1))
            setBackgroundColor(Color.parseColor("#E2E8F0"))
        })
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpPx(16), dpPx(10), dpPx(16), dpPx(10))
        }
        val btnImport = TextView(context).apply {
            text = "IMPORT"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#009688"))
            setPadding(dpPx(8), dpPx(8), dpPx(14), dpPx(8))
        }
        val btnExport = TextView(context).apply {
            text = "EXPORT"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#009688"))
            setPadding(dpPx(14), dpPx(8), dpPx(14), dpPx(8))
        }
        val footerSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val btnOk = TextView(context).apply {
            text = "OK"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#009688"))
            setPadding(dpPx(14), dpPx(8), dpPx(8), dpPx(8))
        }
        footer.addView(btnImport)
        footer.addView(btnExport)
        footer.addView(footerSpacer)
        footer.addView(btnOk)
        root.addView(footer)

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .create()

        lateinit var renderGrid: () -> Unit

        fun buildTile(
            previewBmp: Bitmap,
            isSelected: Boolean,
            showDeleteBadge: Boolean,
            onTap: () -> Unit,
            onLongPress: (() -> Unit)? = null,
            onDelete: (() -> Unit)? = null
        ): FrameLayout {
            val frame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, dpPx(68), 1f).apply {
                    setMargins(dpPx(4), dpPx(4), dpPx(4), dpPx(4))
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#CBD5E1"))
                    setStroke(
                        dpPx(if (isSelected) 3 else 1),
                        Color.parseColor(if (isSelected) "#2FB2E5" else "#CBD5E1")
                    )
                }
                setPadding(dpPx(2), dpPx(2), dpPx(2), dpPx(2))
            }
            val iv = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(previewBmp)
            }
            frame.addView(iv)

            if (isSelected) {
                val badge = TextView(context).apply {
                    text = "✓"
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setBackgroundColor(Color.parseColor("#2FB2E5"))
                    layoutParams = FrameLayout.LayoutParams(dpPx(22), dpPx(22), Gravity.TOP or Gravity.END)
                }
                frame.addView(badge)
            }

            if (showDeleteBadge && onDelete != null) {
                val delBtn = TextView(context).apply {
                    text = "×"
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setBackgroundColor(Color.parseColor("#CC111111"))
                    layoutParams = FrameLayout.LayoutParams(dpPx(24), dpPx(24), Gravity.BOTTOM or Gravity.END)
                    setOnClickListener { onDelete() }
                }
                frame.addView(delBtn)
            }

            frame.setOnClickListener { onTap() }
            if (onLongPress != null) {
                frame.setOnLongClickListener {
                    onLongPress()
                    true
                }
            }
            return frame
        }

        renderGrid = {
            lineDefault.visibility = if (activeTab == 0) View.VISIBLE else View.INVISIBLE
            lineMine.visibility = if (activeTab == 1) View.VISIBLE else View.INVISIBLE

            val isMine = activeTab == 1
            btnImport.visibility = if (isMine) View.VISIBLE else View.GONE
            btnExport.visibility = if (isMine) View.VISIBLE else View.GONE
            if (!isMine) selectedIds.clear()

            selectionBar.visibility = if (isMine && selectedIds.isNotEmpty()) View.VISIBLE else View.GONE
            if (selectedIds.isNotEmpty()) {
                tvSelectionCount.text = "${selectedIds.size} selected"
            }

            grid.removeAllViews()

            if (!isMine) {
                val defaultStyles = JsonPresetCodec.referenceDefaultStyles
                val assetStyles = AssetRepository.loadTextStyles(context)
                val entries = mutableListOf<Triple<Bitmap, () -> Unit, Boolean>>()

                for (st in defaultStyles) {
                    val sample = st.applyTo(
                        TextLayer(
                            id = st.id,
                            name = "SAMPLE",
                            x = tileW / 2f,
                            y = tileH / 2f,
                            text = "SAMPLE",
                            fontSize = 46f
                        )
                    )
                    val (nw, nh) = PixelLabRenderer.measureTextBounds(sample, context)
                    val bmp = renderSampleBitmap(sample.copy(width = nw, height = nh))
                    entries.add(Triple(bmp, {
                        onApplySavedStyle(st)
                        dialog.dismiss()
                    }, false))
                }

                for (ast in assetStyles) {
                    val thumb = ast.thumbUri?.let { PixelLabRenderer.loadBitmap(context, it) }
                    val bmp = thumb ?: run {
                        val s = ast.template.copy(
                            x = tileW / 2f,
                            y = tileH / 2f,
                            text = "SAMPLE",
                            fontSize = 44f
                        )
                        val (nw, nh) = PixelLabRenderer.measureTextBounds(s, context)
                        renderSampleBitmap(s.copy(width = nw, height = nh))
                    }
                    entries.add(Triple(bmp, {
                        onApplyAssetStyle(ast)
                        dialog.dismiss()
                    }, false))
                }

                for (i in entries.indices step 2) {
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val first = entries[i]
                    row.addView(buildTile(first.first, isSelected = false, showDeleteBadge = false, onTap = first.second))
                    if (i + 1 < entries.size) {
                        val second = entries[i + 1]
                        row.addView(buildTile(second.first, isSelected = false, showDeleteBadge = false, onTap = second.second))
                    } else {
                        row.addView(View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, dpPx(68), 1f).apply {
                                setMargins(dpPx(4), dpPx(4), dpPx(4), dpPx(4))
                            }
                        })
                    }
                    grid.addView(row)
                }
            } else {
                val myStyles = JsonPresetCodec.loadMyStyles(context)
                if (myStyles.isEmpty()) {
                    grid.addView(TextView(context).apply {
                        text = "No saved styles yet.\nUse Styles -> save to save a style or tap IMPORT to load a .json file."
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#64748B"))
                        setPadding(dpPx(16), dpPx(40), dpPx(16), dpPx(40))
                    })
                } else {
                    for (i in myStyles.indices step 2) {
                        val row = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }
                        for (col in 0..1) {
                            val idx = i + col
                            if (idx < myStyles.size) {
                                val st = myStyles[idx]
                                val sample = st.applyTo(
                                    TextLayer(
                                        id = st.id,
                                        name = "SAMPLE",
                                        x = tileW / 2f,
                                        y = tileH / 2f,
                                        text = "SAMPLE",
                                        fontSize = 46f
                                    )
                                )
                                val (nw, nh) = PixelLabRenderer.measureTextBounds(sample, context)
                                val bmp = renderSampleBitmap(sample.copy(width = nw, height = nh))
                                val isSel = selectedIds.contains(st.id)
                                row.addView(
                                    buildTile(
                                        previewBmp = bmp,
                                        isSelected = isSel,
                                        showDeleteBadge = true,
                                        onTap = {
                                            if (selectedIds.isNotEmpty()) {
                                                if (!selectedIds.add(st.id)) selectedIds.remove(st.id)
                                                renderGrid()
                                            } else {
                                                onApplySavedStyle(st)
                                                dialog.dismiss()
                                            }
                                        },
                                        onLongPress = {
                                            if (!selectedIds.add(st.id)) selectedIds.remove(st.id)
                                            renderGrid()
                                        },
                                        onDelete = {
                                            selectedIds.remove(st.id)
                                            val remaining = JsonPresetCodec.loadMyStyles(context).filterNot { it.id == st.id }
                                            JsonPresetCodec.saveMyStyles(context, remaining)
                                            renderGrid()
                                        }
                                    )
                                )
                            } else {
                                row.addView(View(context).apply {
                                    layoutParams = LinearLayout.LayoutParams(0, dpPx(68), 1f).apply {
                                        setMargins(dpPx(4), dpPx(4), dpPx(4), dpPx(4))
                                    }
                                })
                            }
                        }
                        grid.addView(row)
                    }
                }
            }
        }

        tabDefault.setOnClickListener {
            activeTab = 0
            renderGrid()
        }
        tabMine.setOnClickListener {
            activeTab = 1
            renderGrid()
        }
        btnSelectAll.setOnClickListener {
            val all = JsonPresetCodec.loadMyStyles(context)
            selectedIds.clear()
            selectedIds.addAll(all.map { it.id })
            renderGrid()
        }
        btnDeleteSelected.setOnClickListener {
            if (selectedIds.isNotEmpty()) {
                val remaining = JsonPresetCodec.loadMyStyles(context).filterNot { selectedIds.contains(it.id) }
                JsonPresetCodec.saveMyStyles(context, remaining)
                selectedIds.clear()
                renderGrid()
            }
        }
        btnClearSelection.setOnClickListener {
            selectedIds.clear()
            renderGrid()
        }
        btnImport.setOnClickListener {
            onRequestImportStyles { imported ->
                if (imported.isNotEmpty()) {
                    val merged = JsonPresetCodec.loadMyStyles(context) + imported
                    JsonPresetCodec.saveMyStyles(context, merged)
                    activeTab = 1
                    renderGrid()
                    Toast.makeText(context, "Imported ${imported.size} style(s)", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnExport.setOnClickListener {
            val myStyles = JsonPresetCodec.loadMyStyles(context)
            if (myStyles.isEmpty()) {
                Toast.makeText(context, "No styles in My Styles to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val toExport = if (selectedIds.isNotEmpty()) {
                myStyles.filter { selectedIds.contains(it.id) }
            } else {
                myStyles
            }
            onRequestExportStyles(toExport)
        }
        btnOk.setOnClickListener { dialog.dismiss() }

        dialog.setOnDismissListener {
            if (!cachedBgBmp.isRecycled) cachedBgBmp.recycle()
        }

        renderGrid()
        dialog.show()
    }
}
