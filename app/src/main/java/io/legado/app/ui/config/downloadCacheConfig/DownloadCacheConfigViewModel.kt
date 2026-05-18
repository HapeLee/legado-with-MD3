package io.legado.app.ui.config.downloadCacheConfig

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.domain.usecase.ShrinkDatabaseUseCase
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.HttpCacheType
import io.legado.app.help.http.clearHttpCache
import io.legado.app.help.http.getHttpCacheSize
import io.legado.app.model.ImageProvider
import io.legado.app.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadCacheConfigViewModel(
    private val clearBookCacheUseCase: ClearBookCacheUseCase,
    private val shrinkDatabaseUseCase: ShrinkDatabaseUseCase
) : ViewModel() {

    var coverCacheSize by mutableDoubleStateOf(0.0)
    var mangaCacheSize by mutableDoubleStateOf(0.0)

    init {
        loadCacheSizes()
    }

    fun loadCacheSizes() {
        viewModelScope.launch(Dispatchers.IO) {
            coverCacheSize = getHttpCacheSize(HttpCacheType.COVER) / (1024.0 * 1024.0)
            mangaCacheSize = getHttpCacheSize(HttpCacheType.MANGA) / (1024.0 * 1024.0)
        }
    }

    fun clearCoverCache() {
        viewModelScope.launch(Dispatchers.IO) {
            clearHttpCache(HttpCacheType.COVER)
            coverCacheSize = 0.0
        }
    }

    fun clearMangaCache() {
        viewModelScope.launch(Dispatchers.IO) {
            clearHttpCache(HttpCacheType.MANGA)
            mangaCacheSize = 0.0
        }
    }

    fun clearBookCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            clearBookCacheUseCase.executeAll()
            FileUtils.delete(context.cacheDir.absolutePath)
            context.externalCacheDir?.deleteRecursively()
        }
    }

    fun shrinkDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            shrinkDatabaseUseCase.execute()
        }
    }

    fun updateBitmapCacheSize(size: Int) {
        AppConfig.bitmapCacheSize = size
        ImageProvider.bitmapLruCache.resize(ImageProvider.cacheSize)
    }

    fun saveUserAgent(input: String) {
        DownloadCacheConfig.userAgent = input
        AppConfig.userAgent = DownloadCacheConfig.userAgent
    }
}
