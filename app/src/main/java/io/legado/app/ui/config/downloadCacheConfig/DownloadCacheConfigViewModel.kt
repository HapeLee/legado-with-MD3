package io.legado.app.ui.config.downloadCacheConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsUpdate
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.domain.usecase.ShrinkDatabaseUseCase
import io.legado.app.help.http.HttpCacheType
import io.legado.app.help.http.clearHttpCache
import io.legado.app.help.http.getHttpCacheSize
import io.legado.app.model.CacheBook
import io.legado.app.model.ImageProvider
import io.legado.app.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class DownloadCacheConfigViewModel(
    private val clearBookCacheUseCase: ClearBookCacheUseCase,
    private val shrinkDatabaseUseCase: ShrinkDatabaseUseCase,
    private val settingsGateway: DownloadCacheSettingsGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadCacheConfigUiState())
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
            is DownloadCacheConfigIntent.SetThreadCount -> update(
                DownloadCacheSettingsUpdate.ThreadCount(intent.value)
            )
            is DownloadCacheConfigIntent.SetCacheBookThreadCount -> update(
                DownloadCacheSettingsUpdate.CacheBookThreadCount(
                    intent.value.coerceIn(1, CacheBook.maxDownloadConcurrency)
                )
            )
            is DownloadCacheConfigIntent.SetPreDownloadNum -> update(
                DownloadCacheSettingsUpdate.PreDownloadNum(intent.value)
            )
            is DownloadCacheConfigIntent.SetBitmapCacheSize -> {
                viewModelScope.launch {
                    settingsGateway.update(
                        DownloadCacheSettingsUpdate.BitmapCacheSize(intent.value)
                    )
                    ImageProvider.bitmapLruCache.resize(ImageProvider.cacheSize)
                }
            }
            is DownloadCacheConfigIntent.SetImageRetainNum -> update(
                DownloadCacheSettingsUpdate.ImageRetainNum(intent.value)
            )
            is DownloadCacheConfigIntent.SetUserAgent -> update(
                DownloadCacheSettingsUpdate.UserAgent(intent.value)
            )
            is DownloadCacheConfigIntent.SetCronetEnabled -> update(
                DownloadCacheSettingsUpdate.CronetEnabled(intent.value)
            )
            is DownloadCacheConfigIntent.ShowDialog ->
                _uiState.update { it.copy(dialog = intent.dialog) }
            DownloadCacheConfigIntent.DismissDialog ->
                _uiState.update { it.copy(dialog = null) }
            DownloadCacheConfigIntent.ConfirmDialog -> confirmDialog()
        }
    }

    private fun update(update: DownloadCacheSettingsUpdate) {
        viewModelScope.launch { settingsGateway.update(update) }
    }

    private fun loadCacheSizes() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    coverCacheSizeMb = getHttpCacheSize(HttpCacheType.COVER) / (1024.0 * 1024.0),
                    mangaCacheSizeMb = getHttpCacheSize(HttpCacheType.MANGA) / (1024.0 * 1024.0),
                )
            }
        }
    }

    private fun confirmDialog() {
        val dialog = _uiState.value.dialog ?: return
        _uiState.update { it.copy(dialog = null) }
        viewModelScope.launch(Dispatchers.IO) {
            when (dialog) {
                DownloadCacheConfigDialog.ClearCoverCache -> {
                    clearHttpCache(HttpCacheType.COVER)
                    _uiState.update { it.copy(coverCacheSizeMb = 0.0) }
                }
                DownloadCacheConfigDialog.ClearMangaCache -> {
                    clearHttpCache(HttpCacheType.MANGA)
                    _uiState.update { it.copy(mangaCacheSizeMb = 0.0) }
                }
                DownloadCacheConfigDialog.ClearBookCache -> {
                    clearBookCacheUseCase.executeAll()
                    FileUtils.delete(appCtx.cacheDir.absolutePath)
                    appCtx.externalCacheDir?.deleteRecursively()
                }
                DownloadCacheConfigDialog.ShrinkDatabase -> shrinkDatabaseUseCase.execute()
            }
        }
    }
}
