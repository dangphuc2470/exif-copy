package com.phucdnh.exifcopy

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

// Simple model representing an item in the drag-and-drop list
data class ImageItem(
    val uri: Uri,
    val id: String = java.util.UUID.randomUUID().toString()
)

class DragDropController(
    val sourceList: MutableList<ImageItem>,
    val targetList: MutableList<ImageItem>,
    val onListChanged: () -> Unit = {}
) {
    var draggedItem by mutableStateOf<ImageItem?>(null)
    var isDraggingFromSource by mutableStateOf(false)
    var dragOffset by mutableStateOf(Offset.Zero)

    fun startDrag(item: ImageItem, fromSource: Boolean) {
        draggedItem = item
        isDraggingFromSource = fromSource
        dragOffset = Offset.Zero
    }

    fun updateDrag(offset: Offset) {
        dragOffset += offset
    }

    fun endDrag(
        dropOnTargetContainer: Boolean? = null,
        selectedIds: Set<String> = emptySet()
    ) {
        val item = draggedItem ?: return
        
        if (dropOnTargetContainer != null) {
            val isMultiple = selectedIds.contains(item.id)
            val itemsToMove = if (isMultiple) {
                if (isDraggingFromSource) {
                    sourceList.filter { selectedIds.contains(it.id) }
                } else {
                    targetList.filter { selectedIds.contains(it.id) }
                }
            } else {
                listOf(item)
            }

            for (mItem in itemsToMove) {
                if (isDraggingFromSource && dropOnTargetContainer) {
                    if (sourceList.remove(mItem)) {
                        targetList.add(mItem)
                    }
                } else if (!isDraggingFromSource && !dropOnTargetContainer) {
                    if (targetList.remove(mItem)) {
                        sourceList.add(mItem)
                    }
                }
            }
            onListChanged()
        }
        
        draggedItem = null
        dragOffset = Offset.Zero
    }

    fun moveItem(item: ImageItem, fromSource: Boolean, toSource: Boolean) {
        if (fromSource && !toSource) {
            if (sourceList.remove(item)) {
                targetList.add(item)
                onListChanged()
            }
        } else if (!fromSource && toSource) {
            if (targetList.remove(item)) {
                sourceList.add(item)
                onListChanged()
            }
        }
    }

    fun reorderSource(fromIndex: Int, toIndex: Int) {
        if (fromIndex in sourceList.indices && toIndex in sourceList.indices) {
            val item = sourceList.removeAt(fromIndex)
            sourceList.add(toIndex, item)
            onListChanged()
        }
    }

    fun reorderTarget(fromIndex: Int, toIndex: Int) {
        if (fromIndex in targetList.indices && toIndex in targetList.indices) {
            val item = targetList.removeAt(fromIndex)
            targetList.add(toIndex, item)
            onListChanged()
        }
    }

    fun swapAll() {
        val temp = sourceList.toList()
        sourceList.clear()
        sourceList.addAll(targetList)
        targetList.clear()
        targetList.addAll(temp)
        onListChanged()
    }
}
