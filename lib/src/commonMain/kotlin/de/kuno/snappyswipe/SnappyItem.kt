package de.kuno.snappyswipe

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
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
    backgroundLeft: SnappyBackground? = null,
    backgroundRight: SnappyBackground? = null,
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
        Box(
            modifier = Modifier.matchParentSize()
                .drawBehind {
                    if (!isDragOwner) return@drawBehind

                    val offset = snappyDragState.offset
                    when (snappyDragState.dragSettings.enabledDragDirection) {
                        EnabledDragDirection.Left -> if (offset >= 0f) return@drawBehind
                        EnabledDragDirection.Right -> if (offset <= 0f) return@drawBehind
                        EnabledDragDirection.Both -> Unit
                    }

                    val swipedWidth = offset.absoluteValue
                    val gapPx = backgroundGap.toPx()
                    val drawWidth = swipedWidth - gapPx
                    if (drawWidth < 1f) return@drawBehind

                    val left = if (offset > 0f) 0f else size.width - drawWidth

                    val background = when {
                        offset < 0f -> backgroundLeft
                        offset > 0f -> backgroundRight
                        else -> null
                    } ?: return@drawBehind

                    translate(left = left, top = 0f) {
                        val drawSize = Size(drawWidth, size.height)
                        when (background.shape) {
                            SnappyBackgroundShape.Pill -> drawRoundRect(
                                color = background.containerColor,
                                size = drawSize,
                                cornerRadius = CornerRadius(size.height / 2f),
                            )

                            SnappyBackgroundShape.FollowItem -> drawOutline(
                                outline = shapeHelper.shape.createOutline(
                                    size = drawSize,
                                    layoutDirection = layoutDirection,
                                    density = this,
                                ),
                                color = background.containerColor,
                            )
                        }
                    }

                    val iconSizePx = background.iconSize.toPx()
                    if (drawWidth < iconSizePx) return@drawBehind

                    val iconLeft = left + (drawWidth - iconSizePx) / 2f
                    val iconTop = (size.height - iconSizePx) / 2f

                    val iconAlpha = drawWidth.reverseLerp(iconSizePx + gapPx, size.height)

                    translate(iconLeft, iconTop) {
                        with(background.icon) {
                            draw(
                                size = Size(iconSizePx, iconSizePx),
                                alpha = iconAlpha,
                                colorFilter = ColorFilter.tint(background.iconTint),
                            )
                        }
                    }
                }
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

/**
 * Maps a continuous Float value to a 0f..1f fraction based on a given range.
 * The result is safely clamped between 0f and 1f.
 *
 * @receiver The value to be mapped.
 * @param min The minimum value of the range.
 * @param max The maximum value of the range.
 *
 * @return The corresponding fraction between 0f and 1f.
 */
private fun Float.reverseLerp(min: Float, max: Float): Float {
    val range = max - min
    if (range == 0f) return 0f
    return ((this - min) / range).coerceIn(0f, 1f)
}

@Stable
data class SnappyBackground(
    val icon: Painter,
    val iconTint: Color,
    val containerColor: Color,
    val iconSize: Dp,
    val shape: SnappyBackgroundShape,
)

/**
 * Controls the shape used to draw a [SnappyBackground] behind a [SnappyItem].
 */
@Stable
sealed interface SnappyBackgroundShape {
    /**
     * The background adopts the item's animated drag shape, sharing the same
     * [DragShapeSettings] as the item content. Top and bottom corners animate
     * in lockstep with the item, producing a consistent "cutout" look.
     */
    data object FollowItem : SnappyBackgroundShape

    /**
     * A fully rounded pill — corner radius equals half the item height. Gives
     * a rounder, more capsule-like reveal, especially in the early phase of a
     * swipe while the revealed width is still small.
     */
    data object Pill : SnappyBackgroundShape
}

@Composable
fun rememberSnappyBackground(
    icon: Painter,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = contentColorFor(containerColor),
    iconSize: Dp = SnappySwipeDefaults.BackgroundIconSize,
    shape: SnappyBackgroundShape = SnappySwipeDefaults.BackgroundShape,
): SnappyBackground = remember(icon, iconTint, containerColor, iconSize, shape) {
    SnappyBackground(icon, iconTint, containerColor, iconSize, shape)
}

@Composable
fun rememberSnappyBackground(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = contentColorFor(containerColor),
    iconSize: Dp = SnappySwipeDefaults.BackgroundIconSize,
    shape: SnappyBackgroundShape = SnappySwipeDefaults.BackgroundShape,
): SnappyBackground {
    val painter = rememberVectorPainter(icon)
    return rememberSnappyBackground(
        icon = painter,
        containerColor = containerColor,
        iconTint = iconTint,
        iconSize = iconSize,
        shape = shape,
    )
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