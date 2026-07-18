package io.legado.app.ui.config.downloadCacheConfig

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.DownloadCacheSettings

@Stable
data class DownloadCacheConfigUiState(
    val settings: DownloadCacheSettings = DownloadCacheSettings(),
    val coverCacheSizeMb: Double = 0.0,
    val mangaCacheSizeMb: Double = 0.0,
    val dialog: DownloadCacheConfigDialog? = null,
)

enum class DownloadCacheConfigDialog {
    ClearCoverCache,
    ClearMangaCache,
    ClearBookCache,
    ShrinkDatabase,
}

sealed interface DownloadCacheConfigIntent {
    data class SetThreadCount(val value: Int) : DownloadCacheConfigIntent
    data class SetCacheBookThreadCount(val value: Int) : DownloadCacheConfigIntent
    data class SetPreDownloadNum(val value: Int) : DownloadCacheConfigIntent
    data class SetBitmapCacheSize(val value: Int) : DownloadCacheConfigIntent
    data class SetImageRetainNum(val value: Int) : DownloadCacheConfigIntent
    data class SetUserAgent(val value: String) : DownloadCacheConfigIntent
    data class SetCronetEnabled(val value: Boolean) : DownloadCacheConfigIntent
    data class ShowDialog(val dialog: DownloadCacheConfigDialog) : DownloadCacheConfigIntent
    data object DismissDialog : DownloadCacheConfigIntent
    data object ConfirmDialog : DownloadCacheConfigIntent
}

sealed interface DownloadCacheConfigEffect
