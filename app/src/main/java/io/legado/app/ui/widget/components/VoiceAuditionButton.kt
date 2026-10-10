package io.legado.app.ui.widget.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 试听按钮的运行态：[rememberVoiceAuditionController] 造，[VoiceAuditionButton]（行内文字+图标）
 * 与 [VoiceAuditionAction]（弹层头部槽位）共用同一份。
 *
 * 拆开是因为两处形态差别很大：行内要带标签占位，弹层头部只有一个图标位，
 * 但合成、播放、释放这套逻辑只有一份，不能各写一遍。
 */
@Immutable
class VoiceAuditionController internal constructor(
    /** 合成中：这一轮点不动，免得连点叠出好几个播放器。 */
    val busy: Boolean,
    val playing: Boolean,
    /** 音色或试听文本没定下来时为false，调用方据此把按钮置灰。 */
    val enabled: Boolean,
    val toggle: () -> Unit,
)

/**
 * 音色试听：用这一条音色合成 [text] 并播放，再点一次停止。
 *
 * 播放器跟着组合走（组合销毁即释放），不占用朗读服务与朗读队列，所以在正文内的悬浮窗里
 * 点它不会打断正在进行的朗读。用 ExoPlayer 而不是 MediaPlayer，是因为要在这里就能听到
 * [effect] 选中的变声器效果（音高/语速走 PlaybackParameters，混响/金属感挂音频会话效果），
 * 和朗读时完全同一套实现。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberVoiceAuditionController(
    voiceId: String,
    text: String,
    /** 变声器预设名，空 = 不变声。 */
    effect: String = "",
    /**
     * 编辑框里还没保存的草稿预设。非空时直接按它出声：调滑杆的当下就能听见，
     * 不用先保存再试听；也绕开了 [VoiceEffectStore.byName] 的「停用算没有」口径——
     * 正在编辑的这份可能还没启用。
     */
    draft: VoiceEffectPreset? = null,
): VoiceAuditionController {
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
    val enabled = voiceId.isNotBlank() && text.isNotBlank() && !busy
    return VoiceAuditionController(
        busy = busy,
        playing = playing,
        enabled = enabled,
        toggle = {
            if (playing) {
                runCatching { player?.stop() }
                playing = false
            } else {
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
            }
        },
    )
}

/**
 * 行内试听：标签 + 图标按钮。合成中图标自转，播放中换成停止。
 *
 * 合成/播放/释放都在 [rememberVoiceAuditionController] 里，这里只负责长什么样。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VoiceAuditionButton(
    voiceId: String,
    text: String,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.cloud_tts_preview),
    effect: String = "",
    draft: VoiceEffectPreset? = null,
) {
    val audition = rememberVoiceAuditionController(
        voiceId = voiceId,
        text = text,
        effect = effect,
        draft = draft,
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (label.isNotBlank()) {
            AppText(
                text = label,
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
        VoiceAuditionIconButton(
            voiceId = voiceId,
            text = text,
            effect = effect,
            draft = draft,
        )
    }
}

/**
 * 只有一颗图标的试听按钮：列表行尾这类窄位置用这个。
 *
 * 之前那些地方是靠 `label = ""` 把标签藏掉，标签位仍占着一段空隙；这里干脆不留标签。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VoiceAuditionIconButton(
    voiceId: String,
    text: String,
    modifier: Modifier = Modifier,
    effect: String = "",
    draft: VoiceEffectPreset? = null,
) {
    val audition = rememberVoiceAuditionController(
        voiceId = voiceId,
        text = text,
        effect = effect,
        draft = draft,
    )
    AuditionIcon(
        audition = audition,
        onClick = audition.toggle,
        modifier = modifier,
    )
}

/**
 * 弹层头部槽位用的试听动作（Medium 系列）：头部只有一个图标位，放不下一行标签，
 * 三态与 [AuditionIcon] 同一套——合成中自转的刷新图标 / 播放中停止 / 待命试听。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VoiceAuditionAction(
    voiceId: String,
    text: String,
    modifier: Modifier = Modifier,
    effect: String = "",
    draft: VoiceEffectPreset? = null,
) {
    val audition = rememberVoiceAuditionController(
        voiceId = voiceId,
        text = text,
        effect = effect,
        draft = draft,
    )
    MediumTonalButton(
        onClick = audition.toggle,
        enabled = audition.enabled,
        modifier = modifier.rotate(if (audition.busy) auditionSpinAngle() else 0f),
        selected = audition.playing,
        icon = auditionIconOf(audition),
        contentDescription = auditionDescriptionOf(audition, R.string.cloud_tts_preview),
    )
}

/**
 * 试听按钮里那颗会变的状态图标：合成中自转的刷新图标 / 播放中停止 / 待命播放。
 *
 * 用 [SmallPlainButton] 是为了跟列表行尾其余动作（编辑、删除）同一套尺寸与外观；它只收
 * [ImageVector]，塞不进进度圈，所以合成中的加载态改成「图标自转」——容器是圆的，转起来
 * 看到的只有图标本身在动。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AuditionIcon(
    audition: VoiceAuditionController,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmallPlainButton(
        onClick = onClick,
        modifier = modifier.rotate(if (audition.busy) auditionSpinAngle() else 0f),
        // 控制器给的 enabled 已经算进了合成中，这里直接透传：合成期间按钮既不可点也变灰
        enabled = audition.enabled,
        selected = audition.playing,
        icon = auditionIconOf(audition),
        contentDescription = auditionDescriptionOf(audition, R.string.cast_bgm_play),
    )
}

/** 试听三态的图标：合成中自转的刷新图标 / 播放中停止 / 待命播放。 */
private fun auditionIconOf(audition: VoiceAuditionController): ImageVector = when {
    audition.busy -> Icons.Default.Refresh
    audition.playing -> Icons.Default.Pause
    else -> Icons.Default.PlayArrow
}

/**
 * 试听三态的描述。[idle] 由调用方给：列表行尾那颗是「播放」，弹层头部那颗是「试听」。
 *
 * 合成中一律念「加载中」：这一刻按钮点不动，念「播放/试听」会被当成点了没反应。
 */
@Composable
private fun auditionDescriptionOf(
    audition: VoiceAuditionController,
    @StringRes idle: Int,
): String = stringResource(
    when {
        audition.busy -> R.string.loading
        audition.playing -> R.string.cast_bgm_stop
        else -> idle
    },
)

/**
 * 合成中的自转角度。
 *
 * 只在合成这一小会儿才组合进来：声音池展开后同屏十几行按钮，静置的行不该各自挂一条无限动画。
 */
@Composable
private fun auditionSpinAngle(): Float {
    val transition = rememberInfiniteTransition(label = "auditionSpin")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
        label = "auditionSpinAngle",
    ).value
}