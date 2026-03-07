package de.kuno.snappyswipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalTypeInference

@OptIn(ExperimentalTypeInference::class)
class ShapeHelper(
    minCornerRadius: () -> Dp,
    maxCornerRadius: () -> Dp,
    animationSpec: () -> AnimationSpec<Dp>,
    private val maxOffsetDelta: Float,
    private val itemState: () -> ItemState<SnappyDraggedItemInfo>?,
) {
    private val topCornerRadiusAnimator = Animatable(calcTopCornerRadius(itemState()!!, minCornerRadius(), maxCornerRadius()), Dp.VectorConverter)
    private val bottomCornerRadiusAnimator = Animatable(calcBottomCornerRadius(itemState()!!, minCornerRadius(), maxCornerRadius()), Dp.VectorConverter)

    var minCornerRadius by mutableStateOf(minCornerRadius)
    var maxCornerRadius by mutableStateOf(maxCornerRadius)

    var animationSpec by mutableStateOf(animationSpec)

    val shape: Shape
        get() {
            val minCornerRadius = minCornerRadius()
            val maxCornerRadius = maxCornerRadius()

            val top = topCornerRadiusAnimator.value.coerceIn(minCornerRadius, maxCornerRadius)
            val bottom = bottomCornerRadiusAnimator.value.coerceIn(minCornerRadius, maxCornerRadius)

            return RoundedCornerShape(
                topStart = top,
                topEnd = top,
                bottomStart = bottom,
                bottomEnd = bottom,
            )
        }

    suspend fun observeChanges() = coroutineScope {
        combine(
            snapshotFlow { itemState() }.filterNotNull(),
            snapshotFlow { minCornerRadius() },
            snapshotFlow { maxCornerRadius() },
            snapshotFlow { animationSpec() },
        ) { itemState, minCornerRadius, maxCornerRadius, animationSpec ->
            launch { topCornerRadiusAnimator.animateTo(calcTopCornerRadius(itemState, minCornerRadius, maxCornerRadius), animationSpec) }
            launch { bottomCornerRadiusAnimator.animateTo(calcBottomCornerRadius(itemState, minCornerRadius, maxCornerRadius), animationSpec) }
        }.collect()
    }

    private fun calcRadius(offsetDelta: Float, minCornerRadius: Dp, maxCornerRadius: Dp): Dp {
        val progress = (offsetDelta / maxOffsetDelta).coerceIn(0f, 1f)
        return (minCornerRadius + (maxCornerRadius - minCornerRadius) * progress)
    }

    private fun calcTopCornerRadius(itemState: ItemState<SnappyDraggedItemInfo>, minCornerRadius: Dp, maxCornerRadius: Dp): Dp {
        return when {
            !itemState.sameSegmentAsTopNeighbor || (itemState.isDraggedItem || itemState.draggedItemRelation?.indexDelta == 1) && !itemState.draggedItemRelation!!.draggedItemInfo.stuck -> maxCornerRadius
            else -> calcRadius(itemState.offsetDeltaTop, minCornerRadius, maxCornerRadius)

        }
    }

    private fun calcBottomCornerRadius(itemState: ItemState<SnappyDraggedItemInfo>, minCornerRadius: Dp, maxCornerRadius: Dp): Dp {
        return when {
            !itemState.sameSegmentAsBottomNeighbor || (itemState.isDraggedItem || itemState.draggedItemRelation?.indexDelta == -1) && !itemState.draggedItemRelation!!.draggedItemInfo.stuck -> maxCornerRadius
            else -> calcRadius(itemState.offsetDeltaBottom, minCornerRadius, maxCornerRadius)
        }
    }
}

@Composable
fun rememberShapeHelper(
    minCornerRadius: () -> Dp,
    maxCornerRadius: () -> Dp,
    maxAtOffsetDelta: Dp,
    itemState: () -> ItemState<SnappyDraggedItemInfo>?,
    animationSpec: () -> AnimationSpec<Dp>,
): ShapeHelper {
    val density = LocalDensity.current

    val helper = remember {
        ShapeHelper(
            minCornerRadius = minCornerRadius,
            maxCornerRadius = maxCornerRadius,
            maxOffsetDelta = with(density) { maxAtOffsetDelta.toPx() },
            itemState = itemState,
            animationSpec = animationSpec
        )
    }.apply {
        this.minCornerRadius = minCornerRadius
        this.maxCornerRadius = maxCornerRadius
        this.animationSpec = animationSpec
    }

    LaunchedEffect(Unit) {
        helper.observeChanges()
    }

    return helper
}