package io.legado.app.ui.widget.components.explore

import io.legado.app.data.entities.rule.ExploreKind
import kotlin.math.roundToInt

fun calculateExploreKindRows(
    kinds: List<ExploreKind>,
    maxSpan: Int
): List<List<Pair<ExploreKind, Int>>> {
    val rows = mutableListOf<MutableList<Pair<ExploreKind, Int>>>()
    var currentRow = mutableListOf<Pair<ExploreKind, Int>>()
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
                val (kind, span) = currentRow[index]
                val add = addEach + if (extra > 0) {
                    extra -= 1
                    1
                } else {
                    0
                }
                currentRow[index] = kind to (span + add)
            }
        } else {
            val (lastKind, lastSpan) = currentRow.last()
            currentRow[currentRow.lastIndex] = lastKind to (lastSpan + remain)
        }
        currentSpan += remain
    }

    kinds.forEach { kind ->
        val style = kind.style()
        val span = when {
            style.layout_wrapBefore || style.layout_flexBasisPercent >= 1.0f -> maxSpan
            style.layout_flexBasisPercent > 0 -> (maxSpan * style.layout_flexBasisPercent).roundToInt()
                .coerceIn(1, maxSpan)

            style.layout_flexGrow > 0f -> 3
            else -> 2
        }
        if ((style.layout_wrapBefore && currentRow.isNotEmpty()) || (currentSpan + span > maxSpan)) {
            fillCurrentRowTail()
            rows.add(currentRow)
            currentRow = mutableListOf()
            currentSpan = 0
        }
        currentRow.add(kind to span)
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
