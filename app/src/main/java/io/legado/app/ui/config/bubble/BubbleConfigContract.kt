package io.legado.app.ui.config.bubble

import androidx.compose.runtime.Stable
import io.legado.app.help.config.BubblePackageManager

@Stable
data class BubbleConfigUiState(
    val entries: List<BubblePackageManager.Entry> = emptyList(),
    val activeDirName: String = BubblePackageManager.BUILTIN_DIR_NAME,
    val forceSoftwareParagraphBubble: Boolean = false,
    val loading: Boolean = true,
    val editor: BubbleEditorState? = null,
) {
    val activeEntry: BubblePackageManager.Entry?
        get() = entries.firstOrNull { it.dirName == activeDirName }
}

/** 编辑中的气泡包；[original] 为 null 表示新建。 */
@Stable
data class BubbleEditorState(
    val original: BubblePackageManager.Entry?,
    val config: BubblePackageManager.Config,
    val saving: Boolean = false,
)

sealed interface BubbleConfigIntent {
    data object Refresh : BubbleConfigIntent
    data class Apply(val entry: BubblePackageManager.Entry) : BubbleConfigIntent
    data class Delete(val entry: BubblePackageManager.Entry) : BubbleConfigIntent
    data class Export(val entry: BubblePackageManager.Entry, val targetUri: String) : BubbleConfigIntent
    data class Import(val uri: String) : BubbleConfigIntent
    data class ForceSoftwareChanged(val enabled: Boolean) : BubbleConfigIntent
    data object StartCreate : BubbleConfigIntent
    data class StartEdit(val entry: BubblePackageManager.Entry) : BubbleConfigIntent
    data class UpdateDraft(val config: BubblePackageManager.Config) : BubbleConfigIntent
    data object SaveDraft : BubbleConfigIntent
    data object DismissEditor : BubbleConfigIntent
}

sealed interface BubbleConfigEffect {
    data class Message(val text: String) : BubbleConfigEffect
    data class Exported(val message: String) : BubbleConfigEffect
}
