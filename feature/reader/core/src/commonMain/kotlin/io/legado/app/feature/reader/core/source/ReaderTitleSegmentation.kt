package io.legado.app.feature.reader.core.source

/** Persisted title options, captured once for a pagination pass. */
data class ReaderTitleSegmentation(
    val type: Int = 0,
    val distance: Int = 4,
    val delimiter: String = "",
    val subtitleScale: Float = 1f,
) {
    fun blocks(title: String): List<ReaderChapterSourceBlock.Text> {
        var position = 0
        return title.split('\n').map(String::trim).filter(String::isNotBlank).flatMap { line ->
            split(line).mapIndexed { index, text ->
                ReaderChapterSourceBlock.Text(
                    value = text,
                    chapterPosition = position,
                    isTitle = true,
                    fontSizeScale = if (index == 0) 1f else subtitleScale,
                    isSubtitle = index > 0,
                ).also { position += text.length + 1 }
            }
        }
    }

    private fun split(title: String): List<String> = when (type) {
        1 -> if (distance in 1 until title.length) {
            listOf(title.take(distance), title.substring(distance))
        } else listOf(title)
        2, 3 -> {
            val pattern = if (type == 3) delimiter else delimiter.split(',')
                .map(String::trim).filter(String::isNotEmpty).joinToString("|", transform = Regex::escape)
            if (pattern.isEmpty()) listOf(title) else {
                // A malformed imported expression must not make the chapter unreadable.
                val regex = runCatching { Regex("(?<=$pattern)") }.getOrNull()
                if (regex == null) listOf(title) else title.split(regex)
                    .map(String::trim).filter(String::isNotEmpty)
            }
        }
        else -> listOf(title)
    }
}
