package com.imaginstudio.imagetools.pixellab.undo

import com.imaginstudio.imagetools.pixellab.model.ProjectState

class HistoryManager(private val limit: Int = 150) {
    private val undoStack = ArrayDeque<ProjectState>()
    private val redoStack = ArrayDeque<ProjectState>()

    fun push(snapshot: ProjectState) {
        if (undoStack.lastOrNull() == snapshot) return
        if (undoStack.size >= limit) {
            undoStack.removeFirst()
        }
        undoStack.addLast(snapshot)
        redoStack.clear()
    }

    fun undo(current: ProjectState): ProjectState? {
        if (undoStack.isEmpty()) return null
        val prev = undoStack.removeLast()
        if (redoStack.size >= limit) {
            redoStack.removeFirst()
        }
        redoStack.addLast(current)
        return prev
    }

    fun redo(current: ProjectState): ProjectState? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        if (undoStack.size >= limit) {
            undoStack.removeFirst()
        }
        undoStack.addLast(current)
        return next
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
