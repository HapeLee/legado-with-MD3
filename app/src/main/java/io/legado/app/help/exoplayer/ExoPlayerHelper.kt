package io.legado.app.help.exoplayer

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import com.google.gson.reflect.TypeToken
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalCache
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.isJsonArray
import okhttp3.CacheControl
import splitties.init.appCtx
import java.io.File
import java.util.concurrent.TimeUnit


@Suppress("unused")
@SuppressLint("UnsafeOptInUsageError")
object ExoPlayerHelper {

    private const val SPLIT_TAG = "\uD83D\uDEA7"

    private val mapType by lazy {
        object : TypeToken<Map<String, String>>() {}.type
    }

    fun createMediaItem(url: String, headers: Map<String, String>): MediaItem {
        val formatUrl = url + SPLIT_TAG + GSON.toJson(headers, mapType)
        val mediaItemBuilder = MediaItem.Builder().setUri(formatUrl)
        return mediaItemBuilder.build()
    }

    fun createMediaRequest(url: String, headers: Map<String, String>): MediaRequest {
        return MediaRequest(url, headers.toMap())
    }

    fun createHttpExoPlayer(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context).setLoadControl(
            DefaultLoadControl.Builder().setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS / 10,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 10
            ).build()
        ).setMediaSourceFactory(
            DefaultMediaSourceFactory(
                context,
                DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
            ).setDataSourceFactory(resolvingDataSource)
                .setLiveTargetOffsetMs(5000)
        ).build()
    }


    private val resolvingDataSource: ResolvingDataSource.Factory by lazy {
        ResolvingDataSource.Factory(audioReadDataSourceFactory) {
            var res = it

            if (it.uri.toString().contains(SPLIT_TAG)) {
                val urls = it.uri.toString().split(SPLIT_TAG)
                val url = urls[0]
                res = res.withUri(Uri.parse(url))
                try {
                    val headers: Map<String, String> = GSON.fromJson(urls[1], mapType)
                    okhttpDataFactory.setDefaultRequestProperties(headers)
                } catch (_: Exception) {
                }
            }

            res

        }
    }


    /**
     * 支持缓存的DataSource.Factory
     */
    val cacheDataSourceFactory by lazy {
        //使用自定义的CacheDataSource以支持设置UA
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(okhttpDataFactory)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(cache)
                    .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)
            )
    }

    private val audioCacheDataSourceFactory by lazy {
        CacheDataSource.Factory()
            .setCache(audioCache)
            .setUpstreamDataSourceFactory(okhttpDataFactory)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(audioCache)
                    .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)
            )
    }

    private val audioReadDataSourceFactory by lazy {
        CacheDataSource.Factory()
            .setCache(audioCache)
            .setUpstreamDataSourceFactory(okhttpDataFactory)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setCacheWriteDataSinkFactory(null)
    }

    /**
     * Okhttp DataSource.Factory
     */
    private val okhttpDataFactory by lazy {
        val client = okHttpClient.newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
        OkHttpDataSource.Factory(client)
            .setCacheControl(CacheControl.Builder().maxAge(1, TimeUnit.DAYS).build())
    }

    /**
     * Exoplayer 内置的缓存
     */
    private val cache: Cache by lazy {
        val databaseProvider = StandaloneDatabaseProvider(appCtx)
        return@lazy SimpleCache(
            //Exoplayer的缓存路径
            File(appCtx.externalCache, "exoplayer"),
            //100M的缓存
            LeastRecentlyUsedCacheEvictor((100 * 1024 * 1024).toLong()),
            //记录缓存的数据库
            databaseProvider
        )
    }

    private val audioCache: Cache by lazy {
        val databaseProvider = StandaloneDatabaseProvider(appCtx)
        return@lazy SimpleCache(
            File(appCtx.externalCache, "audio_exoplayer"),
            LeastRecentlyUsedCacheEvictor(AUDIO_OFFLINE_CACHE_MAX_BYTES),
            databaseProvider
        )
    }

    private val audioCompleteMarkerDir: File by lazy {
        File(appCtx.externalCache, "audio_exoplayer_complete").apply { mkdirs() }
    }

    /**
     * 通过kotlin扩展函数+反射实现CacheDataSource.Factory设置默认请求头
     * 需要添加混淆规则 -keepclassmembers class com.google.android.exoplayer2.upstream.cache.CacheDataSource$Factory{upstreamDataSourceFactory;}
     * @param headers
     * @return
     */
