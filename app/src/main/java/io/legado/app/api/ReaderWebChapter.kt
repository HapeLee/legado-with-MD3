package io.legado.app.api

import androidx.annotation.Keep
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** Opt-in Web API representation; coordinates belong to the Android reader's UTF-16 body. */
@Keep
data class ReaderWebChapter(val title: String, val characterCount: Int, val paragraphs: List<ReaderWebParagraph>) {
    companion object {
        fun fromSource(source: ReaderChapterSource): ReaderWebChapter {
            val contentBlocks = source.blocks.filterNot {
                it is ReaderChapterSourceBlock.Text && it.isTitle
            }.iterator()
            var block = if (contentBlocks.hasNext()) contentBlocks.next() else null
            val paragraphs = source.paragraphs.map { paragraph ->
                val html = buildString {
                    while (true) {
                        val current = block ?: break
                        if (current.chapterPosition >= paragraph.endExclusive) break
                        when (current) {
                            is ReaderChapterSourceBlock.Html -> append(current.value)
                            is ReaderChapterSourceBlock.Paragraph -> current.items.forEach { item ->
                                when (item) {
                                    is ReaderChapterInlineSource.Text -> append(TextNode(item.value).outerHtml())
                                    is ReaderChapterInlineSource.Image -> append(Element("img").attr("src", item.source).outerHtml())
                                    else -> Unit
                                }
                            }
                            else -> Unit
                        }
                        block = if (contentBlocks.hasNext()) contentBlocks.next() else null
                    }
                }
                ReaderWebParagraph(html, paragraph.start, paragraph.endExclusive)
            }.filter { it.endExclusive > it.start }
            return ReaderWebChapter(source.title, source.characterCount, paragraphs)
        }
    }
}

@Keep
data class ReaderWebParagraph(val html: String, val start: Int, val endExclusive: Int)
