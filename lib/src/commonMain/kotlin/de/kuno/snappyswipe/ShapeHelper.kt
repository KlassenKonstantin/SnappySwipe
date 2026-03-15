package de.kuno.snappyswipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalTypeInference

@OptIn(ExperimentalTypeInference::class)
class ShapeHelper internal constructor(
    private val settings: DragShapeSettings,
    private val itemState: () -> ItemState<out DraggedItemInfo>?,
    private val density: Density,
) {
    private val topCornerRadiusAnimator = Animatable(
        initialValue = calcTopCornerRadius(
            itemState = itemState(),
            minCornerRadius = settings.minCornerRadius,
            maxCornerRadius = settings.maxCornerRadius
        ),
        typeConverter = Dp.VectorConverter
    )

    private val bottomCornerRadiusAnimator = Animatable(
        initialValue = calcBottomCornerRadius(
            itemState = itemState(),
            minCornerRadius = settings.minCornerRadius,
            maxCornerRadius = settings.maxCornerRadius
        ),
        typeConverter = Dp.VectorConverter
    )

    val shape: Shape
        get() {
            val top = topCornerRadiusAnimator.value.coerceAtLeast(
                settings.minCornerRadius,
            )

            val bottom = bottomCornerRadiusAnimator.value.coerceAtLeast(
                settings.minCornerRadius,
            )

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
            snapshotFlow { settings.minCornerRadius },
            snapshotFlow { settings.maxCornerRadius },
            snapshotFlow { settings.cornerRadiusAnimationSpec },
        ) { itemState, minCornerRadius, maxCornerRadius, animationSpec ->
            launch {
                topCornerRadiusAnimator.animateTo(
                    targetValue = calcTopCornerRadius(
                        itemState = itemState,
                        minCornerRadius = minCornerRadius,
                        maxCornerRadius = maxCornerRadius
                    ),
                    animationSpec = animationSpec
                )
            }

            launch {
                bottomCornerRadiusAnimator.animateTo(
                    targetValue = calcBottomCornerRadius(
                        itemState = itemState,
                        minCornerRadius = minCornerRadius,
                        maxCornerRadius = maxCornerRadius
                    ),
                    animationSpec = animationSpec
                )
            }
        }.collect()
    }

    private fun calcRadius(offsetDelta: Float, minCornerRadius: Dp, maxCornerRadius: Dp): Dp {
        val progress = (offsetDelta / density.run { settings.maxCornerRadiusAtOffsetDelta.toPx() }).coerceIn(0f, 1f)
        return (minCornerRadius + (maxCornerRadius - minCornerRadius) * progress)
    }

    private fun calcTopCornerRadius(itemState: ItemState<out DraggedItemInfo>?, minCornerRadius: Dp, maxCornerRadius: Dp): Dp {
        return when {
            itemState == null -> minCornerRadius
            !itemState.sameSegmentAsTopNeighbor -> maxCornerRadius
            else -> calcRadius(itemState.offsetDeltaTop, minCornerRadius, maxCornerRadius)
        }
    }

    private fun calcBottomCornerRadius(itemState: ItemState<out DraggedItemInfo>?, minCornerRadius: Dp, maxCornerRadius: Dp): Dp {
        return when {
            itemState == null -> minCornerRadius
            !itemState.sameSegmentAsBottomNeighbor -> maxCornerRadius
            else -> calcRadius(itemState.offsetDeltaBottom, minCornerRadius, maxCornerRadius)
        }
    }
}

@Composable
fun rememberShapeHelper(
    settings: DragShapeSettings,
    itemState: () -> ItemState<out DraggedItemInfo>?,
): ShapeHelper {
    val density = LocalDensity.current

    val currentItemState by rememberUpdatedState(itemState)

    val helper = remember(settings, density) {
        ShapeHelper(
            settings = settings,
            itemState = { currentItemState() },
            density = density,
        )
    }

    LaunchedEffect(helper) {
        helper.observeChanges()
    }

    return helper
}
