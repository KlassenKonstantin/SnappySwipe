package de.kuno.snappyswipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun SnappyItem(
    key: Any,
    dragCoordinatorState: DragCoordinatorState<SnappyDraggedItemInfo>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    settings: SnappyDragSettings = SnappySwipeDefaults.settings(),
    dragDirection: DragDirection= DragDirection.Both,
    overdrag: Overdrag = SnappySwipeDefaults.overdrag(),
    onUnstick: (() -> Unit)? = null,
    onRestick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var dismissing by remember(key) { mutableStateOf(false) }

    val offsetAnimatable = remember(key) { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    val unstickHapticFeedbackType by rememberUpdatedState(settings.unstickHapticFeedbackType)
    val restickHapticFeedbackType by rememberUpdatedState(settings.restickHapticFeedbackType)

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
                onUnstick = {
                    unstickHapticFeedbackType?.let(haptics::performHapticFeedback)
                    onUnstick?.invoke()
                },
                onRestick = {
                    restickHapticFeedbackType?.let(haptics::performHapticFeedback)
                    onRestick?.invoke()
                },
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

    LaunchedEffect(key, settings.affectedNeighbors) {
        snapshotFlow { itemState() }.filterNotNull().collect { itemState ->
            if (dismissing) return@collect

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
                    val isAffected = draggedItemRelation.sameSegmentAsDraggedItem && draggedItemRelation.indexDelta.absoluteValue <= settings.affectedNeighbors
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
                        draggedItemRelation.draggedItemInfo.stuck && isAffected -> draggedItemOffset / (settings.affectedNeighbors + 1) * ((settings.affectedNeighbors + 1) - draggedItemRelation.indexDelta.absoluteValue)

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
        }
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
                                unstickHapticFeedbackType?.let(haptics::performHapticFeedback)
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

@Immutable
data class SnappyDragSettings(
    val unstickDistance: Dp,
    val restickDistance: Dp,
    val friction: Float,
    val affectedNeighbors: Int,
    val offsetAnimationSpec: FiniteAnimationSpec<Float>,
    val draggedItemOffsetAnimationSpec: FiniteAnimationSpec<Float>,
    val unstickHapticFeedbackType: HapticFeedbackType?,
    val restickHapticFeedbackType: HapticFeedbackType?,
)

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

@Immutable
data class SnappyDraggedItemInfo(
    override val key: Any,
    override val dragOffset: Float,
    val unstuckProgress: Float,
    val stuck: Boolean,
) : DraggedItemInfo

enum class DragDirection {
    Left, Right, Both
}

@Immutable
data class Overdrag(
    val friction: Float,
    val maxOffset: Float,
)

private const val DISMISS_MIN_VELOCITY = 4000f
