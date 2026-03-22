package de.kuno.snappyswipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import kotlin.math.absoluteValue

@Stable
class SnappyDragState(
    val dragSettings: SnappyDragSettings,
    private val hapticFeedback: HapticFeedback,
    private val onDismiss: () -> Unit = { },
    private val onRestick: () -> Unit = { },
    private val onUnstick: () -> Unit = { },
    density: Density,
) {
    private val offsetAnimatable = Animatable(0f)

    private val unstickDistance = with(density) { dragSettings.unstickDistance.toPx() }
    private val restickDistance = with(density) { dragSettings.restickDistance.toPx() }

    val offset: Float
        get() = offsetAnimatable.value

    internal var ignoreCoordinator by mutableStateOf(false)

    private var width by mutableIntStateOf(0)

    private var dragInfo: SnappyDraggedItemInfo? = null

    suspend fun animateTo(
        value: Float,
        animationSpec: FiniteAnimationSpec<Float>,
    ) {
        if (ignoreCoordinator) return

        offsetAnimatable.animateTo(
            targetValue = value,
            animationSpec = animationSpec
        )
    }

    suspend fun dismiss(
        direction: Direction,
        animationSpec: FiniteAnimationSpec<Float>,
        initialVelocity: Float = 0f,
    ) {
        ignoreCoordinator = true

        offsetAnimatable.animateTo(
            targetValue = when (direction) {
                Direction.Left -> -width.toFloat()
                Direction.Right -> width.toFloat()
            },
            animationSpec = animationSpec,
            initialVelocity = initialVelocity
        )

        onDismiss()
    }

    fun updateWidth(width: Int) {
        this.width = width
    }

    suspend fun updateOffset(
        itemState: ItemState<SnappyDraggedItemInfo>,
        affectedNeighbors: Int
    ) {
        if (ignoreCoordinator) return

        val draggedItemRelation = requireNotNull(itemState.draggedItemRelation)
        val dragOffset = draggedItemRelation.draggedItemInfo.dragOffset
        val isAffected = draggedItemRelation.sameSegmentAsDraggedItem && draggedItemRelation.indexDelta.absoluteValue <= affectedNeighbors
        val isOverdragging = dragSettings.enabledDragDirection == EnabledDragDirection.Left && dragOffset > 0 || dragSettings.enabledDragDirection == EnabledDragDirection.Right && dragOffset < 0

        val draggedItemOffset = if (isOverdragging) {
            dragOffset / dragSettings.overdrag.friction
        } else {
            dragOffset / if (draggedItemRelation.draggedItemInfo.stuck) dragSettings.friction else 1f
        }

        val offset = when {
            itemState.isDraggedItem -> draggedItemOffset

            // Is one of the affected neighbors. The higher the distance to the dragged item, the less the offset
            draggedItemRelation.draggedItemInfo.stuck && isAffected -> draggedItemOffset / (affectedNeighbors + 1) * ((affectedNeighbors + 1) - draggedItemRelation.indexDelta.absoluteValue)

            // Reset
            else -> 0f
        }

        if (itemState.isDraggedItem) {
            animateTo(
                offset,
                dragSettings.draggedItemOffsetAnimationSpec,
            )
        } else {
            animateTo(
                offset,
                dragSettings.offsetAnimationSpec,
            )
        }
    }

    internal fun onDragStarted(
        key: Any,
    ) = SnappyDraggedItemInfo(
        key = key,
        dragOffset = offsetAnimatable.value.restrictByDragDirection(dragSettings.enabledDragDirection),
        stuck = offsetAnimatable.value.absoluteValue < unstickDistance,
        unstuckProgress = (offsetAnimatable.value.absoluteValue / unstickDistance).coerceAtMost(1f)
    ).also {
        ignoreCoordinator = false
        dragInfo = it
    }

    internal fun onDragUpdated(dragDelta: Float): SnappyDraggedItemInfo? {
        val currentDragInfo = dragInfo ?: return null

        val newDragOffset = (currentDragInfo.dragOffset + dragDelta).restrictByDragDirection(dragSettings.enabledDragDirection)

        val newStuck = if (currentDragInfo.stuck) {
            val isOverDragging = newDragOffset < 0f && dragSettings.enabledDragDirection == EnabledDragDirection.Right || newDragOffset > 0f && dragSettings.enabledDragDirection == EnabledDragDirection.Left
            if (isOverDragging) true else newDragOffset.absoluteValue < unstickDistance
        } else {
            newDragOffset.absoluteValue < restickDistance
        }

        if (newStuck != currentDragInfo.stuck) {
            if (newStuck) {
                dragSettings.unstickHapticFeedbackType?.let { hapticFeedback.performHapticFeedback(it) }
                onRestick()
            } else {
                dragSettings.restickHapticFeedbackType?.let { hapticFeedback.performHapticFeedback(it) }
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

    internal suspend fun onDragStopped(velocity: Float) {
        val currentDragInfo = dragInfo ?: return

        val dismissRight =
            dragSettings.enabledDragDirection != EnabledDragDirection.Left && velocity >= DISMISS_MIN_VELOCITY ||
                    velocity >= 0f && !currentDragInfo.stuck && currentDragInfo.dragOffset >= 0f
        val dismissLeft =
            dragSettings.enabledDragDirection != EnabledDragDirection.Right && velocity <= -DISMISS_MIN_VELOCITY ||
                    velocity <= 0f && !currentDragInfo.stuck && currentDragInfo.dragOffset <= 0f

        if (dismissRight || dismissLeft) {
            if (currentDragInfo.stuck) {
                dragSettings.unstickHapticFeedbackType?.let(hapticFeedback::performHapticFeedback)
            }
            dismiss(
                direction = if (dismissRight) Direction.Right else Direction.Left,
                animationSpec = dragSettings.draggedItemOffsetAnimationSpec,
                initialVelocity = velocity
            )
            onDismiss()
        }
    }

    fun reset() {
        ignoreCoordinator = false
        dragInfo = null
    }

    private fun Float.restrictByDragDirection(
        enabledDragDirection: EnabledDragDirection
    ) = when (enabledDragDirection) {
        EnabledDragDirection.Left -> coerceAtMost(dragSettings.overdrag.maxOffset * dragSettings.overdrag.friction)
        EnabledDragDirection.Right -> coerceAtLeast(-dragSettings.overdrag.maxOffset * dragSettings.overdrag.friction)
        else -> this
    }
}

@Composable
fun rememberSnappyDragState(
    dragSettings: SnappyDragSettings = SnappySwipeDefaults.settings(),
    onUnstick: () -> Unit = { },
    onRestick: () -> Unit = { },
    onDismiss: () -> Unit = { },
): SnappyDragState {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    val currentOnUnstick by rememberUpdatedState(onUnstick)
    val currentOnRestick by rememberUpdatedState(onRestick)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    return remember(
        density,
        dragSettings,
        hapticFeedback,
    ) {
        SnappyDragState(
            dragSettings = dragSettings,
            hapticFeedback = hapticFeedback,
            density = density,
            onUnstick = {
                currentOnUnstick()
            },
            onRestick = {
                currentOnRestick()
            },
            onDismiss = currentOnDismiss,
        )
    }
}

private const val DISMISS_MIN_VELOCITY = 4000f