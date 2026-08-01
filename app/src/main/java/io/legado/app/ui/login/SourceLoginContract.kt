package io.legado.app.ui.login

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.Serializable

@Serializable
enum class SourceLoginType {
    BookSource,
    RssSource,
    HttpTts,
    ReadingBook,
    AudioBook,
}

enum class SourceLoginMode { Form, Web }

@Immutable
data class LoginRowLayoutUi(
    val flexGrow: Float = 0f,
    val basisPercent: Float = -1f,
    val wrapBefore: Boolean = false,
    val justify: String = "auto",
)

@Immutable
sealed interface LoginRowUi {
    val key: String
    val title: String
    val action: String?
    val layout: LoginRowLayoutUi

    data class Text(
        override val key: String,
        override val title: String,
        override val action: String?,
        override val layout: LoginRowLayoutUi,
        val password: Boolean,
    ) : LoginRowUi

    data class Select(
        override val key: String,
        override val title: String,
        override val action: String?,
        override val layout: LoginRowLayoutUi,
        val options: ImmutableList<String>,
    ) : LoginRowUi

    data class Button(
        override val key: String,
        override val title: String,
        override val action: String?,
        override val layout: LoginRowLayoutUi,
    ) : LoginRowUi

    data class Toggle(
        override val key: String,
        override val title: String,
        override val action: String?,
        override val layout: LoginRowLayoutUi,
        val options: ImmutableList<String>,
        val valueOnStart: Boolean,
    ) : LoginRowUi
}

sealed interface SourceLoginSheet {
    data object Form : SourceLoginSheet
    data class LoginHeader(val content: String) : SourceLoginSheet
    data object Log : SourceLoginSheet
}

@Stable
data class SourceLoginUiState(
    val loading: Boolean = true,
    val title: String = "",
    val mode: SourceLoginMode = SourceLoginMode.Form,
    val rows: ImmutableList<LoginRowUi> = persistentListOf(),
    val values: ImmutableMap<String, String> = persistentMapOf(),
    val webUrl: String? = null,
    val headers: ImmutableMap<String, String> = persistentMapOf(),
    val webProgress: Int = 0,
    val checkingCookie: Boolean = false,
    val activeSheet: SourceLoginSheet? = null,
)

sealed interface SourceLoginIntent {
    data class Initialize(
        val type: SourceLoginType,
        val sourceKey: String? = null,
        val bookUrl: String? = null,
    ) : SourceLoginIntent

    data class ValueChanged(val key: String, val value: String) : SourceLoginIntent
    data class ValueCommitted(val key: String) : SourceLoginIntent
    data class RunAction(val key: String, val longClick: Boolean = false) : SourceLoginIntent
    data class WebProgressChanged(val progress: Int) : SourceLoginIntent
    data class WebPageStarted(val url: String) : SourceLoginIntent
    data class WebPageFinished(val url: String) : SourceLoginIntent
    data object Confirm : SourceLoginIntent
    data object ShowLoginHeader : SourceLoginIntent
    data object DeleteLoginHeader : SourceLoginIntent
    data object ShowLog : SourceLoginIntent
    data class CopyLoginHeader(val content: String) : SourceLoginIntent
    data object DismissSheet : SourceLoginIntent
    data object Back : SourceLoginIntent
}

sealed interface SourceLoginEffect {
    data object Finish : SourceLoginEffect
    data class ShowMessage(val message: String) : SourceLoginEffect
    data class OpenExternalUrl(val url: String) : SourceLoginEffect
    data class CopyText(val text: String) : SourceLoginEffect
}
