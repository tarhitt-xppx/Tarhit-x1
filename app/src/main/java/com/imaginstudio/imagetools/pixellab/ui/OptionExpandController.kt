package com.imaginstudio.imagetools.pixellab.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.switchmaterial.SwitchMaterial
import com.imaginstudio.imagetools.pixellab.R
import com.imaginstudio.imagetools.pixellab.databinding.ActivityMainBinding
import com.imaginstudio.imagetools.pixellab.engine.PixelLabRenderer
import com.imaginstudio.imagetools.pixellab.model.AppDefaults
import com.imaginstudio.imagetools.pixellab.model.ArrowHeadStyle
import com.imaginstudio.imagetools.pixellab.model.ArrowLayer
import com.imaginstudio.imagetools.pixellab.model.AssetRepository
import com.imaginstudio.imagetools.pixellab.model.BackgroundType
import com.imaginstudio.imagetools.pixellab.model.BezierLayer
import com.imaginstudio.imagetools.pixellab.model.BoundedShapeLabel
import com.imaginstudio.imagetools.pixellab.model.CanvasLayer
import com.imaginstudio.imagetools.pixellab.model.DrawPoint
import com.imaginstudio.imagetools.pixellab.model.FillType
import com.imaginstudio.imagetools.pixellab.model.GradientDirection
import com.imaginstudio.imagetools.pixellab.model.GradientFill
import com.imaginstudio.imagetools.pixellab.model.ImageLayer
import com.imaginstudio.imagetools.pixellab.model.JsonPresetCodec
import com.imaginstudio.imagetools.pixellab.model.ProjectState
import com.imaginstudio.imagetools.pixellab.model.ShadowConfig
import com.imaginstudio.imagetools.pixellab.model.ShapeLayer
import com.imaginstudio.imagetools.pixellab.model.StrokeConfig
import com.imaginstudio.imagetools.pixellab.model.TextLayer
import com.imaginstudio.imagetools.pixellab.view.CanvasStageView
import kotlin.math.min
import kotlin.math.roundToInt

