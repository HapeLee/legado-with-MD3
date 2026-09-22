package io.legado.app.feature.settings.downloadcache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.model.settings.DownloadCacheSettings
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.domain.usecase.ShrinkDatabaseUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// M5-7：从 `:app` 的 `io.legado.app.ui.config.downloadCacheConfig` 迁来。
//
// 五处平台直连全部收进 [DownloadCachePlatform]（`:app` 的 `AndroidDownloadCachePlatform`
// 提供实现，Koin 注入）：`CacheBook.maxDownloadConcurrency`、`getHttpCacheSize` ×2、
// `clearHttpCache` ×2、`FileUtils.delete(appCtx.cacheDir…)` + `externalCacheDir`、
// `ImageProvider.bitmapLruCache.resize(...)`。
//
// 两处需要说明的**非逐字**改动：
//
// 1. **`launch(Dispatchers.IO)` → `launch`**（`loadCacheSizes` 与 `confirmDialog`）。
//    调度器下沉到实现侧（契约把这几项标成 `suspend`，Android 实现内部 `withContext(IO)`）
//    —— 与 `AndroidAboutDiagnostics` 同一写法。VM 因此不再需要知道「这几件事是 IO」。
//
// 2. **`loadCacheSizes` 里两个 `getHttpCacheSize` 从 `_uiState.update { }` 的 lambda 内
//    挪到外面**。原因很直接：契约里它们是 `suspend`，而 `update { }` 的 lambda 不是挂起上下文。
//    语义不变 —— 迁移前也是先取封面再取漫画（lambda 内顺序求值），`copy` 同样只覆盖这两个字段；
//    新写法反而少在原子更新里做两次阻塞 IO。
class DownloadCacheConfigViewModel(
    private val clearBookCacheUseCase: ClearBookCacheUseCase,
    private val shrinkDatabaseUseCase: ShrinkDatabaseUseCase,
    private val settingsGateway: DownloadCacheSettingsGateway,
    private val platform: DownloadCachePlatform,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DownloadCacheConfigUiState(
            settings = settingsGateway.currentSettings,
            maxDownloadConcurrency = platform.maxDownloadConcurrency,
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        loadCacheSizes()
    }

    fun onIntent(intent: DownloadCacheConfigIntent) {
        when (intent) {
            is DownloadCacheConfigIntent.SetThreadCount ->
                update { it.copy(threadCount = intent.value) }
            is DownloadCacheConfigIntent.SetCacheBookThreadCount -> {
                val value = intent.value.coerceIn(1, platform.maxDownloadConcurrency)
                update { it.copy(cacheBookThreadCount = value) }
            }
            is DownloadCacheConfigIntent.SetPreDownloadNum ->
                update { it.copy(preDownloadNum = intent.value) }
            is DownloadCacheConfigIntent.SetBitmapCacheSize -> {
                viewModelScope.launch {
                    settingsGateway.update { it.copy(bitmapCacheSize = intent.value) }
                    platform.resizeImageCache()
                }
            }
            is DownloadCacheConfigIntent.SetImageRetainNum ->
                update { it.copy(imageRetainNum = intent.value) }
            is DownloadCacheConfigIntent.SetUserAgent ->
                update { it.copy(userAgent = intent.value) }
            is DownloadCacheConfigIntent.SetCronetEnabled ->
                update { it.copy(cronetEnabled = intent.value) }
            is DownloadCacheConfigIntent.ShowDialog ->
                _uiState.update { it.copy(dialog = intent.dialog) }
            DownloadCacheConfigIntent.DismissDialog ->
                _uiState.update { it.copy(dialog = null) }
            DownloadCacheConfigIntent.ConfirmDialog -> confirmDialog()
        }
    }

    private fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings) {
        viewModelScope.launch { settingsGateway.update(transform) }
    }

    private fun loadCacheSizes() {
        viewModelScope.launch {
            val coverBytes = platform.httpCacheSizeBytes(HttpCacheKind.COVER)
            val mangaBytes = platform.httpCacheSizeBytes(HttpCacheKind.MANGA)
            _uiState.update {
                it.copy(
                    coverCacheSizeMb = coverBytes / (1024.0 * 1024.0),
                    mangaCacheSizeMb = mangaBytes / (1024.0 * 1024.0),
                )
            }
        }
    }

    private fun confirmDialog() {
        val dialog = _uiState.value.dialog ?: return
        _uiState.update { it.copy(dialog = null) }
        viewModelScope.launch {
            when (dialog) {
                DownloadCacheConfigDialog.ClearCoverCache -> {
                    platform.clearHttpCache(HttpCacheKind.COVER)
                    _uiState.update { it.copy(coverCacheSizeMb = 0.0) }
                }
                DownloadCacheConfigDialog.ClearMangaCache -> {
                    platform.clearHttpCache(HttpCacheKind.MANGA)
                    _uiState.update { it.copy(mangaCacheSizeMb = 0.0) }
                }
                DownloadCacheConfigDialog.ClearBookCache -> {
                    clearBookCacheUseCase.executeAll()
                    platform.clearCacheDirectories()
                }
                DownloadCacheConfigDialog.ShrinkDatabase -> shrinkDatabaseUseCase.execute()
            }
        }
    }
}
