package de.kuno.snappyswipe.testapp

import androidx.compose.runtime.mutableStateOf

@OptIn(ExperimentalStdlibApi::class)
class ListHolder(
    groups: Int = 10,
    itemsPerGroup: Int = 4
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

    private var nextId = groups * (itemsPerGroup + 1)

    fun remove(item: Item) {
        list.value = list.value.filterNot { it.id == item.id }
    }

    fun addRandomItem() {
        val currentList = list.value
        // Find a random non-header position to insert after
        val insertableIndices = currentList.indices.filter { !currentList[it].isHeader }
        if (insertableIndices.isEmpty()) return
        val insertAfterIndex = insertableIndices.random()
        val id = nextId++
        val newItem = Item(id = id, isHeader = false, text = "New Item $id")
        list.value = currentList.toMutableList().apply {
            add(insertAfterIndex + 1, newItem)
        }
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