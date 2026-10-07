package io.legado.app.model.reader

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.BookContent
import io.legado.app.feature.reader.core.source.ReaderChapterSource

/** Processed chapter input published before any View-era page layout is consumed. */
data class ReaderChapterInput(
    val book: Book,
    val bookSource: BookSource?,
    val chapter: BookChapter,
    val displayTitle: String,
    val content: BookContent,
    val source: ReaderChapterSource,
    /** Precomputed off the main thread; used to deduplicate Compose pagination requests. */
    val contentHash: Int,
    val contentProcessesHash: Int,
    val sourceHash: Int,
    val bookSourceHash: Int,
    val pageEstimateGeneration: Long,
    /**
     * 多角色分配 / 变声器的渲染签名（见 `CastRenderOptions.signatureFor`）。
     *
     * 它不改正文一个字，却决定这一章要不要重排：没有它，开开关、加变声器都撞不上分页身份，
     * 旧页被当成有效结果继续用，胶囊与徽记就要等退出重进阅读器才更新。
     */
    val castRenderHash: Int = 0,
)

data class ReaderChapterInputWindow(
    val previous: ReaderChapterInput? = null,
    val current: ReaderChapterInput? = null,
    val next: ReaderChapterInput? = null,
)