class OptionExpandController(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val canvasStage: CanvasStageView,
    private val getProject: () -> ProjectState,
    private val getSelectedLayer: () -> CanvasLayer?,
    private val pushHistory: (ProjectState) -> Unit,
    private val updateProjectLive: (ProjectState) -> Unit,
    private val updateSelectedLayerLive: (CanvasLayer) -> Unit,
    private val addLayer: (CanvasLayer) -> Unit,
    private val onPickBgImage: () -> Unit,
    private val onPickTextureImage: () -> Unit,
    private val onRequestImportJson: ((String) -> Unit) -> Unit,
    private val onRequestExportJson: (String, String) -> Unit,
    private val onToolClosed: () -> Unit
) {

    var activeToolId: String? = null
        private set

    private var snapshotBeforeEdit: ProjectState? = null
    private var snapshotSelectedId: String? = null
    private var lastEyedropperColor: Int = Color.WHITE

    private val tealColor = Color.parseColor("#009688")
    private val accentBlue = Color.parseColor("#2693B5")
    private val sheetBg = Color.parseColor("#F5F5F5")
    private val sageColBg = Color.parseColor("#8FB0AF")
    private val sageDivider = Color.parseColor("#789A99")

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).roundToInt()

    init {
        binding.btnOptionCancel.setOnClickListener {
            cancelCurrentTool()
        }

        binding.btnOptionOk.setOnClickListener {
            commitCurrentTool()
        }
    }

    private fun cancelCurrentTool() {
        if (activeToolId == "draw") {
            canvasStage.clearPendingDrawing()
        } else {
            snapshotBeforeEdit?.let { prev ->
                updateProjectLive(prev)
            }
        }
        canvasStage.toolMode = CanvasStageView.StageToolMode.NORMAL
        closePanel()
    }

    private fun commitCurrentTool() {
        if (activeToolId == "draw") {
            val drawing = canvasStage.finishDrawingLayer()
            canvasStage.toolMode = CanvasStageView.StageToolMode.NORMAL
            if (drawing != null) {
                addLayer(drawing)
            }
        } else {
            val prev = snapshotBeforeEdit
            if (prev != null && prev != getProject()) {
                pushHistory(prev)
            }
        }
        snapshotBeforeEdit = null
        closePanel()
    }

    fun isOpen(): Boolean = binding.optionExpandPanel.visibility == View.VISIBLE

    fun closePanel() {
        canvasStage.toolMode = CanvasStageView.StageToolMode.NORMAL
        canvasStage.onEyedropperColor = null
        activeToolId = null
        snapshotBeforeEdit = null
        binding.optionExpandBody.removeAllViews()
        binding.optionExpandPanel.clearAnimation()
        binding.optionExpandPanel.visibility = View.GONE
        onToolClosed()
    }

    fun openTool(toolId: String, title: String) {
        snapshotBeforeEdit = getProject()
        snapshotSelectedId = getSelectedLayer()?.id

        activeToolId = toolId
        binding.optionExpandHeader.visibility = View.GONE
        binding.optionExpandBody.setPadding(0, 0, 0, 0)
        binding.optionExpandTitle.text = title
        binding.optionExpandBody.removeAllViews()
        binding.optionExpandPanel.visibility = View.VISIBLE

        // Smooth bottom sheet entrance animation over bottomControls (matching PixelLab)
        binding.optionExpandPanel.translationY = dp(84).toFloat()
        binding.optionExpandPanel.alpha = 0f
        binding.optionExpandPanel.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(190L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        canvasStage.toolMode = when (toolId) {
            "draw" -> CanvasStageView.StageToolMode.DRAW
            "layer_perspective" -> CanvasStageView.StageToolMode.PERSPECTIVE
            "layer_mask" -> CanvasStageView.StageToolMode.MASK
            "bezier_edit" -> CanvasStageView.StageToolMode.BEZIER
            "img_eraser" -> CanvasStageView.StageToolMode.BITMAP_ERASER
            "eyedropper" -> CanvasStageView.StageToolMode.EYEDROPPER
            else -> CanvasStageView.StageToolMode.NORMAL
        }

        populateTool(toolId)
    }

    private fun buildPopupShell(
        body: LinearLayout,
        centerVertically: Boolean = true,
        buildLeft: (LinearLayout) -> Unit
    ) {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(sheetBg)
            minimumHeight = dp(132)
        }

        val leftCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (centerVertically) Gravity.CENTER_VERTICAL else Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = if (centerVertically) Gravity.CENTER_VERTICAL else Gravity.TOP
            }
            minimumHeight = dp(132)
            setPadding(dp(14), dp(10), dp(14), dp(12))
        }
        buildLeft(leftCol)

        val actionCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(54), LinearLayout.LayoutParams.MATCH_PARENT)
            minimumHeight = dp(132)
            setBackgroundColor(sageColBg)
        }
        val btnOk = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = null
            setImageResource(R.drawable.ic_check)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(13), dp(13), dp(13), dp(13))
            setOnClickListener { commitCurrentTool() }
        }
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(sageDivider)
        }
        val btnCancel = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = null
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(13), dp(13), dp(13), dp(13))
            setOnClickListener { cancelCurrentTool() }
        }
        actionCol.addView(btnOk)
        actionCol.addView(divider)
        actionCol.addView(btnCancel)

        outer.addView(leftCol)
        outer.addView(actionCol)
        body.addView(outer)
    }

    private fun populateTool(toolId: String) {
        val body = binding.optionExpandBody
        val sel = getSelectedLayer()
        val proj = getProject()

        when (toolId) {
            "text_styles" -> {
                val tl = sel as? TextLayer ?: return
                buildPopupShell(body) { left ->
                    val assetStyles = AssetRepository.loadTextStyles(context)
                    if (assetStyles.isNotEmpty()) {
                        val hs = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
                        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                        for (style in assetStyles) {
                            val thumbBmp = style.thumbUri?.let {
                                PixelLabRenderer.loadBitmap(context, it)
                            }
                            if (thumbBmp != null) {
                                val iv = ImageView(context).apply {
                                    layoutParams = LinearLayout.LayoutParams(dp(76), dp(46)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    background = cardPillDrawable(false)
                                    setPadding(dp(4), dp(4), dp(4), dp(4))
                                    setImageBitmap(thumbBmp)
                                    setOnClickListener {
                                        (getSelectedLayer() as? TextLayer)?.let { cur ->
                                            val s = style.template
                                            val applied = cur.copy(
                                                fontName = s.fontName,
                                                bold = s.bold,
                                                italic = s.italic,
                                                underline = s.underline,
                                                fillType = s.fillType,
                                                color = s.color,
                                                gradient = s.gradient,
                                                textureUri = s.textureUri,
                                                stroke = s.stroke,
                                                shadow = s.shadow,
                                                innerShadow = s.innerShadow,
                                                emboss = s.emboss,
                                                background = s.background,
                                                threeD = s.threeD,
                                                reflection = s.reflection
                                            )
                                            val (nw, nh) = PixelLabRenderer.measureTextBounds(applied, context)
                                            updateSelectedLayerLive(applied.copy(width = nw, height = nh))
                                        }
                                    }
                                }
                                row.addView(iv)
                            }
                        }
                        hs.addView(row)
                        left.addView(hs)
                    }
                    addPillButtonRow(
                        left,
                        listOf(
                            "Default" to {
                                updateSelectedLayerLive(
                                    tl.copy(
                                        fillType = FillType.COLOR, color = Color.WHITE,
                                        stroke = tl.stroke.copy(enabled = false),
                                        threeD = tl.threeD.copy(enabled = false),
                                        shadow = tl.shadow.copy(enabled = true)
                                    )
                                )
                            },
                            "Gradient Pop" to {
                                updateSelectedLayerLive(
                                    tl.copy(
                                        fillType = FillType.GRADIENT,
                                        gradient = AppDefaults.defaultGradients[8],
                                        stroke = tl.stroke.copy(enabled = true, color = Color.BLACK, width = 5f)
                                    )
                                )
                            },
                            "3D Block" to {
                                updateSelectedLayerLive(
                                    tl.copy(threeD = tl.threeD.copy(enabled = true, depth = 18, colorAuto = true))
                                )
                            },
                            "Neon" to {
                                updateSelectedLayerLive(
                                    tl.copy(
                                        fillType = FillType.COLOR,
                                        color = Color.parseColor("#F0ABFC"),
                                        shadow = tl.shadow.copy(
                                            enabled = true, color = Color.parseColor("#E879F9"),
                                            blur = 28f, offsetX = 0f, offsetY = 0f
                                        )
                                    )
                                )
                            }
                        )
                    )
                }
            }

            "position" -> {
                if (sel == null) return
                var step = 7
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "move by", 1, 60, step, { "${it}px" }) { step = it }
                    val btnRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(0, dp(8), 0, dp(2))
                    }
                    btnRow.addView(createCircleIconButton(R.drawable.ic_chevron_left) {
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(x = it.x - step)) }
                    })
                    btnRow.addView(createCircleIconButton(R.drawable.ic_chevron_right) {
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(x = it.x + step)) }
                    })
                    btnRow.addView(createCircleIconButton(R.drawable.ic_chevron_up) {
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(y = it.y - step)) }
                    })
                    btnRow.addView(createCircleIconButton(R.drawable.ic_chevron_down) {
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(y = it.y + step)) }
                    })
                    left.addView(btnRow)
                }
            }

            "rel_position" -> {
                if (sel == null) return
                buildPopupShell(body) { left ->
                    val row1 = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(0, dp(2), 0, dp(6))
                    }
                    row1.addView(createCircleIconButton(R.drawable.ic_align_edge_left) {
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(x = it.width / 2f + 24f)) }
                    })
                    row1.addView(createCircleIconButton(R.drawable.ic_align_center_h) {
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(x = proj.width / 2f)) }
                    })
                    row1.addView(createCircleIconButton(R.drawable.ic_align_edge_right) {
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(x = proj.width - it.width / 2f - 24f)) }
                    })

                    val row2 = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(0, dp(4), 0, dp(2))
                    }
                    row2.addView(createCircleIconButton(R.drawable.ic_align_edge_top) {
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(y = it.height / 2f + 24f)) }
                    })
                    row2.addView(createCircleIconButton(R.drawable.ic_align_center_v) {
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(y = proj.height / 2f)) }
                    })
                    row2.addView(createCircleIconButton(R.drawable.ic_align_edge_bottom) {
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(y = proj.height - it.height / 2f - 24f)) }
                    })

                    left.addView(row1)
                    left.addView(row2)
                }
            }

            "rel_size" -> {
                val l = sel ?: return
                val curWPct = ((l.width / proj.width.coerceAtLeast(1)) * 100f).roundToInt().coerceIn(5, 100)
                val curHPct = ((l.height / proj.height.coerceAtLeast(1)) * 100f).roundToInt().coerceIn(5, 100)
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "width", 5, 100, curWPct, { "$it%" }) { v ->
                        getSelectedLayer()?.let { cur ->
                            updateSelectedLayerLive(cur.copyBase(width = proj.width * (v / 100f)))
                        }
                    }
                    addPixelLabSlider(left, "height", 5, 100, curHPct, { "$it%" }) { v ->
                        getSelectedLayer()?.let { cur ->
                            updateSelectedLayerLive(cur.copyBase(height = proj.height * (v / 100f)))
                        }
                    }
                }
            }

            "text_size" -> {
                val tl = sel as? TextLayer ?: return
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "size", 16, 260, tl.fontSize.toInt(), { "${it}px" }) { v ->
                        (getSelectedLayer() as? TextLayer)?.let { cur ->
                            val updated = cur.copy(fontSize = v.toFloat())
                            val (nw, nh) = PixelLabRenderer.measureTextBounds(updated, context)
                            updateSelectedLayerLive(updated.copy(width = nw, height = nh))
                        }
                    }
                }
            }

            "text_padding" -> {
                val tl = sel as? TextLayer ?: return
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "left padding", 0, 100, tl.paddingLeft.toInt(), { "${it}px" }) { v ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(paddingLeft = v.toFloat())) }
                    }
                    addPixelLabSlider(left, "right padding", 0, 100, tl.paddingRight.toInt(), { "${it}px" }) { v ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(paddingRight = v.toFloat())) }
                    }
                }
            }

            "text_color", "shape_color" -> {
                openFillSheet(body, isBackground = false)
            }

            "layer_radius" -> {
                openRadiusSheet(body)
            }

            "opacity" -> {
                val l = sel ?: return
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "opacity", 0, 100, l.opacity, { "$it%" }) { v ->
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(opacity = v)) }
                    }
                }
            }

            "rotate" -> {
                val l = sel ?: return
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "angle", -180, 180, l.rotation.roundToInt(), { "${it}°" }) { v ->
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(rotation = v.toFloat())) }
                    }
                }
            }

            "3d_rotate" -> {
                val l = sel ?: return
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "x axis", -60, 60, l.rotX.roundToInt(), { "${it}°" }) { v ->
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(rotX = v.toFloat())) }
                    }
                    addPixelLabSlider(left, "y axis", -60, 60, l.rotY.roundToInt(), { "${it}°" }) { v ->
                        getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(rotY = v.toFloat())) }
                    }
                }
            }

            "text_style" -> {
                val tl = sel as? TextLayer ?: return
                buildPopupShell(body) { left ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                    fun rebuild() {
                        row.removeAllViews()
                        val cur = getSelectedLayer() as? TextLayer ?: return
                        row.addView(createCircleTextButton("B", cur.bold, Typeface.BOLD) {
                            (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(bold = !it.bold)) }
                            rebuild()
                        })
                        row.addView(createCircleTextButton("I", cur.italic, Typeface.ITALIC) {
                            (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(italic = !it.italic)) }
                            rebuild()
                        })
                        row.addView(createCircleTextButton("U", cur.underline, Typeface.NORMAL) {
                            (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(underline = !it.underline)) }
                            rebuild()
                        })
                        row.addView(createCircleTextButton("S", cur.strikethrough, Typeface.NORMAL) {
                            (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(strikethrough = !it.strikethrough)) }
                            rebuild()
                        })
                    }
                    rebuild()
                    left.addView(row)
                }
            }

            "text_curve" -> {
                val tl = sel as? TextLayer ?: return
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "bending", -100, 100, tl.curve, { "${it}°" }) { v ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(curve = v)) }
                    }
                }
            }

            "text_bg" -> {
                val tl = sel as? TextLayer ?: return
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, tl.background.enabled) { en ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(background = it.background.copy(enabled = en))) }
                    }
                    addSwatchStrip(content, "Background Color") { c ->
                        (getSelectedLayer() as? TextLayer)?.let {
                            updateSelectedLayerLive(it.copy(background = it.background.copy(enabled = true, fillType = FillType.COLOR, color = c)))
                        }
                    }
                    addPixelLabSlider(content, "corner radius", 0, 60, tl.background.radius.toInt(), { "${it}px" }) { v ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(background = it.background.copy(radius = v.toFloat()))) }
                    }
                }
            }

            "text_align" -> {
                if (sel !is TextLayer) return
                buildPopupShell(body) { left ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                    fun rebuild() {
                        row.removeAllViews()
                        val cur = getSelectedLayer() as? TextLayer ?: return
                        row.addView(createCircleIconButton(R.drawable.ic_align_edge_left, cur.align == 0) {
                            (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(align = 0)) }
                            rebuild()
                        })
                        row.addView(createCircleIconButton(R.drawable.ic_align_center_h, cur.align == 1) {
                            (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(align = 1)) }
                            rebuild()
                        })
                        row.addView(createCircleIconButton(R.drawable.ic_align_edge_right, cur.align == 2) {
                            (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(align = 2)) }
                            rebuild()
                        })
                    }
                    rebuild()
                    left.addView(row)
                }
            }

            "text_spacing" -> {
                val tl = sel as? TextLayer ?: return
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "letter spacing", -10, 80, (tl.letterSpacing * 100f).roundToInt(), { "$it" }) { v ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(letterSpacing = v / 100f)) }
                    }
                    addPixelLabSlider(left, "line spacing", 70, 220, (tl.lineSpacing * 100f).roundToInt(), { "$it" }) { v ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(lineSpacing = v / 100f)) }
                    }
                }
            }

            "text_stroke", "shape_stroke" -> {
                val st = when (sel) {
                    is TextLayer -> sel.stroke
                    is ShapeLayer -> sel.stroke
                    is ArrowLayer -> sel.stroke
                    is ImageLayer -> sel.stroke
                    else -> return
                }
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, st.enabled) { en ->
                        updateStroke { it.copy(enabled = en) }
                    }
                    addSwatchStrip(content, "Stroke Color") { c ->
                        updateStroke { it.copy(enabled = true, color = c) }
                    }
                    addPixelLabSlider(content, "stroke width", 1, 32, st.width.toInt(), { "${it}px" }) { v ->
                        updateStroke { it.copy(enabled = true, width = v.toFloat()) }
                    }
                }
            }

            "text_shadow", "shape_shadow" -> {
                val sh = when (sel) {
                    is TextLayer -> sel.shadow
                    is ShapeLayer -> sel.shadow
                    is ArrowLayer -> sel.shadow
                    is ImageLayer -> sel.shadow
                    else -> return
                }
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, sh.enabled) { en ->
                        updateShadow { it.copy(enabled = en) }
                    }
                    addSwatchStrip(content, "Shadow Color") { c ->
                        updateShadow { it.copy(enabled = true, color = c) }
                    }
                    addPixelLabSlider(content, "blur radius", 0, 50, sh.blur.toInt(), { "$it" }) { v ->
                        updateShadow { it.copy(enabled = true, blur = v.toFloat()) }
                    }
                    addPixelLabSlider(content, "offset x", -40, 40, sh.offsetX.toInt(), { "$it" }) { v ->
                        updateShadow { it.copy(enabled = true, offsetX = v.toFloat()) }
                    }
                    addPixelLabSlider(content, "offset y", -40, 40, sh.offsetY.toInt(), { "$it" }) { v ->
                        updateShadow { it.copy(enabled = true, offsetY = v.toFloat()) }
                    }
                }
            }

            "text_3d", "shape_3d" -> {
                val td = when (sel) {
                    is TextLayer -> sel.threeD
                    is ShapeLayer -> sel.threeD
                    else -> return
                }
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, td.enabled) { en ->
                        when (val cur = getSelectedLayer()) {
                            is TextLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = en)))
                            is ShapeLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = en)))
                            else -> Unit
                        }
                    }
                    addPixelLabSlider(content, "depth", 1, 45, td.depth, { "$it" }) { v ->
                        when (val cur = getSelectedLayer()) {
                            is TextLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = true, depth = v)))
                            is ShapeLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = true, depth = v)))
                            else -> Unit
                        }
                    }
                    addPixelLabSlider(content, "angle", 0, 360, td.angle, { "${it}°" }) { v ->
                        when (val cur = getSelectedLayer()) {
                            is TextLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = true, angle = v)))
                            is ShapeLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = true, angle = v)))
                            else -> Unit
                        }
                    }
                    addPixelLabSlider(content, "darken", 10, 85, td.darken, { "$it%" }) { v ->
                        when (val cur = getSelectedLayer()) {
                            is TextLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = true, darken = v)))
                            is ShapeLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = true, darken = v)))
                            else -> Unit
                        }
                    }
                }
            }

            "text_emboss" -> {
                val tl = sel as? TextLayer ?: return
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, tl.emboss.enabled) { en ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(emboss = it.emboss.copy(enabled = en))) }
                    }
                    addPixelLabSlider(content, "light angle", 0, 360, tl.emboss.lightAngle, { "${it}°" }) { v ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(emboss = it.emboss.copy(enabled = true, lightAngle = v))) }
                    }
                    addPixelLabSlider(content, "bevel", 5, 60, tl.emboss.bevel, { "$it" }) { v ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(emboss = it.emboss.copy(enabled = true, bevel = v))) }
                    }
                }
            }

            "text_reflection" -> {
                val tl = sel as? TextLayer ?: return
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, tl.reflection.enabled) { en ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(reflection = it.reflection.copy(enabled = en))) }
                    }
                    addPixelLabSlider(content, "vertical offset", -40, 80, tl.reflection.offset.toInt(), { "${it}px" }) { v ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(reflection = it.reflection.copy(enabled = true, offset = v.toFloat()))) }
                    }
                    addPixelLabSlider(content, "opacity", 10, 90, tl.reflection.opacity, { "$it%" }) { v ->
                        (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(reflection = it.reflection.copy(enabled = true, opacity = v))) }
                    }
                }
            }

            "pick_shape" -> {
                val existingShape = sel as? ShapeLayer
                buildPopupShell(body) { left ->
                    addPillButtonRow(
                        left,
                        BoundedShapeLabel.entries.map { lbl ->
                            lbl.label to {
                                val cur = getSelectedLayer() as? ShapeLayer
                                if (cur != null) {
                                    updateSelectedLayerLive(cur.copy(shapeLabel = lbl, name = lbl.label))
                                } else {
                                    addLayer(
                                        ShapeLayer(
                                            id = "shape_${System.currentTimeMillis()}",
                                            name = lbl.label,
                                            x = proj.width / 2f,
                                            y = proj.height / 2f,
                                            shapeLabel = lbl
                                        )
                                    )
                                }
                            }
                        }
                    )
                    if (existingShape != null) {
                        addPixelLabSlider(left, "corner radius", 0, 100, existingShape.cornerRadius, { "$it%" }) { v ->
                            (getSelectedLayer() as? ShapeLayer)?.let {
                                updateSelectedLayerLive(it.copy(cornerRadius = v))
                            }
                        }
                    }
                }
            }

            "arrow_edit" -> {
                val al = sel as? ArrowLayer ?: return
                buildPopupShell(body) { left ->
                    addPillButtonRow(
                        left,
                        ArrowHeadStyle.entries.map { style ->
                            "End: ${style.name}" to {
                                (getSelectedLayer() as? ArrowLayer)?.let { updateSelectedLayerLive(it.copy(endStyle = style)) }
                            }
                        }
                    )
                    addPillButtonRow(
                        left,
                        ArrowHeadStyle.entries.map { style ->
                            "Start: ${style.name}" to {
                                (getSelectedLayer() as? ArrowLayer)?.let { updateSelectedLayerLive(it.copy(startStyle = style)) }
                            }
                        }
                    )
                    addPixelLabSlider(left, "stem width", 4, 60, al.stemWidth.toInt(), { "${it}px" }) { v ->
                        (getSelectedLayer() as? ArrowLayer)?.let { updateSelectedLayerLive(it.copy(stemWidth = v.toFloat())) }
                    }
                    addPixelLabSlider(left, "head size", 16, 120, al.headSize.toInt(), { "${it}px" }) { v ->
                        (getSelectedLayer() as? ArrowLayer)?.let { updateSelectedLayerLive(it.copy(headSize = v.toFloat())) }
                    }
                }
            }

            "draw" -> {
                val brush = canvasStage.brushConfig
                buildPopupShell(body) { left ->
                    addSwatchStrip(left, "Pen Color") { c -> brush.color = c }
                    addPixelLabSlider(left, "pen size", 2, 70, brush.width.toInt(), { "${it}px" }) { v -> brush.width = v.toFloat() }
                    addPixelLabSlider(left, "blur", 0, 30, brush.blur.toInt(), { "$it" }) { v -> brush.blur = v.toFloat() }
                    addPillButtonRow(
                        left,
                        listOf(
                            "Eraser" to { brush.eraser = !brush.eraser },
                            "Dashed" to { brush.dashed = !brush.dashed },
                            "Neon" to { brush.neon = !brush.neon },
                            "Fill" to { brush.fill = !brush.fill },
                            "Undo" to { canvasStage.undoPendingStroke() }
                        )
                    )
                }
            }

            "img_crop" -> {
                if (sel !is ImageLayer) return
                buildPopupShell(body) { left ->
                    addPillButtonRow(
                        left,
                        listOf(
                            "Circular Crop" to {
                                (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(circularCrop = !it.circularCrop)) }
                            },
                            "Flip H" to {
                                (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(flipH = !it.flipH)) }
                            },
                            "Flip V" to {
                                (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(flipV = !it.flipV)) }
                            }
                        )
                    )
                }
            }

            "img_filters" -> {
                val il = sel as? ImageLayer ?: return
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "brightness", -100, 100, il.brightness, { "$it" }) { v ->
                        (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(brightness = v)) }
                    }
                    addPixelLabSlider(left, "contrast", -80, 150, il.contrast, { "$it" }) { v ->
                        (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(contrast = v)) }
                    }
                    addPixelLabSlider(left, "saturation", -100, 150, il.saturation, { "$it" }) { v ->
                        (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(saturation = v)) }
                    }
                    addPixelLabSlider(left, "hue", -180, 180, il.hue, { "${it}°" }) { v ->
                        (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(hue = v)) }
                    }
                }
            }

            "img_remove_color" -> {
                val il = sel as? ImageLayer ?: return
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, il.removeColorEnabled) { en ->
                        (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(removeColorEnabled = en)) }
                    }
                    addPixelLabSlider(content, "tolerance", 1, 80, il.removeColorTolerance, { "$it%" }) { v ->
                        (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(removeColorEnabled = true, removeColorTolerance = v)) }
                    }
                    addSwatchStrip(content, "Target Color") { c ->
                        (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(removeColorEnabled = true, removeColorTarget = c)) }
                    }
                }
            }

            "bg_color" -> {
                openFillSheet(body, isBackground = true)
            }

            "bg_crop" -> {
                buildPopupShell(body) { left ->
                    addPillButtonRow(
                        left,
                        listOf(
                            "Gallery" to { onPickBgImage() },
                            "Presets" to { openTool("bg_presets", "Background Presets") },
                            "Rotate 90°" to {
                                val p = getProject()
                                val rot = (p.background.imageRotation + 90) % 360
                                updateProjectLive(p.copy(background = p.background.copy(imageRotation = rot)))
                            },
                            "Flip H" to {
                                val p = getProject()
                                updateProjectLive(p.copy(background = p.background.copy(flipH = !p.background.flipH)))
                            },
                            "Flip V" to {
                                val p = getProject()
                                updateProjectLive(p.copy(background = p.background.copy(flipV = !p.background.flipV)))
                            },
                            "Circle" to {
                                val p = getProject()
                                updateProjectLive(p.copy(background = p.background.copy(circular = !p.background.circular)))
                            }
                        )
                    )
                }
            }

            "bg_presets" -> {
                buildPopupShell(body) { left ->
                    val bgs = AssetRepository.loadBackgrounds(context)
                    val hs = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
                    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    for (item in bgs) {
                        val bmp = PixelLabRenderer.loadBitmap(context, item.thumbUri)
                        if (bmp != null) {
                            val iv = ImageView(context).apply {
                                layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                setImageBitmap(bmp)
                                setOnClickListener {
                                    val p = getProject()
                                    updateProjectLive(
                                        p.copy(background = p.background.copy(type = BackgroundType.IMAGE, imageUri = item.fullUri))
                                    )
                                }
                            }
                            row.addView(iv)
                        }
                    }
                    hs.addView(row)
                    left.addView(hs)
                }
            }

            "fx_vignette" -> {
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, proj.effects.vignetteEnabled) { en ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(vignetteEnabled = en)))
                    }
                    addPixelLabSlider(content, "alpha", 0, 255, proj.effects.vignetteAlpha, { "$it" }) { v ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(vignetteEnabled = true, vignetteAlpha = v)))
                    }
                    addSwatchStrip(content, "Vignette Color") { c ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(vignetteEnabled = true, vignetteColor = c)))
                    }
                }
            }

            "fx_noise" -> {
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, proj.effects.noiseEnabled) { en ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(noiseEnabled = en)))
                    }
                    addPixelLabSlider(content, "alpha", 0, 200, proj.effects.noiseAlpha, { "$it" }) { v ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(noiseEnabled = true, noiseAlpha = v)))
                    }
                    addPixelLabSlider(content, "grain scale", 2, 20, proj.effects.noiseScale, { "$it" }) { v ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(noiseEnabled = true, noiseScale = v)))
                    }
                }
            }

            "fx_stripes" -> {
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, proj.effects.stripesEnabled) { en ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(stripesEnabled = en)))
                    }
                    addPixelLabSlider(content, "alpha", 0, 200, proj.effects.stripesAlpha, { "$it" }) { v ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(stripesEnabled = true, stripesAlpha = v)))
                    }
                    addPixelLabSlider(content, "repeat count", 2, 40, proj.effects.stripesRepeat, { "$it" }) { v ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(stripesEnabled = true, stripesRepeat = v)))
                    }
                }
            }

            "fx_brightness" -> {
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "brightness", -100, 100, proj.effects.brightness, { "$it" }) { v ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(brightness = v)))
                    }
                }
            }

            "fx_contrast" -> {
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "contrast", -80, 150, proj.effects.contrast, { "$it" }) { v ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(contrast = v)))
                    }
                }
            }

            "fx_hue" -> {
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "hue", -180, 180, proj.effects.hue, { "${it}°" }) { v ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(hue = v)))
                    }
                }
            }

            "text_inner_shadow", "shape_inner_shadow" -> {
                val sh = when (sel) {
                    is TextLayer -> sel.innerShadow
                    is ShapeLayer -> sel.innerShadow
                    is ImageLayer -> sel.innerShadow
                    else -> return
                }
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, sh.enabled) { en ->
                        updateInnerShadow { it.copy(enabled = en) }
                    }
                    addSwatchStrip(content, "Inner Shadow Color") { c ->
                        updateInnerShadow { it.copy(enabled = true, color = c) }
                    }
                    addPixelLabSlider(content, "blur radius", 0, 40, sh.blur.toInt(), { "$it" }) { v ->
                        updateInnerShadow { it.copy(enabled = true, blur = v.toFloat()) }
                    }
                    addPixelLabSlider(content, "offset x", -30, 30, sh.offsetX.toInt(), { "$it" }) { v ->
                        updateInnerShadow { it.copy(enabled = true, offsetX = v.toFloat()) }
                    }
                    addPixelLabSlider(content, "offset y", -30, 30, sh.offsetY.toInt(), { "$it" }) { v ->
                        updateInnerShadow { it.copy(enabled = true, offsetY = v.toFloat()) }
                    }
                }
            }

            "text_texture", "shape_texture" -> {
                if (sel !is TextLayer && sel !is ShapeLayer) return
                buildPopupShell(body) { left ->
                    addPillButtonRow(
                        left,
                        listOf(
                            "Pick Texture Image" to { onPickTextureImage() },
                            "Remove Texture" to {
                                when (val cur = getSelectedLayer()) {
                                    is TextLayer -> updateSelectedLayerLive(cur.copy(textureUri = null))
                                    is ShapeLayer -> updateSelectedLayerLive(cur.copy(textureUri = null))
                                    else -> Unit
                                }
                            }
                        )
                    )
                }
            }

            "layer_mask" -> {
                val (en, inv) = when (sel) {
                    is TextLayer -> sel.maskEnabled to sel.maskInvert
                    is ShapeLayer -> sel.maskEnabled to sel.maskInvert
                    is ImageLayer -> sel.maskEnabled to sel.maskInvert
                    else -> return
                }
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, en) { checked ->
                        when (val cur = getSelectedLayer()) {
                            is TextLayer -> updateSelectedLayerLive(cur.copy(maskEnabled = checked))
                            is ShapeLayer -> updateSelectedLayerLive(cur.copy(maskEnabled = checked))
                            is ImageLayer -> updateSelectedLayerLive(cur.copy(maskEnabled = checked))
                            else -> Unit
                        }
                    }
                    addPillButtonRow(
                        content,
                        listOf(
                            (if (inv) "Mode: Out (Inverted)" else "Mode: In") to {
                                when (val cur = getSelectedLayer()) {
                                    is TextLayer -> updateSelectedLayerLive(cur.copy(maskInvert = !cur.maskInvert))
                                    is ShapeLayer -> updateSelectedLayerLive(cur.copy(maskInvert = !cur.maskInvert))
                                    is ImageLayer -> updateSelectedLayerLive(cur.copy(maskInvert = !cur.maskInvert))
                                    else -> Unit
                                }
                            },
                            "Undo Point" to {
                                when (val cur = getSelectedLayer()) {
                                    is TextLayer -> updateSelectedLayerLive(cur.copy(maskPoints = cur.maskPoints.dropLast(1)))
                                    is ShapeLayer -> updateSelectedLayerLive(cur.copy(maskPoints = cur.maskPoints.dropLast(1)))
                                    is ImageLayer -> updateSelectedLayerLive(cur.copy(maskPoints = cur.maskPoints.dropLast(1)))
                                    else -> Unit
                                }
                            },
                            "Clear Mask" to {
                                when (val cur = getSelectedLayer()) {
                                    is TextLayer -> updateSelectedLayerLive(cur.copy(maskEnabled = false, maskPoints = emptyList()))
                                    is ShapeLayer -> updateSelectedLayerLive(cur.copy(maskEnabled = false, maskPoints = emptyList()))
                                    is ImageLayer -> updateSelectedLayerLive(cur.copy(maskEnabled = false, maskPoints = emptyList()))
                                    else -> Unit
                                }
                            }
                        )
                    )
                }
            }

            "layer_perspective" -> {
                val en = when (sel) {
                    is TextLayer -> sel.perspectiveEnabled
                    is ShapeLayer -> sel.perspectiveEnabled
                    is ImageLayer -> sel.perspectiveEnabled
                    else -> return
                }
                buildPopupShell(body) { left ->
                    val content = addEnabledSection(left, en) { checked ->
                        when (val cur = getSelectedLayer()) {
                            is TextLayer -> updateSelectedLayerLive(cur.copy(perspectiveEnabled = checked))
                            is ShapeLayer -> updateSelectedLayerLive(cur.copy(perspectiveEnabled = checked))
                            is ImageLayer -> updateSelectedLayerLive(cur.copy(perspectiveEnabled = checked))
                            else -> Unit
                        }
                    }
                    addPillButtonRow(
                        content,
                        listOf(
                            "Reset Corners" to {
                                val reset = listOf(
                                    DrawPoint(0f, 0f),
                                    DrawPoint(1f, 0f),
                                    DrawPoint(1f, 1f),
                                    DrawPoint(0f, 1f)
                                )
                                when (val cur = getSelectedLayer()) {
                                    is TextLayer -> updateSelectedLayerLive(cur.copy(perspectiveEnabled = false, perspectivePoints = reset))
                                    is ShapeLayer -> updateSelectedLayerLive(cur.copy(perspectiveEnabled = false, perspectivePoints = reset))
                                    is ImageLayer -> updateSelectedLayerLive(cur.copy(perspectiveEnabled = false, perspectivePoints = reset))
                                    else -> Unit
                                }
                            }
                        )
                    )
                }
            }

            "bezier_edit" -> {
                val bl = sel as? BezierLayer ?: return
                buildPopupShell(body) { left ->
                    addPillButtonRow(
                        left,
                        listOf(
                            "Add Point" to { canvasStage.bezierActionMode = 0 },
                            "Drag Point" to { canvasStage.bezierActionMode = 1 },
                            "Close Path" to {
                                (getSelectedLayer() as? BezierLayer)?.let { updateSelectedLayerLive(it.copy(closed = !it.closed)) }
                            },
                            "Fill" to {
                                (getSelectedLayer() as? BezierLayer)?.let { updateSelectedLayerLive(it.copy(fillEnabled = !it.fillEnabled)) }
                            },
                            "Undo Point" to {
                                (getSelectedLayer() as? BezierLayer)?.let { updateSelectedLayerLive(it.copy(anchors = it.anchors.dropLast(1))) }
                            }
                        )
                    )
                    addPixelLabSlider(left, "stroke width", 1, 36, bl.stroke.width.toInt(), { "${it}px" }) { v ->
                        (getSelectedLayer() as? BezierLayer)?.let {
                            updateSelectedLayerLive(it.copy(stroke = it.stroke.copy(enabled = true, width = v.toFloat())))
                        }
                    }
                    addSwatchStrip(left, "Bezier Color") { c ->
                        (getSelectedLayer() as? BezierLayer)?.let {
                            updateSelectedLayerLive(it.copy(color = c, stroke = it.stroke.copy(color = c)))
                        }
                    }
                }
            }

            "img_eraser" -> {
                if (sel !is ImageLayer) return
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "eraser size", 6, 120, canvasStage.bitmapEraserWidth.toInt(), { "${it}px" }) { v ->
                        canvasStage.bitmapEraserWidth = v.toFloat()
                    }
                    addPillButtonRow(
                        left,
                        listOf(
                            "Undo Erase" to {
                                (getSelectedLayer() as? ImageLayer)?.let {
                                    updateSelectedLayerLive(it.copy(eraserStrokes = it.eraserStrokes.dropLast(1)))
                                }
                            },
                            "Reset Image" to {
                                (getSelectedLayer() as? ImageLayer)?.let {
                                    updateSelectedLayerLive(it.copy(eraserStrokes = emptyList()))
                                }
                            }
                        )
                    )
                }
            }

            "eyedropper" -> {
                buildPopupShell(body) { left ->
                    val topRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 0, 0, dp(8))
                    }
                    val previewDot = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(lastEyedropperColor)
                            setStroke(dp(1), Color.parseColor("#CBD5E1"))
                        }
                    }
                    val tvHint = TextView(context).apply {
                        text = "Tap canvas to sample a color"
                        textSize = 12f
                        setTextColor(Color.parseColor("#555555"))
                    }
                    topRow.addView(previewDot)
                    topRow.addView(tvHint)
                    left.addView(topRow)

                    canvasStage.onEyedropperColor = { picked ->
                        lastEyedropperColor = picked
                        previewDot.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(picked)
                            setStroke(dp(1), Color.parseColor("#CBD5E1"))
                        }
                    }

                    addPillButtonRow(
                        left,
                        listOf(
                            "Apply to Layer" to {
                                when (val cur = getSelectedLayer()) {
                                    is TextLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.COLOR, color = lastEyedropperColor))
                                    is ShapeLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.COLOR, color = lastEyedropperColor))
                                    is ArrowLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.COLOR, color = lastEyedropperColor))
                                    is ImageLayer -> updateSelectedLayerLive(cur.copy(colorOverlayEnabled = true, colorOverlay = lastEyedropperColor))
                                    is BezierLayer -> updateSelectedLayerLive(cur.copy(color = lastEyedropperColor, stroke = cur.stroke.copy(color = lastEyedropperColor)))
                                    else -> Unit
                                }
                            },
                            "Apply to Background" to {
                                val p = getProject()
                                updateProjectLive(p.copy(background = p.background.copy(type = BackgroundType.COLOR, color = lastEyedropperColor)))
                            }
                        )
                    )
                }
            }

            "fx_saturation" -> {
                buildPopupShell(body) { left ->
                    addPixelLabSlider(left, "saturation", -100, 150, proj.effects.saturation, { "$it" }) { v ->
                        val p = getProject()
                        updateProjectLive(p.copy(effects = p.effects.copy(saturation = v)))
                    }
                }
            }
        }
    }

    private fun openRadiusSheet(body: LinearLayout) {
        val layer = getSelectedLayer()
        val isImage = layer is ImageLayer
        val isRectShape = layer is ShapeLayer && layer.shapeLabel == BoundedShapeLabel.RECT
        if (!isImage && !isRectShape) return

        val maxR = (min(layer!!.width, layer.height) / 2f).roundToInt().coerceAtLeast(1)

        val radii = when (layer) {
            is ImageLayer -> layer.cornerRadii.toMutableList()
            is ShapeLayer -> (layer.cornerRadii ?: listOf(0f, 0f, 0f, 0f)).toMutableList()
            else -> mutableListOf(0f, 0f, 0f, 0f)
        }
        while (radii.size < 4) radii.add(0f)

        val enabled = when (layer) {
            is ImageLayer -> layer.cornerEnabled.toMutableList()
            is ShapeLayer -> layer.cornerEnabled.toMutableList()
            else -> mutableListOf(true, true, true, true)
        }
        while (enabled.size < 4) enabled.add(true)

        var locked = when (layer) {
            is ImageLayer -> layer.cornerLocked
            is ShapeLayer -> layer.cornerLocked
            else -> true
        }

        fun applyRadiusChanges() {
            when (val cur = getSelectedLayer()) {
                is ImageLayer -> updateSelectedLayerLive(
                    cur.copy(
                        cornerRadii = radii.toList(),
                        cornerEnabled = enabled.toList(),
                        cornerLocked = locked
                    )
                )
                is ShapeLayer -> updateSelectedLayerLive(
                    cur.copy(
                        cornerRadii = radii.toList(),
                        cornerEnabled = enabled.toList(),
                        cornerLocked = locked
                    )
                )
                else -> Unit
            }
        }

        buildPopupShell(body, centerVertically = false) { container ->
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(2), dp(2), dp(6))
            }

            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                setImageResource(R.drawable.ic_radius)
                setColorFilter(Color.parseColor("#222222"))
            }
            header.addView(iconView)
            header.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })

            val lockBtn = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(28)).apply { marginEnd = dp(8) }
            }
            val lockIcon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
            }
            lockBtn.addView(lockIcon)

            fun refreshLockUi() {
                lockBtn.background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (locked) "#3778DE" else "#CBD5E1"))
                    cornerRadius = dp(999).toFloat()
                }
                lockIcon.setImageResource(if (locked) R.drawable.ic_lock else R.drawable.ic_unlock)
                lockIcon.setColorFilter(if (locked) Color.WHITE else Color.parseColor("#334155"))
            }
            refreshLockUi()

            val resetBtn = ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
                background = null
                setImageResource(R.drawable.ic_reset)
                setColorFilter(Color.parseColor("#333333"))
            }

            header.addView(lockBtn)
            header.addView(resetBtn)
            container.addView(header)

            val seekBars = arrayOfNulls<SeekBar>(4)
            val valLabels = arrayOfNulls<TextView>(4)
            val switches = arrayOfNulls<SwitchMaterial>(4)
            var updatingUi = false

            fun syncCardControls() {
                updatingUi = true
                for (i in 0..3) {
                    val r = radii[i].roundToInt().coerceIn(0, maxR)
                    seekBars[i]?.progress = r
                    seekBars[i]?.isEnabled = enabled[i]
                    valLabels[i]?.text = r.toString()
                    switches[i]?.isChecked = enabled[i]
                }
                updatingUi = false
            }

            fun buildCornerCard(cornerIdx: Int): LinearLayout {
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dp(3), dp(3), dp(3), dp(3))
                    }
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), Color.parseColor("#E2E8F0"))
                    }
                    setPadding(dp(8), dp(5), dp(6), dp(5))
                }

                val topRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val diagram = object : View(context) {
                    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#B0B7C3")
                        style = Paint.Style.STROKE
                        strokeWidth = 2f * context.resources.displayMetrics.density
                    }
                    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#3778DE")
                        style = Paint.Style.STROKE
                        strokeWidth = 2.6f * context.resources.displayMetrics.density
                        strokeCap = Paint.Cap.ROUND
                    }
                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        val pad = 3f * context.resources.displayMetrics.density
                        val r = 6f * context.resources.displayMetrics.density
                        val rect = RectF(pad, pad, width - pad, height - pad)
                        canvas.drawRoundRect(rect, r, r, basePaint)
                        val d = r * 2f
                        val arcRect = when (cornerIdx) {
                            0 -> RectF(rect.left, rect.top, rect.left + d, rect.top + d)
                            1 -> RectF(rect.right - d, rect.top, rect.right, rect.top + d)
                            2 -> RectF(rect.right - d, rect.bottom - d, rect.right, rect.bottom)
                            else -> RectF(rect.left, rect.bottom - d, rect.left + d, rect.bottom)
                        }
                        val startAngle = when (cornerIdx) {
                            0 -> 180f
                            1 -> 270f
                            2 -> 0f
                            else -> 90f
                        }
                        canvas.drawArc(arcRect, startAngle, 90f, false, activePaint)
                    }
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                }
                topRow.addView(diagram)
                topRow.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })

                val sw = SwitchMaterial(context).apply {
                    isChecked = enabled[cornerIdx]
                    setOnCheckedChangeListener { _, isChecked ->
                        if (updatingUi) return@setOnCheckedChangeListener
                        enabled[cornerIdx] = isChecked
                        syncCardControls()
                        applyRadiusChanges()
                    }
                }
                switches[cornerIdx] = sw
                topRow.addView(sw)
                card.addView(topRow)

                val bottomRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(2), 0, 0)
                }
                val tvVal = TextView(context).apply {
                    text = radii[cornerIdx].roundToInt().toString()
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#334155"))
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(dp(30), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val sb = SeekBar(context).apply {
                    max = maxR
                    progress = radii[cornerIdx].roundToInt().coerceIn(0, maxR)
                    isEnabled = enabled[cornerIdx]
                    thumbTintList = ColorStateList.valueOf(tealColor)
                    progressTintList = ColorStateList.valueOf(tealColor)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                            if (!fromUser || updatingUi) return
                            if (locked) {
                                for (i in 0..3) radii[i] = p.toFloat()
                            } else {
                                radii[cornerIdx] = p.toFloat()
                            }
                            syncCardControls()
                            applyRadiusChanges()
                        }
                        override fun onStartTrackingTouch(s: SeekBar?) = Unit
                        override fun onStopTrackingTouch(s: SeekBar?) = Unit
                    })
                }
                seekBars[cornerIdx] = sb
                valLabels[cornerIdx] = tvVal
                bottomRow.addView(sb)
                bottomRow.addView(tvVal)
                card.addView(bottomRow)

                return card
            }

            val row1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row1.addView(buildCornerCard(0))
            row1.addView(buildCornerCard(1))

            val row2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row2.addView(buildCornerCard(3))
            row2.addView(buildCornerCard(2))

            lockBtn.setOnClickListener {
                locked = !locked
                if (locked) {
                    val baseVal = radii[0]
                    for (i in 0..3) radii[i] = baseVal
                    syncCardControls()
                }
                refreshLockUi()
                applyRadiusChanges()
            }

            resetBtn.setOnClickListener {
                for (i in 0..3) {
                    radii[i] = 0f
                    enabled[i] = true
                }
                syncCardControls()
                applyRadiusChanges()
            }

            container.addView(row1)
            container.addView(row2)
        }
    }

    private fun openFillSheet(body: LinearLayout, isBackground: Boolean) {
        val sel = if (isBackground) null else getSelectedLayer() ?: return
        val proj = getProject()

        var isGradientMode = when {
            isBackground -> proj.background.type == BackgroundType.GRADIENT
            sel is TextLayer -> sel.fillType == FillType.GRADIENT
            sel is ShapeLayer -> sel.fillType == FillType.GRADIENT
            sel is ArrowLayer -> sel.fillType == FillType.GRADIENT
            sel is ImageLayer -> sel.colorOverlayFillType == FillType.GRADIENT
            else -> false
        }

        var tintEnabled = (sel as? ImageLayer)?.colorOverlayEnabled ?: true

        fun currentColor(): Int = when {
            isBackground -> proj.background.color
            sel is TextLayer -> (getSelectedLayer() as? TextLayer)?.color ?: sel.color
            sel is ShapeLayer -> (getSelectedLayer() as? ShapeLayer)?.color ?: sel.color
            sel is ArrowLayer -> (getSelectedLayer() as? ArrowLayer)?.color ?: sel.color
            sel is ImageLayer -> (getSelectedLayer() as? ImageLayer)?.colorOverlay ?: sel.colorOverlay
            else -> Color.WHITE
        }

        fun currentGrad(): GradientFill = when {
            isBackground -> getProject().background.gradient
            sel is TextLayer -> (getSelectedLayer() as? TextLayer)?.gradient ?: sel.gradient
            sel is ShapeLayer -> (getSelectedLayer() as? ShapeLayer)?.gradient ?: sel.gradient
            sel is ArrowLayer -> (getSelectedLayer() as? ArrowLayer)?.gradient ?: sel.gradient
            sel is ImageLayer -> (getSelectedLayer() as? ImageLayer)?.colorOverlayGradient ?: sel.colorOverlayGradient
            else -> GradientFill()
        }

        fun applyColor(c: Int) {
            if (isBackground) {
                val p = getProject()
                updateProjectLive(p.copy(background = p.background.copy(type = BackgroundType.COLOR, color = c)))
            } else {
                when (val cur = getSelectedLayer()) {
                    is TextLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.COLOR, color = c))
                    is ShapeLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.COLOR, color = c))
                    is ArrowLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.COLOR, color = c))
                    is ImageLayer -> updateSelectedLayerLive(
                        cur.copy(colorOverlayEnabled = true, colorOverlayFillType = FillType.COLOR, colorOverlay = c)
                    )
                    else -> Unit
                }
            }
        }

        fun applyGradient(g: GradientFill) {
            if (isBackground) {
                val p = getProject()
                updateProjectLive(p.copy(background = p.background.copy(type = BackgroundType.GRADIENT, gradient = g)))
            } else {
                when (val cur = getSelectedLayer()) {
                    is TextLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.GRADIENT, gradient = g))
                    is ShapeLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.GRADIENT, gradient = g))
                    is ArrowLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.GRADIENT, gradient = g))
                    is ImageLayer -> updateSelectedLayerLive(
                        cur.copy(colorOverlayEnabled = true, colorOverlayFillType = FillType.GRADIENT, colorOverlayGradient = g)
                    )
                    else -> Unit
                }
            }
        }

        buildPopupShell(body) { leftCol ->
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(8))
            }
            val btnImport = TextView(context).apply {
                text = "IMPORT"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tealColor)
                setPadding(dp(4), dp(4), dp(8), dp(4))
                visibility = if (sel is ImageLayer && !tintEnabled) View.INVISIBLE else View.VISIBLE
            }
            val btnExport = TextView(context).apply {
                text = "EXPORT"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tealColor)
                setPadding(dp(8), dp(4), dp(4), dp(4))
                visibility = if (sel is ImageLayer && !tintEnabled) View.INVISIBLE else View.VISIBLE
            }
            topRow.addView(btnImport)
            topRow.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })

            val tintBody = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (sel !is ImageLayer || tintEnabled) View.VISIBLE else View.GONE
            }

            if (sel is ImageLayer) {
                topRow.addView(TextView(context).apply {
                    text = "Enabled : "
                    textSize = 13f
                    setTextColor(Color.parseColor("#666666"))
                })
                val sw = SwitchMaterial(context).apply {
                    isChecked = tintEnabled
                    setOnCheckedChangeListener { _, checked ->
                        tintEnabled = checked
                        tintBody.visibility = if (checked) View.VISIBLE else View.GONE
                        btnImport.visibility = if (checked) View.VISIBLE else View.INVISIBLE
                        btnExport.visibility = if (checked) View.VISIBLE else View.INVISIBLE
                        (getSelectedLayer() as? ImageLayer)?.let { cur ->
                            updateSelectedLayerLive(cur.copy(colorOverlayEnabled = checked))
                        }
                    }
                }
                topRow.addView(sw)
                topRow.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            }

            topRow.addView(btnExport)
            leftCol.addView(topRow)

            val pink = Color.parseColor("#EC2166")
            val segWrap = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(2), 0, dp(10))
            }
            val segBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT)
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    setStroke(dp(1), pink)
                    cornerRadius = dp(5).toFloat()
                }
                setPadding(dp(1), dp(1), dp(1), dp(1))
            }
            val btnSegColor = TextView(context).apply {
                text = "color"
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(6))
            }
            val btnSegGrad = TextView(context).apply {
                text = "gradient"
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(6))
            }
            segBox.addView(btnSegColor)
            segBox.addView(btnSegGrad)
            segWrap.addView(segBox)
            tintBody.addView(segWrap)

            val stripFrame = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#A9A9A9"))
                    cornerRadius = dp(6).toFloat()
                }
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            val swatchScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val swatchRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            swatchScroll.addView(swatchRow)
            stripFrame.addView(swatchScroll)
            tintBody.addView(stripFrame)

            fun renderSwatches() {
                btnSegColor.setBackgroundColor(if (!isGradientMode) pink else Color.WHITE)
                btnSegColor.setTextColor(if (!isGradientMode) Color.WHITE else pink)
                btnSegGrad.setBackgroundColor(if (isGradientMode) pink else Color.WHITE)
                btnSegGrad.setTextColor(if (isGradientMode) Color.WHITE else pink)

                swatchRow.removeAllViews()

                val plusBtn = TextView(context).apply {
                    text = "+"
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#334155"))
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                        setMargins(dp(4), dp(2), dp(6), dp(2))
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.WHITE)
                        setStroke(dp(1), Color.parseColor("#CBD5E1"))
                    }
                    setOnClickListener {
                        if (isGradientMode) {
                            Dialogs.showGradientMakerDialog(context, currentGrad()) { g ->
                                JsonPresetCodec.saveCustomGradient(context, g)
                                applyGradient(g)
                                renderSwatches()
                            }
                        } else {
                            Dialogs.showColorPickerDialog(context, currentColor()) { picked ->
                                JsonPresetCodec.saveCustomColor(context, picked)
                                applyColor(picked)
                                renderSwatches()
                            }
                        }
                    }
                }
                swatchRow.addView(plusBtn)

                if (!isGradientMode) {
                    val activeCol = currentColor()
                    val allColors = (JsonPresetCodec.loadCustomColors(context) + AppDefaults.defaultColors).distinct()
                    for (c in allColors) {
                        val isSel = activeCol == c
                        val dot = View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                                setMargins(dp(4), dp(2), dp(4), dp(2))
                            }
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(c)
                                setStroke(dp(if (isSel) 3 else 1), if (isSel) Color.WHITE else Color.parseColor("#888888"))
                            }
                            setOnClickListener {
                                applyColor(c)
                                renderSwatches()
                            }
                        }
                        swatchRow.addView(dot)
                    }
                } else {
                    val allGrads = JsonPresetCodec.loadCustomGradients(context) + AppDefaults.defaultGradients
                    val activeGradKey = JsonPresetCodec.gradToWebJson(currentGrad()).toString()
                    for (g in allGrads) {
                        val isSel = JsonPresetCodec.gradToWebJson(g).toString() == activeGradKey
                        val cols = if (g.stops.size >= 2) g.stops.map { it.color }.toIntArray()
                        else intArrayOf(Color.BLACK, Color.WHITE)
                        val orient = when (g.type) {
                            GradientDirection.LINEAR_H -> GradientDrawable.Orientation.LEFT_RIGHT
                            GradientDirection.LINEAR_DIAG -> GradientDrawable.Orientation.TL_BR
                            else -> GradientDrawable.Orientation.TOP_BOTTOM
                        }
                        val dot = View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                                setMargins(dp(4), dp(2), dp(4), dp(2))
                            }
                            background = GradientDrawable(orient, cols).apply {
                                shape = GradientDrawable.OVAL
                                setStroke(dp(if (isSel) 3 else 1), if (isSel) Color.WHITE else Color.parseColor("#888888"))
                            }
                            setOnClickListener {
                                applyGradient(g)
                                renderSwatches()
                            }
                        }
                        swatchRow.addView(dot)
                    }
                }
            }

            btnSegColor.setOnClickListener {
                isGradientMode = false
                applyColor(currentColor())
                renderSwatches()
            }
            btnSegGrad.setOnClickListener {
                isGradientMode = true
                applyGradient(currentGrad())
                renderSwatches()
            }

            btnImport.setOnClickListener {
                onRequestImportJson { raw ->
                    runCatching {
                        val res = JsonPresetCodec.importColorsAndGradientsJson(context, raw)
                        if (res.gradients.isNotEmpty()) {
                            isGradientMode = true
                            applyGradient(res.gradients.first())
                        } else if (res.colors.isNotEmpty()) {
                            isGradientMode = false
                            applyColor(res.colors.first())
                        }
                        renderSwatches()
                        Toast.makeText(
                            context,
                            "Imported ${res.colors.size} color(s), ${res.gradients.size} gradient(s)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure {
                        Toast.makeText(context, "Invalid color/gradient JSON file", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            btnExport.setOnClickListener {
                val activeCol = if (!isGradientMode) currentColor() else null
                val activeGrad = if (isGradientMode) currentGrad() else null
                val json = JsonPresetCodec.exportColorsAndGradientsJson(context, activeCol, activeGrad)
                onRequestExportJson("pixellab_colors_gradients.json", json)
            }

            leftCol.addView(tintBody)
            renderSwatches()
        }
    }

    private fun updateInnerShadow(transform: (ShadowConfig) -> ShadowConfig) {
        when (val cur = getSelectedLayer()) {
            is TextLayer -> updateSelectedLayerLive(cur.copy(innerShadow = transform(cur.innerShadow)))
            is ShapeLayer -> updateSelectedLayerLive(cur.copy(innerShadow = transform(cur.innerShadow)))
            is ImageLayer -> updateSelectedLayerLive(cur.copy(innerShadow = transform(cur.innerShadow)))
            else -> Unit
        }
    }

    private fun updateStroke(transform: (StrokeConfig) -> StrokeConfig) {
        when (val cur = getSelectedLayer()) {
            is TextLayer -> updateSelectedLayerLive(cur.copy(stroke = transform(cur.stroke)))
            is ShapeLayer -> updateSelectedLayerLive(cur.copy(stroke = transform(cur.stroke)))
            is ArrowLayer -> updateSelectedLayerLive(cur.copy(stroke = transform(cur.stroke)))
            is ImageLayer -> updateSelectedLayerLive(cur.copy(stroke = transform(cur.stroke)))
            else -> Unit
        }
    }

    private fun updateShadow(transform: (ShadowConfig) -> ShadowConfig) {
        when (val cur = getSelectedLayer()) {
            is TextLayer -> updateSelectedLayerLive(cur.copy(shadow = transform(cur.shadow)))
            is ShapeLayer -> updateSelectedLayerLive(cur.copy(shadow = transform(cur.shadow)))
            is ArrowLayer -> updateSelectedLayerLive(cur.copy(shadow = transform(cur.shadow)))
            is ImageLayer -> updateSelectedLayerLive(cur.copy(shadow = transform(cur.shadow)))
            else -> Unit
        }
    }

    private fun addEnabledSection(
        parent: LinearLayout,
        initialEnabled: Boolean,
        onToggle: (Boolean) -> Unit
    ): LinearLayout {
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(6))
        }
        val label = TextView(context).apply {
            text = "Enabled : "
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
        }
        val contentBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (initialEnabled) View.VISIBLE else View.GONE
        }
        val sw = SwitchMaterial(context).apply {
            isChecked = initialEnabled
            setOnCheckedChangeListener { _, isChecked ->
                contentBox.visibility = if (isChecked) View.VISIBLE else View.GONE
                onToggle(isChecked)
            }
        }
        headerRow.addView(label)
        headerRow.addView(sw)
        parent.addView(headerRow)
        parent.addView(contentBox)
        return contentBox
    }

    private fun addPixelLabSlider(
        parent: LinearLayout,
        label: String,
        min: Int,
        max: Int,
        current: Int,
        formatValue: (Int) -> String,
        onChange: (Int) -> Unit
    ) {
        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(5))
        }

        val labelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(2))
        }
        val tvLabel = TextView(context).apply {
            text = "$label : "
            textSize = 12.5f
            setTextColor(Color.parseColor("#555555"))
        }
        val tvVal = TextView(context).apply {
            text = formatValue(current.coerceIn(min, max))
            textSize = 12.5f
            setTextColor(accentBlue)
            typeface = Typeface.DEFAULT_BOLD
        }
        labelRow.addView(tvLabel)
        labelRow.addView(tvVal)
        block.addView(labelRow)

        val sliderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val range = (max - min).coerceAtLeast(1)
        var valueState = current.coerceIn(min, max)

        val seek = SeekBar(context).apply {
            this.max = range
            this.progress = valueState - min
            thumbTintList = ColorStateList.valueOf(tealColor)
            progressTintList = ColorStateList.valueOf(tealColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = min + progress
                    valueState = v
                    tvVal.text = formatValue(v)
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }

        val btnMinus = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#E2E8F0"))
            }
            elevation = dp(1).toFloat()
            setImageResource(R.drawable.ic_minus)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener {
                val next = (valueState - 1).coerceIn(min, max)
                valueState = next
                seek.progress = next - min
                tvVal.text = formatValue(next)
                onChange(next)
            }
        }

        val btnPlus = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#E2E8F0"))
            }
            elevation = dp(1).toFloat()
            setImageResource(R.drawable.ic_plus_simple)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener {
                val next = (valueState + 1).coerceIn(min, max)
                valueState = next
                seek.progress = next - min
                tvVal.text = formatValue(next)
                onChange(next)
            }
        }

        sliderRow.addView(btnMinus)
        sliderRow.addView(seek)
        sliderRow.addView(btnPlus)
        block.addView(sliderRow)
        parent.addView(block)
    }

    private fun createCircleIconButton(
        iconRes: Int,
        active: Boolean = false,
        onClick: () -> Unit
    ): ImageButton {
        return ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                setMargins(dp(7), dp(2), dp(7), dp(2))
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (active) Color.parseColor("#E0F2FE") else Color.WHITE)
                setStroke(dp(1), if (active) accentBlue else Color.parseColor("#E2E8F0"))
            }
            elevation = dp(2).toFloat()
            setImageResource(iconRes)
            setColorFilter(accentBlue)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { onClick() }
        }
    }

    private fun createCircleTextButton(
        label: String,
        active: Boolean,
        style: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 15f
            setTypeface(null, style)
            gravity = Gravity.CENTER
            setTextColor(if (active) Color.WHITE else accentBlue)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                setMargins(dp(7), dp(2), dp(7), dp(2))
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (active) accentBlue else Color.WHITE)
                setStroke(dp(1), if (active) accentBlue else Color.parseColor("#E2E8F0"))
            }
            elevation = dp(2).toFloat()
            setOnClickListener { onClick() }
        }
    }

    private fun cardPillDrawable(active: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            setColor(if (active) Color.parseColor("#E0F2FE") else Color.WHITE)
            cornerRadius = dp(999).toFloat()
            setStroke(dp(1), if (active) accentBlue else Color.parseColor("#DCE3EC"))
        }
    }

    private fun addPillButtonRow(parent: LinearLayout, items: List<Pair<String, () -> Unit>>) {
        val hs = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        for ((title, action) in items) {
            val pill = TextView(context).apply {
                text = title
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(accentBlue)
                gravity = Gravity.CENTER
                background = cardPillDrawable(false)
                elevation = dp(1).toFloat()
                setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(4), dp(2), dp(4), dp(2)) }
                setOnClickListener { action() }
            }
            row.addView(pill)
        }
        hs.addView(row)
        parent.addView(hs)
    }

    private fun addSwatchStrip(parent: LinearLayout, label: String, onPick: (Int) -> Unit) {
        parent.addView(TextView(context).apply {
            text = label
            textSize = 11.5f
            setTextColor(Color.parseColor("#666666"))
            setPadding(dp(2), dp(4), 0, dp(4))
        })
        val stripFrame = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#A9A9A9"))
                cornerRadius = dp(6).toFloat()
            }
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        val hs = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val customBtn = TextView(context).apply {
            text = "+"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { setMargins(dp(3), dp(2), dp(5), dp(2)) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#CBD5E1"))
            }
            setOnClickListener {
                Dialogs.showColorPickerDialog(context, Color.parseColor("#0079FF")) { picked ->
                    onPick(picked)
                }
            }
        }
        row.addView(customBtn)
        for (c in AppDefaults.defaultColors) {
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { setMargins(dp(4), dp(2), dp(4), dp(2)) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(dp(1), Color.parseColor("#888888"))
                }
                setOnClickListener { onPick(c) }
            }
            row.addView(dot)
        }
        hs.addView(row)
        stripFrame.addView(hs)
        parent.addView(stripFrame)
    }
}
