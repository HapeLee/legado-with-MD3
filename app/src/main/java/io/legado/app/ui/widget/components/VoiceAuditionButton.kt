package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.R
import io.legado.app.data.entities.VoiceEffectPreset
import io.legado.app.help.readaloud.cast.VoiceAudition
import io.legado.app.help.readaloud.effect.VoiceEffectAudio
import io.legado.app.help.readaloud.effect.VoiceEffectStore
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音色试听按钮：用这一条音色合成 [text] 并播放，再点一次停止。
 *
 * 播放器跟着组合走（组合销毁即释放），不占用朗读服务与朗读队列，所以在正文内的悬浮窗里
 * 点它不会打断正在进行的朗读。用 ExoPlayer 而不是 MediaPlayer，是因为要在这里就能听到
 * [effect] 选中的变声器效果（音高/语速走 PlaybackParameters，混响/金属感挂音频会话效果），
 * 和朗读时完全同一套实现。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VoiceAuditionButton(
    voiceId: String,
    text: String,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.cloud_tts_preview),
    /** 变声器预设名，空 = 不变声。 */
    effect: String = "",
    /**
     * 编辑框里还没保存的草稿预设。非空时直接按它出声：调滑杆的当下就能听见，
     * 不用先保存再试听；也绕开了 [VoiceEffectStore.byName] 的「停用算没有」口径——
     * 正在编辑的这份可能还没启用。
     */
    draft: VoiceEffectPreset? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    // 播放器第一次点播放时才建：声音池展开后同屏可能有十几行按钮，一人一个太浪费
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val effects = remember { VoiceEffectAudio() }
    val failedText = stringResource(R.string.cast_audition_failed)
    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.release() }
            effects.release()
        }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(
            enabled = voiceId.isNotBlank() && text.isNotBlank() && !busy,
            onClick = {
                if (playing) {
                    runCatching { player?.stop() }
                    playing = false
                    return@IconButton
                }
                scope.launch {
                    busy = true
                    val preset = draft ?: withContext(Dispatchers.IO) {
                        // byName 读的是内存表，先 list() 一次把它灌满
                        VoiceEffectStore.enabledNames()
                        VoiceEffectStore.byName(effect)
                    }
                    val file = withContext(Dispatchers.IO) {
                        VoiceAudition.synthesize(context, voiceId, text)
                    }
                    busy = false
                    if (file == null) {
                        context.toastOnUi(failedText)
                        return@launch
                    }
                    val exo = player ?: ExoPlayer.Builder(context)
                        .setAudioAttributes(
                            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build(),
                            /* handleAudioFocus= */ false,
                        )
                        .setHandleAudioBecomingNoisy(false)
                        .build()
                        .also { created ->
                            created.addListener(
                                object : Player.Listener {
                                    override fun onPlaybackStateChanged(playbackState: Int) {
                                        // 会话号是 Media3 在播放线程异步生成的，prepare 那一刻往往还是 0：
                                        // 开播时再补挂一次，混响/金属感才挂得上（否则会静默丢掉）
                                        if (playbackState == Player.STATE_READY) {
                                            effects.attach(created.audioSessionId)
                                        }
                                        if (playbackState == Player.STATE_ENDED) playing = false
                                    }

                                    override fun onPlayerError(error: PlaybackException) {
                                        playing = false
                                    }
                                },
                            )
                            player = created
                        }
                    runCatching {
                        exo.setMediaItem(MediaItem.fromUri(file.absolutePath))
                        exo.playbackParameters = VoiceEffectAudio.parameters(preset, 1f)
                        exo.prepare()
                        // 先挂一次：会话号可能还没生成（0 会被忽略），真正生效在 STATE_READY 那一次
                        effects.attach(exo.audioSessionId)
                        effects.apply(preset)
                        exo.playWhenReady = true
                        playing = true
                    }.onFailure {
                        playing = false
                        context.toastOnUi(failedText)
                    }
                }
            },
        ) {
            when {
                busy -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                playing -> Icon(
                    Icons.Default.Pause,
                    contentDescription = stringResource(R.string.cast_bgm_stop),
                )
                else -> Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cast_bgm_play),
                )
            }
        }
    }
}
