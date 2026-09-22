package io.legado.app.feature.settings.otherconfig

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// M5-8a：从 `:app` 的 `io.legado.app.ui.config.otherConfig` 迁来。
//
// 本片只迁**逻辑层**（Contract + ViewModel），Screen / RouteScreen / DirectLinkUploadBottomSheet
// 仍留在 `:app` —— 它们的文案 54 条 + 4 个 `string-array`（其中 `default_app_variant` 的条目是
// `@string/*` 间接引用，而共享层的数组约定是纯字面量，需要展开）值得单独一片做。
//
// ⚠️ **一处非改名的结构变更**：[OtherConfigMessage] 的 `resId: Int?`（`@StringRes`）→
// [OtherConfigMessageRes] 枚举。理由与 about 的 `AboutMessage` 完全一样：`androidx.annotation.StringRes`
// 与 `Int` 资源 id 都是 Android 概念，进不了 commonMain；契约因此改成
// **枚举 + UI 侧查表**（表在 `OtherConfigMessageText.kt`），让本模块的公开契约零资源依赖。
//
// 该改动**牵动留在 `:app` 的 RouteScreen**（它是渲染消息的地方）—— 那里把 `resId` 分支换成
// `message.res?.localizedText()`，行为等价（见 `OtherConfigRouteScreen` 的注释）。

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

/**
 * 本页需要**查表成文案**的消息种类（迁移前是 `R.string.*` 的 `Int` 资源 id）。
 *
 * 与 `OtherConfigMessage.text` 那条路径相对：这里只放**必须走资源**的（否则会把它降级成硬编码
 * 字符串，丢掉多语言）；错误详情这类运行期字符串仍走 [OtherConfigMessage.text]。
 */
enum class OtherConfigMessageRes {
    ClearWebViewDataSuccess,
    ClearWebViewDataFailed,
    CompleteRequiredInformation,
}

@Stable
data class OtherConfigMessage(
    val id: Long,
    val res: OtherConfigMessageRes?,
    val text: String?,
) {
    init {
        require((res == null) != (text == null))
    }

    companion object {
        fun resource(id: Long, res: OtherConfigMessageRes) =
            OtherConfigMessage(id = id, res = res, text = null)

        fun text(id: Long, text: String) =
            OtherConfigMessage(id = id, res = null, text = text)
    }
}

sealed interface OtherConfigOverlay {
    data object FilePicker : OtherConfigOverlay
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
