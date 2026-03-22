package de.kuno.snappyswipe

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

@Composable
fun Modifier.dragShape(
    key: Any,
    dragCoordinatorState: DragCoordinatorState<out DraggedItemInfo>,
    settings: DragShapeSettings = SnappySwipeDefaults.shapeSettings(),
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

@Stable
data class DragShapeSettings(
    val minCornerRadius: Dp,
    val maxCornerRadius: Dp,
    val maxCornerRadiusAtOffsetDelta: Dp,
    val cornerRadiusAnimationSpec: FiniteAnimationSpec<Dp>,
)
