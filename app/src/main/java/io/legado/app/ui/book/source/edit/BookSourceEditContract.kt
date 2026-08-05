package io.legado.app.ui.book.source.edit

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.legado.app.ui.widget.components.variable.VariableEditorUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

enum class BookSourceEditTab(@StringRes val titleRes: Int) {
    Base(io.legado.app.R.string.source_tab_base),
    Search(io.legado.app.R.string.source_tab_search),
    Explore(io.legado.app.R.string.source_tab_find),
    Info(io.legado.app.R.string.source_tab_info),
    Toc(io.legado.app.R.string.source_tab_toc),
    Content(io.legado.app.R.string.source_tab_content),
}

@Immutable
data class BookSourceEditFieldUi(
    val path: String,
    @StringRes val labelRes: Int? = null,
    val label: String? = null,
    val value: String = "",
)

@Stable
data class BookSourceEditUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val selectedTab: BookSourceEditTab = BookSourceEditTab.Base,
    val fieldGroups: ImmutableMap<BookSourceEditTab, ImmutableList<BookSourceEditFieldUi>> = persistentMapOf(),
    val enabled: Boolean = true,
    val enabledExplore: Boolean = true,
    val enabledCookieJar: Boolean = true,
    val eventListener: Boolean = false,
    val customButton: Boolean = false,
    val bookSourceType: Int = 0,
    val autoComplete: Boolean = false,
    val dirty: Boolean = false,
    val activeSheet: BookSourceEditSheet? = null,
    val activeDialog: BookSourceEditDialog? = null,
)

sealed interface BookSourceEditDialog {
    data object ConfirmDiscard : BookSourceEditDialog
}

sealed interface BookSourceEditSheet {
    data object Log : BookSourceEditSheet
    data class Help(val content: String) : BookSourceEditSheet
    data class Variable(val editor: VariableEditorUiState) : BookSourceEditSheet
}

sealed interface BookSourceEditIntent {
    data class Load(val sourceUrl: String?) : BookSourceEditIntent
    data class SelectTab(val tab: BookSourceEditTab) : BookSourceEditIntent
    data class UpdateField(val path: String, val value: String) : BookSourceEditIntent
    data class SetEnabled(val value: Boolean) : BookSourceEditIntent
    data class SetExploreEnabled(val value: Boolean) : BookSourceEditIntent
    data class SetCookieJarEnabled(val value: Boolean) : BookSourceEditIntent
    data class SetEventListener(val value: Boolean) : BookSourceEditIntent
    data class SetCustomButton(val value: Boolean) : BookSourceEditIntent
    data class SetSourceType(val value: Int) : BookSourceEditIntent
    data class ImportText(val text: String) : BookSourceEditIntent
    data object ToggleAutoComplete : BookSourceEditIntent
    data object Save : BookSourceEditIntent
    data object SaveAndDebug : BookSourceEditIntent
    data object SaveAndLogin : BookSourceEditIntent
    data object SaveAndSearch : BookSourceEditIntent
    data object Copy : BookSourceEditIntent
    data object Share : BookSourceEditIntent
    data object Paste : BookSourceEditIntent
    data object ClearCookie : BookSourceEditIntent
    data object ShowLog : BookSourceEditIntent
    data object ShowHelp : BookSourceEditIntent
    data object DismissSheet : BookSourceEditIntent
    data object SaveAndSetVariable : BookSourceEditIntent
    data class UpdateVariable(val value: String) : BookSourceEditIntent
    data object SaveVariable : BookSourceEditIntent
    data object RequestBack : BookSourceEditIntent
    data object DismissDialog : BookSourceEditIntent
    data object DiscardChanges : BookSourceEditIntent
}

sealed interface BookSourceEditEffect {
    data class Finish(val sourceUrl: String) : BookSourceEditEffect
    data class OpenDebug(val sourceUrl: String) : BookSourceEditEffect
    data class OpenLogin(val sourceUrl: String) : BookSourceEditEffect
    data class OpenSearch(val sourceJson: String) : BookSourceEditEffect
    data class CopyText(val text: String) : BookSourceEditEffect
    data class ShareText(val text: String) : BookSourceEditEffect
    data object ReadClipboard : BookSourceEditEffect
    data class OpenVariable(val sourceUrl: String) : BookSourceEditEffect
    data class ShowMessage(val message: String) : BookSourceEditEffect
}
