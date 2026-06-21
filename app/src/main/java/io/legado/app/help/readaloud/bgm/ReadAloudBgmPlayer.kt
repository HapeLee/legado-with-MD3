package io.legado.app.help.readaloud.bgm

import android.animation.ValueAnimator
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.help.ai.AiReadAloudBgmService
import io.legado.app.help.config.AppConfig
import io.legado.app.help.readaloud.ReadAloudPlaybackState
import io.legado.app.model.ReadBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReadAloudBgmPlayer(
    context: Context,
    private val scope: CoroutineScope
) {

    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var currentTrackId: Long? = null
    private var currentCueKey: String = ""
    private var currentSoundEffectCueKey: String = ""
    private var resolveJob: Job? = null
    private var volumeAnimator: ValueAnimator? = null
    private var lastPlaybackState: ReadAloudPlaybackState? = null
    private val soundEffectPlayers = linkedSetOf<ExoPlayer>()
    private val soundEffectJobs = linkedSetOf<Job>()

    private data class ResolvedAudio(
        val bgm: AiReadAloudBgmService.ResolvedAssignment?,
        val soundEffects: List<AiReadAloudBgmService.ResolvedSoundEffectEvent>
    )

    fun onPlaybackState(state: ReadAloudPlaybackState) {
        lastPlaybackState = state
        scope.launch(Dispatchers.Main.immediate) {
            handlePlaybackState(state)
        }
    }

    fun refreshConfig() {
        scope.launch(Dispatchers.Main.immediate) {
            if (!AppConfig.aiReadAloudBgmEnabled) {
                fadeOutAndStop()
                stopSoundEffects()
                return@launch
            }
            currentCueKey = ""
            currentSoundEffectCueKey = ""
            lastPlaybackState?.let(::handlePlaybackState)
        }
    }

    fun release() {
        resolveJob?.cancel()
        resolveJob = null
        volumeAnimator?.cancel()
        volumeAnimator = null
        player?.release()
        player = null
        stopSoundEffects()
        currentTrackId = null
        currentCueKey = ""
        currentSoundEffectCueKey = ""
    }

    private fun handlePlaybackState(state: ReadAloudPlaybackState) {
        if (!AppConfig.aiReadAloudBgmEnabled ||
            state.phase == ReadAloudPlaybackState.PHASE_STOPPED ||
            state.phase == ReadAloudPlaybackState.PHASE_ERROR ||
            !state.serviceRunning
        ) {
            fadeOutAndStop()
            stopSoundEffects()
            return
        }
        if (state.playing != true || state.busy) {
            player?.pause()
            stopSoundEffects()
            return
        }
        val bookUrl = ReadBook.book?.bookUrl
        val chapterIndex = state.chapterIndex.takeIf { it >= 0 } ?: ReadBook.durChapterIndex
        val cueIndex = state.cueIndex
        if (bookUrl.isNullOrBlank() || chapterIndex < 0 || cueIndex < 0) {
            fadeOutAndStop()
            stopSoundEffects()
            return
        }
        val cueKey = "$bookUrl|$chapterIndex|$cueIndex"
        val bgmHandled = cueKey == currentCueKey &&
                (currentTrackId == null || player?.isPlaying == true)
        val soundEffectsHandled = cueKey == currentSoundEffectCueKey
        if (bgmHandled && soundEffectsHandled) return
        resolveJob?.cancel()
        resolveJob = scope.launch(Dispatchers.IO) {
            val resolved = ResolvedAudio(
                bgm = AiReadAloudBgmService.cachedAssignmentForCue(bookUrl, chapterIndex, cueIndex),
                soundEffects = AiReadAloudBgmService.cachedSoundEffectsForCue(bookUrl, chapterIndex, cueIndex)
            )
            withContext(Dispatchers.Main.immediate) {
                applyResolvedAudio(cueKey, resolved)
            }
        }
    }

    private fun applyResolvedAudio(
        cueKey: String,
        resolved: ResolvedAudio
    ) {
        applyResolvedAssignment(cueKey, resolved.bgm)
        if (cueKey != currentSoundEffectCueKey) {
            currentSoundEffectCueKey = cueKey
            playSoundEffects(resolved.soundEffects)
        }
    }

    private fun applyResolvedAssignment(
        cueKey: String,
        resolved: AiReadAloudBgmService.ResolvedAssignment?
    ) {
        if (!AppConfig.aiReadAloudBgmEnabled) {
            fadeOutAndStop()
            return
        }
        currentCueKey = cueKey
        if (resolved == null) {
            fadeOutAndStop()
            return
        }
        val file = File(resolved.track.filePath)
        if (!file.exists() || file.length() <= 0L) {
            fadeOutAndStop()
            return
        }
        val bgmPlayer = ensurePlayer()
        val targetVolume = (resolved.assignment.volume *
                resolved.track.defaultVolume *
                AppConfig.aiReadAloudBgmVolumeScale)
            .coerceIn(0f, 1f)
        if (currentTrackId == resolved.track.id) {
            if (!bgmPlayer.isPlaying) {
                bgmPlayer.play()
            }
            animateVolume(targetVolume, resolved.assignment.fadeInMs)
            return
        }
        currentTrackId = resolved.track.id
        volumeAnimator?.cancel()
        bgmPlayer.stop()
        bgmPlayer.clearMediaItems()
        bgmPlayer.repeatMode = Player.REPEAT_MODE_ONE
        bgmPlayer.volume = 0f
        bgmPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        bgmPlayer.prepare()
        bgmPlayer.play()
        animateVolume(targetVolume, resolved.assignment.fadeInMs)
    }

    private fun playSoundEffects(events: List<AiReadAloudBgmService.ResolvedSoundEffectEvent>) {
        if (events.isEmpty() || !AppConfig.aiReadAloudBgmEnabled) return
        events.take(4).forEach { resolved ->
            val job = scope.launch(Dispatchers.Main.immediate) {
                if (resolved.event.offsetMs > 0) {
                    delay(resolved.event.offsetMs.toLong())
                }
                playSoundEffect(resolved)
            }
            soundEffectJobs += job
            job.invokeOnCompletion { soundEffectJobs.remove(job) }
        }
    }

    private fun playSoundEffect(resolved: AiReadAloudBgmService.ResolvedSoundEffectEvent) {
        val file = File(resolved.track.filePath)
        if (!file.exists() || file.length() <= 0L) return
        while (soundEffectPlayers.size >= 4) {
            soundEffectPlayers.firstOrNull()?.let(::releaseSoundEffectPlayer) ?: break
        }
        val effectPlayer = ExoPlayer.Builder(appContext).build()
        soundEffectPlayers += effectPlayer
        effectPlayer.volume = (resolved.event.volume *
                resolved.track.defaultVolume *
                AppConfig.aiReadAloudSfxVolumeScale)
            .coerceIn(0f, 1f)
        effectPlayer.repeatMode = Player.REPEAT_MODE_OFF
        effectPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        effectPlayer.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                        releaseSoundEffectPlayer(effectPlayer)
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    releaseSoundEffectPlayer(effectPlayer)
                }
            }
        )
        effectPlayer.prepare()
        effectPlayer.play()
    }

    private fun stopSoundEffects() {
        soundEffectJobs.toList().forEach { it.cancel() }
        soundEffectJobs.clear()
        soundEffectPlayers.toList().forEach(::releaseSoundEffectPlayer)
        soundEffectPlayers.clear()
    }

    private fun releaseSoundEffectPlayer(effectPlayer: ExoPlayer) {
        soundEffectPlayers.remove(effectPlayer)
        runCatching {
            effectPlayer.stop()
            effectPlayer.release()
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        return player ?: ExoPlayer.Builder(appContext)
            .build()
            .also {
                it.repeatMode = Player.REPEAT_MODE_ONE
                it.volume = 0f
                player = it
            }
    }

    private fun fadeOutAndStop(durationMs: Int = 1200) {
        val bgmPlayer = player ?: return
        if (currentTrackId == null && !bgmPlayer.isPlaying) return
        animateVolume(0f, durationMs) {
            bgmPlayer.stop()
            bgmPlayer.clearMediaItems()
            currentTrackId = null
            currentCueKey = ""
        }
    }

    private fun animateVolume(
        target: Float,
        durationMs: Int,
        onEnd: (() -> Unit)? = null
    ) {
        val bgmPlayer = player ?: return
        volumeAnimator?.cancel()
        val start = bgmPlayer.volume
        if (durationMs <= 0 || kotlin.math.abs(start - target) < 0.01f) {
            bgmPlayer.volume = target
            onEnd?.invoke()
            return
        }
        volumeAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = durationMs.toLong()
            addUpdateListener { animator ->
                bgmPlayer.volume = animator.animatedValue as Float
            }
            addListener(
                object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onEnd?.invoke()
                    }
                }
            )
            start()
        }
    }
}
