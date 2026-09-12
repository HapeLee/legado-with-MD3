@file:Suppress("DEPRECATION")

package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.annotation.CallSuper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Status
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackCursor
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackInfo
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackQueue
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.domain.model.readaloud.SpeechAnalysisMode
import io.legado.app.domain.model.readaloud.SpeechPlanItem
import io.legado.app.domain.model.readaloud.resolveReadAloudStartPosition
import io.legado.app.domain.usecase.PrepareChapterSpeechPlanUseCase
import io.legado.app.feature.reader.core.readaloud.ReaderReadAloudChapter
import io.legado.app.help.MediaHelp
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.receiver.MediaButtonReceiver
import io.legado.app.service.BaseReadAloudService.Companion.speechDrivingNavigation
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.LogUtils
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.isNightMode
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import splitties.init.appCtx
import splitties.systemservices.audioManager
import splitties.systemservices.notificationManager
import splitties.systemservices.powerManager
import splitties.systemservices.telephonyManager
import splitties.systemservices.wifiManager

/**
 * 朗读服务
 */
abstract class BaseReadAloudService : BaseService(),
    AudioManager.OnAudioFocusChangeListener {

    companion object {
        @JvmStatic
        @Volatile
        var isRun = false
            private set

        @JvmStatic
        @Volatile
        var pause = true
            private set

        @Volatile
        private var stopRequested = false

        /** 让停止意图在服务异步销毁前即可被阅读器观察到，避免重排正文时重新启动朗读。 */
        @JvmStatic
        @Synchronized
        fun requestStop(): Boolean {
            if (!isRun || stopRequested) return false
            stopRequested = true
            isRun = false
            pause = true
            return true
        }

        @JvmStatic
        var timeMinute: Int = 0
            private set(value) {
                field = PlaybackTimer.normalize(value)
            }

        @JvmStatic
        @Volatile
        var currentChapterIndex: Int = -1
            private set

        @JvmStatic
        @Volatile
        var currentProgress: Int = 0
            private set

        /**
         * 朗读服务自身驱动页面移动（按页朗读、进度翻页、换章）时为 true；
         * 供 [io.legado.app.model.ReadBook] 区分「朗读驱动翻页」与「用户手动导航」，
         * 避免朗读推进被误判为手动脱离。
         */
        @JvmStatic
        @Volatile
        var speechDrivingNavigation: Boolean = false
            private set

        /** 在朗读驱动的页面移动/同步外侧调用，期间 [speechDrivingNavigation] 为 true。 */
        @JvmStatic
        fun <T> withSpeechNavigation(block: () -> T): T {
            speechDrivingNavigation = true
            return try {
                block()
            } finally {
                speechDrivingNavigation = false
            }
        }

        fun isPlay(): Boolean {
            return isRun && !pause
        }

        private const val TAG = "BaseReadAloudService"
        private const val ACTION_ADD_TIMER = "io.legado.app.action.ADD_READ_ALOUD_TIMER"
        private const val ACTION_OPEN_MEDIA_CONTROL_READER =
            "io.legado.app.action.OPEN_READ_ALOUD_MEDIA_CONTROL"

        /**
         * 语速 1.0x 时的每秒朗读字数估算值, 用于把字符进度换算成媒体播放器时间轴
         */
        private const val ESTIMATED_CHARS_PER_SECOND = 4f

        private const val READ_ALOUD_MEDIA_SESSION_ACTIONS =
            (PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                    or PlaybackStateCompat.ACTION_STOP
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT)

    }

    private val sessionStore: ReadAloudSessionStore by lazy {
        get(ReadAloudSessionStore::class.java)
    }

    private val useWakeLock = ReadConfig.readAloudWakeLock
    private val wakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:ReadAloudService")
            .apply {
                this.setReferenceCounted(false)
            }
    }
    private val wifiLock by lazy {
        @Suppress("DEPRECATION")
        wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "legado:AudioPlayService")
            ?.apply {
                setReferenceCounted(false)
            }
    }
    private val mFocusRequest: AudioFocusRequestCompat by lazy {
        MediaHelp.buildAudioFocusRequestCompat(this)
    }
    private val mediaSessionCompat: MediaSessionCompat by lazy {
        MediaSessionCompat(this, "readAloud")
    }
    private val phoneStateListener by lazy {
        ReadAloudPhoneStateListener()
    }
    internal var contentList = emptyList<String>()
    private var contentChapterPositions = emptyList<Int?>()
    /** Canonical, character-aware plan for the current chapter. Playback adoption is incremental. */
    internal var speechPlan = emptyList<SpeechPlanItem>()
    internal var playbackQueue = ReadAloudPlaybackQueue.Empty
    internal var playbackCursor: ReadAloudPlaybackCursor? = null
    internal var nowSpeak: Int = 0
    internal var readAloudNumber: Int = 0
    internal var readerReadAloudChapter: ReaderReadAloudChapter? = null
    internal var pageIndex = 0
    private var needResumeOnAudioFocusGain = false
    private var needResumeOnCallStateIdle = false
    private var registeredPhoneStateListener = false
    private var dsJob: Job? = null
    private var upNotificationJob: Job? = null
    private var upMediaProgressJob: Job? = null
    private var lastMediaSessionState = PlaybackStateCompat.STATE_NONE
    private var lastMediaSessionPositionMs = -1L
    private var lastMediaSessionUpdateElapsedMs = 0L
    @Volatile
    private var systemMediaCompatibilityEnabled =
        ReadConfig.systemMediaControlCompatibilityChange
    @Volatile
    private var androidMediaControlEnabled = ReadConfig.androidMediaControlEnabled
    private val finishChapterTimerLock = Any()
    private var finishChapterAtIndex = NO_FINISH_CHAPTER
    private var prepareReadAloudJob: Coroutine<*>? = null
    private var prepareReadAloudGeneration = 0L
    private var cover: Bitmap =
        BitmapFactory.decodeResource(appCtx.resources, R.drawable.ic_launcher)
    var pageChanged = false
    private var toLast = false
    var paragraphStartPos = 0
    var readAloudByPage = false
        private set

    /** 当前朗读段在章节语义文本中的绝对起始位置；页内切段不引入换行符，进度必须以它为准 */
    protected fun paragraphChapterPositionAt(index: Int): Int? =
        contentChapterPositions.getOrNull(index)

    protected fun isChapterTitleAt(index: Int): Boolean =
        index in contentChapterPositions.indices && contentChapterPositions[index] == null
    protected open val useSpeechPlaybackQueue: Boolean = false

    /**
     * newReadAloud 整体替换本章播放状态(队列/游标/段落)后回调,
     * 引擎实现用它作废旧章节发言的迟到回调
     */
    protected open fun onPlaybackStateReplaced() {}
    protected val hasSpeechPlaybackQueue: Boolean
        get() = useSpeechPlaybackQueue && !playbackQueue.isEmpty

    /** 当前朗读倍速, 用于把字符进度估算成媒体播放器时间轴 */
    protected open val currentSpeechRate: Float
        get() = 1f

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                pauseReadAloud()
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        stopRequested = false
        isRun = true
        pause = false
        // 新朗读会话默认跟随当前显示页（用户手动翻页脱离后由阅读界面负责恢复）
        sessionStore.restoreReadAloudFollow()
        observeLiveBus()
        initMediaSession()
        observeMediaControlSettings()
        initBroadcastReceiver()
        initPhoneStateListener()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        setTimer(ReadConfig.ttsTimer)
        if (timeMinute > 0) {
            toastOnUi("朗读定时 $timeMinute 分钟")
        }
        execute {
            ImageLoader
                .loadBitmap(this@BaseReadAloudService, ReadBook.book?.getDisplayCover())
                .submit()
                .get()
        }.onSuccess {
            if (it.width > 16 && it.height > 16) {
                cover = it
                upMediaMetadata()
                upReadAloudNotification()
            }
        }
    }

    fun observeLiveBus() {
        observeEvent<Bundle>(EventBus.READ_ALOUD_PLAY) {
            val play = it.getBoolean("play")
            val pageIndex = it.getInt("pageIndex")
            val startPos = it.getInt("startPos")
            val chapterPosition = it.getInt("chapterPosition", -1).takeIf { position -> position >= 0 }
            newReadAloud(play, pageIndex, startPos, chapterPosition)
        }
        lifecycleScope.launch {
            merge(
                AppConfigStore.observeBoolean(PreferKey.ignoreAudioFocus).drop(1),
                AppConfigStore.observeBoolean(PreferKey.pauseReadAloudWhilePhoneCalls).drop(1),
            ).collect {
                initPhoneStateListener()
            }
        }
    }

    override fun onDestroy() {
        ReadBook.upReadTime()
        super.onDestroy()
        prepareReadAloudGeneration++
        prepareReadAloudJob?.cancel()
        if (useWakeLock) {
            wakeLock.release()
            wifiLock?.release()
        }
        isRun = false
        pause = true
        sessionStore.stop()
        currentChapterIndex = -1
        currentProgress = 0
        abandonFocus()
        unregisterReceiver(broadcastReceiver)
        postEvent(EventBus.ALOUD_STATE, Status.STOP)
        notificationManager.cancel(NotificationId.ReadAloudService)
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        systemMediaCompatibilityEnabled = false
        androidMediaControlEnabled = false
        mediaSessionCompat.isActive = false
        mediaSessionCompat.release()
        ReadBook.uploadProgress()
        unregisterPhoneStateListener(phoneStateListener)
        if (!ReadBook.isUiActive) {
            ReadBook.stopAutoSaveSession()
            ReadBook.commitReadSession()
        }
        upNotificationJob?.invokeOnCompletion {
            notificationManager.cancel(NotificationId.ReadAloudService)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.play -> newReadAloud(
                intent.getBooleanExtra("play", true),
                intent.getIntExtra("pageIndex", ReadBook.durPageIndex),
                intent.getIntExtra("startPos", 0),
                intent.getIntExtra("chapterPosition", -1).takeIf { it >= 0 },
            )

            IntentAction.pause -> pauseReadAloud()
            IntentAction.resume -> resumeReadAloud()
            IntentAction.upTtsSpeechRate -> upSpeechRate(true)
            IntentAction.syncReadAloudLayout -> syncReaderLayout()
            IntentAction.prevParagraph -> prevP()
            IntentAction.nextParagraph -> nextP()
            IntentAction.prev -> prevChapter()
            IntentAction.next -> nextChapter()
            IntentAction.addTimer -> addTimer()
            IntentAction.setTimer -> setTimer(intent.getIntExtra("minute", 0))
            IntentAction.stop -> stopReadAloudService()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun stopReadAloudService() {
        requestStop()
        stopSelf()
    }

    private fun newReadAloud(
        play: Boolean,
        requestedPageIndex: Int,
        requestedStartPos: Int,
        requestedChapterPosition: Int?,
    ) {
        // 每次"从指定位置开始朗读"都是新会话：恢复页面跟随朗读（手动脱离后点"从此处朗读"等）
        sessionStore.restoreReadAloudFollow()
        clearFinishChapterTimerIfChapterChanged(ReadBook.durChapterIndex)
        val generation = ++prepareReadAloudGeneration
        prepareReadAloudJob?.cancel()
        prepareReadAloudJob = execute(executeContext = IO) {
            val input = ReadBook.readerChapterInputWindow.current ?: return@execute
            val pagination = ReadBook.readerPagination(input.chapter.index) ?: run {
                AppLog.put("启动朗读失败：章节分页未完成 chapterIndex=${input.chapter.index}")
                return@execute
            }
            val preparedChapter = ReaderReadAloudChapter.create(
                chapterIndex = input.chapter.index,
                title = input.displayTitle,
                semanticContent = input.source.semanticContent,
                pageStarts = pagination.pageStarts,
            )
            val start = resolveReadAloudStartPosition(
                requestedPageIndex = requestedPageIndex,
                requestedOffsetInPage = requestedStartPos,
                requestedChapterPosition = requestedChapterPosition,
                pageIndexAt = preparedChapter::pageIndexAt,
                pageStart = preparedChapter::pageStart,
            )
            val pageIndex = start.pageIndex
            val startPos = start.offsetInPage
            val preparedReadAloudByPage = ReadConfig.readAloudByPage
            var preparedReadAloudNumber = preparedChapter.pageStart(pageIndex) + startPos
            val startsAtChapterBeginning = preparedReadAloudNumber == 0
            val preparedParagraphs = preparedChapter.paragraphs(preparedReadAloudByPage)
            var preparedContentList = preparedParagraphs
                .map { it.text.replace(Regex("[袮祢꧁\uFFFC]"), " ") }
            var preparedContentChapterPositions: List<Int?> =
                preparedParagraphs.map { it.chapterPosition }
            val preparedSpeechPlan = buildSpeechPlan(
                bookUrl = ReadBook.book?.bookUrl.orEmpty(),
                chapterIndex = ReadBook.durChapterIndex,
                paragraphs = preparedChapter.canonicalSpeechParagraphs(),
            )
            if (generation != prepareReadAloudGeneration) return@execute
            var preparedPlaybackQueue = runCatching {
                ReadAloudPlaybackQueue.from(preparedSpeechPlan)
            }.onFailure {
                AppLog.put("创建多角色播放队列失败，使用原朗读方式\n${it.localizedMessage}", it)
            }.getOrDefault(ReadAloudPlaybackQueue.Empty)
            var preparedPlaybackCursor = preparedPlaybackQueue.cursorAt(preparedReadAloudNumber)
            var pos = startPos
            val usePreparedPlaybackQueue = useSpeechPlaybackQueue && !preparedPlaybackQueue.isEmpty
            var preparedNowSpeak = preparedChapter.paragraphIndexAtOrAfter(
                preparedReadAloudNumber + 1,
                preparedReadAloudByPage,
            )
            if (!usePreparedPlaybackQueue && preparedNowSpeak !in preparedContentList.indices) {
                AppLog.put(
                    "启动朗读失败：无法定位朗读段落 position=$preparedReadAloudNumber " +
                        "pageIndex=$pageIndex startPos=$startPos"
                )
                return@execute
            }
            val moveToLast = toLast
            if (moveToLast) {
                preparedReadAloudNumber = preparedParagraphs.last().chapterPosition
                preparedNowSpeak = preparedContentList.lastIndex
                pos = 0
            }
            // startPos 是页内偏移，需换算为目标朗读段内的偏移
            if (!usePreparedPlaybackQueue && !moveToLast &&
                preparedNowSpeak in preparedParagraphs.indices
            ) {
                val target = preparedParagraphs[preparedNowSpeak]
                pos = (preparedReadAloudNumber - target.chapterPosition)
                    .coerceIn(0, target.text.length)
            }
            var preparedParagraphStartPos = pos
            if (usePreparedPlaybackQueue) {
                preparedPlaybackQueue.cursorAt(preparedReadAloudNumber)?.let { cursor ->
                    preparedPlaybackCursor = cursor
                    preparedContentList = preparedPlaybackQueue.cues.map { it.text }
                    preparedNowSpeak = cursor.cueIndex
                    preparedParagraphStartPos = cursor.offset
                    preparedReadAloudNumber = preparedPlaybackQueue.cues[cursor.cueIndex].chapterStart
                }
            }
            val shouldReadChapterTitle = !moveToLast && startsAtChapterBeginning &&
                    preparedChapter.title.isNotBlank()
            if (shouldReadChapterTitle) {
                if (usePreparedPlaybackQueue) {
                    preparedPlaybackQueue =
                        preparedPlaybackQueue.withChapterTitle(preparedChapter.title)
                    preparedPlaybackCursor = ReadAloudPlaybackCursor(0, 0)
                    preparedContentList = preparedPlaybackQueue.cues.map { it.text }
                    preparedContentChapterPositions = preparedPlaybackQueue.cues.map { cue ->
                        cue.chapterStart.takeUnless { cue.isChapterTitle }
                    }
                } else {
                    preparedContentList = listOf(preparedChapter.title.trim()) + preparedContentList
                    preparedContentChapterPositions = listOf(null) + preparedContentChapterPositions
                }
                preparedNowSpeak = 0
                preparedParagraphStartPos = 0
                preparedReadAloudNumber = 0
            } else if (usePreparedPlaybackQueue) {
                preparedContentChapterPositions = preparedPlaybackQueue.cues.map { it.chapterStart }
            }
            if (generation != prepareReadAloudGeneration) return@execute
            this@BaseReadAloudService.pageIndex = pageIndex
            readerReadAloudChapter = preparedChapter
            readAloudByPage = preparedReadAloudByPage
            contentList = preparedContentList
            contentChapterPositions = preparedContentChapterPositions
            speechPlan = preparedSpeechPlan
            playbackQueue = preparedPlaybackQueue
            playbackCursor = preparedPlaybackCursor
            nowSpeak = preparedNowSpeak
            readAloudNumber = preparedReadAloudNumber
            paragraphStartPos = preparedParagraphStartPos
            updateReadAloudProgressSnapshot(preparedReadAloudNumber + 1)
            onPlaybackStateReplaced()
            if (moveToLast) toLast = false
            preparedPlaybackCursor?.takeIf { hasSpeechPlaybackQueue }?.let(::publishPlaybackInfo)
            launch(Main) {
                if (generation != prepareReadAloudGeneration) return@launch
                upMediaMetadata()
                if (play) play() else pageChanged = true
            }
        }.onError {
            AppLog.put("启动朗读出错\n${it.localizedMessage}", it, true)
        }
    }

    protected suspend fun buildSpeechPlan(
        bookUrl: String,
        chapterIndex: Int,
        paragraphs: List<CanonicalSpeechParagraph>,
    ): List<SpeechPlanItem> {
        if (bookUrl.isEmpty() || !ReadConfig.useMultiSpeaker) return emptyList()
        val prepareSpeechPlan: PrepareChapterSpeechPlanUseCase =
            get(PrepareChapterSpeechPlanUseCase::class.java)
        return runCatching {
            prepareSpeechPlan(
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                paragraphs = paragraphs,
                analysisMode = SpeechAnalysisMode.fromStorage(ReadConfig.speechAnalysisMode),
                useMultiSpeaker = ReadConfig.useMultiSpeaker,
            )
        }.onFailure {
            AppLog.put("生成多角色朗读计划失败，使用原朗读方式\n${it.localizedMessage}", it)
        }.getOrDefault(emptyList())
    }

    @SuppressLint("WakelockTimeout")
    open fun play() {
        if (stopRequested) return
        if (useWakeLock) {
            wakeLock.acquire()
            wifiLock?.acquire()
        }
        isRun = true
        pause = false
        needResumeOnAudioFocusGain = false
        needResumeOnCallStateIdle = false
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        upMediaProgress()
        upReadAloudNotification()
        sessionStore.setStatus(ReadAloudSessionStatus.Playing)
        postEvent(EventBus.ALOUD_STATE, Status.PLAY)
        if (!ReadBook.isAutoSaveSessionRunning) {
            ReadBook.startReadSession()
        }
    }

    abstract fun playStop()

    @CallSuper
    open fun pauseReadAloud(abandonFocus: Boolean = true) {
        ReadBook.upReadTime()
        if (useWakeLock) {
            wakeLock.release()
            wifiLock?.release()
        }
        pause = true
        if (abandonFocus) {
            abandonFocus()
        }
        upMediaProgressJob?.cancel()
        upReadAloudNotification()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED)
        sessionStore.setStatus(ReadAloudSessionStatus.Paused)
        postEvent(EventBus.ALOUD_STATE, Status.PAUSE)
        ReadBook.uploadProgress()
        doDs()
        if (!ReadBook.isUiActive) {
            ReadBook.stopAutoSaveSession()
            ReadBook.commitReadSession()
        }
    }

    @SuppressLint("WakelockTimeout")
    @CallSuper
    open fun resumeReadAloud() {
        resumeReadAloudInternal()
    }

    private fun resumeReadAloudInternal() {
        pause = false
        needResumeOnAudioFocusGain = false
        needResumeOnCallStateIdle = false
        upReadAloudNotification()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        upMediaProgress()
        sessionStore.setStatus(ReadAloudSessionStatus.Playing)
        postEvent(EventBus.ALOUD_STATE, Status.PLAY)
        if (!ReadBook.isAutoSaveSessionRunning) {
            ReadBook.startReadSession()
        }
    }

    abstract fun upSpeechRate(reset: Boolean = false)

    fun upTtsProgress(progress: Int) {
        ReadBook.upReadTime()
        val chapterPosition = progress.coerceAtLeast(0)
        if (hasSpeechPlaybackQueue) {
            playbackQueue.cursorAt(chapterPosition)?.let(::publishPlaybackInfo)
        } else {
            val chapterLength = readerReadAloudChapter?.chapterLength ?: chapterPosition
            sessionStore.updatePlayback(
                ReadAloudPlaybackInfo(
                    chapterPosition = chapterPosition,
                    chapterLength = chapterLength.coerceAtLeast(1),
                    text = contentList.getOrNull(nowSpeak).orEmpty(),
                )
            )
            refreshMediaSessionPlaybackState()
        }
        updateReadAloudProgressSnapshot(progress)
        postEvent(EventBus.TTS_PROGRESS, progress)
    }

    protected fun updateReadAloudProgressSnapshot(progress: Int) {
        currentChapterIndex = readerReadAloudChapter?.chapterIndex ?: currentChapterIndex
        val newProgress = progress.coerceAtLeast(0)
        if (newProgress < currentProgress) {
            // 进度回退(上一段/上一章等), 重置媒体进度锚点, 允许进度条跟随回退
            lastMediaSessionPositionMs = -1L
        }
        currentProgress = newProgress
    }

    protected fun moveToReadAloudPage(chapterPosition: Int): Boolean {
        val chapter = readerReadAloudChapter ?: return false
        val targetPageIndex = findReadAloudPageIndex(
            currentPageIndex = pageIndex,
            chapterPosition = chapterPosition,
            pageCount = chapter.pageCount,
            pageStart = chapter::pageStart,
        )
        if (targetPageIndex == pageIndex) return false
        // 页面脱离朗读位置（用户手动翻页）后不再驱动可见页面，仅推进朗读内部页游标
        val follow = sessionStore.state.value.followReadAloudPosition
        repeat(targetPageIndex - pageIndex) {
            pageIndex++
            if (follow) {
                withSpeechNavigation { ReadBook.moveToNextPage() }
            }
        }
        return true
    }

    private fun syncReaderLayout() {
        val input = ReadBook.readerChapterInputWindow.current ?: return
        val serviceChapter = readerReadAloudChapter ?: return
        if (input.chapter.index != serviceChapter.chapterIndex) return
        val pagination = ReadBook.readerPagination(input.chapter.index) ?: return
        val latestChapter = ReaderReadAloudChapter.create(
            chapterIndex = input.chapter.index,
            title = input.displayTitle,
            semanticContent = input.source.semanticContent,
            pageStarts = pagination.pageStarts,
        )
        val latestPageIndex = latestChapter.pageIndexAt((currentProgress - 1).coerceAtLeast(0))
        readerReadAloudChapter = latestChapter
        pageIndex = latestPageIndex
        if (sessionStore.state.value.followReadAloudPosition) {
            ReadBook.syncReadAloudPage(
                chapterIndex = latestChapter.chapterIndex,
                chapterPos = latestChapter.pageStart(latestPageIndex),
            )
        }
        upTtsProgress(currentProgress)
    }

    private fun prevP() {
        ReadBook.upReadTime()
        if (hasSpeechPlaybackQueue) {
            val current = playbackCursor ?: ReadAloudPlaybackCursor(nowSpeak, paragraphStartPos)
            playbackQueue.previous(current)?.let { previous ->
                playStop()
                moveToPlaybackCursor(previous)
                play()
            } ?: run {
                toLast = true
                withSpeechNavigation { ReadBook.moveToPrevChapter(true) }
            }
            return
        }
        if (nowSpeak > 0) {
            playStop()
            var foundPreviousReadableParagraph = false
            do {
                nowSpeak--
                readAloudNumber -= contentList[nowSpeak].length + 1 + paragraphStartPos
                paragraphStartPos = 0
                foundPreviousReadableParagraph =
                    !contentList[nowSpeak].matches(AppPattern.notReadAloudRegex)
            } while (!foundPreviousReadableParagraph && nowSpeak > 0)
            if (!foundPreviousReadableParagraph) {
                toLast = true
                withSpeechNavigation { ReadBook.moveToPrevChapter(true) }
                return
            }
            readerReadAloudChapter?.let {
                readAloudNumber = paragraphChapterPositionAt(nowSpeak) ?: 0
                if (readAloudNumber < it.pageStart(pageIndex)) {
                    pageIndex--
                    withSpeechNavigation { ReadBook.moveToPrevPage() }
                }
            }
            upTtsProgress(readAloudNumber + 1)
            upMediaMetadata(showContent = true)
            play()
        } else {
            toLast = true
            withSpeechNavigation { ReadBook.moveToPrevChapter(true) }
        }
    }

    private fun nextP() {
        if (hasSpeechPlaybackQueue) {
            val current = playbackCursor ?: ReadAloudPlaybackCursor(nowSpeak, paragraphStartPos)
            playbackQueue.next(current)?.let { next ->
                playStop()
                moveToPlaybackCursor(next)
                play()
            } ?: nextChapter()
            return
        }
        if (nowSpeak < contentList.size - 1) {
            playStop()
            readAloudNumber += contentList[nowSpeak].length.plus(1) - paragraphStartPos
            paragraphStartPos = 0
            nowSpeak++
            readerReadAloudChapter?.let {
                readAloudNumber = paragraphChapterPositionAt(nowSpeak) ?: 0
                if (pageIndex + 1 < it.pageCount
                    && readAloudNumber >= it.pageStart(pageIndex + 1)
                ) {
                    pageIndex++
                    withSpeechNavigation { ReadBook.moveToNextPage() }
                }
            }
            upTtsProgress(readAloudNumber + 1)
            upMediaMetadata(showContent = true)
            play()
        } else {
            nextChapter()
        }
    }

    protected fun moveToPlaybackCursor(cursor: ReadAloudPlaybackCursor) {
        val cue = playbackQueue.cues[cursor.cueIndex]
        playbackCursor = cursor
        nowSpeak = cursor.cueIndex
        paragraphStartPos = cursor.offset
        readAloudNumber = cue.chapterStart
        publishPlaybackInfo(cursor)
        readerReadAloudChapter?.let { chapter ->
            val targetPosition = cue.chapterStart + cursor.offset
            val targetPage = chapter.pageIndexAt(targetPosition)
            while (pageIndex < targetPage) {
                pageIndex++
                withSpeechNavigation { ReadBook.moveToNextPage() }
            }
            while (pageIndex > targetPage) {
                pageIndex--
                withSpeechNavigation { ReadBook.moveToPrevPage() }
            }
            if (cue.isChapterTitle) {
                updateReadAloudProgressSnapshot(0)
            } else {
                upTtsProgress(targetPosition + 1)
            }
        }
        upMediaMetadata(showContent = true)
    }

    private fun publishPlaybackInfo(cursor: ReadAloudPlaybackCursor) {
        val cue = playbackQueue.cues.getOrNull(cursor.cueIndex) ?: return
        sessionStore.updatePlayback(ReadAloudPlaybackInfo(
            chapterPosition = cue.chapterStart + cursor.offset,
            chapterLength = playbackQueue.cues.lastOrNull()?.chapterEnd ?: cue.chapterEnd,
            text = cue.text,
            engineName = cue.voice?.displayName.orEmpty(),
            characterName = speechPlan.getOrNull(
                cursor.cueIndex - playbackQueue.leadingTitleCueCount
            )?.segment?.characterName.orEmpty(),
            roleType = cue.roleType,
        ))
        refreshMediaSessionPlaybackState()
    }

    private fun setTimer(minute: Int) {
        clearFinishChapterTimer()
        timeMinute = minute
        doDs()
    }

    private fun addTimer() {
        clearFinishChapterTimer()
        timeMinute = PlaybackTimer.addIncrement(timeMinute)
        doDs()
    }

    /**
     * 定时
     */
    @Synchronized
    private fun doDs() {
        sessionStore.updateTimer(timeMinute)
        postEvent(EventBus.READ_ALOUD_DS, timeMinute)
        upReadAloudNotification()
        dsJob?.cancel()
        dsJob = null
        if (timeMinute == PlaybackTimer.MIN_MINUTES) return
        dsJob = lifecycleScope.launch {
            while (isActive) {
                delay(60000)
                if (timeMinute == PlaybackTimer.MIN_MINUTES) break
                if (!pause) {
                    val finishChapter = synchronized(finishChapterTimerLock) {
                        timeMinute--
                        if (timeMinute == PlaybackTimer.MIN_MINUTES &&
                            ReadConfig.finishCurrentChapterAfterTimer
                        ) {
                            // 臂标锚定正在朗读的章节：脱离（手动翻到后章）时 durChapterIndex
                            // 已领先，"读完本章"指正在读的这章，不是页面停留的章节
                            finishChapterAtIndex = BaseReadAloudService.currentChapterIndex
                                .takeIf { it >= 0 }
                                ?: ReadBook.durChapterIndex
                            finishChapterAtIndex != NO_FINISH_CHAPTER
                        } else {
                            false
                        }
                    }
                    if (timeMinute == PlaybackTimer.MIN_MINUTES) {
                        if (!finishChapter) {
                            ReadAloud.stop(this@BaseReadAloudService)
                        }
                        sessionStore.updateTimer(timeMinute)
                        postEvent(EventBus.READ_ALOUD_DS, timeMinute)
                        upReadAloudNotification()
                        break
                    }
                }
                sessionStore.updateTimer(timeMinute)
                postEvent(EventBus.READ_ALOUD_DS, timeMinute)
                upReadAloudNotification()
            }
        }
    }

    /**
     * 请求音频焦点
     * @return 音频焦点
     */
    fun requestFocus(): Boolean {
        if (ReadConfig.ignoreAudioFocus) {
            return true
        }
        val requestFocus = MediaHelp.requestFocus(mFocusRequest)
        if (!requestFocus) {
            pauseReadAloud(false)
            toastOnUi("未获取到音频焦点")
        }
        return requestFocus
    }

    /**
     * 放弃音频焦点
     */
    private fun abandonFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, mFocusRequest)
    }

    /**
     * 更新媒体状态
     */
    private fun upMediaSessionPlaybackState(state: Int) {
        val now = SystemClock.elapsedRealtime()
        val position = nextMediaSessionPositionMs(
            state = state,
            lastState = lastMediaSessionState,
            estimate = mediaProgressPositionMs(),
            lastPosition = lastMediaSessionPositionMs,
            nowElapsedRealtime = now,
            lastUpdateElapsedRealtime = lastMediaSessionUpdateElapsedMs,
        )
        if (state == lastMediaSessionState && position == lastMediaSessionPositionMs) {
            return
        }
        lastMediaSessionState = state
        lastMediaSessionPositionMs = position
        lastMediaSessionUpdateElapsedMs = now
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                if (androidMediaControlEnabled) {
                    READ_ALOUD_MEDIA_SESSION_ACTIONS
                } else {
                    // 老的"使用媒体通道"路径保持原有行为，避免锁屏媒体控件功能变化。
                    MediaHelp.MEDIA_SESSION_ACTIONS
                }
            )
            // TTS 没有真实时间轴, 位置按字符进度与语速估算为毫秒时间
            .setState(
                state,
                position,
                if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f,
            )
        if (androidMediaControlEnabled) {
            playbackState.addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_ADD_TIMER,
                    getString(R.string.set_timer),
                    R.drawable.ic_time_add_24dp,
                ).build()
            )
        }
        mediaSessionCompat.setPlaybackState(playbackState.build())
    }

    private fun refreshMediaSessionPlaybackState() {
        upMediaSessionPlaybackState(
            if (pause) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING
        )
    }

    /**
     * 播放时每秒推送一次媒体进度, 系统媒体播放器进度条随朗读推进
     */
    private fun upMediaProgress() {
        upMediaProgressJob?.cancel()
        upMediaProgressJob = lifecycleScope.launch {
            while (isActive) {
                refreshMediaSessionPlaybackState()
                delay(1000)
            }
        }
    }

    private fun mediaProgressPositionMs(): Long {
        val chapterLength = currentChapterLength()
        val charsPerSecond = currentCharsPerSecond()
        if (chapterLength <= 0 || charsPerSecond <= 0f) return 0L
        return estimatedReadAloudTimeMs(
            currentProgress.coerceIn(0, chapterLength),
            charsPerSecond,
        )
    }

    /**
     * 当前章节按语速估算的朗读总时长, TTS 没有真实时间轴
     */
    private fun mediaProgressDurationMs(): Long {
        val chapterLength = currentChapterLength()
        val charsPerSecond = currentCharsPerSecond()
        if (chapterLength <= 0 || charsPerSecond <= 0f) return 0L
        return estimatedReadAloudTimeMs(chapterLength, charsPerSecond)
    }

    private fun currentChapterLength(): Int = readerReadAloudChapter?.chapterLength ?: 0

    private fun currentCharsPerSecond(): Float =
        (ESTIMATED_CHARS_PER_SECOND * currentSpeechRate).coerceAtLeast(0.1f)

    /**
     * 更新媒体元数据, 用于车机蓝牙显示
     * @param showContent 是否显示当前朗读内容作为歌词
     */
    internal fun upMediaMetadata(showContent: Boolean = false) {
        val currentContent = if (showContent && nowSpeak in contentList.indices) {
            contentList[nowSpeak]
        } else {
            null
        }
        val metadata = MediaMetadataCompat.Builder()
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, cover)
            .putText(MediaMetadataCompat.METADATA_KEY_TITLE, ReadBook.book?.name ?: "")
            .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, readerReadAloudChapter?.title ?: "")
            .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, ReadBook.book?.author ?: "")
            .putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, currentContent ?: "")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaProgressDurationMs())
            .build()
        mediaSessionCompat.setMetadata(metadata)
    }

    /**
     * 初始化MediaSession, 注册多媒体按钮
     */
    @SuppressLint("UnspecifiedImmutableFlag")
    private fun initMediaSession() {
        mediaSessionCompat.setSessionActivity(readAloudMediaControlActivityPendingIntent())
        mediaSessionCompat.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                resumeReadAloud()
            }

            override fun onPause() {
                pauseReadAloud()
            }

            override fun onSkipToNext() {
                if (ReadConfig.mediaButtonPerNext) {
                    nextChapter()
                } else {
                    nextP()
                }
            }

            override fun onSkipToPrevious() {
                if (ReadConfig.mediaButtonPerNext) {
                    prevChapter()
                } else {
                    prevP()
                }
            }

            override fun onStop() {
                stopReadAloudService()
            }

            override fun onCustomAction(action: String, extras: Bundle?) {
                if (action == ACTION_ADD_TIMER) addTimer()
            }

            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                return MediaButtonReceiver.handleIntent(
                    this@BaseReadAloudService, mediaButtonEvent
                )
            }
        })
        updateMediaSessionActivation()
    }

    private fun observeMediaControlSettings() {
        lifecycleScope.launch {
            AppConfigStore.observeBoolean(PreferKey.readAloudAndroidMediaControl).collect {
                setAndroidMediaControlEnabled(it == true)
            }
        }
        lifecycleScope.launch {
            AppConfigStore.observeBoolean(
                PreferKey.systemMediaControlCompatibilityChange
            ).collect {
                setSystemMediaCompatibilityEnabled(it ?: true)
            }
        }
        lifecycleScope.launch {
            AppConfigStore.observeBoolean(PreferKey.mediaButtonPerNext)
                .drop(1)
                .collect { upReadAloudNotification() }
        }
        lifecycleScope.launch {
            AppConfigStore.observeBoolean(PreferKey.finishCurrentChapterAfterTimer)
                .drop(1)
                .collect { enabled ->
                    if (enabled != true) clearFinishChapterTimer()
                }
        }
    }

    private fun setAndroidMediaControlEnabled(enabled: Boolean) {
        val changed = androidMediaControlEnabled != enabled
        androidMediaControlEnabled = enabled
        updateMediaSessionActivation()
        if (enabled) {
            upMediaMetadata()
        }
        // 强制重新推送, 让新的媒体按键集合生效
        lastMediaSessionState = PlaybackStateCompat.STATE_NONE
        refreshMediaSessionPlaybackState()
        if (changed || enabled) {
            upReadAloudNotification()
        }
    }

    private fun setSystemMediaCompatibilityEnabled(enabled: Boolean) {
        if (systemMediaCompatibilityEnabled == enabled) {
            updateMediaSessionActivation()
            return
        }
        systemMediaCompatibilityEnabled = enabled
        updateMediaSessionActivation()
        refreshMediaSessionPlaybackState()
        upReadAloudNotification()
    }

    private fun updateMediaSessionActivation() {
        mediaSessionCompat.isActive =
            systemMediaCompatibilityEnabled || androidMediaControlEnabled
    }

    /**
     * 注册多媒体按钮监听
     */
    private fun initBroadcastReceiver() {
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(broadcastReceiver, intentFilter)
    }

    /**
     * 音频焦点变化
     */
    override fun onAudioFocusChange(focusChange: Int) {
        if (ReadConfig.ignoreAudioFocus) {
            AppLog.put("忽略音频焦点处理(TTS)")
            return
        }
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (needResumeOnAudioFocusGain) {
                    AppLog.put("音频焦点获得,继续朗读")
                    resumeReadAloud()
                } else {
                    AppLog.put("音频焦点获得")
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                AppLog.put("音频焦点丢失,暂停朗读")
                pauseReadAloud()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                AppLog.put("音频焦点暂时丢失并会很快再次获得,暂停朗读")
                if (!pause) {
                    needResumeOnAudioFocusGain = true
                    pauseReadAloud(false)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 短暂丢失焦点，这种情况是被其他应用申请了短暂的焦点希望其他声音能压低音量（或者关闭声音）凸显这个声音（比如短信提示音），
                AppLog.put("音频焦点短暂丢失,不做处理")
            }
        }
    }

    private fun upReadAloudNotification() {
        upNotificationJob = lifecycleScope.launch(Main.immediate) {
            try {
                val notification = createForegroundNotification()
                notificationManager.notify(NotificationId.ReadAloudService, notification.build())
            } catch (e: Exception) {
                AppLog.put("创建朗读通知出错,${e.localizedMessage}", e, true)
            }
        }
    }

    private fun choiceMediaStyle(): androidx.media.app.NotificationCompat.MediaStyle {
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(1, 2, 4)
        if (systemMediaCompatibilityEnabled && !androidMediaControlEnabled) {
            mediaStyle.setMediaSession(mediaSessionCompat.sessionToken)
        }
        return mediaStyle
    }

    private fun createNotification(): NotificationCompat.Builder {
        var nTitle: String = when {
            pause -> getString(R.string.read_aloud_pause)
            timeMinute > 0 -> getString(
                R.string.read_aloud_timer,
                timeMinute
            )

            else -> getString(R.string.read_aloud_t)
        }
        nTitle += ": ${ReadBook.book?.name}"
        var nSubtitle = readerReadAloudChapter?.title
        if (nSubtitle.isNullOrBlank())
            nSubtitle = getString(R.string.read_aloud_s)
        val builder = NotificationCompat
            .Builder(this, AppConst.channelIdReadAloud)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setSubText(getString(R.string.read_aloud))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(nTitle)
            .setContentText(nSubtitle)
            .setContentIntent(readAloudActivityPendingIntent())
            .setVibrate(null)
            .setSound(null)
            .setLights(0, 0, 0)
        builder.setLargeIcon(cover)
        // 按钮定义：上一章、播放、停止、下一章、定时
        builder.addAction(
            R.drawable.ic_skip_previous,
            getString(R.string.previous_chapter),
            aloudServicePendingIntent(IntentAction.prev)
        )
        if (pause) {
            builder.addAction(
                R.drawable.ic_play,
                getString(R.string.resume),
                aloudServicePendingIntent(IntentAction.resume)
            )
        } else {
            builder.addAction(
                R.drawable.ic_pause,
                getString(R.string.pause),
                aloudServicePendingIntent(IntentAction.pause)
            )
        }
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.stop),
            aloudServicePendingIntent(IntentAction.stop)
        )
        builder.addAction(
            R.drawable.ic_skip_next,
            getString(R.string.next_chapter),
            aloudServicePendingIntent(IntentAction.next)
        )
        builder.addAction(
            R.drawable.ic_time_add_24dp,
            getString(R.string.set_timer),
            aloudServicePendingIntent(IntentAction.addTimer)
        )
        builder.setStyle(choiceMediaStyle())
        return builder
    }

    private fun createForegroundNotification(): NotificationCompat.Builder =
        if (androidMediaControlEnabled) {
            createAndroidMediaControlNotification()
        } else {
            createNotification()
        }

    private fun createAndroidMediaControlNotification(): NotificationCompat.Builder {
        val navigateByChapter = ReadConfig.mediaButtonPerNext
        val previousAction = if (navigateByChapter) {
            IntentAction.prev
        } else {
            IntentAction.prevParagraph
        }
        val nextAction = if (navigateByChapter) IntentAction.next else IntentAction.nextParagraph
        val previousLabel = getString(
            if (navigateByChapter) R.string.previous_chapter else R.string.prev_sentence
        )
        val nextLabel = getString(
            if (navigateByChapter) R.string.next_chapter else R.string.next_sentence
        )
        val chapterTitle = readerReadAloudChapter?.title
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.read_aloud_s)
        return NotificationCompat.Builder(this, AppConst.channelIdReadAloud)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setSubText(ReadBook.book?.author ?: getString(R.string.read_aloud))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(ReadBook.book?.name ?: getString(R.string.read_aloud))
            .setContentText(chapterTitle)
            .setContentIntent(readAloudMediaControlActivityPendingIntent())
            .setLargeIcon(cover)
            .setVibrate(null)
            .setSound(null)
            .setLights(0, 0, 0)
            .apply {
                if (!resources.configuration.isNightMode) {
                    // Some OEM media controls derive a white progress tint from light artwork.
                    // Keep the system surface and provide a contrasting accent in light mode.
                    setColor(Color.BLACK)
                    setColorized(false)
                }
            }
            .addAction(
                R.drawable.ic_skip_previous,
                previousLabel,
                aloudServicePendingIntent(previousAction),
            )
            .addAction(
                if (pause) R.drawable.ic_play else R.drawable.ic_pause,
                getString(if (pause) R.string.resume else R.string.pause),
                aloudServicePendingIntent(
                    if (pause) IntentAction.resume else IntentAction.pause
                ),
            )
            .addAction(
                R.drawable.ic_skip_next,
                nextLabel,
                aloudServicePendingIntent(nextAction),
            )
            .addAction(
                R.drawable.ic_time_add_24dp,
                getString(R.string.set_timer),
                aloudServicePendingIntent(IntentAction.addTimer),
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSessionCompat.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
    }

    private fun readAloudActivityPendingIntent(): PendingIntent? = activityPendingIntent(
        MainActivity.createReadBookIntent(this, readAloud = true),
        "activity",
    )

    private fun readAloudMediaControlActivityPendingIntent(): PendingIntent? = activityPendingIntent(
        MainActivity.createReadBookMediaControlIntent(this),
        ACTION_OPEN_MEDIA_CONTROL_READER,
    )

    /**
     * 更新通知
     */
    override fun startForegroundNotification() {
        try {
            val notification = createForegroundNotification()
            startForeground(NotificationId.ReadAloudService, notification.build())
        } catch (e: Exception) {
            AppLog.put("创建朗读通知出错,${e.localizedMessage}", e, true)
            //创建通知出错不结束服务就会崩溃,服务必须绑定通知
            stopReadAloudService()
        }
    }

    abstract fun aloudServicePendingIntent(actionStr: String): PendingIntent?

    open fun prevChapter() {
        clearFinishChapterTimer()
        ReadBook.upReadTime()
        toLast = false
        resumeReadAloudInternal()
        withSpeechNavigation { ReadBook.moveToPrevChapter(true, toLast = false) }
    }

    open fun nextChapter() {
        clearFinishChapterTimer()
        ReadBook.upReadTime()
        AppLog.putDebug("${readerReadAloudChapter?.title} 朗读结束跳转下一章并朗读")
        resumeReadAloudInternal()
        if (!withSpeechNavigation { ReadBook.moveToNextChapter(true) }) {
            stopReadAloudService()
        }
    }

    /** Handles a playback engine's natural chapter boundary atomically with timer expiry. */
    protected fun completeCurrentChapter() {
        synchronized(finishChapterTimerLock) {
            val chapterIndex = readerReadAloudChapter?.chapterIndex ?: currentChapterIndex
            val decision = decideChapterCompletion(
                durChapterIndex = ReadBook.durChapterIndex,
                finishedChapterIndex = chapterIndex,
                finishChapterAtIndex = finishChapterAtIndex,
                finishChapterSettingEnabled = ReadConfig.finishCurrentChapterAfterTimer,
            )
            if (decision.clearTimer) {
                finishChapterAtIndex = NO_FINISH_CHAPTER
            }
            when (decision.action) {
                ChapterCompletionAction.STOP -> {
                    pause = true
                    stopReadAloudService()
                }

                ChapterCompletionAction.ADVANCE -> {
                    // synchronized is reentrant, so nextChapter() may clear the same state safely.
                    nextChapter()
                }

                ChapterCompletionAction.SKIP -> Unit
            }
        }
    }

    private fun clearFinishChapterTimer() {
        synchronized(finishChapterTimerLock) {
            finishChapterAtIndex = NO_FINISH_CHAPTER
        }
    }

    private fun clearFinishChapterTimerIfChapterChanged(chapterIndex: Int) {
        synchronized(finishChapterTimerLock) {
            if (finishChapterAtIndex != NO_FINISH_CHAPTER &&
                finishChapterAtIndex != chapterIndex
            ) {
                finishChapterAtIndex = NO_FINISH_CHAPTER
            }
        }
    }

    private fun initPhoneStateListener() {
        val needRegister = ReadConfig.ignoreAudioFocus && ReadConfig.pauseReadAloudWhilePhoneCalls
        if (needRegister && registeredPhoneStateListener) {
            return
        }
        if (needRegister) {
            registerPhoneStateListener(phoneStateListener)
        } else {
            unregisterPhoneStateListener(phoneStateListener)
        }
    }

    private fun unregisterPhoneStateListener(l: PhoneStateListener) {
        if (registeredPhoneStateListener) {
            withReadPhoneStatePermission {
                telephonyManager.listen(l, PhoneStateListener.LISTEN_NONE)
                registeredPhoneStateListener = false
            }
        }
    }

    private fun registerPhoneStateListener(l: PhoneStateListener) {
        withReadPhoneStatePermission {
            telephonyManager.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            registeredPhoneStateListener = true
        }
    }

    private fun withReadPhoneStatePermission(block: () -> Unit) {
        try {
            block.invoke()
        } catch (_: SecurityException) {
            PermissionsCompat.Builder()
                .addPermissions(Permissions.READ_PHONE_STATE)
                .rationale(R.string.read_aloud_read_phone_state_permission_rationale)
                .onGranted {
                    try {
                        block.invoke()
                    } catch (_: SecurityException) {
                        LogUtils.d(TAG, "Grant read phone state permission fail.")
                    }
                }
                .request()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    inner class ReadAloudPhoneStateListener : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)
            when (state) {
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (needResumeOnCallStateIdle) {
                        AppLog.put("来电结束,继续朗读")
                        resumeReadAloud()
                    } else {
                        AppLog.put("来电结束")
                    }
                }

                TelephonyManager.CALL_STATE_RINGING -> {
                    if (!pause) {
                        AppLog.put("来电响铃,暂停朗读")
                        needResumeOnCallStateIdle = true
                        pauseReadAloud()
                    } else {
                        AppLog.put("来电响铃")
                    }
                }

                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    AppLog.put("来电接听,不做处理")
                }
            }
        }
    }

}

