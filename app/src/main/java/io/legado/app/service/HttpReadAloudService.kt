package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.script.ScriptException
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.repository.HttpTtsRepository
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.VoiceEffectPreset
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
import io.legado.app.domain.model.settings.ReadAloudContentSplitMode
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.exception.NoStackTraceException
import io.legado.app.feature.reader.core.readaloud.ReaderReadAloudChapter
import io.legado.app.feature.reader.core.readaloud.readAloudSpeechText
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSemanticTextResolver
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.exoplayer.InputStreamDataSource
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.readaloud.cast.CastAssignmentStore
import io.legado.app.help.readaloud.effect.VoiceEffectAudio
import io.legado.app.help.readaloud.effect.VoiceEffectStore
import io.legado.app.help.readaloud.playback.CharacterPerformanceInstructionBuilder
import io.legado.app.help.readaloud.playback.HttpTtsAudioSynthesizer
import io.legado.app.help.readaloud.playback.HttpTtsEmotionMapper
import io.legado.app.help.readaloud.playback.httpTtsAudioCacheSourceKey
import io.legado.app.help.readaloud.playback.CloudTtsAudioSynthesizer
import io.legado.app.help.readaloud.playback.CloudTtsEmotionMapper
import io.legado.app.help.readaloud.playback.CloudTtsRoleInstructionMapper
import io.legado.app.help.readaloud.playback.ReadAloudAudioStore
import io.legado.app.help.readaloud.playback.SystemTtsFileSynthesizer
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
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
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.get
import org.mozilla.javascript.WrappedException
import splitties.init.appCtx
import splitties.systemservices.notificationManager
import java.io.File
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * 在线朗读
 */
