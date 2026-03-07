package de.kuno.snappyswipe.testapp

import androidx.compose.runtime.mutableStateOf

@OptIn(ExperimentalStdlibApi::class)
class ListHolder(
    groups: Int = 10,
    itemsPerGroup: Int = 20
) {
    val list = mutableStateOf(createList(groups, itemsPerGroup))

    private fun createList(groups: Int, itemsPerGroup: Int) = buildList {
        var id = 0
        repeat(groups) { header ->
            add(Item(id = Int.MAX_VALUE - header, isHeader = true, text = "Group $header"))

            repeat(itemsPerGroup) {
                add(Item(id = id, isHeader = false, text = "Item $id"))
                id++
            }
        }
    }

    fun remove(item: Item) {
        list.value = list.value.filterNot { it.id == item.id }
    }

    fun shuffle() {
        list.value = list.value.shuffled()
    }
}

data class Item(
    val id: Int,
    val isHeader: Boolean,
    val text: String,
)