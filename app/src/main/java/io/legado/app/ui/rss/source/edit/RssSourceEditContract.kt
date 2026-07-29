package io.legado.app.ui.rss.source.edit

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.legado.app.ui.book.source.edit.BookSourceEditFieldUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

enum class RssSourceEditTab(@StringRes val title: Int) {
    Base(io.legado.app.R.string.source_tab_base),
    Start(io.legado.app.R.string.source_tab_start),
    List(io.legado.app.R.string.source_tab_list),
    WebView(io.legado.app.R.string.source_tab_web_view),
}
@Stable data class RssSourceEditUiState(
    val loading: Boolean = true, val selectedTab: RssSourceEditTab = RssSourceEditTab.Base,
    val fields: ImmutableMap<RssSourceEditTab, ImmutableList<BookSourceEditFieldUi>> = persistentMapOf(),
    val enabled: Boolean = true, val singleUrl: Boolean = false, val cookieJar: Boolean = true,
    val preload: Boolean = false, val type: Int = 0, val articleStyle: Int = 0,
    val autoComplete: Boolean = false, val dirty: Boolean = false, val selectedField: String? = null,
    val activeSheet: RssSourceEditSheet? = null,
)

sealed interface RssSourceEditSheet {
    data object Log : RssSourceEditSheet;
    data class Help(val content: String) : RssSourceEditSheet
}
sealed interface RssSourceEditIntent {
    data class Load(val url: String?) : RssSourceEditIntent; data class SelectTab(val tab: RssSourceEditTab) : RssSourceEditIntent
    data class UpdateField(val key: String, val value: String) : RssSourceEditIntent; data class EditField(val key: String?) : RssSourceEditIntent
    data class SetEnabled(val value: Boolean) : RssSourceEditIntent; data class SetSingleUrl(val value: Boolean) : RssSourceEditIntent
    data class SetCookieJar(val value: Boolean) : RssSourceEditIntent; data class SetPreload(val value: Boolean) : RssSourceEditIntent
    data class SetType(val value: Int) : RssSourceEditIntent; data class SetArticleStyle(val value: Int) : RssSourceEditIntent
    data object ToggleAutoComplete : RssSourceEditIntent; data object Save : RssSourceEditIntent; data object SaveDebug : RssSourceEditIntent
    data object SaveLogin : RssSourceEditIntent; data object Copy : RssSourceEditIntent; data object Paste : RssSourceEditIntent
    data class Import(val text: String) : RssSourceEditIntent; data object Share : RssSourceEditIntent; data object ClearCookie : RssSourceEditIntent
    data object SetVariable : RssSourceEditIntent;
    data object ShowLog : RssSourceEditIntent;
    data object Help : RssSourceEditIntent;
    data object DismissSheet : RssSourceEditIntent;
    data object Back : RssSourceEditIntent
}
sealed interface RssSourceEditEffect {
    data class Finish(val url: String) : RssSourceEditEffect; data class Debug(val url: String) : RssSourceEditEffect; data class Login(val url: String) : RssSourceEditEffect
    data class Copy(val text: String) : RssSourceEditEffect; data class Share(val text: String) : RssSourceEditEffect; data object ReadClipboard : RssSourceEditEffect
    data class Variable(val url: String) : RssSourceEditEffect;
    data class Message(val text: String) : RssSourceEditEffect
}
