package io.legado.app.ui.book.readaloud.cast

import android.net.Uri
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.VoiceEffectPreset
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 变声器管理页状态（多角色规则 → 变声器）。
 *
 * [rows] 里带的是预设本身 + 一行中文摘要（音高/语速/混响/金属感），摘要在 VM 里用资源串
 * 拼好，界面只管显示。[editTarget] 非空即弹出编辑框（新建时是一条未落库的空预设）。
 */
@Stable
data class VoiceEffectUiState(
    val rows: ImmutableList<VoiceEffectRow> = persistentListOf(),
    val editTarget: VoiceEffectPreset? = null,
    val isNew: Boolean = false,
    val deleteTarget: VoiceEffectPreset? = null,
    val showIoSheet: Boolean = false,
)

@Stable
data class VoiceEffectRow(
    val preset: VoiceEffectPreset,
    val summary: String,
    /** 混响/金属感在当前朗读引擎下挂不上（系统 TTS 直读没有我们持有的播放器）。 */
    val sessionLayerDropped: Boolean = false,
)

sealed interface VoiceEffectIntent {
    data object Refresh : VoiceEffectIntent
    data object ShowCreate : VoiceEffectIntent
    data class ShowEdit(val preset: VoiceEffectPreset) : VoiceEffectIntent
    data object DismissEdit : VoiceEffectIntent
    data class Save(val preset: VoiceEffectPreset) : VoiceEffectIntent
    data class Toggle(val name: String, val enabled: Boolean) : VoiceEffectIntent
    data class ShowDelete(val preset: VoiceEffectPreset) : VoiceEffectIntent
    data object DismissDelete : VoiceEffectIntent
    data class Delete(val name: String) : VoiceEffectIntent
    data object RestoreBuiltins : VoiceEffectIntent
    data object ShowIoSheet : VoiceEffectIntent
    data object DismissIoSheet : VoiceEffectIntent
    data object OpenImporter : VoiceEffectIntent
    data object RequestExporter : VoiceEffectIntent
    data class ImportFrom(val text: String) : VoiceEffectIntent
    data class ExportTo(val uri: Uri) : VoiceEffectIntent
}

sealed interface VoiceEffectEffect {
    data class ShowToast(val message: String) : VoiceEffectEffect
    data object OpenImporter : VoiceEffectEffect
    data class SaveExporter(val fileName: String) : VoiceEffectEffect
}
