package de.kuno.snappyswipe

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@Composable
fun SnappyItem(
    key: Any,
    dragCoordinatorState: DragCoordinatorState<SnappyDraggedItemInfo>,
    modifier: Modifier = Modifier,
    affectedNeighbors: Int = SnappySwipeDefaults.AffectedNeighbors,
    snappyDragState: SnappyDragState = rememberSnappyDragState(),
    content: @Composable SnappyItemScope.() -> Unit,
) {
    val itemState = remember(key, snappyDragState) {
        { dragCoordinatorState.getItemState(key) }
    }

    val draggedKey = {
        itemState()?.draggedItemRelation?.draggedItemInfo?.key
    }

    LaunchedEffect(key, snappyDragState) {
        snapshotFlow { snappyDragState.offset }.collect {
            launch {
                dragCoordinatorState.updateOffset(key, it)
            }
        }
    }

    LaunchedEffect(key, affectedNeighbors, snappyDragState) {
        combine(
            snapshotFlow { snappyDragState.ignoreCoordinator },
            snapshotFlow { itemState() }.filterNotNull()
        ) { _, itemState ->
            launch(Dispatchers.Main.immediate) {
                if (itemState.draggedItemRelation == null) {
                    snappyDragState.animateTo(0f, spring())
                } else {
                    snappyDragState.updateOffset(itemState, affectedNeighbors)
                }
            }
        }.collect()
    }

    DisposableEffect(key) {
        onDispose {
            snappyDragState.reset()
            if (draggedKey() == key) {
                dragCoordinatorState.dragInfo = null
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged {
                snappyDragState.updateWidth(it.width)
            }.draggable(
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    if (draggedKey() == null) {
                        dragCoordinatorState.dragInfo = snappyDragState.onDragStarted(key)
                    }
                },
                state = rememberDraggableState {
                    if (draggedKey() == key) {
                        dragCoordinatorState.dragInfo = snappyDragState.onDragUpdated(it)
                    }
                },
                onDragStopped = { velocity ->
                    if (draggedKey() == key) {
                        dragCoordinatorState.dragInfo = null
                        snappyDragState.onDragStopped(velocity)
                    }
                }
            ),
    ) {
        Box(
            modifier = Modifier.offset {
                IntOffset(
                    snappyDragState.offset.toInt(),
                    0
                )
            },
        ) {
            val scope = remember(key, dragCoordinatorState) {
                SnappyItemScopeImpl(key, dragCoordinatorState)
            }
            with(scope) {
                content()
            }
        }
    }
}

@Immutable
data class SnappyDragSettings(
    val unstickDistance: Dp,
    val restickDistance: Dp,
    val friction: Float,
    val enabledDragDirection: EnabledDragDirection,
    val overdrag: Overdrag,
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
    val unstickProgress: Float,
    val stuck: Boolean,
) : DraggedItemInfo

enum class EnabledDragDirection {
    Left, Right, Both
}

@Immutable
data class Overdrag(
    val friction: Float,
    val maxOffset: Float,
)

interface SnappyItemScope {
    @Stable
    @Composable
    fun Modifier.dragShape(
        settings: DragShapeSettings = SnappySwipeDefaults.shapeSettings()
    ): Modifier
}

internal class SnappyItemScopeImpl(
    private val key: Any,
    private val dragCoordinatorState: DragCoordinatorState<out DraggedItemInfo>,
) : SnappyItemScope {
    @Composable
    override fun Modifier.dragShape(
        settings: DragShapeSettings
    ) = this.dragShape(
        key = key,
        dragCoordinatorState = dragCoordinatorState,
        settings = settings,
    )
}

enum class Direction {
    Left, Right
}