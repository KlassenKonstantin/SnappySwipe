package de.kuno.snappyswipe.snappyswipe

import kotlin.math.absoluteValue

class SnappyDragHelper(
    private val key: Any?,
    private val unstickDistance: Float,
    private val restickDistance: Float,
    private val onStuck: () -> Unit = { },
    private val onUnstuck: () -> Unit = { },
) {
    var dragInfo: SnappyDraggedItemInfo? = null

    fun onDragStarted(
        initialOffset: Float,
    ) = SnappyDraggedItemInfo(
        key = key,
        dragOffset = initialOffset,
        stuck = initialOffset.absoluteValue < unstickDistance,
        unstuckProgress = (initialOffset.absoluteValue / unstickDistance).coerceAtMost(1f)
    ).also {
        dragInfo = it
    }

    fun updateDragInfo(dragDelta: Float): SnappyDraggedItemInfo {
        val currentDragInfo = requireNotNull(dragInfo)

        val newStuck = if (currentDragInfo.stuck) {
            currentDragInfo.dragOffset.absoluteValue < unstickDistance
        } else {
            currentDragInfo.dragOffset.absoluteValue < restickDistance
        }

        if (newStuck != currentDragInfo.stuck) {
            if (newStuck) {
                onStuck()
            } else {
                onUnstuck()
            }
        }

        val newDragOffset = currentDragInfo.dragOffset + dragDelta

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