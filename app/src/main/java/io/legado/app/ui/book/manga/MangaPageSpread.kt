package io.legado.app.ui.book.manga

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
internal data class MangaPageSpread(
    val key: String,
    val slots: ImmutableList<MangaPageSlot>,
) {
    val itemIndices: List<Int> get() = slots.map(MangaPageSlot::itemIndex)
    operator fun contains(itemIndex: Int): Boolean = itemIndex in itemIndices
}

@Immutable
internal data class MangaPageSlot(
    val itemIndex: Int,
    val slice: MangaPageSlice = MangaPageSlice.FULL,
)

internal enum class MangaPageSlice { FULL, LEFT, RIGHT }

internal fun buildMangaSpreads(
    items: List<MangaReaderItemUi>,
    doublePage: Boolean,
    aspectRatios: Map<String, Float> = emptyMap(),
    coverSingle: Boolean = false,
    shiftPairing: Boolean = false,
    splitWidePages: Boolean = false,
    splitRightToLeft: Boolean = false,
): List<MangaPageSpread> {
    if (!doublePage && !splitWidePages) return items.indices.map { index -> items.singleSpread(index) }
    val result = mutableListOf<MangaPageSpread>()
    var index = 0
    while (index < items.size) {
        val first = items[index]
        val second = items.getOrNull(index + 1)
        val firstIsWide = first is MangaReaderItemUi.Page &&
            aspectRatios[first.key]?.let { it > 1f } == true
        val secondIsWide = second is MangaReaderItemUi.Page &&
            aspectRatios[second.key]?.let { it > 1f } == true
        if (firstIsWide && splitWidePages) {
            val slices = if (splitRightToLeft) {
                listOf(MangaPageSlice.RIGHT, MangaPageSlice.LEFT)
            } else {
                listOf(MangaPageSlice.LEFT, MangaPageSlice.RIGHT)
            }
            slices.forEach { slice ->
                result += MangaPageSpread(
                    key = "spread:${first.key}:$slice",
                    slots = listOf(MangaPageSlot(index, slice)).toImmutableList(),
                )
            }
            index++
            continue
        }
        val firstMustStaySingle = first is MangaReaderItemUi.Page &&
                ((coverSingle && first.pageIndex == 0) ||
                        (shiftPairing && previousPageIsDifferentChapter(items, index)))
        if (doublePage && !firstMustStaySingle &&
            first is MangaReaderItemUi.Page && second is MangaReaderItemUi.Page &&
            first.chapterIndex == second.chapterIndex && !firstIsWide && !secondIsWide
        ) {
            result += MangaPageSpread(
                key = "spread:${first.key}|${second.key}",
                slots = listOf(MangaPageSlot(index), MangaPageSlot(index + 1)).toImmutableList(),
            )
            index += 2
        } else {
            result += items.singleSpread(index)
            index++
        }
    }
    return result
}

private fun previousPageIsDifferentChapter(items: List<MangaReaderItemUi>, index: Int): Boolean {
    val current = items.getOrNull(index) as? MangaReaderItemUi.Page ?: return false
    val previous = items.subList(0, index).lastOrNull { it is MangaReaderItemUi.Page }
            as? MangaReaderItemUi.Page
    return previous?.chapterIndex != current.chapterIndex
}

private fun List<MangaReaderItemUi>.singleSpread(index: Int) = MangaPageSpread(
    key = "spread:${get(index).key}",
    slots = listOf(MangaPageSlot(index)).toImmutableList(),
)
