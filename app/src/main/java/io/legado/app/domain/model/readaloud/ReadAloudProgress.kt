package io.legado.app.domain.model.readaloud

/** 进度产生时捕获会话身份，缓冲和重放不再把旧偏移拼到新章节上。 */
data class ReadAloudProgress(
    val bookUrl: String,
    val chapterIndex: Int,
    val chapterPosition: Int,
    val sessionGeneration: Long,
) {
    fun matches(bookUrl: String?, chapterIndex: Int, sessionGeneration: Long): Boolean =
        this.bookUrl == bookUrl && this.chapterIndex == chapterIndex &&
            this.sessionGeneration == sessionGeneration && chapterIndex >= 0 && chapterPosition >= 0
}
