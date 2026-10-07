package io.legado.app.ui.book.readaloud.cloudtts

import android.net.Uri
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.HttpTTS
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class CloudTtsUiState(
    val loading: Boolean = true,
    val engines: ImmutableList<CloudTtsEngineItemUi> = persistentListOf(),
    val systemEngines: ImmutableList<TtsManagedEngineItemUi> = persistentListOf(),
    val httpEngines: ImmutableList<TtsManagedEngineItemUi> = persistentListOf(),
    val voices: ImmutableList<CloudTtsVoiceItemUi> = persistentListOf(),
    val discoveredVoices: ImmutableList<CloudTtsDiscoveredVoiceUi> = persistentListOf(),
    val availableEngines: ImmutableList<TtsEngineOptionUi> = persistentListOf(),
    val engineEditor: CloudTtsEngineEditorUi? = null,
    val voiceEditor: TtsVoicePresetEditorUi? = null,
    val httpTtsEditor: HttpTTS? = null,
    val httpTtsImportState: BaseImportUiState<HttpTTS> = BaseImportUiState.Idle,
    val showVoiceEnginePicker: Boolean = false,
    val activeDialog: CloudTtsDialog? = null,
    val testing: Boolean = false,
    val discovering: Boolean = false,
    val selectedTab: CloudTtsTab = CloudTtsTab.Voices,
    /** 试听文本（用户改过的或内置默认），试听悬浮窗打开时填它。 */
    val previewText: String = "",
    /**
     * 音色批量选择模式：开了之后行尾的试听/删除按钮换成勾选，
     * 列表上方出现「全选 / 删除(n) / 退出」。
     */
    val voiceBatchMode: Boolean = false,
    val selectedVoiceIds: ImmutableSet<String> = persistentSetOf(),
    /** 「添加到声音池」弹层：非空 = 打开，带着要加进去的音色 id。 */
    val poolPicker: VoicePoolPickerUi? = null,
)

/** 把音色加进角色声音池的弹层内容：待加的音色 + 可选的池列表。 */
@Stable
data class VoicePoolPickerUi(
    val voiceIds: ImmutableList<String>,
    val voiceLabel: String,
    val pools: ImmutableList<VoicePoolOptionUi>,
)

@Stable
data class VoicePoolOptionUi(
    val id: String,
    val name: String,
    val groupName: String,
    val memberCount: Int,
)

enum class CloudTtsTab { Voices, Engines }

@Stable
data class CloudTtsEngineItemUi(
    val id: String,
    val title: String,
    val summary: String,
    val selected: Boolean = false,
)

@Stable
data class TtsManagedEngineItemUi(
    val engineType: String,
    val engineId: String,
    val title: String,
    val summary: String = "",
    val selected: Boolean = false,
    val loginUrl: String = "",
)
@Stable data class CloudTtsVoiceItemUi(
    val id: String,
    val title: String,
    val summary: String,
    val deletable: Boolean,
    val editable: Boolean,
)
@Stable data class TtsEngineOptionUi(
    val engineType: String,
    val engineId: String,
    val title: String,
    val summary: String,
    val catalogHint: String,
    val remoteCatalog: Boolean = false,
)
@Stable data class CloudTtsDiscoveredVoiceUi(
    val id: String,
    val label: String,
    val locale: String,
    val styles: ImmutableList<String>,
    val roles: ImmutableList<String>,
)

@Stable
data class CloudTtsEngineEditorUi(
    val editingEngineId: String? = null,
    val name: String = "",
    val provider: String = "mimo",
    val baseUrl: String = "",
    val apiKey: String = "",
    val secretKey: String = "",
    val region: String = "",
    val appId: String = "",
    val model: String = "",
    val optionsJson: String = "{}",
)

@Stable
data class TtsVoicePresetEditorUi(
    val editingVoiceId: String? = null,
    val engineType: String = "",
    val engineId: String = "",
    val engineName: String = "",
    val voiceId: String = "",
    val voiceName: String = "",
    val locale: String = "",
    val style: String = "",
    val role: String = "",
    val instructions: String = "",
    val automaticEmotion: Boolean = true,
    val characterPersonality: Boolean = true,
    val thoughtPerformance: Boolean = true,
    val speed: String = "1.0",
    val pitch: String = "1.0",
    val volume: String = "1.0",
    val format: String = "mp3",
    val formatOptions: ImmutableList<String> = persistentListOf(),
)