/**
 * 把已朗读字符数按每秒朗读字数估算为媒体播放器时间轴毫秒值
 */
internal fun estimatedReadAloudTimeMs(
    chars: Int,
    charsPerSecond: Float,
): Long {
    if (chars <= 0 || charsPerSecond <= 0f) return 0L
    return (chars * 1000.0 / charsPerSecond).toLong()
}

/**
 * 计算推送给系统媒体播放器的进度位置。
 * 播放中保持单调不后退: 字符估算值领先时跟随估算值, 落后时按墙钟推进,
 * 避免每秒重复推送同一估算值导致进度条来回跳变;
 * 暂停时冻结在系统插值的显示位置, 恢复时从冻结位置继续, 不跳变。
 */
internal fun nextMediaSessionPositionMs(
    state: Int,
    lastState: Int,
    estimate: Long,
    lastPosition: Long,
    nowElapsedRealtime: Long,
    lastUpdateElapsedRealtime: Long,
): Long {
    val playing = PlaybackStateCompat.STATE_PLAYING
    val paused = PlaybackStateCompat.STATE_PAUSED
    return when {
        state == paused && lastState == playing ->
            if (lastPosition < 0) estimate
            else lastPosition + (nowElapsedRealtime - lastUpdateElapsedRealtime)

        state == paused -> lastPosition.coerceAtLeast(0)

        state == playing && lastState == paused ->
            if (lastPosition < 0) estimate else lastPosition

        state == playing ->
            if (lastPosition < 0) estimate
            else maxOf(estimate, lastPosition + (nowElapsedRealtime - lastUpdateElapsedRealtime))

        else -> estimate
    }
}

