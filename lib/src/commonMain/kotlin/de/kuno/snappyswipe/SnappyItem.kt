package de.kuno.snappyswipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun SnappyItem(
    key: Any,
    dragCoordinatorState: DragCoordinatorState<SnappyDraggedItemInfo>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    settings: SnappyDragSettings = rememberSnappyDragSettings(),
    dragDirection: DragDirection= DragDirection.Both,
    overdrag: Overdrag = rememberOverdrag(),
    content: @Composable BoxScope.() -> Unit,
) {
    var dismissing by remember(key) { mutableStateOf(false) }

    val offsetAnimatable = remember(key) { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    val snappyDragHelper = remember(
        key,
        settings.unstickDistance,
        settings.restickDistance,
        dragDirection
    ) {
        density.run {
            SnappyDragHelper(
                key = key,
                unstickDistance = settings.unstickDistance.toPx(),
                restickDistance = settings.restickDistance.toPx(),
                dragDirection = dragDirection,
                overdrag = overdrag,
                onStuck = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                },
                onUnstuck = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }
            )
        }
    }

    LaunchedEffect(key) {
        snapshotFlow { offsetAnimatable.value }.collect {
            launch {
                dragCoordinatorState.updateOffset(key, it)
            }
        }
    }

    val itemState = remember(key) {
        { dragCoordinatorState.getItemState(key) }
    }

    LaunchedEffect(Unit) {
        if (dragCoordinatorState.dragInfo?.key == key) {
            dragCoordinatorState.dragInfo = null
        }
    }

    LaunchedEffect(key) {
        combine(
            snapshotFlow { itemState() }.filterNotNull(),
            snapshotFlow { settings.affectedNeighbors },
        ) { itemState, affectedNeighbors ->
            if (dismissing) return@combine

            launch(Dispatchers.Main.immediate) {
                if (itemState.draggedItemRelation == null) {
                    snappyDragHelper.reset()
                    offsetAnimatable.animateTo(
                        0f,
                        settings.offsetAnimationSpec
                    )
                } else {
                    val draggedItemRelation = itemState.draggedItemRelation
                    val dragOffset = draggedItemRelation.draggedItemInfo.dragOffset
                    val isAffected = draggedItemRelation.sameSegmentAsDraggedItem && draggedItemRelation.indexDelta.absoluteValue <= affectedNeighbors
                    val isOverdragging = dragDirection == DragDirection.Left && dragOffset > 0 || dragDirection == DragDirection.Right && dragOffset < 0

                    val draggedItemOffset = if (isOverdragging) {
                        dragOffset / overdrag.friction
                    } else {
                        dragOffset / if (draggedItemRelation.draggedItemInfo.stuck) settings.friction else 1f
                    }

                    val offset = when {
                        // Follow the drag offset. Add friction if stuck
                        itemState.isDraggedItem -> draggedItemOffset

                        // Is one of the affected neighbors. The higher the distance to the dragged item, the less the offset
                        draggedItemRelation.draggedItemInfo.stuck && isAffected -> draggedItemOffset / (affectedNeighbors + 1) * ((affectedNeighbors + 1) - draggedItemRelation.indexDelta.absoluteValue)

                        // Reset
                        else -> 0f
                    }

                    if (itemState.isDraggedItem) {
                        offsetAnimatable.animateTo(
                            offset,
                            settings.draggedItemOffsetAnimationSpec
                        )
                    } else {
                        offsetAnimatable.animateTo(
                            offset,
                            settings.offsetAnimationSpec
                        )
                    }
                }
            }
        }.collect()
    }

    val draggedKey = {
        itemState()?.draggedItemRelation?.draggedItemInfo?.key
    }

    DisposableEffect(key) {
        onDispose {
            snappyDragHelper.reset()
            if (draggedKey() == key) {
                dragCoordinatorState.dragInfo = null
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged {
                width = it.width
            }.draggable(
                state = rememberDraggableState {
                    if (draggedKey() == key) {
                        snappyDragHelper.updateDragInfo(it)
                        dragCoordinatorState.dragInfo = snappyDragHelper.dragInfo
                    }
                },
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    if (draggedKey() == null) {
                        dismissing = false
                        snappyDragHelper.onDragStarted(offsetAnimatable.value)
                        dragCoordinatorState.dragInfo = snappyDragHelper.dragInfo
                    }
                },
                onDragStopped = { velocity ->
                    if (draggedKey() == key) {
                        val dragInfo = requireNotNull(snappyDragHelper.dragInfo)

                        dragCoordinatorState.dragInfo = null

                        val dismissRight =
                            dragDirection != DragDirection.Left && velocity >= DISMISS_MIN_VELOCITY || velocity >= 0f && !dragInfo.stuck && dragInfo.dragOffset >= 0f
                        val dismissLeft =
                            dragDirection != DragDirection.Right && velocity <= -DISMISS_MIN_VELOCITY || velocity <= 0f && !dragInfo.stuck && dragInfo.dragOffset <= 0f

                        if (dismissRight || dismissLeft) {
                            if (dragInfo.stuck) {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                            dismissing = true
                            offsetAnimatable.animateTo(
                                targetValue = (if (dismissRight) width else -width).toFloat(),
                                initialVelocity = velocity,
                            )
                            onDismiss()
                        }
                    }
                }
            ),
    ) {
        Box(
            modifier = Modifier.offset {
                IntOffset(
                    offsetAnimatable.value.toInt(),
                    0
                )
            },
        ) {
            content()
        }
    }
}

@Stable
class SnappyDragSettings(
    unstickDistance: Dp,
    restickDistance: Dp,
    friction: Float,
    affectedNeighbors: Int,
    offsetAnimationSpec: FiniteAnimationSpec<Float>,
    draggedItemOffsetAnimationSpec: FiniteAnimationSpec<Float>,
) {
    var unstickDistance by mutableStateOf(unstickDistance)
    var restickDistance by mutableStateOf(restickDistance)
    var friction by mutableFloatStateOf(friction)
    var affectedNeighbors by mutableIntStateOf(affectedNeighbors)
    var offsetAnimationSpec by mutableStateOf(offsetAnimationSpec)
    var draggedItemOffsetAnimationSpec by mutableStateOf(draggedItemOffsetAnimationSpec)
}

@Composable
fun rememberSnappyDragSettings(
    /**
     * From the edges, how far the item can be dragged before it unsticks from its neighbors.
     */
    unstickDistance: Dp = 100.dp,

    /**
     * Distance from the edges at which the item will restick to its neighbors.
     */
    restickDistance: Dp = 50.dp,

    /**
     * Added friction to the dragged item when it is stuck to its neighbors.
     * A value of 2f means that the dragged item moves at half the drag amount.
     */
    friction: Float = 2f,

    /**
     * How many items to the top and bottom are affected by the dragged item.
     */
    affectedNeighbors: Int = 2,

    /**
     * The animation spec used to animate the offset of affected items.
     */
    offsetAnimationSpec: FiniteAnimationSpec<Float> = spring(),

    /**
     * The animation spec used to animate the offset of the dragged item.
     */
    draggedItemOffsetAnimationSpec: FiniteAnimationSpec<Float> = spring(),
): SnappyDragSettings {
    return remember {
        SnappyDragSettings(
            unstickDistance = unstickDistance,
            restickDistance = restickDistance,
            friction = friction,
            affectedNeighbors = affectedNeighbors,
            offsetAnimationSpec = offsetAnimationSpec,
            draggedItemOffsetAnimationSpec = draggedItemOffsetAnimationSpec,
        )
    }
}

@Composable
fun <T> rememberSnappyDragCoordinatorState(
    items: List<T>,
    key: (T) -> Any,
    segmentType: (T) -> Any? = { null }
) = rememberDragCoordinatorState<SnappyDraggedItemInfo, T>(
    items = items,
    key = key,
    segmentType = segmentType,
)

@Stable
data class SnappyDraggedItemInfo(
    override val key: Any,
    override val dragOffset: Float,
    val unstuckProgress: Float,
    val stuck: Boolean,
) : DraggedItemInfo

enum class DragDirection {
    Left, Right, Both
}

@Composable
fun rememberOverdrag(
    friction: Float = 15f,
    maxOffset: Dp = 16.dp
): Overdrag {
    val density = LocalDensity.current

    return remember(density) {
        Overdrag.Enabled(
            friction = friction,
            maxOffset = density.run { maxOffset.toPx() }
        )
    }
}

sealed interface Overdrag {
    val friction: Float
    val maxOffset: Float

    @ConsistentCopyVisibility
    data class Enabled internal constructor(
        override val friction: Float,
        override val maxOffset: Float,
    ) : Overdrag

    data object None : Overdrag {
        override val friction: Float = Float.MAX_VALUE
        override val maxOffset: Float = 0f
    }
}

private const val DISMISS_MIN_VELOCITY = 4000f
