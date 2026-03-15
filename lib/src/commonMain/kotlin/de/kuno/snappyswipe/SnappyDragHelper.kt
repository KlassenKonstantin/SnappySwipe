package de.kuno.snappyswipe

import kotlin.math.absoluteValue

class SnappyDragHelper(
    private val key: Any,
    private val unstickDistance: Float,
    private val restickDistance: Float,
    private val dragDirection: DragDirection,
    private val overdrag: Overdrag,
    private val overdragFriction: Float = 10f,
    private val onRestick: () -> Unit = { },
    private val onUnstick: () -> Unit = { },
) {
    var dragInfo: SnappyDraggedItemInfo? = null

    fun onDragStarted(
        initialOffset: Float,
    ) = SnappyDraggedItemInfo(
        key = key,
        dragOffset = initialOffset.restrictByDragDirection(dragDirection),
        stuck = initialOffset.absoluteValue < unstickDistance,
        unstuckProgress = (initialOffset.absoluteValue / unstickDistance).coerceAtMost(1f)
    ).also {
        dragInfo = it
    }

    private fun Float.restrictByDragDirection(
        dragDirection: DragDirection
    ) = when (dragDirection) {
        DragDirection.Left -> coerceAtMost(overdrag.maxOffset * overdragFriction)
        DragDirection.Right -> coerceAtLeast(-overdrag.maxOffset * overdragFriction)
        else -> this
    }

    fun updateDragInfo(dragDelta: Float): SnappyDraggedItemInfo {
        val currentDragInfo = requireNotNull(dragInfo)

        val newDragOffset = (currentDragInfo.dragOffset + dragDelta).restrictByDragDirection(dragDirection)

        val newStuck = if (currentDragInfo.stuck) {
            val isOverDragging = newDragOffset < 0f && dragDirection == DragDirection.Right || newDragOffset > 0f && dragDirection == DragDirection.Left
            if (isOverDragging) true else newDragOffset.absoluteValue < unstickDistance
        } else {
            newDragOffset.absoluteValue < restickDistance
        }

        if (newStuck != currentDragInfo.stuck) {
            if (newStuck) {
                onRestick()
            } else {
                onUnstick()
            }
        }

        val newUnstuckProgress = if (newStuck) {
            (newDragOffset / unstickDistance).coerceIn(0f, 1f)
        } else {
            (newDragOffset / restickDistance).coerceIn(0f, 1f)
        }

        return currentDragInfo.copy(
            dragOffset = newDragOffset,
            stuck = newStuck,
            unstuckProgress = newUnstuckProgress
        ).also {
            dragInfo = it
        }
    }

    fun reset() {
        dragInfo = null
    }
}
