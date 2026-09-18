package io.legado.app.domain.model

import io.legado.app.domain.model.PlaybackTimer.MAX_CHAPTERS


object PlaybackTimer {

    const val MIN_MINUTES = 0
    const val MAX_MINUTES = 180
    private const val INCREMENT_MINUTES = 10

    /** 章节定时的上限；0 表示未开启。 */
    const val MAX_CHAPTERS = 50

    fun normalize(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    fun addIncrement(minutes: Int): Int {
        val normalized = normalize(minutes)
        return if (normalized == MAX_MINUTES) {
            MIN_MINUTES
        } else {
            (normalized + INCREMENT_MINUTES).coerceAtMost(MAX_MINUTES)
        }
    }

    /** 章节定时剩余章数：0 表示未开启，其余收敛到 1..[MAX_CHAPTERS]。 */
    fun normalizeChapters(chapters: Int): Int = chapters.coerceIn(0, MAX_CHAPTERS)

    /** 递减剩余章数；已为 0 时保持 0，不会变成负数。 */
    fun consumeChapter(chapters: Int): Int = (chapters - 1).coerceAtLeast(0)
}
