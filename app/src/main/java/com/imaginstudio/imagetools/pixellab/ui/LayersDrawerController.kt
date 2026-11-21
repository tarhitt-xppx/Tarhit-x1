package com.imaginstudio.imagetools.pixellab.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.imaginstudio.imagetools.pixellab.databinding.ActivityMainBinding
import com.imaginstudio.imagetools.pixellab.databinding.ItemLayerBinding
import com.imaginstudio.imagetools.pixellab.engine.PixelLabRenderer
import com.imaginstudio.imagetools.pixellab.model.CanvasLayer
import com.imaginstudio.imagetools.pixellab.model.ImageLayer
import com.imaginstudio.imagetools.pixellab.model.ProjectState
import com.imaginstudio.imagetools.pixellab.model.TextLayer
import java.io.File
import java.io.FileOutputStream

class LayersDrawerController(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val getProject: () -> ProjectState,
    private val getSelectedId: () -> String?,
    private val onSelectLayer: (String?) -> Unit,
    private val onMutateProject: ((ProjectState) -> ProjectState) -> Unit
) {
    private var multiSelectMode = false
    private val checkedIds = LinkedHashSet<String>()

    init {
        binding.btnCloseLayers.setOnClickListener {
            binding.layersDrawer.visibility = View.GONE
        }

        binding.btnLayersMultiSelect.setOnClickListener {
            multiSelectMode = !multiSelectMode
            checkedIds.clear()
            binding.btnLayersMultiSelect.text = if (multiSelectMode) "Done" else "Select"
            binding.layersBatchBar.visibility = if (multiSelectMode) View.VISIBLE else View.GONE
            refresh()
        }

        binding.btnBatchHide.setOnClickListener {
            if (checkedIds.isEmpty()) return@setOnClickListener
            onMutateProject { proj ->
                val anyVisible = proj.layers.any { it.id in checkedIds && !it.hidden }
                proj.copy(layers = proj.layers.map {
                    if (it.id in checkedIds) it.copyBase(hidden = anyVisible) else it
                })
            }
        }

        binding.btnBatchLock.setOnClickListener {
            if (checkedIds.isEmpty()) return@setOnClickListener
            onMutateProject { proj ->
                val anyUnlocked = proj.layers.any { it.id in checkedIds && !it.locked }
                proj.copy(layers = proj.layers.map {
                    if (it.id in checkedIds) it.copyBase(locked = anyUnlocked) else it
                })
            }
        }

        binding.btnBatchDelete.setOnClickListener {
            if (checkedIds.isEmpty()) return@setOnClickListener
            onMutateProject { proj ->
                proj.copy(layers = proj.layers.filterNot { it.id in checkedIds })
            }
            checkedIds.clear()
            onSelectLayer(null)
        }

        binding.btnBatchMerge.setOnClickListener {
            if (checkedIds.size < 2) {
                Toast.makeText(context, "Select 2 or more layers to merge", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val proj = getProject()
            val selectedLayers = proj.layers.filter { it.id in checkedIds }
            if (selectedLayers.size < 2) return@setOnClickListener

            val tempProject = proj.copy(layers = selectedLayers)
            val mergedBmp = PixelLabRenderer.exportToBitmap(
                context,
                tempProject,
                proj.width,
                proj.height,
                includeBackground = false
            )
            val mergeFile = File(context.cacheDir, "merged_${System.currentTimeMillis()}.png")
            FileOutputStream(mergeFile).use { out ->
                mergedBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            mergedBmp.recycle()

            val mergedLayer = ImageLayer(
                id = "merged_${System.currentTimeMillis()}",
                name = "Merged Layer",
                x = proj.width / 2f,
                y = proj.height / 2f,
                width = proj.width.toFloat(),
                height = proj.height.toFloat(),
                uriString = mergeFile.absolutePath
            )

            onMutateProject { p ->
                val remaining = p.layers.filterNot { it.id in checkedIds }.toMutableList()
                remaining.add(mergedLayer)
                p.copy(layers = remaining)
            }
            checkedIds.clear()
            multiSelectMode = false
            binding.btnLayersMultiSelect.text = "Select"
            binding.layersBatchBar.visibility = View.GONE
            onSelectLayer(mergedLayer.id)
        }
    }

    fun toggleDrawer() {
        val visible = binding.layersDrawer.visibility == View.VISIBLE
        binding.layersDrawer.visibility = if (visible) View.GONE else View.VISIBLE
        if (!visible) refresh()
    }

    fun refresh() {
        if (binding.layersDrawer.visibility != View.VISIBLE) return
        val container = binding.layersContainer
        container.removeAllViews()
        val project = getProject()
        val selectedId = getSelectedId()
        val inflater = LayoutInflater.from(context)

        // Reverse order so front-most layer appears on top
        val layersRev = project.layers.indices.reversed()
        for (idx in layersRev) {
            val layer = project.layers[idx]
            val item = ItemLayerBinding.inflate(inflater, container, false)
            item.layerName.text = when (layer) {
                is TextLayer -> layer.text.replace("\n", " ").ifBlank { "Text" }
                else -> layer.name
            }

            if (layer.id == selectedId) {
                item.root.setBackgroundColor(Color.parseColor("#E0F2FE"))
            }

            item.layerCheck.visibility = if (multiSelectMode) View.VISIBLE else View.GONE
            item.layerCheck.isChecked = layer.id in checkedIds
            item.layerCheck.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checkedIds.add(layer.id) else checkedIds.remove(layer.id)
            }

            item.btnLayerHide.alpha = if (layer.hidden) 0.35f else 1f
            item.btnLayerLock.alpha = if (layer.locked) 1f else 0.4f

            item.root.setOnClickListener {
                if (multiSelectMode) {
                    item.layerCheck.isChecked = !item.layerCheck.isChecked
                } else {
                    onSelectLayer(layer.id)
                }
            }

            item.btnLayerUp.setOnClickListener {
                onMutateProject { p ->
                    val list = p.layers.toMutableList()
                    val pos = list.indexOfFirst { it.id == layer.id }
                    if (pos in 0 until list.lastIndex) {
                        val tmp = list[pos]
                        list[pos] = list[pos + 1]
                        list[pos + 1] = tmp
                    }
                    p.copy(layers = list)
                }
            }

            item.btnLayerDown.setOnClickListener {
                onMutateProject { p ->
                    val list = p.layers.toMutableList()
                    val pos = list.indexOfFirst { it.id == layer.id }
                    if (pos > 0) {
                        val tmp = list[pos]
                        list[pos] = list[pos - 1]
                        list[pos - 1] = tmp
                    }
                    p.copy(layers = list)
                }
            }

            item.btnLayerHide.setOnClickListener {
                onMutateProject { p ->
                    p.copy(layers = p.layers.map {
                        if (it.id == layer.id) it.copyBase(hidden = !it.hidden) else it
                    })
                }
            }

            item.btnLayerLock.setOnClickListener {
                onMutateProject { p ->
                    p.copy(layers = p.layers.map {
                        if (it.id == layer.id) it.copyBase(locked = !it.locked) else it
                    })
                }
            }

            item.btnLayerDelete.setOnClickListener {
                onMutateProject { p ->
                    p.copy(layers = p.layers.filterNot { it.id == layer.id })
                }
                if (selectedId == layer.id) onSelectLayer(null)
            }

            container.addView(item.root)
        }
    }
}
