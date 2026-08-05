package io.legado.app.ui.widget.components.explore

import io.legado.app.data.entities.rule.ExploreKind
import kotlin.math.roundToInt

fun calculateExploreKindRows(
    kinds: List<ExploreKind>,
    maxSpan: Int
): List<List<Pair<ExploreKind, Int>>> = calculateFlexRows(
    items = kinds,
    maxSpan = maxSpan,
    layout = { kind ->
        val style = kind.style()
        FlexItemLayout(
            flexGrow = style.layout_flexGrow,
            basisPercent = style.layout_flexBasisPercent,
            wrapBefore = style.layout_wrapBefore,
        )
    },
)

data class FlexItemLayout(
    val flexGrow: Float = 0f,
    val basisPercent: Float = -1f,
    val wrapBefore: Boolean = false,
)

fun <T> calculateFlexRows(
    items: List<T>,
    maxSpan: Int,
    layout: (T) -> FlexItemLayout,
): List<List<Pair<T, Int>>> {
    val rows = mutableListOf<MutableList<Pair<T, Int>>>()
    var currentRow = mutableListOf<Pair<T, Int>>()
    var currentSpan = 0

    fun fillCurrentRowTail() {
        if (currentRow.isEmpty()) return
        val remain = maxSpan - currentSpan
        if (remain <= 0) return
        val allSameSpan = currentRow.map { it.second }.distinct().size == 1
        if (allSameSpan && currentRow.size > 1) {
            val addEach = remain / currentRow.size
            var extra = remain % currentRow.size
            currentRow.indices.forEach { index ->
                val (item, span) = currentRow[index]
                val add = addEach + if (extra > 0) {
                    extra -= 1
                    1
                } else {
                    0
                }
                currentRow[index] = item to (span + add)
            }
        } else {
            val (lastItem, lastSpan) = currentRow.last()
            currentRow[currentRow.lastIndex] = lastItem to (lastSpan + remain)
        }
        currentSpan += remain
    }

    items.forEach { item ->
        val style = layout(item)
        val span = when {
            style.wrapBefore || style.basisPercent >= 1.0f -> maxSpan
            style.basisPercent > 0 -> (maxSpan * style.basisPercent).roundToInt()
                .coerceIn(1, maxSpan)

            style.flexGrow > 0f -> 3
            else -> 2
        }
        if ((style.wrapBefore && currentRow.isNotEmpty()) || (currentSpan + span > maxSpan)) {
            fillCurrentRowTail()
            rows.add(currentRow)
            currentRow = mutableListOf()
            currentSpan = 0
        }
        currentRow.add(item to span)
        currentSpan += span
        if (currentSpan >= maxSpan) {
            rows.add(currentRow)
            currentRow = mutableListOf()
            currentSpan = 0
        }
    }
    if (currentRow.isNotEmpty()) {
        fillCurrentRowTail()
        rows.add(currentRow)
    }
    return rows
}
