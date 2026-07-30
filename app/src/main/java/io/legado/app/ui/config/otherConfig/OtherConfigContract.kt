package io.legado.app.ui.config.otherConfig

import androidx.compose.runtime.Stable
import androidx.annotation.StringRes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class OtherConfigUiState(
    val language: String = "auto",
    val updateToVariant: String = "official_version",
    val autoCheckUpdateOnStart: Boolean = false,
    val webServiceAutoStart: Boolean = false,
    val autoRefresh: Boolean = false,
    val defaultToRead: Boolean = false,
    val firebaseEnable: Boolean = true,
    val defaultBookTreeUri: String? = null,
    val antiAlias: Boolean = false,
    val replaceEnableDefault: Boolean = true,
    val mediaButtonOnExit: Boolean = true,
    val readAloudByMediaButton: Boolean = false,
    val ignoreAudioFocus: Boolean = false,
    val autoClearExpired: Boolean = true,
    val showAddToShelfAlert: Boolean = true,
    val showMangaUi: Boolean = true,
    val webServiceWakeLock: Boolean = false,
    val sourceEditMaxLine: Int = Int.MAX_VALUE,
    val webPort: Int = 1122,
    val processText: Boolean = true,
    val recordLog: Boolean = false,
    val recordHeapDump: Boolean = false,
    val checkSourceTimeoutSeconds: Long = 180,
    val checkSearch: Boolean = true,
    val checkDiscovery: Boolean = true,
    val checkInfo: Boolean = true,
    val checkCategory: Boolean = true,
    val checkContent: Boolean = true,
    val directUploadUrl: String = "",
    val directDownloadUrlRule: String = "",
    val directSummary: String = "",
    val directCompress: Boolean = false,
    val directRulePresets: ImmutableList<DirectLinkRuleUi> = persistentListOf(),
    val directTestResult: String? = null,
    val activeOverlay: OtherConfigOverlay? = null,
    val pendingMessages: ImmutableList<OtherConfigMessage> = persistentListOf(),
)

@Stable
data class DirectLinkRuleUi(
    val uploadUrl: String,
    val downloadUrlRule: String,
    val summary: String,
    val compress: Boolean,
) {
    override fun toString(): String = summary
}

@Stable
data class OtherConfigMessage(
    val id: Long,
    @StringRes val resId: Int?,
    val text: String?,
) {
    init {
        require((resId == null) != (text == null))
    }

    companion object {
        fun resource(id: Long, @StringRes resId: Int) =
            OtherConfigMessage(id = id, resId = resId, text = null)

        fun text(id: Long, text: String) =
            OtherConfigMessage(id = id, resId = null, text = text)
    }
}

sealed interface OtherConfigOverlay {
    data object FilePicker : OtherConfigOverlay
    data object CheckSource : OtherConfigOverlay
    data object DirectLinkUpload : OtherConfigOverlay
    data object ClearWebViewConfirmation : OtherConfigOverlay
    data object Password : OtherConfigOverlay
}

sealed interface OtherConfigIntent {
    data class LanguageChanged(val value: String) : OtherConfigIntent
    data class UpdateToVariantChanged(val value: String) : OtherConfigIntent
    data class AutoCheckUpdateOnStartChanged(val value: Boolean) : OtherConfigIntent
    data class WebServiceAutoStartChanged(val value: Boolean) : OtherConfigIntent
    data class AutoRefreshChanged(val value: Boolean) : OtherConfigIntent
    data class DefaultToReadChanged(val value: Boolean) : OtherConfigIntent
    data class FirebaseEnableChanged(val value: Boolean) : OtherConfigIntent
    data class DefaultBookTreeUriChanged(val value: String?) : OtherConfigIntent
    data class AntiAliasChanged(val value: Boolean) : OtherConfigIntent
    data class ReplaceEnableDefaultChanged(val value: Boolean) : OtherConfigIntent
    data class MediaButtonOnExitChanged(val value: Boolean) : OtherConfigIntent
    data class ReadAloudByMediaButtonChanged(val value: Boolean) : OtherConfigIntent
    data class IgnoreAudioFocusChanged(val value: Boolean) : OtherConfigIntent
    data class AutoClearExpiredChanged(val value: Boolean) : OtherConfigIntent
    data class ShowAddToShelfAlertChanged(val value: Boolean) : OtherConfigIntent
    data class ShowMangaUiChanged(val value: Boolean) : OtherConfigIntent
    data class WebServiceWakeLockChanged(val value: Boolean) : OtherConfigIntent
    data class SourceEditMaxLineChanged(val value: Int) : OtherConfigIntent
    data class WebPortChanged(val value: Int) : OtherConfigIntent
    data class ProcessTextChanged(val value: Boolean) : OtherConfigIntent
    data class RecordLogChanged(val value: Boolean) : OtherConfigIntent
    data class RecordHeapDumpChanged(val value: Boolean) : OtherConfigIntent
    data class CheckSourceTimeoutChanged(val value: Long) : OtherConfigIntent
    data class CheckSearchChanged(val value: Boolean) : OtherConfigIntent
    data class CheckDiscoveryChanged(val value: Boolean) : OtherConfigIntent
    data class CheckInfoChanged(val value: Boolean) : OtherConfigIntent
    data class CheckCategoryChanged(val value: Boolean) : OtherConfigIntent
    data class CheckContentChanged(val value: Boolean) : OtherConfigIntent
    data object ConfirmCheckSource : OtherConfigIntent
    data class DirectUploadUrlChanged(val value: String) : OtherConfigIntent
    data class DirectDownloadUrlRuleChanged(val value: String) : OtherConfigIntent
    data class DirectSummaryChanged(val value: String) : OtherConfigIntent
    data class DirectCompressChanged(val value: Boolean) : OtherConfigIntent
    data class DirectRuleChanged(
        val uploadUrl: String,
        val downloadUrlRule: String,
        val summary: String,
        val compress: Boolean,
    ) : OtherConfigIntent
    data object ConfirmDirectLinkRule : OtherConfigIntent
    data object TestDirectLinkRule : OtherConfigIntent
    data object DismissDirectTestResult : OtherConfigIntent
    data class ShowOverlay(val overlay: OtherConfigOverlay) : OtherConfigIntent
    data object DismissOverlay : OtherConfigIntent
    data object RequestNotificationPermission : OtherConfigIntent
    data object RequestBatteryPermission : OtherConfigIntent
    data object RequestSystemDirectory : OtherConfigIntent
    data object ConfirmClearWebViewData : OtherConfigIntent
    data class SaveLocalPassword(val password: String) : OtherConfigIntent
    data class MessageShown(val id: Long) : OtherConfigIntent
}

sealed interface OtherConfigEffect {
    data object RequestNotificationPermission : OtherConfigEffect
    data object RequestBatteryPermission : OtherConfigEffect
    data object OpenSystemDirectory : OtherConfigEffect
    data object RestartWebService : OtherConfigEffect
    data object RestartApp : OtherConfigEffect
}
