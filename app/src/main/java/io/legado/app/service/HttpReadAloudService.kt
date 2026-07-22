package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.script.ScriptException
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.HttpTTS
import io.legado.app.domain.gateway.CloudTtsEngineGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackCursor
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackQueue
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechEngineRoute
import io.legado.app.domain.model.readaloud.SpeechRoleType
import io.legado.app.domain.model.readaloud.SpeechVoiceRouter
import io.legado.app.domain.model.readaloud.SystemTtsVoiceConfig
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.exoplayer.InputStreamDataSource
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.readaloud.playback.CharacterPerformanceInstructionBuilder
import io.legado.app.help.readaloud.playback.CloudTtsAudioSynthesizer
import io.legado.app.help.readaloud.playback.CloudTtsEmotionMapper
import io.legado.app.help.readaloud.playback.CloudTtsRoleInstructionMapper
import io.legado.app.help.readaloud.playback.SystemTtsFileSynthesizer
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Response
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.get
import org.mozilla.javascript.WrappedException
import splitties.init.appCtx
import java.io.File
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * 在线朗读
 */
@SuppressLint("UnsafeOptInUsageError")
class HttpReadAloudService : BaseReadAloudService(),
    Player.Listener {
    override val useSpeechPlaybackQueue: Boolean = true

    private val readAloudSettingsGateway = GlobalContext.get().get<ReadAloudSettingsGateway>()
    private val readSettingsGateway = GlobalContext.get().get<ReadSettingsGateway>()
    private val otherSettingsGateway = GlobalContext.get().get<OtherSettingsGateway>()
    private var readAloudSettings: ReadAloudSettings = readAloudSettingsGateway.currentSettings
    private var readSettings: ReadSettings = readSettingsGateway.currentSettings
    private var otherSettings: OtherSettings = otherSettingsGateway.currentSettings

    private val speechRatePlay: Int
        get() = if (readAloudSettings.ttsFollowSys) 5 else readAloudSettings.ttsSpeechRate

    private data class PreDownloadChapter(
        val textChapter: TextChapter,
        val queue: ReadAloudPlaybackQueue,
        val contentList: List<String>,
    )

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build()
    }

    // 改为外部存储
    private val ttsFolderPath: String by lazy {
        val baseDir = externalCacheDir ?: cacheDir
        baseDir.absolutePath + File.separator + "httpTTS" + File.separator
    }

    private val cache by lazy {
        val baseDir = externalCacheDir ?: cacheDir
        SimpleCache(
            File(baseDir, "httpTTS_cache"),
            LeastRecentlyUsedCacheEvictor(128 * 1024 * 1024),
            StandaloneDatabaseProvider(appCtx)
        )
    }
    private val cacheDataSinkFactory by lazy {
        CacheDataSink.Factory()
            .setCache(cache)
    }
    private val loadErrorHandlingPolicy by lazy {
        CustomLoadErrorHandlingPolicy()
    }
    private var speechRate: Int = speechRatePlay + 5
    private var downloadTask: Coroutine<*>? = null
    private var preDownloadJob: Job? = null
    private var playIndexJob: Job? = null
    private var downloadErrorNo: Int = 0
    private var playErrorNo = 0
    private val downloadTaskActiveLock = Mutex()
    private val systemTtsFileSynthesizer by lazy { SystemTtsFileSynthesizer(this) }
    private val cloudTtsAudioSynthesizer by lazy {
        CloudTtsAudioSynthesizer(get(CloudTtsEngineGateway::class.java))
    }

    override fun onCreate() {
        super.onCreate()
        exoPlayer.addListener(this)
        lifecycleScope.launch {
            readAloudSettingsGateway.settings.collectLatest { readAloudSettings = it }
        }
        lifecycleScope.launch {
            readSettingsGateway.settings.collectLatest { readSettings = it }
        }
        lifecycleScope.launch {
            otherSettingsGateway.settings.collectLatest { otherSettings = it }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadTask?.cancel()
        preDownloadJob?.cancel()
        exoPlayer.release()
        cache.release()
        Coroutine.async {
            systemTtsFileSynthesizer.close()
            removeCacheFile()
        }
    }

    override fun play() {
        pageChanged = false
        exoPlayer.stop()
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            ReadBook.readAloud()
        } else {
            super.play()
            if (readAloudSettings.streamReadAloudAudio && !hasFileSynthesisCue()) {
                downloadAndPlayAudiosStream()
            } else {
                downloadAndPlayAudios()
            }
        }
    }

    override fun playStop() {
        exoPlayer.stop()
        playIndexJob?.cancel()
        preDownloadJob?.cancel()
    }

    private fun updateNextPos() {
        if (!playbackQueue.isEmpty) {
            val current = playbackCursor ?: ReadAloudPlaybackCursor(nowSpeak, paragraphStartPos)
            playbackQueue.next(current)?.let(::moveToPlaybackCursor) ?: nextChapter()
            return
        }
        readAloudNumber += contentList[nowSpeak].length + 1 - paragraphStartPos
        paragraphStartPos = 0
        if (nowSpeak < contentList.lastIndex) {
            nowSpeak++
        } else {
            nextChapter()
        }
    }

    private fun downloadAndPlayAudios() {
        exoPlayer.clearMediaItems()
        downloadTask?.cancel()
        preDownloadJob?.cancel()
        downloadTask = execute {
            downloadTaskActiveLock.withLock {
                ensureActive()
                val httpTts = ReadAloud.httpTTS ?: throw NoStackTraceException("tts is null")

                contentList.forEachIndexed { index, content ->
                    ensureActive()
                    if (index < nowSpeak) return@forEachIndexed
                    var text = content
                    if (paragraphStartPos > 0 && index == nowSpeak) {
                        text = text.substring(paragraphStartPos)
                    }
                    val routedVoice = voiceForCue(playbackQueue, index, httpTts)
                    val cue = playbackQueue.cues.getOrNull(index)
                    val cueEmotion = cue?.emotion.orEmpty()
                    val characterPerformance = cue?.characterPerformance
                    val cueRoleType = cue?.roleType ?: SpeechRoleType.Unknown
                    val sourceKey = sourceKeyForCue(routedVoice, cue, httpTts)
                    val fileName = md5SpeakFileName(text, sourceKey = sourceKey)
                    val speakText = text.replace(AppPattern.notReadAloudRegex, "")
                    if (speakText.isEmpty()) {
                        AppLog.put("阅读段落内容为空，使用无声音频代替。\n朗读文本：$text")
                        createSilentSound(fileName)
                    } else if (!hasSpeakFile(fileName)) {
                        runCatching {
                            when (routedVoice.engineType) {
                                ReadAloudVoice.ENGINE_SYSTEM -> {
                                    val output = getSpeakFileAsMd5(fileName)
                                    val config = runCatching {
                                        GSON.fromJson(
                                            routedVoice.traitsJson,
                                            SystemTtsVoiceConfig::class.java,
                                        )
                                    }.getOrNull() ?: SystemTtsVoiceConfig()
                                    val globalRate = if (readAloudSettings.ttsFollowSys) {
                                        1f
                                    } else {
                                        (readAloudSettings.ttsSpeechRate + 5) / 10f
                                    }
                                    if (!systemTtsFileSynthesizer.synthesize(
                                            routedVoice.engineId,
                                            routedVoice.speakerId,
                                            speakText,
                                            output,
                                            config.speechRate ?: globalRate,
                                            config.pitch ?: 1f,
                                        )
                                    ) {
                                        createSilentSound(fileName)
                                    }
                                }

                                ReadAloudVoice.ENGINE_CLOUD -> {
                                    val output = getSpeakFileAsMd5(fileName)
                                    if (!cloudTtsAudioSynthesizer.synthesize(
                                            routedVoice,
                                            speakText,
                                            output,
                                            styleOverride = cueEmotion,
                                            characterPerformance = characterPerformance,
                                            roleType = cueRoleType,
                                        )
                                    ) {
                                        createSilentSound(fileName)
                                    }
                                }

                                else -> {
                                    val itemHttpTts = routedVoice.engineId.toLongOrNull()
                                        ?.let(appDb.httpTTSDao::get) ?: httpTts
                                    val inputStream = getSpeakStream(itemHttpTts, speakText)
                                    if (inputStream != null) {
                                        createSpeakFile(fileName, inputStream)
                                    } else {
                                        createSilentSound(fileName)
                                    }
                                }
                            }
                        }.onFailure {
                            when (it) {
                                is CancellationException -> Unit
                                else -> pauseReadAloud()
                            }
                            return@execute
                        }
                    }
                    if (speakText.isNotEmpty() && hasSpeakFile(fileName)) {
                        writeTextIndexEntry(fileName, speakText)
                    }
                    val file = getSpeakFileAsMd5(fileName)
                    val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
                    launch(Main) {
                        if (readAloudSettings.ttsParagraphInterval > 0) {
                            if (index == nowSpeak && exoPlayer.mediaItemCount == 0) {
                                exoPlayer.setMediaItem(mediaItem)
                                if (!pause) {
                                    exoPlayer.prepare()
                                }
                                // 当前章开始播放后，立即异步启动后续章节预合成
                                launchPreDownload(httpTts)
                            }
                        } else {
                            if (exoPlayer.mediaItemCount == 0) {
                                exoPlayer.setMediaItem(mediaItem)
                                if (!pause) {
                                    exoPlayer.prepare()
                                }
                                // 当前章开始播放后，立即异步启动后续章节预合成
                                launchPreDownload(httpTts)
                            } else {
                                exoPlayer.addMediaItem(mediaItem)
                            }
                        }
                    }
                }
            }
        }.onError {
            AppLog.put("朗读下载出错\n${it.localizedMessage}", it, true)
        }
    }

    /**
     * 异步启动后续章节的预合成，与当前章节播放并行。
     * 不持有 downloadTaskActiveLock，不阻塞当前章节的合成和播放。
     */
    private fun launchPreDownload(httpTts: HttpTTS) {
        preDownloadJob?.cancel()
        preDownloadJob = lifecycleScope.launch {
            preDownloadAudios(httpTts)
        }
    }

    private suspend fun getPreDownloadChapter(
        book: Book,
        chapter: BookChapter,
    ): PreDownloadChapter? {
        val content = BookHelp.getContent(book, chapter) ?: return null
        val contentProcessor = ContentProcessor.get(book.name, book.origin)
        val displayTitle = chapter.getDisplayTitle(
            contentProcessor.getTitleReplaceRules(),
            book.getUseReplaceRule(otherSettings.replaceEnableDefault),
            chineseConverterType = readSettings.chineseConverterType,
        )
        val processedContent = contentProcessor.getContent(
            book,
            chapter,
            content,
            includeTitle = false,
        )
        val textChapter = ChapterProvider.getTextChapterAsync(
            CoroutineScope(currentCoroutineContext()),
            book,
            chapter,
            displayTitle,
            processedContent,
            ReadBook.simulatedChapterSize,
        )
        for (ignored in textChapter.layoutChannel) {
            currentCoroutineContext().ensureActive()
        }
        val plan = buildSpeechPlan(
            bookUrl = book.bookUrl,
            chapterIndex = chapter.index,
            textChapter = textChapter,
        )
        val queue = runCatching { ReadAloudPlaybackQueue.from(plan) }
            .getOrDefault(ReadAloudPlaybackQueue.Empty)
        val contentList = if (!queue.isEmpty) {
            queue.cues.map { it.text }
        } else {
            textChapter.getNeedReadAloud(0, readAloudSettings.readAloudByPage, 0)
                .split("\n")
                .filter { it.isNotEmpty() }
        }
        return PreDownloadChapter(textChapter, queue, contentList)
    }

    private suspend fun preDownloadAudios(httpTts: HttpTTS) {
        val book = ReadBook.book ?: return
        val currentIdx = ReadBook.durChapterIndex
        val limit = readAloudSettings.audioPreDownloadNum
        val concurrency = readAloudSettings.ttsPreSynthesisConcurrency.coerceIn(1, 8)
        var consecutiveFailures = 0

        try {
            for (i in 1..limit) {
                currentCoroutineContext().ensureActive()
                if (consecutiveFailures >= 3) {
                    AppLog.put("TTS预合成连续失败${consecutiveFailures}章，已停止预合成")
                    break
                }
                val targetIndex = currentIdx + i
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, targetIndex) ?: break
                val prepared = getPreDownloadChapter(book, chapter) ?: continue
                val chapterFailed = synthesizeChapterCues(prepared, httpTts, concurrency)
                consecutiveFailures = if (chapterFailed) consecutiveFailures + 1 else 0
            }
        } catch (e: Exception) {
            AppLog.put("听书预下载异常: ${e.localizedMessage}", e)
        }
    }

    /**
     * 并行合成一个章节的所有 cue，通过 Semaphore 控制并发。
     * 返回 true 表示该章节合成失败（超过半数 cue 失败）。
     */
    private suspend fun synthesizeChapterCues(
        prepared: PreDownloadChapter,
        httpTts: HttpTTS,
        concurrency: Int,
    ): Boolean = coroutineScope {
        val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency)
        var failedCount = 0
        val totalCues = prepared.contentList.size

        prepared.contentList.mapIndexed { index, content ->
            async {
                semaphore.acquire()
                try {
                    val routedVoice = voiceForCue(prepared.queue, index, httpTts)
                    if (routedVoice.engineType == ReadAloudVoice.ENGINE_SYSTEM) {
                        return@async
                    }
                    val cue = prepared.queue.cues.getOrNull(index)
                    val sourceKey = sourceKeyForCue(routedVoice, cue, httpTts)
                    val fileName = md5SpeakFileName(
                        content, prepared.textChapter, sourceKey = sourceKey,
                    )
                    if (hasSpeakFile(fileName)) return@async

                    val success = synthesizeSingleCueWithRetry(
                        routedVoice, cue, content, prepared.textChapter, httpTts,
                    )
                    if (!success) {
                        createSilentSound(fileName)
                        failedCount++
                    }
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll()

        failedCount > totalCues / 2
    }

    /**
     * 单个 cue 合成 + 重试 1 次（500ms 延迟）
     */
    private suspend fun synthesizeSingleCueWithRetry(
        routedVoice: ReadAloudVoice,
        cue: io.legado.app.domain.model.readaloud.ReadAloudPlaybackCue?,
        content: String,
        textChapter: TextChapter?,
        httpTts: HttpTTS,
    ): Boolean {
        if (synthesizeSingleCue(routedVoice, cue, content, textChapter, httpTts)) {
            return true
        }
        delay(500)
        return synthesizeSingleCue(routedVoice, cue, content, textChapter, httpTts)
    }

    /**
     * 单个 cue 合成核心方法
     */
    private suspend fun synthesizeSingleCue(
        routedVoice: ReadAloudVoice,
        cue: io.legado.app.domain.model.readaloud.ReadAloudPlaybackCue?,
        content: String,
        textChapter: TextChapter?,
        httpTts: HttpTTS,
    ): Boolean {
        val sourceKey = sourceKeyForCue(routedVoice, cue, httpTts)
        val fileName = md5SpeakFileName(content, textChapter, sourceKey = sourceKey)
        val speakText = content.replace(AppPattern.notReadAloudRegex, "")
        if (speakText.isEmpty()) {
            createSilentSound(fileName)
            return true
        }
        val success = runCatching {
            when (routedVoice.engineType) {
                ReadAloudVoice.ENGINE_CLOUD -> {
                    val output = getSpeakFileAsMd5(fileName)
                    cloudTtsAudioSynthesizer.synthesize(
                        routedVoice, speakText, output,
                        styleOverride = cue?.emotion.orEmpty(),
                        characterPerformance = cue?.characterPerformance,
                        roleType = cue?.roleType ?: SpeechRoleType.Unknown,
                    )
                }

                ReadAloudVoice.ENGINE_HTTP -> {
                    val itemHttpTts = routedVoice.engineId.toLongOrNull()
                        ?.let(appDb.httpTTSDao::get) ?: httpTts
                    val inputStream = getSpeakStream(itemHttpTts, speakText)
                    if (inputStream != null) {
                        createSpeakFile(fileName, inputStream)
                        true
                    } else {
                        false
                    }
                }

                else -> false
            }
        }.getOrElse {
            when (it) {
                is CancellationException -> throw it
                else -> {
                    AppLog.put("TTS预合成cue失败: ${it.localizedMessage}")
                    false
                }
            }
        }
        if (success && speakText.isNotEmpty()) {
            writeTextIndexEntry(fileName, speakText)
        }
        return success
    }

    /**
     * 将合成的文件名和对应文字写入索引，供缓存管理界面显示。
     */
    private fun writeTextIndexEntry(fileName: String, text: String) {
        try {
            val baseDir = externalCacheDir ?: cacheDir
            val indexFile = File(baseDir, "httpTTS/tts_cache_index.json")
            val index = mutableMapOf<String, String>()
            if (indexFile.exists()) {
                val json = indexFile.readText()
                val regex = Regex("\"([^\"]+)\":\"([^\"]*)\"")
                regex.findAll(json).forEach { match ->
                    index[match.groupValues[1]] = match.groupValues[2]
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                }
            }
            val shortText = if (text.length > 200) text.substring(0, 200) + "…" else text
            index[fileName] = shortText
            // 限制索引大小，最多保留 2000 条
            if (index.size > 2000) {
                val keys = index.keys.toList().takeLast(2000)
                val trimmed = linkedMapOf<String, String>()
                keys.forEach { trimmed[it] = index[it]!! }
                indexFile.writeText(buildIndexJson(trimmed))
            } else {
                indexFile.writeText(buildIndexJson(index))
            }
        } catch (_: Exception) {
        }
    }

    private fun buildIndexJson(index: Map<String, String>): String = buildString {
        append("{")
        index.entries.forEachIndexed { i, (key, value) ->
            if (i > 0) append(",")
            val escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
            append("\"$key\":\"$escaped\"")
        }
        append("}")
    }

    private fun downloadAndPlayAudiosStream() {
        exoPlayer.clearMediaItems()
        downloadTask?.cancel()
        preDownloadJob?.cancel()
        downloadTask = execute {
            downloadTaskActiveLock.withLock {
                ensureActive()
                val httpTts = ReadAloud.httpTTS ?: throw NoStackTraceException("tts is null")
                val downloaderChannel = Channel<Downloader>()
                launch {
                    for (downloader in downloaderChannel) {
                        downloader.download(null)
                    }
                }
                var preDownloadLaunched = false
                contentList.forEachIndexed { index, content ->
                    ensureActive()
                    if (index < nowSpeak) return@forEachIndexed
                    var text = content
                    if (paragraphStartPos > 0 && index == nowSpeak) {
                        text = text.substring(paragraphStartPos)
                    }
                    val speakText = text.replace(AppPattern.notReadAloudRegex, "")
                    if (speakText.isEmpty()) {
                        AppLog.put("阅读段落内容为空，使用无声音频代替。\n朗读文本：$speakText")
                    }
                    val itemHttpTts = httpTtsForCue(index, httpTts)
                    val fileName = md5SpeakFileName(text, httpTts = itemHttpTts)
                    val dataSourceFactory = createDataSourceFactory(itemHttpTts, speakText)
                    val downloader = createDownloader(dataSourceFactory, fileName)
                    downloaderChannel.send(downloader)
                    val mediaSource = createMediaSource(dataSourceFactory, fileName)
                    launch(Main) {
                        if (readAloudSettings.ttsParagraphInterval > 0) {
                            if (index == nowSpeak && exoPlayer.mediaItemCount == 0) {
                                exoPlayer.setMediaSource(mediaSource)
                                if (!pause) {
                                    exoPlayer.prepare()
                                }
                                if (!preDownloadLaunched) {
                                    preDownloadLaunched = true
                                    launchPreDownloadStream(httpTts, downloaderChannel)
                                }
                            }
                        } else {
                            if (exoPlayer.mediaItemCount == 0) {
                                exoPlayer.setMediaSource(mediaSource)
                                if (!pause) {
                                    exoPlayer.prepare()
                                }
                                if (!preDownloadLaunched) {
                                    preDownloadLaunched = true
                                    launchPreDownloadStream(httpTts, downloaderChannel)
                                }
                            } else {
                                exoPlayer.addMediaSource(mediaSource)
                            }
                        }
                    }
                }
            }
        }.onError {
            AppLog.put("朗读下载出错\n${it.localizedMessage}", it, true)
        }
    }

    private fun launchPreDownloadStream(httpTts: HttpTTS, downloaderChannel: Channel<Downloader>) {
        preDownloadJob?.cancel()
        preDownloadJob = lifecycleScope.launch {
            preDownloadAudiosStream(httpTts, downloaderChannel)
        }
    }

    private suspend fun preDownloadAudiosStream(
        httpTts: HttpTTS,
        downloaderChannel: Channel<Downloader>
    ) {
        val book = ReadBook.book ?: return
        val currentIdx = ReadBook.durChapterIndex
        val limit = readAloudSettings.audioPreDownloadNum
        val concurrency = readAloudSettings.ttsPreSynthesisConcurrency.coerceIn(1, 8)
        var consecutiveFailures = 0

        try {
            for (i in 1..limit) {
                currentCoroutineContext().ensureActive()
                if (consecutiveFailures >= 3) {
                    AppLog.put("TTS流式预合成连续失败${consecutiveFailures}章，已停止")
                    break
                }
                val targetIndex = currentIdx + i
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, targetIndex) ?: break
                val prepared = getPreDownloadChapter(book, chapter) ?: continue
                val chapterFailed = synthesizeChapterCuesStream(
                    prepared, httpTts, concurrency, downloaderChannel,
                )
                consecutiveFailures = if (chapterFailed) consecutiveFailures + 1 else 0
            }
        } catch (e: Exception) {
            AppLog.put("听书流式预下载异常: ${e.localizedMessage}", e)
        }
    }

    /**
     * 流式模式下并行预合成一个章节的 cue
     */
    private suspend fun synthesizeChapterCuesStream(
        prepared: PreDownloadChapter,
        httpTts: HttpTTS,
        concurrency: Int,
        downloaderChannel: Channel<Downloader>,
    ): Boolean = coroutineScope {
        val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency)
        var failedCount = 0
        val totalCues = prepared.contentList.size

        prepared.contentList.mapIndexed { index, content ->
            async {
                semaphore.acquire()
                try {
                    val routedVoice = voiceForCue(prepared.queue, index, httpTts)
                    if (routedVoice.engineType == ReadAloudVoice.ENGINE_SYSTEM) {
                        return@async
                    }
                    val cue = prepared.queue.cues.getOrNull(index)
                    if (routedVoice.engineType == ReadAloudVoice.ENGINE_CLOUD) {
                        val sourceKey = sourceKeyForCue(routedVoice, cue, httpTts)
                        val fileName = md5SpeakFileName(
                            content, prepared.textChapter, sourceKey = sourceKey,
                        )
                        if (hasSpeakFile(fileName)) return@async
                        val success = synthesizeSingleCueWithRetry(
                            routedVoice, cue, content, prepared.textChapter, httpTts,
                        )
                        if (!success) {
                            createSilentSound(fileName)
                            failedCount++
                        }
                    } else {
                        val speakText = content.replace(AppPattern.notReadAloudRegex, "")
                        val sourceKey = sourceKeyForCue(routedVoice, cue, httpTts)
                        val fileName = md5SpeakFileName(
                            content, prepared.textChapter, sourceKey = sourceKey,
                        )
                        val dataSourceFactory = createDataSourceFactory(httpTts, speakText)
                        val downloader = createDownloader(dataSourceFactory, fileName)
                        downloaderChannel.send(downloader)
                    }
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll()

        failedCount > totalCues / 2
    }

    private fun createDataSourceFactory(
        httpTts: HttpTTS,
        speakText: String
    ): CacheDataSource.Factory {
        val upstreamFactory = DataSource.Factory {
            InputStreamDataSource {
                if (speakText.isEmpty()) {
                    null
                } else {
                    kotlin.runCatching {
                        runBlocking(lifecycleScope.coroutineContext[Job]!!) {
                            getSpeakStream(httpTts, speakText)
                        }
                    }.onFailure {
                        when (it) {
                            is InterruptedException,
                            is CancellationException -> Unit

                            else -> pauseReadAloud()
                        }
                    }.getOrThrow()
                } ?: resources.openRawResource(R.raw.silent_sound)
            }
        }
        val factory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(cacheDataSinkFactory)
        return factory
    }

    private fun createDownloader(factory: CacheDataSource.Factory, fileName: String): Downloader {
        val uri = fileName.toUri()
        val request = DownloadRequest.Builder(fileName, uri).build()
        return DefaultDownloaderFactory(factory, okHttpClient.dispatcher.executorService)
            .createDownloader(request)
    }

    private fun createMediaSource(factory: DataSource.Factory, fileName: String): MediaSource {
        return DefaultMediaSourceFactory(this)
            .setDataSourceFactory(factory)
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            .createMediaSource(MediaItem.fromUri(fileName))
    }

    private suspend fun getSpeakStream(
        httpTts: HttpTTS,
        speakText: String
    ): InputStream? {
        while (true) {
            try {
                val analyzeUrl = AnalyzeUrl(
                    httpTts.url,
                    speakText = speakText,
                    speakSpeed = speechRate,
                    source = httpTts,
                    readTimeout = 300 * 1000L,
                    coroutineContext = currentCoroutineContext()
                )
                var response = analyzeUrl.getResponseAwait()
                currentCoroutineContext().ensureActive()
                val checkJs = httpTts.loginCheckJs
                if (checkJs?.isNotBlank() == true) {
                    response = analyzeUrl.evalJS(checkJs, response) as Response
                }
                response.headers["Content-Type"]?.let { contentType ->
                    val contentType = contentType.substringBefore(";")
                    val ct = httpTts.contentType
                    if (contentType == "application/json" || contentType.startsWith("text/")) {
                        throw NoStackTraceException(response.body.string())
                    } else if (ct?.isNotBlank() == true) {
                        if (!contentType.matches(ct.toRegex())) {
                            throw NoStackTraceException(
                                "TTS服务器返回错误：" + response.body.string()
                            )
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                response.body.byteStream().let { stream ->
                    downloadErrorNo = 0
                    return stream
                }
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> throw e
                    is ScriptException, is WrappedException -> {
                        AppLog.put("js错误\n${e.localizedMessage}", e, true)
                        e.printOnDebug()
                        throw e
                    }

                    is SocketTimeoutException, is ConnectException -> {
                        downloadErrorNo++
                        if (downloadErrorNo > 5) {
                            val msg = "tts超时或连接错误超过5次\n${e.localizedMessage}"
                            AppLog.put(msg, e, true)
                            throw e
                        }
                    }

                    else -> {
                        downloadErrorNo++
                        val msg = "tts下载错误\n${e.localizedMessage}"
                        AppLog.put(msg, e)
                        e.printOnDebug()
                        if (downloadErrorNo > 5) {
                            val msg1 = "TTS服务器连续5次错误，已暂停阅读。"
                            AppLog.put(msg1, e, true)
                            throw e
                        } else {
                            AppLog.put("TTS下载音频出错，使用无声音频代替。\n朗读文本：$speakText")
                            break
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * 生成音频文件名
     */
    private fun md5SpeakFileName(
        content: String,
        textChapter: TextChapter? = this.textChapter,
        httpTts: HttpTTS? = ReadAloud.httpTTS,
        sourceKey: String = httpTts?.url.orEmpty(),
    ): String {
        val titleToUse = textChapter?.chapter?.title ?: ""
        return MD5Utils.md5Encode16(titleToUse) + "_" +
                MD5Utils.md5Encode16("$sourceKey-|-$speechRate-|-$content")
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private fun hasFileSynthesisCue(): Boolean = playbackQueue.cues.indices.any { index ->
        voiceForCue(playbackQueue, index, ReadAloud.httpTTS ?: return@any false).engineType in
            setOf(
                ReadAloudVoice.ENGINE_SYSTEM,
                ReadAloudVoice.ENGINE_CLOUD,
            )
    }

    /**
     * 根据引擎类型生成用于文件名和缓存的 sourceKey
     */
    private fun sourceKeyForCue(
        routedVoice: ReadAloudVoice,
        cue: io.legado.app.domain.model.readaloud.ReadAloudPlaybackCue?,
        httpTts: HttpTTS,
    ): String {
        val cueEmotion = cue?.emotion.orEmpty()
        val characterPerformance = cue?.characterPerformance
        val cueRoleType = cue?.roleType ?: SpeechRoleType.Unknown
        return when (routedVoice.engineType) {
            ReadAloudVoice.ENGINE_SYSTEM ->
                "system:${routedVoice.id}:${routedVoice.revision}:${routedVoice.engineId}:${routedVoice.speakerId}"

            ReadAloudVoice.ENGINE_CLOUD ->
                "cloud:${routedVoice.id}:${routedVoice.revision}:" +
                        "${CloudTtsEmotionMapper.VERSION}:$cueEmotion:" +
                        "${CharacterPerformanceInstructionBuilder.VERSION}:" +
                        "${characterPerformance?.characterId.orEmpty()}:" +
                        "${characterPerformance?.updatedAt.orZero()}:" +
                        "${CloudTtsRoleInstructionMapper.VERSION}:" +
                        cueRoleType.storageValue

            else -> {
                val itemHttpTts = routedVoice.engineId.toLongOrNull()
                    ?.let(appDb.httpTTSDao::get) ?: httpTts
                itemHttpTts.url
            }
        }
    }

    private fun voiceForCue(
        queue: ReadAloudPlaybackQueue,
        index: Int,
        default: HttpTTS,
    ): ReadAloudVoice {
        val cue = queue.cues.getOrNull(index)
            ?: return ReadAloudVoice(
                id = "runtime-http:${default.id}",
                engineType = ReadAloudVoice.ENGINE_HTTP,
                engineId = default.id.toString(),
                speakerId = "",
                displayName = default.name,
            )
        return SpeechVoiceRouter.route(
            cue = cue,
            supportedEngineTypes = setOf(
                ReadAloudVoice.ENGINE_HTTP,
                ReadAloudVoice.ENGINE_SYSTEM,
                ReadAloudVoice.ENGINE_CLOUD,
            ),
            defaultRoute = SpeechEngineRoute(
                engineType = ReadAloud.coordinatorDefaultEngineType,
                engineId = ReadAloud.coordinatorDefaultEngineId,
                speakerId = ReadAloud.coordinatorDefaultSpeakerId,
            ),
        ).voice!!
    }

    private fun httpTtsForCue(index: Int, default: HttpTTS): HttpTTS {
        return httpTtsForCue(playbackQueue, index, default)
    }

    private fun httpTtsForCue(
        queue: ReadAloudPlaybackQueue,
        index: Int,
        default: HttpTTS,
    ): HttpTTS {
        val cue = queue.cues.getOrNull(index) ?: return default
        val routed = SpeechVoiceRouter.route(
            cue = cue,
            supportedEngineTypes = setOf(ReadAloudVoice.ENGINE_HTTP),
            defaultRoute = SpeechEngineRoute(
                engineType = ReadAloudVoice.ENGINE_HTTP,
                engineId = default.id.toString(),
            ),
        ).voice ?: return default
        val id = routed.engineId.toLongOrNull() ?: return default
        return appDb.httpTTSDao.get(id) ?: default
    }

    private fun createSilentSound(fileName: String) {
        val file = createSpeakFile(fileName)
        file.writeBytes(resources.openRawResource(R.raw.silent_sound).readBytes())
    }

    private fun hasSpeakFile(name: String): Boolean {
        return FileUtils.exist("${ttsFolderPath}$name.mp3")
    }

    private fun getSpeakFileAsMd5(name: String): File {
        return File("${ttsFolderPath}$name.mp3")
    }

    private fun createSpeakFile(name: String): File {
        return FileUtils.createFileIfNotExist("${ttsFolderPath}$name.mp3")
    }

    private fun createSpeakFile(name: String, inputStream: InputStream) {
        FileUtils.createFileIfNotExist("${ttsFolderPath}$name.mp3").outputStream().use { out ->
            inputStream.use {
                it.copyTo(out)
            }
        }
    }

    /**
     * 移除缓存文件
     * 如果时间设置为0，则不再保护当前章节，退出即全删。
     */
    private fun removeCacheFile() {
        val keepTime = readAloudSettings.audioCacheCleanTime * 60 * 1000L
        // 只有当时间大于0时，才需要保护当前章节。如果为0，说明用户想彻底不留缓存。
        val protectCurrentChapter = keepTime > 0
        val titleMd5 = if (protectCurrentChapter) MD5Utils.md5Encode16(this.textChapter?.chapter?.title ?: "") else ""

        FileUtils.listDirsAndFiles(ttsFolderPath)?.forEach {
            val isSilentSound = it.length() == 2160L

            // 判断逻辑：
            // 1. 如果是无声文件 -> 删
            // 2. 如果保留时间设为0 -> 删 (不管是不是当前章节)
            // 3. 如果保留时间>0 -> 保护当前章节，且只删过期的
            val shouldDelete = if (keepTime == 0L) {
                // 模式：即听即焚 (保留时间0)
                true
            } else {
                // 模式：保留一段时间
                // 条件：(不是当前章节) 且 (时间过期了)
                !it.name.startsWith(titleMd5) && (System.currentTimeMillis() - it.lastModified() > keepTime)
            }

            if (shouldDelete || isSilentSound) {
                FileUtils.delete(it.absolutePath)
            }
        }
    }


    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        kotlin.runCatching {
            playIndexJob?.cancel()
            exoPlayer.pause()
        }
    }

    override fun resumeReadAloud() {
        super.resumeReadAloud()
        kotlin.runCatching {
            if (pageChanged) {
                play()
            } else {
                exoPlayer.play()
                upPlayPos()
            }
        }
    }

    private fun upPlayPos() {
        playIndexJob?.cancel()
        if (textChapter == null) return
        playIndexJob = lifecycleScope.launch {
            if (exoPlayer.duration <= 0) {
                upTtsProgress(readAloudNumber + 1)
                return@launch
            }
            val speakTextLength = contentList[nowSpeak].length
            if (speakTextLength <= 0) {
                return@launch
            }
            val sleep = exoPlayer.duration / speakTextLength
            val start = speakTextLength * exoPlayer.currentPosition / exoPlayer.duration
            upTtsProgress(readAloudNumber + start.toInt() + 1)
            for (i in start..contentList[nowSpeak].length) {
                val chapterPosition = readAloudNumber + i.toInt()
                updateReadAloudProgressSnapshot(chapterPosition + 1)
                if (moveToReadAloudPage(chapterPosition)) {
                    upTtsProgress(chapterPosition + 1)
                }
                delay(sleep)
            }
        }
    }

    /**
     * 更新朗读速度
     */
    override fun upSpeechRate(reset: Boolean) {
        downloadTask?.cancel()
        preDownloadJob?.cancel()
        exoPlayer.stop()
        speechRate = speechRatePlay + 5
        if (readAloudSettings.streamReadAloudAudio) {
            downloadAndPlayAudiosStream()
        } else {
            downloadAndPlayAudios()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        when (playbackState) {
            Player.STATE_IDLE -> {
                // 空闲
            }

            Player.STATE_BUFFERING -> {
                // 缓冲中
            }

            Player.STATE_READY -> {
                // 准备好
                if (pause) return
                exoPlayer.play()
                upPlayPos()
            }

            Player.STATE_ENDED -> {
                // 结束
                playErrorNo = 0
                val interval = readAloudSettings.ttsParagraphInterval.toLong()
                if (interval > 0) {
                    val isLastParagraph = nowSpeak >= contentList.lastIndex
                    updateNextPos()
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    if (!pause && !isLastParagraph) {
                        AppLog.putDebug("HttpTTS段落开始停顿: $interval 毫秒")
                        execute {
                            delay(interval)
                            if (!pause) {
                                launch(Main) {
                                    if (!pause) {
                                        play()
                                        AppLog.putDebug("HttpTTS段落停顿结束，恢复播放")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    updateNextPos()
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                }
            }
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        when (reason) {
            Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED -> {
                if (!timeline.isEmpty && exoPlayer.playbackState == Player.STATE_IDLE) {
                    exoPlayer.prepare()
                }
            }

            else -> {}
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            playErrorNo = 0
        }
        updateNextPos()
        upPlayPos()
        upMediaMetadata(showContent = true)
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        AppLog.put("朗读错误\n${contentList[nowSpeak]}", error)
        deleteCurrentSpeakFile()
        playErrorNo++
        if (playErrorNo >= 5) {
            toastOnUi("朗读连续5次错误, 最后一次错误代码(${error.localizedMessage})")
            AppLog.put("朗读连续5次错误, 最后一次错误代码(${error.localizedMessage})", error)
            pauseReadAloud()
        } else {
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
                exoPlayer.prepare()
            } else {
                exoPlayer.clearMediaItems()
                updateNextPos()
            }
        }
    }

    private fun deleteCurrentSpeakFile() {
        if (readAloudSettings.streamReadAloudAudio) {
            return
        }
        val mediaItem = exoPlayer.currentMediaItem ?: return
        val filePath = mediaItem.localConfiguration!!.uri.path!!
        File(filePath).delete()
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<HttpReadAloudService>(actionStr)
    }

    class CustomLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy(0) {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            return C.TIME_UNSET
        }
    }

}
