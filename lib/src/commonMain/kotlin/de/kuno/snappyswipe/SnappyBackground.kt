package de.kuno.snappyswipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


/**
 * Controls the shape used to clip a background behind a [SnappyItem].
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

/**
 * Scope for composable backgrounds in [SnappyItem].
 *
 * Provides information about the current swipe reveal state so that
 * background content can adapt to the amount of space available.
 */
@Stable
class SnappyBackgroundScope internal constructor() {
    /**
     * The currently revealed width in pixels.
     */
    var revealedWidth: Float by mutableFloatStateOf(0f)
        internal set

    /**
     * The full height of the item in pixels.
     */
    var itemHeight: Float by mutableFloatStateOf(0f)
        internal set

    /**
     * The full height of the item in pixels.
     */
    var itemWidth: Float by mutableFloatStateOf(0f)
        internal set
}

/**
 * A default background composable for use with [SnappyItem]'s `backgroundLeft`
 * or `backgroundRight` parameters. Displays a colored container with a centered icon.
 *
 * The icon's alpha begins to increase once the revealed width is large enough to
 * fully display the icon (i.e. [iconSize] + 4dp), and reaches full opacity once
 * the user has swiped a distance equal to the item's height.
 *
 * @receiver The [SnappyBackgroundScope] instance.
 * @param icon The icon to display.
 * @param containerColor The background color of the container.
 * @param iconTint The tint color applied to the icon.
 * @param iconSize The size of the icon.
 */
@Composable
fun SnappyBackgroundScope.IconBackground(
    icon: Painter,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = contentColorFor(containerColor),
    iconSize: Dp = SnappySwipeDefaults.BackgroundIconSize,
) {
    val density = LocalDensity.current

    // Start reveal the icon, once it can be fully shown with a padding of 2dp on each side
    val threshold = with(density) { iconSize.toPx() + 4.dp.toPx() }

    // Once the user swipes as much as the item is heigh, the icon has full opacity
    val iconAlpha = if (itemHeight <= threshold) 0f
    else revealedWidth.inverseLerp(threshold, itemHeight)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.requiredSize(iconSize),
            tint = iconTint.copy(alpha = iconAlpha),
        )
    }
}

/**
 * A default background composable for use with [SnappyItem]'s `backgroundLeft`
 * or `backgroundRight` parameters. Displays a colored container with a centered icon.
 *
 * @receiver The [SnappyBackgroundScope] instance.
 * @param icon The [ImageVector] icon to display.
 * @param containerColor The background color of the container.
 * @param iconTint The tint color applied to the icon.
 * @param iconSize The size of the icon.
 */
@Composable
fun SnappyBackgroundScope.IconBackground(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = contentColorFor(containerColor),
    iconSize: Dp = SnappySwipeDefaults.BackgroundIconSize,
) {
    IconBackground(
        icon = rememberVectorPainter(icon),
        containerColor = containerColor,
        iconTint = iconTint,
        iconSize = iconSize,
    )
}


/**
 * Calculates the linear interpolation percentage (a fraction between 0f and 1f)
 * of this value within a specified range. This function effectively answers the question:
 * "What percentage of the way is this value between [start] and [end]?"
 *
 * The result is safely clamped to the `0f..1f` range. If [start] and [end]
 * are equal, this function defaults to returning `0f` to prevent division by zero.
 *
 * @receiver The target value to evaluate.
 * @param start The starting value of the range (0% point).
 * @param end The ending value of the range (100% point).
 * @return A mapped fraction between 0f and 1f.
 */
private fun Float.inverseLerp(start: Float, end: Float): Float {
    val range = end - start
    if (range == 0f) return 0f
    return ((this - start) / range).coerceIn(0f, 1f)
}