/** Sentinel for "finish current chapter" timer not being armed. */
internal const val NO_FINISH_CHAPTER = -1

internal enum class ChapterCompletionAction {
    /** Timer expired during this chapter — stop read-aloud now. */
    STOP,
    /** No finish-chapter intent — continue to the next chapter. */
    ADVANCE,
    /** The chapter already advanced concurrently — do nothing. */
    SKIP,
}

internal data class ChapterCompletionDecision(
    val action: ChapterCompletionAction,
    val clearTimer: Boolean,
)

/**
 * Decides what a natural chapter boundary should do against the "finish current
 * chapter after timer" state. Pure and thread-free so it can be unit tested.
 *
 * SKIP 只保留给"章已推进且臂标不属于已读完章节"的双重触发竞态（臂标消费后为
 * NO_FINISH，自然落入该分支）。臂标锚定的章节自然读完时必须 STOP：
 * 脱离浏览（durChapterIndex 领先于朗读章节）不算章节已推进。
 */
internal fun decideChapterCompletion(
    durChapterIndex: Int,
    finishedChapterIndex: Int,
    finishChapterAtIndex: Int,
    finishChapterSettingEnabled: Boolean,
): ChapterCompletionDecision {
    if (durChapterIndex != finishedChapterIndex &&
        finishChapterAtIndex != finishedChapterIndex
    ) {
        // The chapter already advanced concurrently (race) — leave any foreign arm alone.
        return ChapterCompletionDecision(
            action = ChapterCompletionAction.SKIP,
            clearTimer = false,
        )
    }
    return when {
        finishChapterAtIndex == NO_FINISH_CHAPTER ->
            ChapterCompletionDecision(ChapterCompletionAction.ADVANCE, clearTimer = false)
        finishChapterAtIndex != finishedChapterIndex || !finishChapterSettingEnabled ->
            ChapterCompletionDecision(ChapterCompletionAction.ADVANCE, clearTimer = true)
        else ->
            ChapterCompletionDecision(ChapterCompletionAction.STOP, clearTimer = true)
    }
}

internal inline fun findReadAloudPageIndex(
    currentPageIndex: Int,
    chapterPosition: Int,
    pageCount: Int,
    pageStart: (Int) -> Int,
): Int {
    var targetPageIndex = currentPageIndex
    while (
        targetPageIndex + 1 < pageCount &&
        chapterPosition > pageStart(targetPageIndex + 1)
    ) {
        targetPageIndex++
    }
    return targetPageIndex
}
