package de.kuno.snappyswipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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
    modifier: Modifier = Modifier,
    dragCoordinatorState: DragCoordinatorState<SnappyDraggedItemInfo>,
    onDismissed: () -> Unit,
    settings: SnappyDragSettings,
    content: @Composable BoxScope.(() -> Shape) -> Unit,
) {
    var dismissing by remember(key) { mutableStateOf(false) }

    // Insertion animation state
    val isNewlyInserted = remember(key) {
        dragCoordinatorState.consumeInsertionFlag(key)
    }
    val insertionProgress = remember(key) {
        Animatable(if (isNewlyInserted) 0f else 1f)
    }

    if (isNewlyInserted) {
        LaunchedEffect(key) {
            insertionProgress.animateTo(
                targetValue = 1f,
                animationSpec = settings.insertionAnimationSpec
            )
        }
    }

    val offsetAnimatable = remember(key) { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    val snappyDragHelper = remember(
        key,
        settings.unstickDistance,
        settings.restickDistance
    ) {
        density.run {
            SnappyDragHelper(
                key = key,
                unstickDistance = settings.unstickDistance.toPx(),
                restickDistance = settings.restickDistance.toPx(),
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
        snapshotFlow { offsetAnimatable.value }.collect { offset ->
            dragCoordinatorState.updateOffset(key, offset)
        }
    }

    val itemState = remember(key, dragCoordinatorState) {
        { dragCoordinatorState.getItemState(key) }
    }

    val minCornerRadiusProvider = remember(settings) { { settings.minCornerRadius } }
    val maxCornerRadiusProvider = remember(settings) { { settings.maxCornerRadius } }
    val maxAtOffsetDeltaProvider = remember(settings) { { settings.unstickDistance / 2 } }
    val animationSpecProvider = remember(settings) { { settings.cornerRadiusAnimationSpec } }

    val shapeHelper = rememberShapeHelper(
        minCornerRadius = minCornerRadiusProvider,
        maxCornerRadius = maxCornerRadiusProvider,
        maxAtOffsetDelta = maxAtOffsetDeltaProvider,
        animationSpec = animationSpecProvider,
        itemState = itemState,
    )

    LaunchedEffect(Unit) {
        if (dragCoordinatorState.dragInfo?.key == key) {
            dragCoordinatorState.dragInfo = null
        }
    }

    LaunchedEffect(key) {
        combine(
            snapshotFlow { itemState() }.filterNotNull(),
            snapshotFlow { settings.affectedNeighbours },
            snapshotFlow { dismissing },
        ) { itemState, affectedNeighbours, isDismissing ->
            if (isDismissing) return@combine

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
                    val isAffected = draggedItemRelation.sameSegmentAsDraggedItem && draggedItemRelation.indexDelta.absoluteValue <= affectedNeighbours

                    val offset = when {
                        // Follow the drag offset. Add friction if stuck
                        itemState.isDraggedItem -> dragOffset / if (draggedItemRelation.draggedItemInfo.stuck) settings.friction else 1f

                        // Is one of the affected neighbours. The higher the distance to the dragged item, the less the offset
                        draggedItemRelation.draggedItemInfo.stuck && isAffected -> dragOffset / (affectedNeighbours + 1) * ((affectedNeighbours + 1) - draggedItemRelation.indexDelta.absoluteValue) / settings.friction

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
            if (draggedKey() == key) {
                snappyDragHelper.reset()
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
                            velocity >= DISMISS_MIN_VELOCITY || velocity >= 0f && !dragInfo.stuck && dragInfo.dragOffset >= 0f
                        val dismissLeft =
                            velocity <= -DISMISS_MIN_VELOCITY || velocity <= 0f && !dragInfo.stuck && dragInfo.dragOffset <= 0f

                        if (dismissRight || dismissLeft) {
                            if (dragInfo.stuck) {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                            dismissing = true
                            offsetAnimatable.animateTo(
                                targetValue = (if (dismissRight) width else -width).toFloat(),
                                initialVelocity = velocity,
                            )
                            onDismissed()
                        }
                    }
                }
            ),
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        offsetAnimatable.value.toInt(),
                        0
                    )
                }
                .graphicsLayer {
                    val progress = insertionProgress.value
                    if (progress < 1f) {
                        // Slide in from the left
                        translationX = (1f - progress) * -size.width * 0.6f
                        // Scale up vertically for a "growing in" effect
                        scaleY = 0.6f + 0.4f * progress
                        scaleX = 0.85f + 0.15f * progress
                        // Fade in
                        alpha = progress.coerceIn(0f, 1f)
                    }
                },
        ) {
            content({ shapeHelper.shape })
        }
    }
}

@Stable
class SnappyDragSettings(
    unstickDistance: Dp,
    restickDistance: Dp,
    minCornerRadius: Dp,
    maxCornerRadius: Dp,
    affectedNeighbours: Int,
    offsetAnimationSpec: FiniteAnimationSpec<Float>,
    draggedItemOffsetAnimationSpec: FiniteAnimationSpec<Float>,
    cornerRadiusAnimationSpec: FiniteAnimationSpec<Dp>,
    friction: Float,
    insertionAnimationSpec: AnimationSpec<Float>,
) {
    var unstickDistance by mutableStateOf(unstickDistance)
    var restickDistance by mutableStateOf(restickDistance)
    var minCornerRadius by mutableStateOf(minCornerRadius)
    var maxCornerRadius by mutableStateOf(maxCornerRadius)
    var affectedNeighbours by mutableIntStateOf(affectedNeighbours)
    var friction by mutableFloatStateOf(friction)
    var offsetAnimationSpec by mutableStateOf(offsetAnimationSpec)
    var draggedItemOffsetAnimationSpec by mutableStateOf(draggedItemOffsetAnimationSpec)
    var cornerRadiusAnimationSpec by mutableStateOf(cornerRadiusAnimationSpec)
    var insertionAnimationSpec by mutableStateOf(insertionAnimationSpec)
}

@Composable
fun rememberSnappyDragSettings(
    unstickDistance: Dp = 100.dp,
    restickDistance: Dp = 50.dp,
    minCornerRadius: Dp = 0.dp,
    maxCornerRadius: Dp = 24.dp,
    affectedNeighbours: Int = 2,
    offsetAnimationSpec: FiniteAnimationSpec<Float> = spring(),
    draggedItemOffsetAnimationSpec: FiniteAnimationSpec<Float> = spring(),
    cornerRadiusAnimationSpec: FiniteAnimationSpec<Dp> = spring(),
    friction: Float = 2f,
    insertionAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    ),
): SnappyDragSettings {
    return remember {
        SnappyDragSettings(
            unstickDistance = unstickDistance,
            restickDistance = restickDistance,
            minCornerRadius = minCornerRadius,
            maxCornerRadius = maxCornerRadius,
            affectedNeighbours = affectedNeighbours,
            offsetAnimationSpec = offsetAnimationSpec,
            draggedItemOffsetAnimationSpec = draggedItemOffsetAnimationSpec,
            cornerRadiusAnimationSpec = cornerRadiusAnimationSpec,
            friction = friction,
            insertionAnimationSpec = insertionAnimationSpec,
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

data class SnappyDraggedItemInfo(
    override val key: Any,
    override val dragOffset: Float,
    val unstuckProgress: Float,
    val stuck: Boolean,
) : DraggedItemInfo

private const val DISMISS_MIN_VELOCITY = 4000f
