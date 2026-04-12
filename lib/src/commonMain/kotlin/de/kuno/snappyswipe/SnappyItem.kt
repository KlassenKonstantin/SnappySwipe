package de.kuno.snappyswipe

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
    modifier: Modifier = Modifier,
    affectedNeighbors: Int = SnappySwipeDefaults.AffectedNeighbors,
    snappyDragState: SnappyDragState = rememberSnappyDragState(),
    dragShapeSettings: DragShapeSettings = SnappySwipeDefaults.shapeSettings(),
    backgroundLeft: (@Composable SnappyBackgroundScope. () -> Unit)? = null,
    backgroundRight: (@Composable SnappyBackgroundScope.() -> Unit)? = null,
    backgroundShape: SnappyBackgroundShape = SnappySwipeDefaults.BackgroundShape,
    backgroundGap: Dp = SnappySwipeDefaults.BackgroundGap,
    content: @Composable SnappyItemScope.() -> Unit,
) {
    val itemState = remember(key, snappyDragState) {
        { dragCoordinatorState.getItemState(key) }
    }

    val shapeHelper = rememberShapeHelper(
        itemState = { dragCoordinatorState.getItemState(key) },
        settings = dragShapeSettings,
    )

    val draggedKey = {
        itemState()?.draggedItemRelation?.draggedItemInfo?.key
    }

    // True while this item is actively dragged OR settling after its own drag.
    // Drives background drawing so the background animates back alongside the
    // item instead of vanishing at release.
    var isDragOwner by remember(key, snappyDragState) { mutableStateOf(false) }

    LaunchedEffect(key, snappyDragState) {
        snapshotFlow { itemState() }.collect { state ->
            when {
                state == null -> Unit
                state.isDraggedItem -> isDragOwner = true
                // Another item is being dragged → this one is just a neighbor.
                state.draggedItemRelation != null -> isDragOwner = false
                // else: no active drag, keep current value so we stay true
                // while settling after our own release.
            }
        }
    }

    LaunchedEffect(key, snappyDragState) {
        snapshotFlow { snappyDragState.offset }.collect { offset ->
            if (offset == 0f) isDragOwner = false
        }
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
        propagateMinConstraints = true
    ) {
        Background(
            isDragOwner = isDragOwner,
            snappyDragState = snappyDragState,
            backgroundGap = backgroundGap,
            backgroundShape = backgroundShape,
            shapeHelper = shapeHelper,
            backgroundLeft = backgroundLeft,
            backgroundRight = backgroundRight,
        )

        Box(
            modifier = Modifier.offset {
                IntOffset(
                    snappyDragState.offset.toInt(),
                    0
                )
            },
        ) {
            val scope = remember(shapeHelper) {
                SnappyItemScopeImpl(shapeHelper)
            }
            with(scope) {
                content()
            }
        }
    }
}

@Composable
private fun BoxScope.Background(
    isDragOwner: Boolean,
    snappyDragState: SnappyDragState,
    backgroundGap: Dp,
    backgroundShape: SnappyBackgroundShape,
    shapeHelper: ShapeHelper,
    backgroundLeft: @Composable (SnappyBackgroundScope.() -> Unit)?,
    backgroundRight: @Composable (SnappyBackgroundScope.() -> Unit)?,
) {
    if (!isDragOwner) return

    val offset = snappyDragState.offset
    val directionAllowed = when (snappyDragState.dragSettings.enabledDragDirection) {
        EnabledDragDirection.Left -> offset < 0f
        EnabledDragDirection.Right -> offset > 0f
        EnabledDragDirection.Both -> offset != 0f
    }
    if (!directionAllowed) return

    val backgroundContent = when {
        offset < 0f -> backgroundLeft
        offset > 0f -> backgroundRight
        else -> null
    } ?: return

    val density = LocalDensity.current
    val gapPx = with(density) { backgroundGap.toPx() }
    val drawWidth = offset.absoluteValue - gapPx
    if (drawWidth < 0f) return

    val clipShape = when (backgroundShape) {
        SnappyBackgroundShape.Pill -> RoundedCornerShape(50)
        SnappyBackgroundShape.FollowItem -> shapeHelper.shape
    }

    val scope = remember { SnappyBackgroundScope() }
    scope.revealedWidth = drawWidth

    Box(
        modifier = Modifier
            .matchParentSize()
            .layout { measurable, constraints ->
                scope.itemHeight = constraints.maxHeight.toFloat()
                scope.itemWidth = constraints.maxWidth.toFloat()

                val widthPx = drawWidth.toInt().coerceIn(0, constraints.maxWidth)
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = widthPx,
                        maxWidth = widthPx,
                    )
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val x = if (offset > 0f) 0
                    else constraints.maxWidth - widthPx
                    placeable.place(x, 0)
                }
            }
            .clip(clipShape),
    ) {
        backgroundContent(scope)
    }
}

@Immutable
data class SnappyDragSettings(
    val unstickDistance: Dp,
    val restickDistance: Dp,
    val friction: Float,
    val neighborDragFactor: Float,
    val enabledDragDirection: EnabledDragDirection,
    val overdrag: Overdrag,
    val offsetAnimationSpec: FiniteAnimationSpec<Float>,
    val draggedItemOffsetAnimationSpec: FiniteAnimationSpec<Float>,
    val unstickHapticFeedbackType: HapticFeedbackType?,
    val restickHapticFeedbackType: HapticFeedbackType?,
) {

    init {
        require(neighborDragFactor >= 0f) { "neighborDragFactor must be greater or equal to 0f" }
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
    fun Modifier.dragShape(): Modifier
}

internal class SnappyItemScopeImpl(
    private val shapeHelper: ShapeHelper,
) : SnappyItemScope {
    override fun Modifier.dragShape(): Modifier = graphicsLayer {
        shape = shapeHelper.shape
        clip = true
    }
}

enum class Direction {
    Left, Right
}