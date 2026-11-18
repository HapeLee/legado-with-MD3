package io.legado.app.service

import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.Player
import io.legado.app.model.AudioPlay.PlayMode

internal data class AudioPlayMediaState(val state: Int, val speed: Float)

internal fun resolveAudioPlayMediaState(
    playerState: Int,
    isPlaying: Boolean,
    hasError: Boolean,
    preparing: Boolean,
    paused: Boolean,
    playbackSpeed: Float,
): AudioPlayMediaState {
    val state = when {
        hasError -> PlaybackStateCompat.STATE_ERROR
        isPlaying -> PlaybackStateCompat.STATE_PLAYING
        preparing || playerState == Player.STATE_BUFFERING -> {
            if (paused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_BUFFERING
        }
        playerState == Player.STATE_READY -> PlaybackStateCompat.STATE_PAUSED
        else -> PlaybackStateCompat.STATE_STOPPED
    }
    val speed = if (state == PlaybackStateCompat.STATE_PLAYING) playbackSpeed else 0f
    return AudioPlayMediaState(state, speed)
}

internal fun canSkipAudioChapter(index: Int, chapterCount: Int, offset: Int): Boolean {
    return index in 0 until chapterCount && index + offset in 0 until chapterCount
}

internal fun canSkipToNextAudioChapter(index: Int, chapterCount: Int, playMode: PlayMode): Boolean =
    index in 0 until chapterCount &&
        (playMode != PlayMode.LIST_END_STOP || canSkipAudioChapter(index, chapterCount, 1))

internal fun audioPlayMediaActions(
    index: Int,
    chapterCount: Int,
    canSeek: Boolean,
    playMode: PlayMode,
): Long {
    var actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP
    if (canSkipAudioChapter(index, chapterCount, -1)) {
        actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    }
    if (canSkipToNextAudioChapter(index, chapterCount, playMode)) {
        actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
    }
    if (canSeek) actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
    return actions
}