//    private fun CacheDataSource.Factory.setDefaultRequestProperties(headers: Map<String, String> = mapOf()): CacheDataSource.Factory {
//        val declaredField = this.javaClass.getDeclaredField("upstreamDataSourceFactory")
//        declaredField.isAccessible = true
//        val df = declaredField[this] as DataSource.Factory
//        if (df is OkHttpDataSource.Factory) {
//            df.setDefaultRequestProperties(headers)
//        }
//        return this
//    }


    fun getMediaSource(context: Context, url: String): MediaSource? {
        val uris = GSON.fromJsonArray<String>(url).getOrNull() ?: return null
        if (uris.isEmpty()) return null
        val mediaSourceBuilder = ConcatenatingMediaSource2.Builder()
        for (uri in uris) {
            mediaSourceBuilder.add(
                ProgressiveMediaSource.Factory(audioReadDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri)), 3000
            )
        }
        return mediaSourceBuilder.build()
    }

    fun cacheMedia(
        request: MediaRequest,
        progress: ((requestLength: Long, bytesCached: Long, newBytesCached: Long) -> Unit)? = null,
        shouldCancel: (() -> Boolean)? = null
    ): Long {
        var totalCached = 0L
        val urls = getMediaUrls(request.url)
        require(urls.isNotEmpty()) { "media url is empty" }
        urls.forEach { url ->
            if (shouldCancel?.invoke() == true) {
                throw kotlinx.coroutines.CancellationException("audio cache cancelled")
            }
            val dataSpec = DataSpec.Builder()
                .setUri(url)
                .setKey(url)
                .setHttpRequestHeaders(request.headers)
                .build()
            var cached = 0L
            CacheWriter(
                audioCacheDataSourceFactory.createDataSourceForDownloading(),
                dataSpec,
                null
            ) { requestLength, bytesCached, newBytesCached ->
                if (shouldCancel?.invoke() == true) {
                    throw kotlinx.coroutines.CancellationException("audio cache cancelled")
                }
                cached = bytesCached
                progress?.invoke(requestLength, bytesCached, newBytesCached)
            }.cache()
            markMediaUrlComplete(url)
            totalCached += cached
        }
        return totalCached
    }

    fun isMediaCached(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val urls = getMediaUrls(url)
        if (urls.isEmpty()) return false
        return urls.all { isMediaUrlCached(it) }
    }

    fun removeMediaCache(url: String?) {
        if (url.isNullOrBlank()) return
        getMediaUrls(url).forEach {
            audioCache.removeResource(it)
            mediaCompleteMarker(it).delete()
        }
    }

    fun copyMediaCache(url: String?, targetDir: File): Int {
        if (url.isNullOrBlank()) return 0
        if (!targetDir.exists()) targetDir.mkdirs()
        var count = 0
        getMediaUrls(url).forEachIndexed { urlIndex, mediaUrl ->
            for (span in audioCache.getCachedSpans(mediaUrl)) {
                if (!span.isCached) continue
                val source = span.file ?: continue
                if (!source.exists() || !source.isFile) continue
                val name = "${urlIndex}_${span.position}_${span.length}_${source.name}"
                source.copyTo(File(targetDir, name), overwrite = true)
                count++
            }
        }
        return count
    }

    fun importMediaCache(url: String?, sourceDir: File): Int {
        if (url.isNullOrBlank() || !sourceDir.exists() || !sourceDir.isDirectory) return 0
        val urls = getMediaUrls(url)
        if (urls.isEmpty()) return 0
        var count = 0
        sourceDir.listFiles()
            ?.filter { it.isFile }
            ?.forEach { source ->
                val prefix = source.name.substringBefore('_', "")
                val urlIndex = prefix.toIntOrNull() ?: return@forEach
                val targetUrl = urls.getOrNull(urlIndex) ?: return@forEach
                val remain = source.name.substringAfter('_', "")
                val position = remain.substringBefore('_', "").toLongOrNull() ?: return@forEach
                val lengthPart = remain.substringAfter("${position}_", "")
                val expectedLength = lengthPart.substringBefore('_', "").toLongOrNull()
                    ?: source.length()
                val cacheFile = audioCache.startFile(targetUrl, position, expectedLength)
                cacheFile.parentFile?.mkdirs()
                source.inputStream().use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                audioCache.commitFile(cacheFile, source.length())
                markMediaUrlComplete(targetUrl)
                count++
            }
        return count
    }

    private fun isMediaUrlCached(url: String): Boolean {
        val contentLength = ContentMetadata.getContentLength(audioCache.getContentMetadata(url))
        return if (contentLength > 0) {
            audioCache.isCached(url, 0, contentLength)
        } else {
            mediaCompleteMarker(url).isFile &&
                audioCache.getCachedBytes(url, 0, Long.MAX_VALUE) > 0
        }
    }

    private fun markMediaUrlComplete(url: String) {
        runCatching {
            mediaCompleteMarker(url).writeText(System.currentTimeMillis().toString())
        }
    }

    private fun mediaCompleteMarker(url: String): File {
        return File(audioCompleteMarkerDir, MD5Utils.md5Encode(url))
    }

    private fun getMediaUrls(url: String): List<String> {
        if (url.isJsonArray()) {
            GSON.fromJsonArray<String>(url).getOrNull()?.filter { it.isNotBlank() }?.let {
                return it
            }
        }
        return listOf(url)
    }

    data class MediaRequest(
        val url: String,
        val headers: Map<String, String> = emptyMap()
    )

    private const val AUDIO_OFFLINE_CACHE_MAX_BYTES = 4L * 1024 * 1024 * 1024
}
