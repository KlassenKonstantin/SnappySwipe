package de.kuno.snappyswipe

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.dragShape(
    key: Any,
    dragCoordinatorState: DragCoordinatorState<out DraggedItemInfo>,
    settings: DragShapeSettings = rememberDragShapeSettings(),
): Modifier {
    val shapeHelper = rememberShapeHelper(
        itemState = { dragCoordinatorState.getItemState(key) },
        settings = settings,
    )

    return graphicsLayer {
        this.shape = shapeHelper.shape
        clip = true
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
fun rememberDragShapeSettings(
    minCornerRadius: Dp = 0.dp,
    maxCornerRadius: Dp = 16.dp,
    maxCornerRadiusAtOffsetDelta: Dp = 48.dp,
    cornerRadiusAnimationSpec: FiniteAnimationSpec<Dp> = spring(),
): DragShapeSettings {
    return remember {
        DragShapeSettings(
            minCornerRadius = minCornerRadius,
            maxCornerRadius = maxCornerRadius,
            maxCornerRadiusAtOffsetDelta = maxCornerRadiusAtOffsetDelta,
            cornerRadiusAnimationSpec = cornerRadiusAnimationSpec,
        )
    }.apply {
        this.minCornerRadius = minCornerRadius
        this.maxCornerRadius = maxCornerRadius
        this.maxCornerRadiusAtOffsetDelta = maxCornerRadiusAtOffsetDelta
        this.cornerRadiusAnimationSpec = cornerRadiusAnimationSpec
    }
}

@Stable
class DragShapeSettings internal constructor(
    minCornerRadius: Dp = 0.dp,
    maxCornerRadius: Dp = 16.dp,
    maxCornerRadiusAtOffsetDelta: Dp = 48.dp,
    cornerRadiusAnimationSpec: FiniteAnimationSpec<Dp> = spring(),
) {
    var minCornerRadius by mutableStateOf(minCornerRadius)
    var maxCornerRadius by mutableStateOf(maxCornerRadius)
    var maxCornerRadiusAtOffsetDelta by mutableStateOf(maxCornerRadiusAtOffsetDelta)
    var cornerRadiusAnimationSpec by mutableStateOf(cornerRadiusAnimationSpec)
}
