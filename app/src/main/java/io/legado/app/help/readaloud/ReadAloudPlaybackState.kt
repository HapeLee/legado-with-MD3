package io.legado.app.help.readaloud

data class ReadAloudPlaybackState(
    val phase: String = PHASE_STOPPED,
    val bookUrl: String = "",
    val chapterIndex: Int = -1,
    val chapterUrl: String = "",
    val cueIndex: Int = -1,
    val cueCount: Int = 0,
    val cueChapterPosition: Int = -1,
    val cueKey: String = "",
    val cueText: String = "",
    val planKey: String = "",
    val message: String = "",
    val playing: Boolean? = null,
    val buffering: Boolean = false,
    val serviceRunning: Boolean = false,
    val sessionId: Long = 0L
) {
    val busy: Boolean
        get() = buffering || phase == PHASE_PREPARING || phase == PHASE_BUFFERING

    companion object {
        const val PHASE_PREPARING = "preparing"
        const val PHASE_BUFFERING = "buffering"
        const val PHASE_PLAYING = "playing"
        const val PHASE_PAUSED = "paused"
        const val PHASE_STOPPED = "stopped"
        const val PHASE_ERROR = "error"
    }
}
