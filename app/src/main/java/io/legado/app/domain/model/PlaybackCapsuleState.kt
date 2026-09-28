package io.legado.app.domain.model

import androidx.compose.runtime.Stable

enum class PlaybackCapsuleSource { ReadAloud, AudioBook }

@Stable
data class PlaybackCapsuleState(
    val source: PlaybackCapsuleSource? = null,
    val bookUrl: String = "",
    val bookName: String = "",
    val author: String = "",
    val coverPath: String? = null,
    val sourceOrigin: String? = null,
    val isPaused: Boolean = true,
    val progress: Float = 0f,
    val inBookshelf: Boolean = true,
    val chapterTitle: String = "",
    val chapterIndex: Int = -1,
)

/** 正在播放的会话优先；都暂停时保留当前会话，停止后回退到仍存活的会话。 */
fun selectPlaybackCapsule(
    readAloud: PlaybackCapsuleState,
    audioBook: PlaybackCapsuleState,
    currentSource: PlaybackCapsuleSource?,
): PlaybackCapsuleState {
    val candidates = listOf(readAloud, audioBook).filter { it.source != null }
    val playing = candidates.filter { !it.isPaused }
    val preferred = playing.ifEmpty { candidates }
    return preferred.firstOrNull { it.source == currentSource }
        ?: preferred.firstOrNull() ?: PlaybackCapsuleState()
}
