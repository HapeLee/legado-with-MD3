package io.legado.app.ui.book.read.page.provider

import io.legado.app.ui.book.read.page.entities.TitleSegment

object TitleStyleParser {
    fun getSegments(
        rawTitle: String,
        segType: Int,
        segDistance: Int,
        segFlag: String,
        scaling: Float
    ): List<TitleSegment> {
        if (rawTitle.isBlank()) return emptyList()

        val results: List<String> = when (segType) {
            1 -> {
                if (segDistance <= 0 || segDistance >= rawTitle.length)
                    listOf(rawTitle)
                else
                    listOf(rawTitle.take(segDistance), rawTitle.substring(segDistance))
            }

            2 -> {
                val flags = segFlag.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (flags.isEmpty()) listOf(rawTitle)
                else {
                    val pattern = flags.joinToString("|") { Regex.escape(it) }
                    val regex = Regex("(?<=$pattern)")
                    rawTitle.split(regex).map { it.trim() }.filter { it.isNotEmpty() }
                }
            }

            else -> listOf(rawTitle)
        }

        // 统一包装成对象返回
        return results.mapIndexed { index, text ->
            TitleSegment(
                text = text,
                isMainTitle = index == 0,
                scale = if (index == 0) 1.0f else scaling
            )
        }
    }
}