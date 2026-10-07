package io.legado.app.ui.book.readaloud.cast

import android.app.Application
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.VoiceEffectPreset
import io.legado.app.help.readaloud.effect.VoiceEffectStore
import io.legado.app.model.ReadAloud
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 变声器管理 ViewModel。
 *
 * DAO 访问收口在 [VoiceEffectStore]（架构护栏：VM 不直连 DAO）。改完预设即生效——
 * 朗读侧每次准备新章会重取快照，正在播的那一条不受影响，下一条起用新参数。
 */
class VoiceEffectViewModel(
    application: Application,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(VoiceEffectUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<VoiceEffectEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        refresh()
    }

    fun onIntent(intent: VoiceEffectIntent) {
        when (intent) {
            VoiceEffectIntent.Refresh -> refresh()
            VoiceEffectIntent.ShowCreate -> _uiState.update {
                it.copy(isNew = true, editTarget = VoiceEffectPreset(name = ""))
            }

            is VoiceEffectIntent.ShowEdit -> _uiState.update {
                it.copy(isNew = false, editTarget = intent.preset)
            }

            VoiceEffectIntent.DismissEdit -> _uiState.update { it.copy(editTarget = null) }
            is VoiceEffectIntent.Save -> save(intent.preset)
            is VoiceEffectIntent.Toggle -> launchIo {
                VoiceEffectStore.setEnabled(intent.name, intent.enabled)
                refresh()
            }

            is VoiceEffectIntent.ShowDelete -> _uiState.update { it.copy(deleteTarget = intent.preset) }
            VoiceEffectIntent.DismissDelete -> _uiState.update { it.copy(deleteTarget = null) }
            is VoiceEffectIntent.Delete -> launchIo {
                VoiceEffectStore.delete(intent.name)
                refresh()
            }

            VoiceEffectIntent.RestoreBuiltins -> launchIo {
                VoiceEffectStore.restoreBuiltins()
                refresh()
                toast(R.string.voice_effect_restored)
            }

            VoiceEffectIntent.ShowIoSheet -> _uiState.update { it.copy(showIoSheet = true) }
            VoiceEffectIntent.DismissIoSheet -> _uiState.update { it.copy(showIoSheet = false) }
            VoiceEffectIntent.OpenImporter -> _effects.tryEmit(VoiceEffectEffect.OpenImporter)
            VoiceEffectIntent.RequestExporter -> _effects.tryEmit(
                VoiceEffectEffect.SaveExporter("voice_effect_${System.currentTimeMillis()}.json"),
            )

            is VoiceEffectIntent.ImportFrom -> launchIo {
                val count = VoiceEffectStore.importText(intent.text)
                refresh()
                toast(if (count > 0) R.string.voice_effect_imported else R.string.voice_effect_import_failed)
            }

            is VoiceEffectIntent.ExportTo -> launchIo {
                val text = VoiceEffectStore.exportText()
                runCatching {
                    context.contentResolver.openOutputStream(intent.uri, "wt")
                        ?.use { it.write(text.toByteArray()) }
                }
            }
        }
    }

    private fun save(preset: VoiceEffectPreset) {
        val name = preset.name.trim()
        if (name.isEmpty()) {
            toast(R.string.voice_effect_name_empty)
            return
        }
        _uiState.update { it.copy(editTarget = null) }
        launchIo {
            VoiceEffectStore.save(preset.copy(name = name))
            refresh()
            toast(R.string.voice_effect_saved)
        }
    }

    private fun refresh() = launchIo {
        // 会话级效果挂不上时（系统 TTS 直读）要在列表里说出来，不能让用户以为预设没存住
        val sessionOk = ReadAloud.supportsSessionAudioEffect
        val rows = VoiceEffectStore.list().map { preset ->
            val dropped = !sessionOk && VoiceEffectStore.needsSessionEffect(preset)
            VoiceEffectRow(preset, summaryOf(preset, dropped), dropped)
        }
        _uiState.update { it.copy(rows = rows.toImmutableList()) }
    }

    private fun summaryOf(preset: VoiceEffectPreset, dropped: Boolean): String {
        val res = context
        val parts = mutableListOf(
            context.getString(R.string.voice_effect_pitch) + " ×" + formatRate(preset.pitch),
            context.getString(R.string.voice_effect_speed) + " ×" + formatRate(preset.speed),
        )
        if (preset.reverbPreset != VoiceEffectStore.REVERB_NONE) {
            parts += context.getString(reverbRes(preset.reverbPreset))
        }
        if (preset.metal) parts += context.getString(R.string.voice_effect_metal)
        val text = parts.joinToString(" · ")
        return if (dropped) {
            text + " · " + res.getString(R.string.voice_effect_session_dropped_suffix)
        } else {
            text
        }
    }

    private fun reverbRes(preset: Int): Int = when (preset) {
        VoiceEffectStore.REVERB_SMALL_ROOM -> R.string.voice_effect_reverb_small_room
        VoiceEffectStore.REVERB_MEDIUM_ROOM -> R.string.voice_effect_reverb_medium_room
        VoiceEffectStore.REVERB_LARGE_ROOM -> R.string.voice_effect_reverb_large_room
        VoiceEffectStore.REVERB_MEDIUM_HALL -> R.string.voice_effect_reverb_medium_hall
        VoiceEffectStore.REVERB_LARGE_HALL -> R.string.voice_effect_reverb_large_hall
        else -> R.string.voice_effect_reverb_plate
    }

    private fun formatRate(value: Float): String =
        String.format(java.util.Locale.getDefault(), "%.2f", value)

    private fun launchIo(block: suspend () -> Unit) {
        execute { runCatching { block() } }
    }

    private fun toast(resId: Int) {
        _effects.tryEmit(VoiceEffectEffect.ShowToast(context.getString(resId)))
    }
}
