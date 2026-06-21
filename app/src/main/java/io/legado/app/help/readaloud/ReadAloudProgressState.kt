package io.legado.app.help.readaloud

data class ReadAloudProgressState(
    val bookUrl: String = "",
    val chapterIndex: Int = -1,
    val chapterUrl: String = "",
    val chapterPosition: Int = 0,
    val cueIndex: Int = -1,
    val sessionId: Long = 0L,
    val planKey: String = ""
)
