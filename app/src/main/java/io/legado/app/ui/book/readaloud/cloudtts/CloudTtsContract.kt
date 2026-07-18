package io.legado.app.ui.book.readaloud.cloudtts

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class CloudTtsUiState(
    val loading: Boolean = true,
    val engines: ImmutableList<CloudTtsEngineItemUi> = persistentListOf(),
    val voices: ImmutableList<CloudTtsVoiceItemUi> = persistentListOf(),
    val discoveredVoices: ImmutableList<CloudTtsDiscoveredVoiceUi> = persistentListOf(),
    val availableEngines: ImmutableList<TtsEngineOptionUi> = persistentListOf(),
    val engineEditor: CloudTtsEngineEditorUi? = null,
    val voiceEditor: TtsVoicePresetEditorUi? = null,
    val showVoiceEnginePicker: Boolean = false,
    val activeDialog: CloudTtsDialog? = null,
    val testing: Boolean = false,
    val discovering: Boolean = false,
    val selectedTab: CloudTtsTab = CloudTtsTab.Voices,
)

enum class CloudTtsTab { Voices, Engines }

@Stable data class CloudTtsEngineItemUi(val id: String, val title: String, val summary: String)
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
    data object AddEngine : CloudTtsIntent
    data object AddVoice : CloudTtsIntent
    data class AddVoiceForEngine(val engineType: String, val engineId: String) : CloudTtsIntent
    data class EditEngine(val id: String) : CloudTtsIntent
    data class DeleteEngine(val id: String) : CloudTtsIntent
    data class DeleteVoice(val id: String) : CloudTtsIntent
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
    data object Save : CloudTtsIntent
    data object DismissError : CloudTtsIntent
    data object CopyError : CloudTtsIntent
    data class ReportError(val message: String) : CloudTtsIntent
}

sealed interface CloudTtsEffect {
    data class ShowToast(val message: String) : CloudTtsEffect
    data class PlayPreview(val path: String) : CloudTtsEffect
    data class CopyText(val text: String) : CloudTtsEffect
}

sealed interface CloudTtsDialog {
    data class Error(val message: String) : CloudTtsDialog
}
