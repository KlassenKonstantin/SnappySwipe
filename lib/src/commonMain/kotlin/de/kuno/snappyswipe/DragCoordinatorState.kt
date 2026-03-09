@file:OptIn(ExperimentalFoundationApi::class)

package de.kuno.snappyswipe

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalCoroutinesApi::class)
class DragCoordinatorState<T : DraggedItemInfo> internal constructor() {
    var dragInfo by mutableStateOf<T?>(null)

    internal val itemOffsets = mutableStateMapOf<Any, Float>()
    internal val itemInfos = mutableStateOf<List<ItemInfo>>(listOf())

    /**
     * Keys of items that were newly inserted in the most recent list update.
     * SnappyItem reads and removes its own key to trigger a one-shot insertion animation.
     */
    internal val newlyInsertedKeys = mutableSetOf<Any>()
    var initialised = false

    private val itemLookup by derivedStateOf {
        itemInfos.value.associateBy { it.key }
    }

    fun getItemState(key: Any): ItemState<T>? {
        val index = itemLookup[key]?.index ?: return null
        val itemInfos = itemInfos.value

        val offsets = Triple(
            itemOffsets[key],
            itemOffsets[itemInfos.getOrNull(index - 1)?.key],
            itemOffsets[itemInfos.getOrNull(index + 1)?.key]
        )

        val currentItem = (itemLookup[key] ?: return null).copy(
            offset = offsets.first ?: 0f
        )

        val topItem = itemInfos.getOrNull(currentItem.index - 1)?.copy(
            offset = offsets.second ?: 0f
        )

        val bottomItem = itemInfos.getOrNull(currentItem.index + 1)?.copy(
            offset = offsets.third ?: 0f
        )

        val draggedItemRelation = dragInfo?.let { dragInfo ->
            val draggedItemLookup = itemLookup[dragInfo.key] ?: return@let null
            val draggedItemIndex = draggedItemLookup.index

            if (index < 0 || draggedItemIndex < 0) return@let null
            val draggedItemInfo = draggedItemLookup

            val distanceToDraggedItem = index - draggedItemIndex
            val sameSegmentAsDraggedItem = itemInfos.subList(
                min(index, draggedItemIndex),
                max(index, draggedItemIndex)
            ).all { it.segmentType == draggedItemInfo.segmentType }

            DraggedItemRelation(
                draggedItemInfo = dragInfo,
                indexDelta = distanceToDraggedItem,
                sameSegmentAsDraggedItem = sameSegmentAsDraggedItem,
            )
        }

        return ItemState(
            draggedItemRelation = draggedItemRelation,
            itemInfo = currentItem,
            topItemInfo = topItem,
            bottomItemInfo = bottomItem,
        )
    }

    fun updateOffset(key: Any, offset: Float) {
        itemOffsets[key] = offset
    }

    /**
     * Returns true if this key was newly inserted (and consumes the flag so it only fires once).
     */
    fun consumeInsertionFlag(key: Any): Boolean {
        return newlyInsertedKeys.remove(key)
    }
}

@Composable
fun <D : DraggedItemInfo, T> rememberDragCoordinatorState(
    items: List<T>,
    key: (T) -> Any,
    segmentType: (T) -> Any? = { null },
): DragCoordinatorState<D> {
    return remember {
        DragCoordinatorState<D>()
    }.apply {
        val newInfos = items.mapIndexed { index, item ->
            val key = key(item)
            ItemInfo(
                key = key,
                index = index,
                segmentType = segmentType(item),
                offset = 0f
            )
        }
        if (itemInfos.value != newInfos) {
            if (initialised) {
                val oldKeys = itemInfos.value.map { it.key }.toSet()
                val insertedKeys = newInfos.map { it.key }.filter { it !in oldKeys }
                newlyInsertedKeys.addAll(insertedKeys)
            }
            initialised = true
            itemInfos.value = newInfos
        }
    }
}

interface DraggedItemInfo {
    val key: Any
    val dragOffset: Float
}

data class ItemState<T : DraggedItemInfo>(
    val draggedItemRelation: DraggedItemRelation<T>?,
    val itemInfo: ItemInfo,
    val topItemInfo: ItemInfo?,
    val bottomItemInfo: ItemInfo?,
) {
    val sameSegmentAsTopNeighbor: Boolean
        get() = itemInfo.segmentType == topItemInfo?.segmentType

    val sameSegmentAsBottomNeighbor: Boolean
        get() = itemInfo.segmentType == bottomItemInfo?.segmentType

    val offsetDeltaTop: Float
        get() = topItemInfo?.offset?.let { topItemOffset ->
            (itemInfo.offset - topItemOffset).absoluteValue
        } ?: 0f

    val offsetDeltaBottom: Float
        get() = bottomItemInfo?.offset?.let { bottomItemOffset ->
            (itemInfo.offset - bottomItemOffset).absoluteValue
        } ?: 0f

    val isDraggedItem: Boolean
        get() = itemInfo.key == draggedItemRelation?.draggedItemInfo?.key
}

data class DraggedItemRelation<T : DraggedItemInfo>(
    /**
     * Information about the item that is being dragged.
     */
    val draggedItemInfo: T,

    /**
     * The distance between this item and the item that is being dragged.
     * A positive value means this item is further down than the item that is being dragged and vice versa.
     * An indexDelta of 0 means this item is the one being dragged.
     */
    val indexDelta: Int,

    /**
     * Whether the dragged item is in the same segment as the item that is being dragged.
     */
    val sameSegmentAsDraggedItem: Boolean,
)

data class ItemInfo(
    val key: Any,
    val index: Int,
    val segmentType: Any?,
    val offset: Float,
)