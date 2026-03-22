package de.kuno.snappyswipe

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object SnappySwipeDefaults {
    val UnstickDistance = 100.dp
    val RestickDistance = 50.dp

    val UnstickHapticFeedbackType = HapticFeedbackType.Confirm
    val RestickHapticFeedbackType = HapticFeedbackType.SegmentTick

    const val Friction = 2f
    const val AffectedNeighbors = 2
    const val DismissMinVelocity = 4000f // Todo, not yet used

    const val OverdragFriction = 15f
    val OverdragMaxOffset = 16.dp

    val MinRadius = 0.dp
    val MaxRadius = 24.dp
    val MaxCornerRadiusAtOffsetDelta = 48.dp

    /**
     * Drag settings for the [SnappyDragState].
     *
     * @param unstickDistance From the edges, how far the item can be dragged before it unsticks
     * from its neighbors.
     * @param restickDistance Distance from the edges at which the item will restick to its neighbors.
     * @param friction Added friction to the dragged item when it is stuck to its neighbors.
     * A value of 2f means that the dragged item moves at half the drag amount.
     * @param enabledDragDirection Which drag directions are allowed.
     * @param overdrag The overdrag settings for the item.
     * @param offsetAnimationSpec The animation spec used to animate the offset of affected items.
     * @param draggedItemOffsetAnimationSpec The animation spec used to animate the offset of the
     * @param unstickHapticFeedbackType The haptic feedback type to use when the item is unstuck
     * @param restickHapticFeedbackType The haptic feedback type to use when the item is restuck
     * dragged item.
     */
    @Composable
    fun settings(
        unstickDistance: Dp = UnstickDistance,
        restickDistance: Dp = RestickDistance,
        friction: Float = Friction,
        enabledDragDirection: EnabledDragDirection= EnabledDragDirection.Right,
        overdrag: Overdrag= overdrag(),
        offsetAnimationSpec: FiniteAnimationSpec<Float> = spring(),
        draggedItemOffsetAnimationSpec: FiniteAnimationSpec<Float> = spring(),
        unstickHapticFeedbackType: HapticFeedbackType? = UnstickHapticFeedbackType,
        restickHapticFeedbackType: HapticFeedbackType? = RestickHapticFeedbackType
    ): SnappyDragSettings {
        return remember(
            unstickDistance,
            restickDistance,
            friction,
            enabledDragDirection,
            overdrag,
            offsetAnimationSpec,
            draggedItemOffsetAnimationSpec,
            unstickHapticFeedbackType,
            restickHapticFeedbackType,
        ) {
            SnappyDragSettings(
                unstickDistance = unstickDistance,
                restickDistance = restickDistance,
                friction = friction,
                enabledDragDirection = enabledDragDirection,
                overdrag = overdrag,
                offsetAnimationSpec = offsetAnimationSpec,
                draggedItemOffsetAnimationSpec = draggedItemOffsetAnimationSpec,
                unstickHapticFeedbackType = unstickHapticFeedbackType,
                restickHapticFeedbackType = restickHapticFeedbackType
            )
        }
    }

    /**
     * Creates and remembers a [DragShapeSettings] instance.
     *
     * @param minCornerRadius The minimum corner radius of the item used when the offset delta to its neighbors is 0f.
     * @param maxCornerRadius The maximum corner radius of the item used when the offset delta to its neighbors is >= [maxCornerRadiusAtOffsetDelta]
     * or the segment type of its neighbor is different.
     * @param maxCornerRadiusAtOffsetDelta The offset delta to its neighbors from which the [maxCornerRadius] is used.
     * @param cornerRadiusAnimationSpec The animation spec used to animate the corner radius of the item.
     */
    @Composable
    fun shapeSettings(
        minCornerRadius: Dp = MinRadius,
        maxCornerRadius: Dp = MaxRadius,
        maxCornerRadiusAtOffsetDelta: Dp = MaxCornerRadiusAtOffsetDelta,
        cornerRadiusAnimationSpec: FiniteAnimationSpec<Dp> = spring(),
    ): DragShapeSettings {
        return remember(
            minCornerRadius,
            maxCornerRadius,
            maxCornerRadiusAtOffsetDelta,
            cornerRadiusAnimationSpec,
        ) {
            DragShapeSettings(
                minCornerRadius = minCornerRadius,
                maxCornerRadius = maxCornerRadius,
                maxCornerRadiusAtOffsetDelta = maxCornerRadiusAtOffsetDelta,
                cornerRadiusAnimationSpec = cornerRadiusAnimationSpec,
            )
        }
    }

    /**
     * Overdrag settings for the [SnappyItem].
     * @param friction The friction of the overdrag.
     * @param maxOffset The maximum offset of the overdrag. Set to 0 to disable overdrag.
     */
    @Composable
    internal fun overdrag(
        friction: Float = OverdragFriction,
        maxOffset: Dp = OverdragMaxOffset
    ): Overdrag {
        val density = LocalDensity.current

        return remember(density) {
            Overdrag(
                friction = friction,
                maxOffset = density.run { maxOffset.toPx() }
            )
        }
    }
}