sealed interface CloudTtsIntent {
    data class SelectTab(val tab: CloudTtsTab) : CloudTtsIntent
    data class SetBookContext(val bookUrl: String?) : CloudTtsIntent
    data object AddEngine : CloudTtsIntent
    data object AddVoice : CloudTtsIntent
    data class AddVoiceForEngine(val engineType: String, val engineId: String) : CloudTtsIntent
    data class EditEngine(val id: String) : CloudTtsIntent
    data class DeleteEngine(val id: String) : CloudTtsIntent
    data class SetDefaultEngine(val engineType: String, val engineId: String) : CloudTtsIntent
    data object ApplyDefaultEngineGlobally : CloudTtsIntent
    data object ApplyDefaultEngineForBook : CloudTtsIntent
    data class EditHttpTts(val engineId: String? = null) : CloudTtsIntent
    data class SaveHttpTts(val value: HttpTTS) : CloudTtsIntent
    data class DeleteHttpTts(val engineId: String) : CloudTtsIntent
    data class OpenHttpTtsLogin(val engineId: String) : CloudTtsIntent
    data object DismissHttpTtsEditor : CloudTtsIntent
    data class ImportHttpTtsSource(val text: String) : CloudTtsIntent
    data object ImportHttpTtsFile : CloudTtsIntent
    data class ImportHttpTtsFileSelected(val uri: Uri) : CloudTtsIntent
    data object CancelHttpTtsImport : CloudTtsIntent
    data class ToggleHttpTtsImportSelection(val index: Int) : CloudTtsIntent
    data class ToggleHttpTtsImportAll(val selected: Boolean) : CloudTtsIntent
    data class UpdateHttpTtsImportItem(val index: Int, val value: HttpTTS) : CloudTtsIntent
    data object SaveImportedHttpTts : CloudTtsIntent
    data object ExportHttpTtsFile : CloudTtsIntent
    data class ExportHttpTtsFileSelected(val uri: Uri) : CloudTtsIntent
    data object ExportHttpTtsUrl : CloudTtsIntent
    data object ClearTtsCache : CloudTtsIntent
       data class RequestDeleteVoice(val id: String) : CloudTtsIntent
    data object ConfirmDeleteVoice : CloudTtsIntent
    /** 进入/退出音色批量选择模式（退出会清空已勾选的）。 */
    data object ToggleVoiceBatchMode : CloudTtsIntent
    data class ToggleVoiceSelection(val id: String) : CloudTtsIntent
    /** 全选/取消全选当前列表里可删的音色。 */
    data object ToggleSelectAllVoices : CloudTtsIntent
    data object RequestDeleteSelectedVoices : CloudTtsIntent
    data object ConfirmDeleteSelectedVoices : CloudTtsIntent
    /** 打开「添加到声音池」弹层；[ids] 为空时按批量勾选的那一批处理。 */
    data class RequestAddToPool(val ids: List<String>) : CloudTtsIntent
    data object DismissPoolPicker : CloudTtsIntent
    data class ConfirmAddToPool(val poolId: String) : CloudTtsIntent
    data class EditVoice(val id: String) : CloudTtsIntent
    data class UpdateEngineEditor(val editor: CloudTtsEngineEditorUi) : CloudTtsIntent
    data class UpdateVoiceEditor(val editor: TtsVoicePresetEditorUi) : CloudTtsIntent
    data object DismissEngineEditor : CloudTtsIntent
    data object DismissVoiceEditor : CloudTtsIntent
    data object DismissVoiceEnginePicker : CloudTtsIntent
    data class SelectEngine(val engineType: String, val engineId: String) : CloudTtsIntent
    data object DiscoverVoices : CloudTtsIntent
    data class SelectVoice(val id: String) : CloudTtsIntent
    data object TestEngine : CloudTtsIntent
    data object Preview : CloudTtsIntent
    /**
     * 列表行上的试听按钮：不打开编辑弹窗，直接按该音色合成一句。
     * [text] 非空 = 用这句（试听悬浮窗里正在编辑的草稿），否则用已保存的默认文本。
     */
    data class PreviewVoice(val id: String, val text: String? = null) : CloudTtsIntent
    /** 保存默认试听文本（试听悬浮窗的「确定」）。 */
    data class SavePreviewText(val text: String) : CloudTtsIntent
    data object Save : CloudTtsIntent
    data object DismissError : CloudTtsIntent
    data object CopyError : CloudTtsIntent
    data class ReportError(val message: String) : CloudTtsIntent
}

sealed interface CloudTtsEffect {
    data class ShowToast(val message: String) : CloudTtsEffect
    data class PlayPreview(val path: String) : CloudTtsEffect
    data class CopyText(val text: String, val message: String) : CloudTtsEffect
    data object OpenHttpTtsImportPicker : CloudTtsEffect
    data object OpenHttpTtsExportPicker : CloudTtsEffect
    data class OpenHttpTtsLogin(val engineId: Long) : CloudTtsEffect
}

sealed interface CloudTtsDialog {
    data class Error(val message: String) : CloudTtsDialog
    data class DeleteVoice(val id: String, val title: String) : CloudTtsDialog
    /** 批量删除确认：[count] 只用于文案，真正删的是 [ids]。 */
    data class DeleteVoices(val ids: ImmutableList<String>, val count: Int) : CloudTtsDialog
    data class DefaultEngineScope(val value: String?, val title: String) : CloudTtsDialog
}
