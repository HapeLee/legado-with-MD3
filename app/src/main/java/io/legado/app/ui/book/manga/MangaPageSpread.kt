package io.legado.app.ui.book.manga

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
internal data class MangaPageSpread(
    val key: String,
    val itemIndices: ImmutableList<Int>,
) {
    operator fun contains(itemIndex: Int): Boolean = itemIndex in itemIndices
}

internal fun buildMangaSpreads(
    items: List<MangaReaderItemUi>,
    doublePage: Boolean,
    aspectRatios: Map<String, Float> = emptyMap(),
): List<MangaPageSpread> {
    if (!doublePage) return items.indices.map { index -> items.singleSpread(index) }
    val result = mutableListOf<MangaPageSpread>()
    var index = 0
    while (index < items.size) {
        val first = items[index]
        val second = items.getOrNull(index + 1)
        val firstIsWide = first is MangaReaderItemUi.Page &&
            aspectRatios[first.key]?.let { it > 1f } == true
        val secondIsWide = second is MangaReaderItemUi.Page &&
            aspectRatios[second.key]?.let { it > 1f } == true
        if (first is MangaReaderItemUi.Page && second is MangaReaderItemUi.Page &&
            first.chapterIndex == second.chapterIndex && !firstIsWide && !secondIsWide
        ) {
            result += MangaPageSpread(
                key = "spread:${first.key}|${second.key}",
                itemIndices = listOf(index, index + 1).toImmutableList(),
            )
            index += 2
        } else {
            result += items.singleSpread(index)
            index++
        }
    }
    return result
}

private fun List<MangaReaderItemUi>.singleSpread(index: Int) = MangaPageSpread(
    key = "spread:${get(index).key}",
    itemIndices = listOf(index).toImmutableList(),
)
