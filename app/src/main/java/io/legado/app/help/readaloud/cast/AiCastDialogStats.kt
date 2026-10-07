package io.legado.app.help.readaloud.cast

import io.legado.app.data.appDb
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI 分配悬浮窗的本章统计：(已分配句数, 一级对话锚点总数)。
 *
 * 锚点计数与注入/渲染侧完全同源（ContentProcessor 后 textList → [ReaderChapterSourceParser.castAnchorText]
 * → CastQuoteTracker），保证「是否全部已分配」的判断与屏幕上看到的胶囊一一对应。
 */
object AiCastDialogStats {

    suspend fun load(bookUrl: String, chapterIndex: Int): Pair<Int, Int> =
        withContext(Dispatchers.IO) {
            val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext 0 to 0
            val chapter = appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)
                ?: return@withContext 0 to 0
            val content = BookHelp.getContent(book, chapter) ?: return@withContext 0 to 0
            val paragraphs = ReaderChapterSourceParser.castAnchorText(
                paragraphs = ContentProcessor.get(book)
                    .getContent(book, chapter, content, includeTitle = false)
                    .textList,
                adaptSpecialStyle = AppConfig.adaptSpecialStyle,
            )
            val tracker = CastMarkers.CastQuoteTracker()
            var anchors = 0
            for (paragraph in paragraphs) {
                for (ch in paragraph) {
                    if (tracker.feed(ch)) anchors++
                }
            }
            val assigned = appDb.chapterRoleAssignmentDao
                .getForChapter(bookUrl, chapterIndex)
                .count { it.characterId.isNotBlank() && it.characterName.isNotBlank() }
            assigned to anchors
        }
}
