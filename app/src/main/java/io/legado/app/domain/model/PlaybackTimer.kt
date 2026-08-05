package io.legado.app.domain.model

object PlaybackTimer {

    const val MIN_MINUTES = 0
    const val MAX_MINUTES = 180
    private const val INCREMENT_MINUTES = 10

    fun normalize(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    fun addIncrement(minutes: Int): Int {
        val normalized = normalize(minutes)
        return if (normalized == MAX_MINUTES) {
            MIN_MINUTES
        } else {
            (normalized + INCREMENT_MINUTES).coerceAtMost(MAX_MINUTES)
        }
    }
}
