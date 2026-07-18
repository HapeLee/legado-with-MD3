package io.legado.app.domain.model.settings

data class DownloadCacheSettings(
    val bitmapCacheSize: Int = 50,
    val imageRetainNum: Int = 0,
    val preDownloadNum: Int = 10,
    val threadCount: Int = 16,
    val cacheBookThreadCount: Int = 16,
    val userAgent: String = "",
    val cronetEnabled: Boolean = false,
)