@SuppressLint("UnsafeOptInUsageError")
class HttpReadAloudService : BaseReadAloudService(),
    Player.Listener {
    override val useSpeechPlaybackQueue: Boolean = true

    protected override val currentSpeechRate: Float
        get() = synthesisSpeed(ReadAloud.httpTTS) * globalPlaybackSpeed

    private val httpAudioSynthesizer = HttpTtsAudioSynthesizer(GlobalContext.get().get<HttpTtsRepository>())
    private val readAloudSettingsGateway = GlobalContext.get().get<ReadAloudSettingsGateway>()
    private val readSettingsGateway = GlobalContext.get().get<ReadSettingsGateway>()
    private val otherSettingsGateway = GlobalContext.get().get<OtherSettingsGateway>()
    private var readAloudSettings: ReadAloudSettings = readAloudSettingsGateway.currentSettings
    private var readSettings: ReadSettings = readSettingsGateway.currentSettings
    private var otherSettings: OtherSettings = otherSettingsGateway.currentSettings

    private val speechRatePlay: Int
        get() = if (readAloudSettings.ttsFollowSys) 5 else readAloudSettings.ttsSpeechRate

    /**
     * 全局语速倍率, 由设置里的朗读语速换算而来, 在播放端 (ExoPlayer) 变速,
     * 对所有来源的音频 (http/系统/云端合成) 统一生效。
     */
    private val globalPlaybackSpeed: Float
        get() = (speechRatePlay + 5) / 10f

    /**
     * 源级合成语速倍率, 仅当源接口支持语速参数 ({{speakSpeed}}) 时影响返回的音频。
     * 全局语速不参与合成, 避免与播放端变速叠加。
     */
    private fun synthesisSpeed(httpTts: HttpTTS?): Float =
        ((httpTts?.speed ?: DEFAULT_TTS_SPEED) + 5) / 10f

    /** 全局任务上限；实际源的配额在共用合成器中约束。 */
    private fun synthesisConcurrency(): Int = readAloudSettings.ttsPreSynthesisConcurrency
        .coerceIn(1, HttpTTS.MAX_SYNTHESIS_THREAD_COUNT)

    private data class PreDownloadChapter(
        val chapterTitle: String,
        val queue: ReadAloudPlaybackQueue,
        val contentList: List<String>,
    )

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build()
    }

    /** 变声器的会话级效果（混响 / 带通）挂在朗读播放器上，随当前条切换。 */
    private val voiceEffectAudio by lazy { VoiceEffectAudio() }

    /** 只是用来把「读时长排音效位置」推到下一帧，不是计时器（计时交给媒体时钟）。 */
    private val soundHandler = Handler(Looper.getMainLooper())

    private fun cueEffect(index: Int): VoiceEffectPreset? =
        playbackQueue.cues.getOrNull(index)
            ?.let { VoiceEffectStore.ofSpeech(it.voiceEffect, it.characterId) }

    /**
     * 把这一单元身上的音效排到**媒体时钟**的命中位置上。
     *
     * 音效串来自 [takeCueSounds]（格式契约在 RegexCastSplitter 底部），落到
     * [io.legado.app.help.readaloud.playback.ReadAloudEffectPlayer] 的 prime + play。
     * 起播时刻用 `PlayerMessage.setPosition` 投递——它按真正播出的位置触发；换句回调比
     * 出声位置提前一整段管线缓冲，不能做基准。时长要等下一循环帧再读（见 [soundHandler]），
     * 文件则现在就 [io.legado.app.help.readaloud.playback.ReadAloudEffectPlayer.prime] 读完，
     * 到点只剩 `start()`。
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun scheduleCueSounds(index: Int) {
        val sounds = takeCueSounds(index)
        if (sounds.isEmpty()) return
        sounds.forEach { (path, permille) ->
            readAloudEffect.prime(path)
            if (permille <= 0) {
                readAloudEffect.play(path)
                return@forEach
            }
            // 转场那一刻 `duration` 可能还是上一条的，下一帧再读时长算位置
            soundHandler.post {
                val durationMs = exoPlayer.duration
                if (durationMs == C.TIME_UNSET || durationMs <= 0L) {
                    readAloudEffect.play(path)
                    return@post
                }
                val target = (exoPlayer.currentPosition + durationMs * permille / 1000L)
                    .coerceAtMost(durationMs)
                val sent = runCatching {
                    exoPlayer.createMessage { _, _ -> readAloudEffect.play(path) }
                        .setType(SOUND_MESSAGE_TYPE)
                        .setLooper(Looper.getMainLooper())
                        .setPosition(exoPlayer.currentMediaItemIndex, target)
                        .setDeleteAfterDelivery(true)
                        .send()
                    true
                }.getOrDefault(false)
                if (!sent) readAloudEffect.play(path)
            }
        }
    }

    /** 某一句应该有的播放参数：这一句的音高/语速 × 全局语速。 */
    private fun playbackParametersFor(index: Int) =
        VoiceEffectAudio.parameters(cueEffect(index), globalPlaybackSpeed)

    /**
     * 换句时把这一句的音高/语速设到播放器上。
     *
     * 句边界串音没有干净的解：`playbackParameters` 在解码链当前位置生效，与出声位置隔着
     * 管线缓冲；本仓库锁的 media3 1.11 没有「参数绑到单条 MediaItem」的 API，只能按
     * [EFFECT_PITCH_SWITCH_LEAD_MS] 打一点提前量逼近。要根除只能把音高烘进合成文件本身
     * （云端引擎请求里有 pitch 字段，本地 TTS Server 只收文本+语速，做不到）。
     */
    private fun applyCuePitch(index: Int) {
        exoPlayer.playbackParameters = playbackParametersFor(index)
    }

    /** 只切会话级效果（混响 / 带通）：这一层挂在音频会话上，播出那一刻才听得到。 */
    private fun applyCueSessionEffect(index: Int) {
        voiceEffectAudio.attach(exoPlayer.audioSessionId)
        voiceEffectAudio.apply(cueEffect(index))
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun applyCueVoiceEffect(index: Int) {
        val preset = cueEffect(index)
        applyCuePitch(index)
        // 混响/金属感挂在音频会话上，是播出那一刻生效的，切在句边界正好
        applyCueSessionEffect(index)
        // 只在预设真的换了时留一行，逐句都写会把日志刷满
        if (preset?.name != effectLogName) {
            effectLogName = preset?.name
            AppLog.putDebug(
                "变声器→句 $index 预设=${preset?.name ?: "无"} 会话=${exoPlayer.audioSessionId}"
            )
        }
        scheduleNextCueEffect(index)
    }

    /** 上一次记日志时生效的预设名。 */
    private var effectLogName: String? = null

    /**
     * 排期消息的载荷：句下标 + 队列代次 + 这一条只管哪一层。
     *
     * 两层各一条消息，因为它们生效的位置不同：音高/语速在解码链上生效，比出声位置早
     * 一整段管线缓冲，要提前 [EFFECT_PITCH_SWITCH_LEAD_MS] 才落在句边界；混响/带通挂在
     * 输出会话上，作用在正在出声的信号上，只提前 [EFFECT_SESSION_LEAD_MS]。
     */
    private class CuePitchTick(val index: Int, val generation: Int, val pitch: Boolean)

    /** 排下去还没投递的两条消息；换队列时要收回，不然旧句的参数会扣在新句上。 */
    private var pendingPitchMessage: PlayerMessage? = null
    private var pendingSessionMessage: PlayerMessage? = null

    /** 队列代次：播放器一重置就 +1，之前的排期全部作废。 */
    private var pitchGeneration = 0

    /** 播放器重置（重新开播、停止、销毁）：作废所有还没投递的排期。 */
    private fun resetPitchSchedule() {
        pitchGeneration++
        pendingPitchMessage?.cancel()
        pendingPitchMessage = null
        pendingSessionMessage?.cancel()
        pendingSessionMessage = null
    }

    /**
     * 把下一句的变声器（音高/语速 + 混响/带通）排在**本句结束前一点**投递。
     *
     * `PlayerMessage` 由媒体时钟（真正播出的位置）投递，但两层的生效点不一样：
     * `playbackParameters` 在解码链当前位置生效，比出声位置早一整段还没出声的管线缓冲，
     * 所以要提前那么多才落在句边界——等换句回调再设，这一句开头就还带着上一句的音色。
     * 音频会话上的效果作用在**正在出声**的信号上，只能贴边换。提前量落在合成音频自带的
     * 句尾静音里；句子很短时按 duration 的三分之一收窄，不啃真正的说话内容。
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun scheduleNextCueEffect(index: Int) {
        val next = index + 1
        if (next > playbackQueue.cues.lastIndex) return
        val durationMs = exoPlayer.duration
        if (durationMs == C.TIME_UNSET || durationMs <= 0) return
        val sessionLead = minOf(EFFECT_SESSION_LEAD_MS, durationMs / 3)
        val pitchLead = minOf(EFFECT_PITCH_SWITCH_LEAD_MS, durationMs / 3)
        pendingPitchMessage?.cancel()
        pendingSessionMessage?.cancel()
        runCatching {
            // 音高层提前得多（补解码链那段缓冲），会话层贴边换（它作用在正在出声的信号上）
            pendingSessionMessage = exoPlayer
                .createMessage { _, payload ->
                    val tick = payload as? CuePitchTick
                    if (tick != null && !tick.pitch && tick.generation == pitchGeneration) {
                        applyCueSessionEffect(tick.index)
                    }
                }
                .setType(PITCH_MESSAGE_TYPE)
                .setPayload(CuePitchTick(next, pitchGeneration, pitch = false))
                .setLooper(Looper.getMainLooper())
                .setPosition(exoPlayer.currentMediaItemIndex, durationMs - sessionLead)
                .setDeleteAfterDelivery(true)
                .send()
            pendingPitchMessage = exoPlayer
                .createMessage { _, payload ->
                    val tick = payload as? CuePitchTick
                    if (tick != null && tick.pitch && tick.generation == pitchGeneration) {
                        applyCuePitch(tick.index)
                    }
                }
                .setType(PITCH_MESSAGE_TYPE)
                .setPayload(CuePitchTick(next, pitchGeneration, pitch = true))
                .setLooper(Looper.getMainLooper())
                .setPosition(exoPlayer.currentMediaItemIndex, durationMs - pitchLead)
                .setDeleteAfterDelivery(true)
                .send()
        }.onFailure {
            AppLog.putDebug("变声器提前排期失败(句 $next): ${it.message}")
        }
    }

    // 缓存目录优先外部存储，externalCacheDir 不可用时退回内部 cacheDir
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
    private var paragraphIntervalJob: Coroutine<*>? = null
    private var paragraphResumeJob: Coroutine<*>? = null
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
        applyCueVoiceEffect(nowSpeak)
        // 上一次会话没走到 onDestroy 留下的缓存音频在这里清掉，理由见 [sweepBurnAfterReadLeftovers]。
        Coroutine.async { sweepBurnAfterReadLeftovers() }
        lifecycleScope.launch {
            readAloudSettingsGateway.settings.collectLatest {
                readAloudSettings = it
                // 全局语速为播放端变速, 设置变化即时生效, 无需重新合成
                applyCueVoiceEffect(nowSpeak)
            }
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
        audioDownloadTask?.cancel()
        resetPitchSchedule()
        // 会话效果挂在播放器持有的音频会话上，服务销毁前不放手就留在一条死会话里
        voiceEffectAudio.release()
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
        resetPitchSchedule()
        paragraphIntervalJob?.cancel()
        paragraphResumeJob?.cancel()
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            preparePlayback()
        } else {
            super.play()
            if (useStreamPlayback()) {
                downloadAndPlayAudiosStream()
            } else {
                downloadAndPlayAudios()
            }
        }
    }

    /**
     * 直读（流式）还是文件合成由这一个判断决定；段落间隔续播 [playNextParagraph] 必须走同一支，
     * 否则两条路算出的缓存文件名不是同一个。
     */
    private fun useStreamPlayback(): Boolean =
        readAloudSettings.streamReadAloudAudio && !hasFileSynthesisCue()

    /**
     * 段落间隔到点后的续播：只把「现在该播的这一格」交给播放器，不重建本章的合成任务。
     *
     * 约束：不能改用 [play]。它会 cancel 本章的合成循环，而 [createSpeakFile] 每读一块就 ensureActive，
     * 于是正在途的那一句连同服务端已回全的响应一起被丢掉，下一轮再向 TTS 请求同一句，
     * 段间静音就成了「一次完整重合成的耗时」，与 [ReadAloudSettings.ttsParagraphInterval] 无关。
     * 这一格的音频正由 [downloadAndPlayAudios] 那条还在跑的循环写进缓存，这里等它出现即可。
     */
    private fun playNextParagraph() {
        pageChanged = false
        resetPitchSchedule()
        if (!requestFocus()) return
        if (contentList.isEmpty()) return
        super.play()
        val httpTts = ReadAloud.httpTTS ?: return
        val index = nowSpeak
        val stream = useStreamPlayback()
        paragraphResumeJob?.cancel()
        // 在本线程（这里是主线程）算这一格的输入：contentList / nowSpeak / playbackQueue 都归主线程，
        // 挪进 IO 协程里读就是跨线程读一份会变的列表。
        val synthesis = cueSynthesisFor(index, httpTts, stream) ?: return
        paragraphResumeJob = execute(executeContext = IO) {
            if (!stream && !awaitSpeakFile(synthesis.fileName)) {
                // 合成任务已经不在这句话上了（本章循环跑完或出错）：退回完整重播，不能让朗读停死
                launch(Main) { if (index == nowSpeak && !pause) play() }
                return@execute
            }
            launch(Main) {
                if (index != nowSpeak || pause || exoPlayer.mediaItemCount != 0) return@launch
                if (stream) {
                    val factory = createDataSourceFactory(
                        synthesis.httpTts, synthesis.speakText, synthesis.voice, synthesis.emotion
                    )
                    exoPlayer.setMediaSource(createMediaSource(factory, synthesis.fileName))
                } else {
                    exoPlayer.setMediaItem(
                        MediaItem.fromUri(Uri.fromFile(speakFileForPlay(synthesis.fileName)))
                    )
                }
                exoPlayer.prepare()
            }
        }
    }

    /**
     * 等到这一格**有音频可播**。就绪只看正式名非空，不看 [hasSpeakFile]：引擎拒掉的那一格
     * （纯符号段只回 `TTS Generation Failed`）合成不出人声，循环给它的只有 [createSilentSound]
     * 那份占位，而 [hasSpeakFile] 有意不把占位算成命中（占位是失败留下的空壳，认了等于把真失败的
     * 句子永久静音）。两边共用 hasSpeakFile 的话这一格永远不就绪，续播要白等满
     * [SPEAK_FILE_WAIT_MILLIS] 才退回 [play]，那段时间播放停死、引擎侧一个请求也没有，
     * 退回后整章重新请求一遍。
     *
     * 只看正式名非空不会认下半截文件：占位与真人声都由 [commitSpeakPart] 改名落成正式名，
     * 写完之前挂的是 `.part` 那个名字。
     */
    private suspend fun awaitSpeakFile(fileName: String): Boolean {
        var waitedMillis = 0L
        while (speakFileForPlay(fileName).length() <= 0L) {
            if (downloadTask?.isActive != true) break
            if (waitedMillis >= SPEAK_FILE_WAIT_MILLIS) break
            delay(SPEAK_FILE_POLL_MILLIS)
            waitedMillis += SPEAK_FILE_POLL_MILLIS
        }
        return speakFileForPlay(fileName).length() > 0L
    }

    /**
     * 段落之间的停顿还没走完。合成循环自己也会给「现在该播的这一格」上条目
     * （见 [downloadAndPlayAudios] 的 ttsParagraphInterval 分支），停顿期间不能让那一条抢先起播，
     * 否则间隔被吃掉；这一格的起播统一交给 [playNextParagraph]。
     */
    private val paragraphGapPending: Boolean
        get() = paragraphIntervalJob?.isActive == true || paragraphResumeJob?.isActive == true

    override fun playStop() {
        downloadTask?.cancel()
        playIndexJob?.cancel()
        preDownloadJob?.cancel()
        paragraphIntervalJob?.cancel()
        paragraphResumeJob?.cancel()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        resetPitchSchedule()
    }

    // ---- 听书音频下载 ----

    private var audioDownloadTask: Coroutine<*>? = null

    /** 句子计数：并发合成时只有这一个写入点，用原子计数免得加锁。 */
    private val audioDownloadSentences = java.util.concurrent.atomic.AtomicInteger(0)

    /** 这一批里合成失败、没落进下载区的句子数。 */
    private val audioDownloadFailed = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * 把 [start]..[end] 章的朗读音频合成到下载区（含只有本章：start == end）。
     *
     * 完全复用实时朗读那一条链路——同一个音色路由、同一个文件名算法、同一套引擎分派，
     * 只是不播。每句先合成进缓存目录，再把文件复制进下载区：缓存目录那套「临时名 + 改名」
     * 已经处理过「半截文件被当成缓存」的坑，不重做一遍；而缓存会被清、下载区不会。
     */
    private fun startAudioDownload(bookUrl: String, start: Int, end: Int) {
        val book = appDb.bookDao.getBook(bookUrl) ?: return
        val httpTts = ReadAloud.httpTTS ?: run {
            toastOnUi("听书下载要用文件合成引擎：请选 HTTP/云端引擎，或打开多角色朗读")
            return
        }
        // 没有在播 = 这次开服务只为下载，下完就自己收，别留一个空转的朗读服务
        val downloadOnly = exoPlayer.mediaItemCount == 0 && !exoPlayer.isPlaying
        val last = end.coerceAtLeast(start)
        audioDownloadTask?.cancel()
        audioDownloadFailed.set(0)
        ReadAloudAudioStore.updateProgress(
            ReadAloudAudioStore.Progress(
                running = true,
                bookUrl = bookUrl,
                chapterTotal = last - start + 1,
            )
        )
        audioDownloadTask = execute(executeContext = IO) {
            var chapterDone = 0
            var consecutiveFailures = 0
            for (index in start..last) {
                ensureActive()
                val chapter = appDb.bookChapterDao.getChapter(bookUrl, index) ?: break
                val prepared = getPreDownloadChapter(book, chapter)
                chapterDone++
                if (prepared == null) {
                    consecutiveFailures++
                    if (consecutiveFailures >= 5) break
                    continue
                }
                consecutiveFailures = 0
                downloadChapterAudio(book, chapter.index, prepared, httpTts, chapterDone)
            }
            ReadAloudAudioStore.finishProgress()
            upAudioDownloadNotification(force = true)
            toastOnUi("听书音频下载结束")
            stopSelfIfDownloadOnly(downloadOnly)
        }.onError {
            if (it !is CancellationException) {
                AppLog.put("听书音频下载失败\n${it.localizedMessage}", it)
            }
            ReadAloudAudioStore.finishProgress()
            notificationManager.cancel(NotificationId.ReadAloudAudioDownload)
            stopSelfIfDownloadOnly(downloadOnly)
        }
    }

    /**
     * 只为下载而开着的服务在下完后自己关掉。
     *
     * 判断以**当前**播放状态为准：下载期间用户点了朗读就当他要继续听，不能把他停掉。
     * 问播放状态必须切回主线程——ExoPlayer 只允许在创建它的线程访问，而这里的调用点
     * 在下载协程（IO 线程）里，直接读就抛「Player is accessed on the wrong thread」。
     */
    private fun stopSelfIfDownloadOnly(startedAsDownloadOnly: Boolean) {
        if (!startedAsDownloadOnly) return
        // context 要显式给 Main：Coroutine 用 withContext(scope + context) 跑块，
        // 不写的话它默认是 IO，会把 launch 的调度器盖掉，又回到错的线程上。
        execute(context = Main) {
            if (!exoPlayer.isPlaying && exoPlayer.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    private suspend fun downloadChapterAudio(
        book: Book,
        chapterIndex: Int,
        prepared: PreDownloadChapter,
        httpTts: HttpTTS,
        chapterDone: Int,
    ) = coroutineScope {
        val bookUrl = book.bookUrl
        val concurrency = synthesisConcurrency()
        val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency)
        audioDownloadSentences.set(0)
        ReadAloudAudioStore.updateProgress(
            ReadAloudAudioStore.progress.value.copy(
                chapterDone = chapterDone - 1,
                currentChapter = prepared.chapterTitle,
                sentenceTotal = prepared.contentList.size,
                sentenceDone = 0,
            )
        )
        // 文件名要和朗读时算出来的一模一样，所以文本、引擎、语速全都沿用那一条路径
        val results = prepared.contentList.mapIndexed { index, raw ->
            async {
                semaphore.acquire()
                try {
                    val content = speechText(raw)
                    val routedVoice = voiceForCue(prepared.queue, index, httpTts)
                    val cue = prepared.queue.cues.getOrNull(index)
                    val fileName = md5SpeakFileName(
                        content, prepared.chapterTitle,
                        sourceKey = sourceKeyForCue(routedVoice, cue, httpTts),
                    )
                    if (ReadAloudAudioStore.downloadedFile(bookUrl, fileName) != null) {
                        finishAudioDownloadSentence(bookUrl, fileName, prepared)
                    } else if (synthesizeCueWithSystemFile(
                            routedVoice, cue, content, prepared.chapterTitle, httpTts, fileName
                        )
                    ) {
                        finishAudioDownloadSentence(bookUrl, fileName, prepared)
                    } else {
                        // 合成失败就什么都不写。落一份无声占位当「下载成功」，这一句以后永远
                        // 播空白：朗读侧在缓存里认到同名文件就不再重新合成，下载区那份又优先
                        // 于缓存，连清缓存都救不回来。
                        audioDownloadFailed.incrementAndGet()
                        null
                    }
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll().filterNotNull()
        ReadAloudAudioStore.record(bookUrl, chapterIndex, prepared.chapterTitle, results)
        ReadAloudAudioStore.updateProgress(
            ReadAloudAudioStore.progress.value.copy(
                chapterDone = chapterDone,
                failed = audioDownloadFailed.get(),
            )
        )
        upAudioDownloadNotification(force = true)
    }

    /** 合成好的那份从缓存目录落进下载区，顺带推进句子计数。 */
    private suspend fun finishAudioDownloadSentence(
        bookUrl: String,
        fileName: String,
        prepared: PreDownloadChapter,
    ): String? {
        val saved = ReadAloudAudioStore.saveFrom(
            bookUrl, fileName, getSpeakFileAsMd5(fileName)
        ) ?: return null
        // 播放先认下载区（见 [speakFileForPlay]），落进下载区后缓存这份副本没有别的用途。即听即焚下
        // 当场删：一次下载能合成整本书，等不到起手那次清扫，占用会一路涨。
        if (cacheBurnAfterRead) {
            FileUtils.delete(getSpeakFileAsMd5(fileName).absolutePath)
        }
        val done = audioDownloadSentences.incrementAndGet()
        ReadAloudAudioStore.updateProgress(
            ReadAloudAudioStore.progress.value.copy(
                sentenceDone = done,
                sentenceTotal = prepared.contentList.size,
            )
        )
        if (done % 8 == 0) upAudioDownloadNotification()
        return fileName
    }

    /**
     * 预合成那份分派不认系统引擎（系统音色是直读的），下载必须认，否则用系统音色的书一句都下不来。
     */
    private suspend fun synthesizeCueWithSystemFile(
        routedVoice: ReadAloudVoice,
        cue: io.legado.app.domain.model.readaloud.ReadAloudPlaybackCue?,
        content: String,
        chapterTitle: String,
        httpTts: HttpTTS,
        fileName: String,
    ): Boolean {
        if (routedVoice.engineType != ReadAloudVoice.ENGINE_SYSTEM) {
            return synthesizeSingleCue(routedVoice, cue, content, chapterTitle, httpTts)
        }
        val speakText = content.readAloudSpeechText()
        if (speakText.isEmpty()) {
            createSilentSound(fileName)
            return true
        }
        val config = runCatching {
            GSON.fromJson(routedVoice.traitsJson, SystemTtsVoiceConfig::class.java)
        }.getOrNull() ?: SystemTtsVoiceConfig()
        val synthesized = synthesizeSpeakFile(fileName) { output ->
            systemTtsFileSynthesizer.synthesize(
                routedVoice.engineId,
                routedVoice.speakerId,
                speakText,
                output,
                config.speechRate ?: 1f,
                config.pitch ?: 1f,
            )
        }
        if (synthesized) writeTextIndexEntry(fileName, speakText)
        return synthesized
    }

    private fun cancelAudioDownload() {
        audioDownloadTask?.cancel()
        audioDownloadTask = null
        ReadAloudAudioStore.finishProgress()
        notificationManager.cancel(NotificationId.ReadAloudAudioDownload)
    }

    /** 下载通知：章节走进度条，句子数写在正文里，两个维度都看得见。 */
    private fun upAudioDownloadNotification(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastAudioDownloadNotifyMs < 500L) return
        lastAudioDownloadNotifyMs = now
        val progress = ReadAloudAudioStore.progress.value
        if (!progress.running) {
            notificationManager.cancel(NotificationId.ReadAloudAudioDownload)
            return
        }
        val notification = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_download_done)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(getString(R.string.read_aloud_audio_download))
            .setContentText(
                getString(
                    R.string.read_aloud_audio_download_progress,
                    progress.chapterDone, progress.chapterTotal,
                    progress.sentenceDone, progress.sentenceTotal,
                )
            )
            .setProgress(progress.chapterTotal, progress.chapterDone, false)
            .build()
        notificationManager.notify(NotificationId.ReadAloudAudioDownload, notification)
    }

    private var lastAudioDownloadNotifyMs = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.downloadReadAloudAudio -> startAudioDownload(
                intent.getStringExtra("bookUrl").orEmpty(),
                intent.getIntExtra("startChapter", ReadBook.durChapterIndex),
                intent.getIntExtra("endChapter", ReadBook.durChapterIndex),
            )

            IntentAction.cancelDownloadReadAloudAudio -> cancelAudioDownload()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateNextPos(naturalCompletion: Boolean = false) {
        if (!playbackQueue.isEmpty) {
            val current = playbackCursor ?: ReadAloudPlaybackCursor(nowSpeak, paragraphStartPos)
            playbackQueue.next(current)?.let(::moveToPlaybackCursor) ?: if (naturalCompletion) {
                completeCurrentChapter()
            } else {
                nextChapter()
            }
            return
        }
        readAloudNumber += contentList[nowSpeak].length + 1 - paragraphStartPos
        // 页内切段不引入换行符，累加会漂移，用段落绝对位置重算
        readAloudNumber = paragraphChapterPositionAt(nowSpeak + 1) ?: 0
        paragraphStartPos = 0
        if (nowSpeak < contentList.lastIndex) {
            nowSpeak++
        } else {
            if (naturalCompletion) completeCurrentChapter() else nextChapter()
        }
    }

    /**
     * 同一句的合成互斥：按目标文件名上锁。
     *
     * 实时播放、后续章节预合成、听书下载三条路径都是「先查缓存，没有就向 TTS 发请求」，
     * 而一次合成要几秒、期间 `.part` 和正式文件都还没落盘，另一条路径查缓存必然查不到、
     * 跟着重复请求。上锁排队 + 拿到锁后再查一次缓存，把重复请求压成一次。
     */
    private val speakFileLocks = ConcurrentHashMap<String, Mutex>()

    private suspend inline fun <T> withSpeakFileLock(
        fileName: String,
        block: suspend () -> T,
    ): T {
        val mutex = speakFileLocks.getOrPut(fileName) { Mutex() }
        return try {
            mutex.withLock { block() }
        } finally {
            speakFileLocks.remove(fileName, mutex)
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

                contentList.forEachIndexed { index, _ ->
                    ensureActive()
                    if (index < nowSpeak) return@forEachIndexed
                    val synthesis = cueSynthesisFor(index, httpTts, stream = false)
                        ?: return@forEachIndexed
                    val routedVoice = synthesis.voice
                    val cue = synthesis.cue
                    val cueEmotion = synthesis.emotion
                    val characterPerformance = cue?.characterPerformance
                    val cueRoleType = cue?.roleType ?: SpeechRoleType.Unknown
                    val itemHttpTts = synthesis.httpTts
                    val fileName = synthesis.fileName
                    val speakText = synthesis.speakText
                    if (speakText.isEmpty()) {
                        AppLog.put("阅读段落内容为空，使用无声音频代替。\n朗读文本：${synthesis.text}")
                        createSilentSound(fileName)
                    } else if (!hasSpeakFile(fileName)) {
                        withSpeakFileLock(fileName) {
                        // 等锁期间另一条路径可能已经把这句合成好了
                        if (!hasSpeakFile(fileName)) {
                        runCatching {
                            when (routedVoice.engineType) {
                                ReadAloudVoice.ENGINE_SYSTEM -> {
                                    val config = runCatching {
                                        GSON.fromJson(
                                            routedVoice.traitsJson,
                                            SystemTtsVoiceConfig::class.java,
                                        )
                                    }.getOrNull() ?: SystemTtsVoiceConfig()
                                    // 全局语速走播放端变速, 系统合成只用音色自带语速, 避免叠加
                                    val synthesized = synthesizeSpeakFile(fileName) { output ->
                                        systemTtsFileSynthesizer.synthesize(
                                            routedVoice.engineId,
                                            routedVoice.speakerId,
                                            speakText,
                                            output,
                                            config.speechRate ?: 1f,
                                            config.pitch ?: 1f,
                                        )
                                    }
                                    if (!synthesized) {
                                        AppLog.put(
                                            "朗读：系统引擎 ${routedVoice.engineId} 没把这句合成成文件" +
                                                "（音色 ${routedVoice.speakerId}），这一句跳过" +
                                                "\n句子：${speakText.take(30)}",
                                        )
                                        createSilentSound(fileName)
                                    }
                                }

                                ReadAloudVoice.ENGINE_CLOUD -> {
                                    val synthesized = synthesizeSpeakFile(fileName) { output ->
                                        cloudTtsAudioSynthesizer.synthesize(
                                            routedVoice,
                                            speakText,
                                            output,
                                            styleOverride = cueEmotion,
                                            characterPerformance = characterPerformance,
                                            roleType = cueRoleType,
                                        )
                                    }
                                    if (!synthesized) {
                                        AppLog.put(
                                            "朗读：云端音色 ${routedVoice.speakerId} 没合成出文件，" +
                                                "这一句跳过\n句子：${speakText.take(30)}",
                                        )
                                        createSilentSound(fileName)
                                    }
                                }

                                else -> {
                                    val inputStream = getSpeakStream(
                                        itemHttpTts, speakText, routedVoice, cueEmotion
                                    )
                                    if (inputStream != null) {
                                        createSpeakFile(fileName, inputStream)
                                    } else {
                                        AppLog.put(
                                            "朗读：HTTP 引擎 ${itemHttpTts.name} 没回音频，这一句跳过" +
                                                "\n句子：${speakText.take(30)}",
                                        )
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
                        }
                    }
                    if (speakText.isNotEmpty() && hasSpeakFile(fileName)) {
                        writeTextIndexEntry(fileName, speakText)
                    }
                    val file = speakFileForPlay(fileName)
                    val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
                    launch(Main) {
                        if (readAloudSettings.ttsParagraphInterval > 0) {
                            if (index == nowSpeak && exoPlayer.mediaItemCount == 0 && !paragraphGapPending) {
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
        val source = ReaderChapterSourceParser.parse(
            chapterIndex = chapter.index,
            title = displayTitle,
            paragraphs = processedContent.textList,
            includeTitle = false,
            adaptSpecialStyle = readSettings.adaptSpecialStyle,
            htmlSemanticTextResolver = AndroidReaderHtmlSemanticTextResolver,
            castLabels = if (io.legado.app.ui.config.readConfig.ReadConfig.multiRoleCast) {
                CastAssignmentStore.labelsForChapter(book.bookUrl, chapter.index)
            } else {
                null
            },
        )
        // 预合成必须与实时朗读用同一种划分方式解析，否则预合成好的音频与实际朗读单元对不上
        val contentSplitMode = resolveContentSplitMode(
            ReadAloudContentSplitMode.fromStorage(readAloudSettings.contentSplitMode)
        )
        val readAloudChapter = ReaderReadAloudChapter.create(
            chapterIndex = chapter.index,
            title = displayTitle,
            semanticContent = source.semanticContent,
            pageStarts = bookSession.pagination?.takeIf {
                bookSession.owns(book.bookUrl) && it.chapterIndex == chapter.index
            }?.pageStarts.orEmpty(),
            contentSplitMode = contentSplitMode,
        )
        val splitPolicy = contentSplitPolicy(contentSplitMode)
        val splitByPage = contentSplitMode == ReadAloudContentSplitMode.Page
        val plan = buildSpeechPlan(
            bookUrl = book.bookUrl,
            chapterIndex = chapter.index,
            paragraphs = readAloudChapter.canonicalSpeechParagraphs(splitByPage, splitPolicy),
            splitPolicy = splitPolicy,
        )
        val queue = runCatching { buildPlaybackQueue(book.bookUrl, plan).withChapterTitle(displayTitle) }
            .getOrDefault(ReadAloudPlaybackQueue.Empty)
        val contentList = if (!queue.isEmpty) {
            queue.cues.map { it.text }
        } else {
            // 与实时朗读同一份单元集合：不发声段不进列表，预合成文件名才会与播放时对上
            listOf(displayTitle.trim()).filter { it.isNotEmpty() } +
                    readAloudChapter.readAloudParagraphs(splitByPage, splitPolicy)
                        .map { it.text }
        }
        return PreDownloadChapter(displayTitle, queue, contentList)
    }

    private suspend fun preDownloadAudios(httpTts: HttpTTS) {
        val book = bookSession.book ?: return
        val currentIdx = bookSession.chapterIndex
        val limit = readAloudSettings.audioPreDownloadNum
        val concurrency = synthesisConcurrency()
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

        // 预合成的文本与实时那条一致：标记不进合成，也不进文件名
        prepared.contentList.map(::speechText).mapIndexed { index, content ->
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
                        content, prepared.chapterTitle, sourceKey = sourceKey,
                    )
                    if (hasSpeakFile(fileName)) return@async

                    val success = synthesizeSingleCueWithRetry(
                        routedVoice, cue, content, prepared.chapterTitle, httpTts,
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
        chapterTitle: String,
        httpTts: HttpTTS,
    ): Boolean {
        if (synthesizeSingleCue(routedVoice, cue, content, chapterTitle, httpTts)) {
            return true
        }
        delay(500)
        return synthesizeSingleCue(routedVoice, cue, content, chapterTitle, httpTts)
    }

    /**
     * 单个 cue 合成核心方法
     */
    private suspend fun synthesizeSingleCue(
        routedVoice: ReadAloudVoice,
        cue: io.legado.app.domain.model.readaloud.ReadAloudPlaybackCue?,
        content: String,
        chapterTitle: String,
        httpTts: HttpTTS,
    ): Boolean {
        val sourceKey = sourceKeyForCue(routedVoice, cue, httpTts)
        val fileName = md5SpeakFileName(content, chapterTitle, sourceKey = sourceKey)
        val speakText = content.readAloudSpeechText()
        if (speakText.isEmpty()) {
            createSilentSound(fileName)
            return true
        }
        val success = withSpeakFileLock(fileName) {
            if (hasSpeakFile(fileName)) {
                // 实时播放那条路径正在合成这一句：等它，别再向 TTS 发第二次请求
                true
            } else runCatching {
            when (routedVoice.engineType) {
                ReadAloudVoice.ENGINE_CLOUD -> {
                    synthesizeSpeakFile(fileName) { output ->
                        cloudTtsAudioSynthesizer.synthesize(
                            routedVoice, speakText, output,
                            styleOverride = cue?.emotion.orEmpty(),
                            characterPerformance = cue?.characterPerformance,
                            roleType = cue?.roleType ?: SpeechRoleType.Unknown,
                        )
                    }
                }

                ReadAloudVoice.ENGINE_HTTP -> {
                    val itemHttpTts = httpSourceForVoice(routedVoice, httpTts)
                    val inputStream = getSpeakStream(
                        itemHttpTts, speakText, routedVoice, cue?.emotion.orEmpty()
                    )
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
                contentList.forEachIndexed { index, _ ->
                    ensureActive()
                    if (index < nowSpeak) return@forEachIndexed
                    val synthesis = cueSynthesisFor(index, httpTts, stream = true)
                        ?: return@forEachIndexed
                    val speakText = synthesis.speakText
                    if (speakText.isEmpty()) {
                        AppLog.put("阅读段落内容为空，使用无声音频代替。\n朗读文本：${synthesis.text}")
                    }
                    val fileName = synthesis.fileName
                    val dataSourceFactory = createDataSourceFactory(
                        synthesis.httpTts, speakText, synthesis.voice, synthesis.emotion
                    )
                    val downloader = createDownloader(dataSourceFactory, fileName)
                    downloaderChannel.send(downloader)
                    val mediaSource = createMediaSource(dataSourceFactory, fileName)
                    launch(Main) {
                        if (readAloudSettings.ttsParagraphInterval > 0) {
                            if (index == nowSpeak && exoPlayer.mediaItemCount == 0 && !paragraphGapPending) {
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
        val book = bookSession.book ?: return
        val currentIdx = bookSession.chapterIndex
        val limit = readAloudSettings.audioPreDownloadNum
        val concurrency = synthesisConcurrency()
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

        // 预合成的文本与实时那条一致：标记不进合成，也不进文件名
        prepared.contentList.map(::speechText).mapIndexed { index, content ->
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
                            content, prepared.chapterTitle, sourceKey = sourceKey,
                        )
                        if (hasSpeakFile(fileName)) return@async
                        val success = synthesizeSingleCueWithRetry(
                            routedVoice, cue, content, prepared.chapterTitle, httpTts,
                        )
                        if (!success) {
                            createSilentSound(fileName)
                            failedCount++
                        }
                    } else {
                        val speakText = content.readAloudSpeechText()
                        val sourceKey = sourceKeyForCue(routedVoice, cue, httpTts)
                        val fileName = md5SpeakFileName(
                            content, prepared.chapterTitle, sourceKey = sourceKey,
                        )
                        // 路由出来的引擎才是这句该用的源，直接用 httpTts 会拿默认源
                        // 去配这条路由的音色 id
                        val itemHttpTts = httpSourceForVoice(routedVoice, httpTts)
                        val dataSourceFactory = createDataSourceFactory(
                            itemHttpTts, speakText, routedVoice, cue?.emotion.orEmpty()
                        )
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
        speakText: String,
        voice: ReadAloudVoice? = null,
        emotionTag: String = ""
    ): CacheDataSource.Factory {
        val upstreamFactory = DataSource.Factory {
            InputStreamDataSource {
                if (speakText.isEmpty()) {
                    null
                } else {
                    kotlin.runCatching {
                        runBlocking(lifecycleScope.coroutineContext[Job]!!) {
                            getSpeakStream(httpTts, speakText, voice, emotionTag, lifecycleScope.coroutineContext[Job])
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

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun createMediaSource(factory: DataSource.Factory, fileName: String): MediaSource {
        return DefaultMediaSourceFactory(this)
            .setDataSourceFactory(factory)
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            .createMediaSource(MediaItem.fromUri(fileName))
    }

    private suspend fun getSpeakStream(
        httpTts: HttpTTS,
        speakText: String,
        voice: ReadAloudVoice? = null,
        emotionTag: String = "",
        streamOwner: Job? = null
    ): InputStream? {
        while (true) {
            try {
                return httpAudioSynthesizer.openStream(
                    httpTts, speakText, voice, emotionTag, streamOwner ?: currentCoroutineContext()[Job]
                ).also {
                    downloadErrorNo = 0
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
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
        chapterTitle: String = readerReadAloudChapter?.title.orEmpty(),
        httpTts: HttpTTS? = ReadAloud.httpTTS,
        sourceKey: String = httpTts?.url.orEmpty(),
    ): String {
        return MD5Utils.md5Encode16(chapterTitle) + "_" +
                MD5Utils.md5Encode16("$sourceKey-|-$speechRate-|-$content")
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private fun hasFileSynthesisCue(): Boolean {
        val default = ReadAloud.httpTTS ?: return false
        // play() 在主线程决定播放模式，只读取一次引擎 ID，避免每个 cue 都查整行源。
        val engineIds = appDb.httpTTSDao.getIds().mapTo(mutableSetOf()) { it.toString() }
        return playbackQueue.cues.indices.any { index ->
            voiceForCue(playbackQueue, index, default, engineIds).engineType in setOf(
                ReadAloudVoice.ENGINE_SYSTEM,
                ReadAloudVoice.ENGINE_CLOUD,
            )
        }
    }

    /**
     * 当前正在朗读的某一格里，送去合成的文本、用的引擎音色与缓存文件名。
     *
     * 同一份输入的三个消费者：[downloadAndPlayAudios]、[downloadAndPlayAudiosStream] 与 [playNextParagraph]
     * 都从这里取同一份输入——缓存文件名必须与写进缓存时算出的完全一致，
     * 否则段落间隔续播会认不出循环刚合成好的那一句，只能再向 TTS 请求一遍。
     */
    private class CueSynthesis(
        val text: String,
        val speakText: String,
        val fileName: String,
        val httpTts: HttpTTS,
        val voice: ReadAloudVoice,
        val emotion: String,
        val cue: io.legado.app.domain.model.readaloud.ReadAloudPlaybackCue?,
    )

    private fun cueSynthesisFor(index: Int, default: HttpTTS, stream: Boolean): CueSynthesis? {
        val content = contentList.getOrNull(index) ?: return null
        var text = content
        if (paragraphStartPos > 0 && index == nowSpeak) {
            text = text.substring(paragraphStartPos)
        }
        // 偏移用完才去掉标记，合成与文件名都用这一份干净文本
        text = speechText(text)
        val cue = playbackQueue.cues.getOrNull(index)
        val voice =
            if (stream) httpVoiceForCue(index, default) else voiceForCue(playbackQueue, index, default)
        val itemHttpTts = httpSourceForVoice(voice, default)
        val emotion = cue?.emotion.orEmpty()
        val sourceKey =
            if (stream) httpSourceKey(voice, emotion, default) else sourceKeyForCue(voice, cue, default)
        return CueSynthesis(
            text = text,
            speakText = text.readAloudSpeechText(),
            fileName = md5SpeakFileName(text, httpTts = itemHttpTts, sourceKey = sourceKey),
            httpTts = itemHttpTts,
            voice = voice,
            emotion = emotion,
            cue = cue,
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

            else -> httpSourceKey(routedVoice, cueEmotion, httpTts)
        }
    }

    /**
     * HTTP 源的缓存键。
     *
     * 必须带上音色与情绪：`currentToneID` 这些变量会拼进 URL，只认 URL 的话
     * 同一句换角色会命中上一句用别的音色合成好的缓存。
     */
    private fun httpSourceKey(
        routedVoice: ReadAloudVoice,
        cueEmotion: String,
        httpTts: HttpTTS,
    ): String {
        val itemHttpTts = httpSourceForVoice(routedVoice, httpTts)
        val effectiveVoice = if (HttpTtsEmotionMapper.parseCatalog(routedVoice.emotionCatalogJson).isEmpty()) {
            routedVoice.copy(
                emotionCatalogJson = appDb.readAloudVoiceDao.getHttpEmotionCatalog(routedVoice.engineId) ?: "[]"
            )
        } else routedVoice
        return httpTtsAudioCacheSourceKey(itemHttpTts, effectiveVoice, cueEmotion)
    }

    private fun httpSourceForVoice(voice: ReadAloudVoice, default: HttpTTS): HttpTTS {
        if (voice.engineType != ReadAloudVoice.ENGINE_HTTP) return default
        return voice.engineId.toLongOrNull()?.let(appDb.httpTTSDao::get)
            ?: throw NoStackTraceException("HTTP TTS 引擎不存在：${voice.engineId}")
    }

    private fun isVoiceExecutable(voice: ReadAloudVoice): Boolean =
        voice.engineType != ReadAloudVoice.ENGINE_HTTP ||
            voice.engineId.toLongOrNull()?.let(appDb.httpTTSDao::get) != null

    private fun coordinatorDefaultRoute(default: HttpTTS, engineIds: Set<String>? = null): SpeechEngineRoute {
        val type = ReadAloud.coordinatorDefaultEngineType
        val id = ReadAloud.coordinatorDefaultEngineId
        val httpExists = engineIds?.contains(id) ?: (id.toLongOrNull()?.let(appDb.httpTTSDao::get) != null)
        if (type == ReadAloudVoice.ENGINE_HTTP && !httpExists) {
            // 整体回落，不能携带已删除引擎的 toneID。
            return SpeechEngineRoute(ReadAloudVoice.ENGINE_HTTP, default.id.toString())
        }
        return SpeechEngineRoute(type, id, ReadAloud.coordinatorDefaultSpeakerId)
    }

    private fun voiceForCue(
        queue: ReadAloudPlaybackQueue,
        index: Int,
        default: HttpTTS,
        engineIds: Set<String>? = null,
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
            isExecutable = { voice ->
                voice.engineType != ReadAloudVoice.ENGINE_HTTP ||
                    (engineIds?.contains(voice.engineId) ?: isVoiceExecutable(voice))
            },
            supportedEngineTypes = setOf(
                ReadAloudVoice.ENGINE_HTTP,
                ReadAloudVoice.ENGINE_SYSTEM,
                ReadAloudVoice.ENGINE_CLOUD,
            ),
            defaultRoute = coordinatorDefaultRoute(default, engineIds),
        ).voice!!
    }

    /** 流式路径用的角色路由结果：带音色 id，合成时要传给源脚本。 */
    private fun httpVoiceForCue(index: Int, default: HttpTTS): ReadAloudVoice {
        return httpVoiceForCue(playbackQueue, index, default)
    }

    private fun httpVoiceForCue(
        queue: ReadAloudPlaybackQueue,
        index: Int,
        default: HttpTTS,
    ): ReadAloudVoice {
        val fallback = ReadAloudVoice(
            id = "runtime-http:${default.id}",
            engineType = ReadAloudVoice.ENGINE_HTTP,
            engineId = default.id.toString(),
            speakerId = "",
            displayName = default.name,
        )
        val cue = queue.cues.getOrNull(index) ?: return fallback
        return SpeechVoiceRouter.route(
            cue = cue,
            isExecutable = ::isVoiceExecutable,
            supportedEngineTypes = setOf(ReadAloudVoice.ENGINE_HTTP),
            defaultRoute = SpeechEngineRoute(
                engineType = ReadAloudVoice.ENGINE_HTTP,
                engineId = default.id.toString(),
            ),
        ).voice ?: fallback
    }

    private fun createSilentSound(fileName: String) {
        val part = createSpeakPartFile(fileName)
        part.outputStream().use { resources.openRawResource(R.raw.silent_sound).copyTo(it) }
        commitSpeakPart(fileName, part)
    }

    /**
     * 缓存命中判定：正式名存在且非空，下载区里那份也算命中。
     *
     * 只判存在会认下一半截文件——预合成的并发写就在这儿和播放抢同一个名字。
     * 也不认无声占位：那是合成失败留下的空壳，认了就等于把这一句永久静音。
     */
    private fun hasSpeakFile(name: String): Boolean {
        val file = speakFileForPlay(name)
        return file.length() > 0L && !ReadAloudAudioStore.isSilentPlaceholder(file)
    }

    /**
     * 播放取文件：下载区优先，其次缓存。
     *
     * 下载目录和缓存目录用**同一套文件名**，所以「播下载好的音频」只是换个查找顺序。
     * 写入路径不走这里——清缓存、重新合成永远只落在缓存目录，下载区不会被覆盖。
     */
    private fun speakFileForPlay(name: String): File {
        return ReadAloudAudioStore.downloadedFile(bookSession.book?.bookUrl, name)
            ?: getSpeakFileAsMd5(name)
    }

    private fun getSpeakFileAsMd5(name: String): File {
        return File("${ttsFolderPath}$name.mp3")
    }

    /** 合成中间名：写完改名成正式缓存，播放侧永远只会看到完整文件。 */
    private fun createSpeakPartFile(name: String): File {
        return FileUtils.createFileIfNotExist("${ttsFolderPath}$name.mp3.part")
    }

    /**
     * 把合成好的临时文件转成正式缓存。
     *
     * 长句合成得久，正好撞上「文件已存在但只写了一半」时 ExoPlayer 认不出容器，
     * 直接报 Source error 把整句跳过（错误类型 UnrecognizedInputFormatException）。
     */
    private fun commitSpeakPart(name: String, part: File): Boolean {
        if (part.length() <= 0L) {
            FileUtils.delete(part.absolutePath)
            return false
        }
        val target = getSpeakFileAsMd5(name)
        target.parentFile?.mkdirs()
        val committed = part.renameTo(target) ||
                runCatching { part.copyTo(target, overwrite = true) }.isSuccess
        FileUtils.delete(part.absolutePath)
        return committed && target.length() > 0L
    }

    /**
     * 引擎直接写文件的合成（系统 TTS 文件合成、云端）走同一个临时名。
     */
    private suspend fun synthesizeSpeakFile(
        fileName: String,
        synthesize: suspend (File) -> Boolean,
    ): Boolean {
        val part = createSpeakPartFile(fileName)
        return try {
            val synthesized = runCatching { synthesize(part) }.getOrElse {
                if (it is CancellationException) throw it
                false
            }
            currentCoroutineContext().ensureActive()
            synthesized && commitSpeakPart(fileName, part)
        } finally {
            part.delete()
        }
    }

    private suspend fun createSpeakFile(name: String, inputStream: InputStream) {
        inputStream.use { input ->
            val part = createSpeakPartFile(name)
            try {
                part.outputStream().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        out.write(buffer, 0, count)
                    }
                }
                currentCoroutineContext().ensureActive()
                commitSpeakPart(name, part)
            } finally {
                part.delete()
            }
        }
    }

    /** 「音频缓存保留时间」= 0：用户要即听即焚，缓存目录里不该长期留下任何一份音频。 */
    private val cacheBurnAfterRead: Boolean
        get() = readAloudSettings.audioCacheCleanTime <= 0

    /**
     * 即听即焚的残留清扫，只在服务起手时跑。
     *
     * 跨文件依赖：[removeCacheFile] 唯一的调用点在 [onDestroy]，进程被杀、划掉最近任务、崩溃都到不了
     * 那里，上一次会话留在 `httpTTS` 里的音频就没人管。这个语义下没有需要保护的正文，起手删干净即可；
     * 保留一段时间的模式不在这里动手，否则当前章的缓存会在开播前删掉，逼出整章重复合成。
     */
    private fun sweepBurnAfterReadLeftovers() {
        if (!cacheBurnAfterRead) return
        FileUtils.listDirsAndFiles(ttsFolderPath)?.forEach {
            FileUtils.delete(it.absolutePath)
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
        val titleMd5 = if (protectCurrentChapter) MD5Utils.md5Encode16(readerReadAloudChapter?.title.orEmpty()) else ""

        FileUtils.listDirsAndFiles(ttsFolderPath)?.forEach {
            // 无声占位文件不算缓存，任何时候都可以删
            val isSilentSound = ReadAloudAudioStore.isSilentPlaceholder(it)

            // 保留时间为 0 即听即焚；否则保护当前章节，只删过期的
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
        paragraphIntervalJob?.cancel()
        paragraphResumeJob?.cancel()
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
            } else if (exoPlayer.mediaItemCount == 0) {
                // 段间停顿或等文件时暂停过：没有媒体可供 ExoPlayer.play() 恢复。
                // 重新接续当前格，保留仍在进行的本章合成。
                playNextParagraph()
            } else {
                exoPlayer.play()
                upPlayPos()
            }
        }
    }

    private fun upPlayPos() {
        playIndexJob?.cancel()
        if (readerReadAloudChapter == null) return
        playIndexJob = lifecycleScope.launch {
            if (isChapterTitleAt(nowSpeak)) return@launch
            if (exoPlayer.duration <= 0) {
                upTtsProgress(readAloudNumber + paragraphStartPos + 1)
                return@launch
            }
            val startOffset = paragraphStartPos
            val speakTextLength = contentList[nowSpeak].length - startOffset
            if (speakTextLength <= 0) {
                return@launch
            }
            val sleep = exoPlayer.duration / speakTextLength
            val start = httpReadAloudParagraphOffset(
                contentList[nowSpeak].length, startOffset,
                exoPlayer.currentPosition, exoPlayer.duration,
            )
            upTtsProgress(readAloudNumber + start)
            for (i in start until contentList[nowSpeak].length) {
                val chapterPosition = readAloudNumber + i
                updateReadAloudProgressSnapshot(chapterPosition)
                if (moveToReadAloudPage(chapterPosition)) {
                    upTtsProgress(chapterPosition)
                }
                delay(sleep)
            }
        }
    }

    /**
     * 更新朗读速度
     * 全局语速走播放端 (ExoPlayer) 变速, 对已合成音频即时生效, 无需重新下载。
     */
    override fun upSpeechRate(reset: Boolean) {
        applyCueVoiceEffect(nowSpeak)
        upMediaMetadata()
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
                // 会话号是 Media3 在播放线程异步生成的：切条时可能还是 0，效果会静默丢掉，
                // 开播这一刻一定有了，所以在这里补挂一次（VoiceEffectAudio 记着当前预设）
                voiceEffectAudio.attach(exoPlayer.audioSessionId)
                // 这一刻才拿到本条音频的时长，提前排下一句的变声（换句回调那次可能还是未知）
                scheduleNextCueEffect(nowSpeak)
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
                    updateNextPos(naturalCompletion = true)
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    if (!pause && !isLastParagraph) {
                        AppLog.putDebug("HttpTTS段落开始停顿: $interval 毫秒")
                        paragraphIntervalJob?.cancel()
                        // 定时器直接挂在主线程：这一格只有 delay 一次，
                        // 绕到别的线程再起播要多付一次线程派发，而那正是被算进「段落间隔」里的
                        paragraphIntervalJob = execute(context = Main) {
                            delay(interval)
                            if (!pause) {
                                playNextParagraph()
                                AppLog.putDebug("HttpTTS段落停顿结束，恢复播放")
                            }
                        }
                    }
                } else {
                    updateNextPos(naturalCompletion = true)
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
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
            // 首条不走 AUTO 分支，也要在开播时套上角色的变声
            applyCueVoiceEffect(nowSpeak)
            scheduleCueSounds(nowSpeak)
            return
        }
        val auto = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        if (auto) {
            playErrorNo = 0
        }
        updateNextPos(naturalCompletion = auto)
        applyCueVoiceEffect(nowSpeak)
        // 音效按「刚开始播的这一格」排：nowSpeak 要等 updateNextPos 才是这一格。
        // 排在它前面用的是刚播完那一格的索引 + 新一格的时间轴，听感就是音效晚了一整句。
        scheduleCueSounds(nowSpeak)
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
                updateNextPos(naturalCompletion = true)
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

/** 音频仅合成定位点后的文字，其时间轴须映射到裁剪后的段落范围。 */
internal fun httpReadAloudParagraphOffset(
    paragraphLength: Int,
    startOffset: Int,
    positionMs: Long,
    durationMs: Long,
): Int {
    val offset = startOffset.coerceIn(0, paragraphLength.coerceAtLeast(0))
    if (durationMs <= 0) return offset
    val remaining = (paragraphLength - offset).coerceAtLeast(0)
    return offset + (remaining * (positionMs.coerceIn(0, durationMs).toDouble() / durationMs)).toInt()
}

/** 源级语速默认值, 对应 1 倍速, 与全局语速共用 0..80 的刻度 */
private const val DEFAULT_TTS_SPEED = 5

/**
 * 会话级效果（混响/带通）提前换句的量：这一层挂在**输出音频会话**上，设下去就作用在
 * 正在出声的那段信号上，所以提前量只能盖过一帧设置耗时。给大了等于把上一句尾巴上的
 * 金属感/混响提前摘掉（听感：句尾突然变声）。
 */
private const val EFFECT_SESSION_LEAD_MS = 150L

/**
 * 音高/语速提前换句的量：这一层是 Sonic 在**解码链**上生效，比出声位置提前一整段
 * AudioTrack 缓冲（几百毫秒到一秒），所以必须给足，否则上一句的音高会拖进下一句开头。
 * 调参方向：大了串到上一句，小了串到下一句。
 */
private const val EFFECT_PITCH_SWITCH_LEAD_MS = 900L

/** 只是转发给自家 Target 的标记，播放器不解释它。 */
private const val PITCH_MESSAGE_TYPE = 0x4C470001

/** 音效排期消息：由媒体时钟在单元里真正播到那个位置时投递。 */
private const val SOUND_MESSAGE_TYPE = 0x4C470002

/** 段落间隔续播时查一次缓存文件的间隔。 */
private const val SPEAK_FILE_POLL_MILLIS = 20L

/**
 * 段落间隔续播最多等这一句合成完。超过它就是合成任务已经不在这一句上了，
 * 回退到 [HttpReadAloudService.play] 完整重播，不能让朗读停死在这里。
 */
private const val SPEAK_FILE_WAIT_MILLIS = 60_000L
