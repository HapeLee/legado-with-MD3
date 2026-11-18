package io.legado.app.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.utils.MediaConstants.SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_NEXT
import androidx.media.utils.MediaConstants.SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_PREV
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.Status
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.PlaybackCapsuleGateway
import io.legado.app.domain.model.PlaybackCapsuleSource
import io.legado.app.service.readaloud.ReadAloudOverlayWindow
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.help.MediaHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.exoplayer.ExoPlayerHelper
import io.legado.app.help.glide.ImageLoader
import io.legado.app.model.AudioPlay
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.getMediaItem
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.koin.core.context.GlobalContext
import splitties.init.appCtx
import splitties.systemservices.audioManager
import splitties.systemservices.notificationManager
import splitties.systemservices.powerManager
import splitties.systemservices.wifiManager
import kotlin.time.Duration.Companion.milliseconds

/**
 * 音频播放服务
 */
class AudioPlayService : BaseService(),
    AudioManager.OnAudioFocusChangeListener,
    Player.Listener {

    companion object {
        @JvmStatic
        var isRun = false
            private set

        @JvmStatic
        var pause = true
            private set

        @JvmStatic
        var timeMinute: Int = 0
            set(value) {
                field = PlaybackTimer.normalize(value)
            }

        var url: String = ""
            private set

        private const val ACTION_OPEN_AUDIO_PLAYER = "io.legado.app.action.OPEN_AUDIO_PLAYER"
        private const val ACTION_STOP_FROM_NOTIFICATION = "io.legado.app.action.STOP_AUDIO_PLAYER"
        private const val MEDIA_ACTION_STOP = "Stop"
        private const val MEDIA_ACTION_TIMER = "Timer"
    }

    private val otherSettingsGateway get() = GlobalContext.get().get<OtherSettingsGateway>()
    private val aloudSettingsGateway get() = GlobalContext.get().get<ReadAloudSettingsGateway>()
    private val useWakeLock = otherSettingsGateway.currentSettings.audioPlayUseWakeLock
    private val wakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:AudioPlayService")
            .apply {
                this.setReferenceCounted(false)
            }
    }
    private val wifiLock by lazy {
        @Suppress("DEPRECATION")
        wifiManager?.createWifiLock(WIFI_MODE_FULL_HIGH_PERF, "legado:AudioPlayService")?.apply {
            setReferenceCounted(false)
        }
    }
    private val mFocusRequest: AudioFocusRequestCompat by lazy {
        MediaHelp.buildAudioFocusRequestCompat(this)
    }
    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayerHelper.createHttpExoPlayer(this)
    }
    private lateinit var mediaSessionCompat: MediaSessionCompat
    private var broadcastReceiver: BroadcastReceiver? = null
    private var needResumeOnAudioFocusGain = false
    private var position = AudioPlay.book?.durChapterPos ?: 0
    private var prepareJob: Coroutine<*>? = null
    private var preparingMedia = false
    private var playbackChapter: BookChapter? = null
    private var reportedDuration = -1L
    private var dsJob: Job? = null
    private var upPlayProgressJob: Job? = null
    private var notificationUpdateJob: Job? = null
    private var playSpeed: Float = 1f
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var cover: Bitmap =
        BitmapFactory.decodeResource(appCtx.resources, R.drawable.ic_launcher)!!

    private var capsuleOverlayWindow: ReadAloudOverlayWindow? = null

    override fun onCreate() {
        pause = true
        // 基类会立即发出前台服务通知，需要先准备通知使用的会话令牌。
        initMediaSession()
        super.onCreate()
        isRun = true
        GlobalContext.get().get<PlaybackCapsuleGateway>()
            .setSessionAvailable(PlaybackCapsuleSource.AudioBook, true)
        capsuleOverlayWindow = ReadAloudOverlayWindow(this, PlaybackCapsuleSource.AudioBook)
        exoPlayer.addListener(this)
        AudioPlay.registerService(this)
        initBroadcastReceiver()
        upMediaMetadata()
        upMediaSessionPlaybackState()
        // 「使用媒体通道」控制通知的展示形式。媒体键始终由当前有声书会话处理。
        mediaSessionCompat.isActive = true
        lifecycleScope.launch {
            aloudSettingsGateway.settings
                .map { it.systemMediaControlCompatibilityChange }
                .distinctUntilChanged()
                .collect { upAudioPlayNotification() }
        }
        doDs()
        execute {
            ImageLoader
                .loadBitmap(this@AudioPlayService, AudioPlay.book?.getDisplayCover())
                .submit()
                .get()
        }.onSuccess {
            if (it.width > 16 && it.height > 16) {
                cover = it
                upMediaMetadata()
                upAudioPlayNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                IntentAction.play -> play(AudioPlay.durChapterPos)
                IntentAction.playNew -> play(0)
                IntentAction.stopPlay -> {
                    resetPlayback()
                    upMediaSessionPlaybackState()
                    upAudioPlayNotification()
                }

                IntentAction.pause -> pause()
                IntentAction.resume -> resume()
                IntentAction.prev -> skipToPreviousChapter()
                IntentAction.next -> skipToNextChapter()
                IntentAction.upPlayMode -> upMediaSessionPlaybackState()
                IntentAction.adjustSpeed -> upSpeed(intent.getFloatExtra("adjust", 1f))
                IntentAction.adjustGain -> applyGain(intent.getIntExtra("gain", 0))
                IntentAction.addTimer -> addTimer()
                IntentAction.setTimer -> setTimer(intent.getIntExtra("minute", 0))
                IntentAction.adjustProgress -> {
                    adjustProgress(intent.getIntExtra("position", position).toLong())
                }

                IntentAction.stop -> stopSelf()
                ACTION_STOP_FROM_NOTIFICATION -> AudioPlay.stop()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        capsuleOverlayWindow?.onConfigurationChanged()
    }

    override fun onDestroy() {
        GlobalContext.get().get<PlaybackCapsuleGateway>()
            .setSessionAvailable(PlaybackCapsuleSource.AudioBook, false)
        capsuleOverlayWindow?.close()
        capsuleOverlayWindow = null
        super.onDestroy()
        releasePlaybackLocks()
        isRun = false
        pause = true
        abandonFocus()
        exoPlayer.removeListener(this)
        exoPlayer.release()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        mediaSessionCompat.isActive = false
        mediaSessionCompat.release()
        unregisterReceiver(broadcastReceiver)
        AudioPlay.status = Status.STOP
        postEvent(EventBus.AUDIO_STATE, Status.STOP)
        AudioPlay.unregisterService()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * 播放音频
     */
    private fun play(startPosition: Int) {
        resetPlayback()
        position = startPosition
        url = AudioPlay.durPlayUrl
        playbackChapter = AudioPlay.durChapter
        upMediaMetadata()
        if (!requestFocus()) {
            AudioPlay.upLoading(false)
            upMediaSessionPlaybackState()
            upAudioPlayNotification()
            return
        }
        pause = false
        preparingMedia = true
        acquirePlaybackLocks()
        upMediaSessionPlaybackState()
        upAudioPlayNotification()
        prepareJob = execute(context = Main) {
            val analyzeUrl = AnalyzeUrl(
                url,
                source = AudioPlay.bookSource,
                ruleData = AudioPlay.book,
                chapter = playbackChapter,
                coroutineContext = coroutineContext
            )
            exoPlayer.setMediaItem(analyzeUrl.getMediaItem())
            exoPlayer.playWhenReady = !pause
            // 片头跳过设定（仅从头播放时生效）
            val skipStartMs = (AudioPlay.book?.getOpenCredits() ?: 0) * 1000L
            val playtime = if (position == 0) skipStartMs else position.toLong()
            exoPlayer.seekTo(playtime)
            preparingMedia = false
            exoPlayer.prepare()
        }.onError {
            AppLog.put("播放出错\n${it.localizedMessage}", it)
            toastOnUi("$url ${it.localizedMessage}")
            stopSelf()
        }
    }

    private fun resetPlayback() {
        prepareJob?.cancel()
        preparingMedia = false
        playbackChapter = null
        reportedDuration = -1L
        pause = true
        needResumeOnAudioFocusGain = false
        releasePlaybackLocks()
        upPlayProgressJob?.cancel()
        exoPlayer.pause()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        if (AudioPlay.status != Status.STOP) {
            AudioPlay.status = Status.STOP
            postEvent(EventBus.AUDIO_STATE, Status.STOP)
        }
    }

    /**
     * 暂停播放
     */
    private fun pause(abandonFocus: Boolean = true) {
        releasePlaybackLocks()
        try {
            pause = true
            if (abandonFocus) {
                needResumeOnAudioFocusGain = false
                abandonFocus()
            }
            upPlayProgressJob?.cancel()
            exoPlayer.pause()
            if (exoPlayer.currentMediaItem != null && isCurrentPlaybackChapter()) publishPlayPosition()
            upMediaSessionPlaybackState()
            AudioPlay.status = Status.PAUSE
            postEvent(EventBus.AUDIO_STATE, Status.PAUSE)
            upAudioPlayNotification()
        } catch (e: Exception) {
            e.printOnDebug()
        }
    }

    /**
     * 恢复播放
     */
    private fun resume(requestAudioFocus: Boolean = true) {
        try {
            val playerNeedsPreparation = exoPlayer.playbackState == Player.STATE_IDLE ||
                exoPlayer.playbackState == Player.STATE_ENDED
            if (!preparingMedia && playerNeedsPreparation) {
                AudioPlay.loadOrUpPlayUrl()
                return
            }
            if (!pause || (requestAudioFocus && !requestFocus())) return
            needResumeOnAudioFocusGain = false
            pause = false
            acquirePlaybackLocks()
            exoPlayer.play()
            upMediaSessionPlaybackState()
            AudioPlay.status = Status.PLAY
            postEvent(EventBus.AUDIO_STATE, Status.PLAY)
            upAudioPlayNotification()
        } catch (e: Exception) {
            e.printOnDebug()
            stopSelf()
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquirePlaybackLocks() {
        if (useWakeLock) {
            wakeLock.acquire()
            wifiLock?.acquire()
        }
    }

    private fun releasePlaybackLocks() {
        if (useWakeLock) {
            if (wakeLock.isHeld) wakeLock.release()
            wifiLock?.let { if (it.isHeld) it.release() }
        }
    }

    /**
     * 调节进度
     */
    private fun adjustProgress(position: Long) {
        if (playbackChapter != null && !isCurrentPlaybackChapter()) return
        val maxPosition = exoPlayer.duration.takeIf { it >= 0 } ?: Long.MAX_VALUE
        this.position = position.coerceIn(0, minOf(maxPosition, Int.MAX_VALUE.toLong())).toInt()
        AudioPlay.seekPositionChanged(this.position)
        if (exoPlayer.currentMediaItem != null) {
            exoPlayer.seekTo(this.position.toLong())
        }
        postEvent(EventBus.AUDIO_PROGRESS, this.position)
        upMediaSessionPlaybackState()
    }

    private fun isCurrentPlaybackChapter(): Boolean = playbackChapter?.let {
        it.bookUrl == AudioPlay.book?.bookUrl && it.index == AudioPlay.durChapterIndex
    } == true

    /**
     * 调节速度
     */
    @SuppressLint(value = ["ObsoleteSdkInt"])
    private fun upSpeed(speed: Float) {
        kotlin.runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                playSpeed = speed
                exoPlayer.setPlaybackSpeed(playSpeed)
                postEvent(EventBus.AUDIO_SPEED, playSpeed)
            }
        }
    }

    /**
     * 播放状态监控
     */
    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        if (!isCurrentPlaybackChapter()) return
        when (playbackState) {
            Player.STATE_READY -> {
                // 准备好
                AudioPlay.upLoading(false)
                applyGain(AudioPlay.book?.getAudioGain() ?: 0)
                if (exoPlayer.playWhenReady) {
                    AudioPlay.status = Status.PLAY
                    postEvent(EventBus.AUDIO_STATE, Status.PLAY)
                } else {
                    AudioPlay.status = Status.PAUSE
                    postEvent(EventBus.AUDIO_STATE, Status.PAUSE)
                }
                val duration = exoPlayer.duration
                postEvent(EventBus.AUDIO_SIZE, duration.toInt())
                if (duration >= 0 && duration != reportedDuration) {
                    reportedDuration = duration
                    AudioPlay.saveDurChapter(duration)
                }
            }

            Player.STATE_ENDED -> {
                // 结束
                upPlayProgressJob?.cancel()
                AudioPlay.playPositionChanged(exoPlayer.duration.toInt())
                if (exoPlayer.playWhenReady) AudioPlay.next()
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            upPlayProgress()
        } else {
            upPlayProgressJob?.cancel()
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            upMediaMetadata()
        }
        upMediaSessionPlaybackState()
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYER_ERROR,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
            )
        ) {
            upAudioPlayNotification()
        }
    }

    private fun upMediaMetadata() {
        val metadata = MediaMetadataCompat.Builder()
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, cover)
            .putText(MediaMetadataCompat.METADATA_KEY_TITLE, AudioPlay.durChapter?.title.orEmpty())
            .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, AudioPlay.book?.name.orEmpty())
            .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, AudioPlay.book?.author.orEmpty())
        val duration = exoPlayer.duration
        if (duration >= 0) metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        mediaSessionCompat.setMetadata(metadata.build())
    }

    /**
     * 播放错误事件
     */
    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        pause = true
        needResumeOnAudioFocusGain = false
        releasePlaybackLocks()
        abandonFocus()
        AudioPlay.status = Status.STOP
        postEvent(EventBus.AUDIO_STATE, Status.STOP)
        AudioPlay.upLoading(false)
        val errorMsg = "音频播放出错\n${error.errorCodeName} ${error.errorCode}"
        AppLog.put(errorMsg, error)
        toastOnUi(errorMsg)
    }

    private fun setTimer(minute: Int) {
        timeMinute = minute
        doDs()
    }

    private fun addTimer() {
        timeMinute = PlaybackTimer.addIncrement(timeMinute)
        doDs()
    }

    /**
     * 定时
     */
    private fun doDs() {
        postEvent(EventBus.AUDIO_DS, timeMinute)
        upAudioPlayNotification()
        dsJob?.cancel()
        dsJob = null
        if (timeMinute == PlaybackTimer.MIN_MINUTES) return
        dsJob = lifecycleScope.launch {
            while (isActive) {
                delay(60000)
                if (timeMinute == PlaybackTimer.MIN_MINUTES) break
                if (!pause) {
                    timeMinute--
                    if (timeMinute == PlaybackTimer.MIN_MINUTES) {
                        AudioPlay.stop()
                        postEvent(EventBus.AUDIO_DS, timeMinute)
                        break
                    }
                }
                postEvent(EventBus.AUDIO_DS, timeMinute)
                upAudioPlayNotification()
            }
        }
    }

    /**
     * 每隔1秒发送播放进度
     */
    private fun upPlayProgress() {
        upPlayProgressJob?.cancel()
        val skipEnds = AudioPlay.book?.getCloseCredits() ?: 0
        upPlayProgressJob = lifecycleScope.launch {
            while (isActive && isCurrentPlaybackChapter()) {
                publishPlayPosition()
                postEvent(EventBus.AUDIO_BUFFER_PROGRESS, exoPlayer.bufferedPosition.toInt())
                postEvent(EventBus.AUDIO_SIZE, exoPlayer.duration.toInt())
                upMediaSessionPlaybackState()
                // 片尾跳过检查
                if (skipEnds > 0) {
                    val duration = exoPlayer.duration
                    if (duration > 0 && exoPlayer.currentPosition >= duration - (skipEnds * 1000L)) {
                        AudioPlay.playPositionChanged(duration.toInt())
                        pause = true
                        AudioPlay.next()
                        break
                    }
                }
                delay(1000.milliseconds)
            }
        }
    }

    private fun publishPlayPosition() {
        position = exoPlayer.currentPosition.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        AudioPlay.playPositionChanged(position)
        postEvent(EventBus.AUDIO_PROGRESS, position)
    }

    /**
     * 应用音频增益（LoudnessEnhancer，单位 mB，范围 -6000..6000）
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun applyGain(gain: Int) {
        try {
            if (gain == 0) {
                loudnessEnhancer?.release()
                loudnessEnhancer = null
                return
            }
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(exoPlayer.audioSessionId).apply {
                    setEnabled(true)
                }
            }
            loudnessEnhancer?.setTargetGain(gain.coerceIn(-6000, 6000))
        } catch (e: Exception) {
            AppLog.put("设置音频增益失败: ${e.localizedMessage}", e)
        }
    }

    /**
     * 更新媒体状态
     */
    private fun upMediaSessionPlaybackState() {
        val playback = resolveAudioPlayMediaState(
            playerState = exoPlayer.playbackState,
            isPlaying = exoPlayer.isPlaying,
            hasError = exoPlayer.playerError != null,
            preparing = preparingMedia,
            paused = pause,
            playbackSpeed = exoPlayer.playbackParameters.speed,
        )
        val actions = audioPlayMediaActions(
            AudioPlay.durChapterIndex,
            AudioPlay.simulatedChapterSize,
            exoPlayer.isCurrentMediaItemSeekable && isCurrentPlaybackChapter(),
            AudioPlay.playMode,
        )
        val mediaPosition = if (exoPlayer.currentMediaItem != null) {
            exoPlayer.currentPosition
        } else {
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
        }
        mediaSessionCompat.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(playback.state, mediaPosition, playback.speed)
                .setBufferedPosition(exoPlayer.bufferedPosition)
                .addCustomAction(MEDIA_ACTION_STOP, getString(R.string.stop), R.drawable.ic_stop_black_24dp)
                .addCustomAction(MEDIA_ACTION_TIMER, getString(R.string.set_timer), R.drawable.ic_time_add_24dp)
                .build()
        )
    }

    /**
     * 初始化MediaSession, 注册多媒体按钮
     */
    private fun initMediaSession() {
        mediaSessionCompat = MediaSessionCompat(this, "audioPlay")
        // 会话结束后避免把有声书的媒体键交给文字朗读接收器。
        mediaSessionCompat.setMediaButtonReceiver(null)
        mediaSessionCompat.setSessionActivity(audioPlayerPendingIntent())
        mediaSessionCompat.setExtras(Bundle().apply {
            putBoolean(SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_PREV, true)
            putBoolean(SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_NEXT, true)
        })
        mediaSessionCompat.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = resume()

            override fun onPause() = pause()

            override fun onStop() = AudioPlay.stop()

            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                @Suppress("DEPRECATION")
                val key = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                // 缓冲时系统会把切换键当作播放键，仍允许用户暂停正在准备的音频。
                if (!pause && !exoPlayer.isPlaying && key?.action == KeyEvent.ACTION_DOWN &&
                    (key.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                        key.keyCode == KeyEvent.KEYCODE_HEADSETHOOK)
                ) {
                    if (key.repeatCount == 0) pause()
                    return true
                }
                return super.onMediaButtonEvent(mediaButtonEvent)
            }

            override fun onSeekTo(pos: Long) {
                if (exoPlayer.isCurrentMediaItemSeekable) adjustProgress(pos)
            }

            override fun onSkipToPrevious() = skipToPreviousChapter()

            override fun onSkipToNext() = skipToNextChapter()

            override fun onCustomAction(action: String?, extras: Bundle?) {
                when (action) {
                    MEDIA_ACTION_STOP -> onStop()
                    MEDIA_ACTION_TIMER -> addTimer()
                }
            }
        })
    }

    private fun skipToPreviousChapter() {
        if (canSkipAudioChapter(AudioPlay.durChapterIndex, AudioPlay.simulatedChapterSize, -1)) {
            AudioPlay.prev()
        }
    }

    private fun skipToNextChapter() {
        if (canSkipToNextAudioChapter(AudioPlay.durChapterIndex, AudioPlay.simulatedChapterSize, AudioPlay.playMode)) {
            AudioPlay.next()
        }
    }

    /**
     * 断开耳机监听
     */
    private fun initBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                    pause()
                }
            }
        }
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(broadcastReceiver, intentFilter)
    }

    /**
     * 音频焦点变化
     */
    override fun onAudioFocusChange(focusChange: Int) {
        if (!isRun) return
        if (aloudSettingsGateway.currentSettings.ignoreAudioFocus) {
            AppLog.put("忽略音频焦点处理(有声)")
            return
        }
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (needResumeOnAudioFocusGain) {
                    AppLog.put("音频焦点获得,继续播放")
                    resume(requestAudioFocus = false)
                } else {
                    AppLog.put("音频焦点获得")
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                AppLog.put("音频焦点丢失,暂停播放")
                pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                AppLog.put("音频焦点暂时丢失并会很快再次获得,暂停播放")
                if (!pause) {
                    needResumeOnAudioFocusGain = true
                    pause(false)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 短暂丢失焦点，这种情况是被其他应用申请了短暂的焦点希望其他声音能压低音量（或者关闭声音）凸显这个声音（比如短信提示音），
                AppLog.put("音频焦点短暂丢失,不做处理")
            }
        }
    }

    private fun createNotification(): NotificationCompat.Builder {
        val nTitle: String = when {
            pause -> getString(R.string.audio_pause)
            timeMinute > PlaybackTimer.MIN_MINUTES ->
                getString(R.string.playing_timer, timeMinute)
            else -> getString(R.string.audio_play_t)
        } + ": ${AudioPlay.book?.name}"

        val nSubtitle = AudioPlay.durChapter?.title ?: getString(R.string.audio_play_s)

        val builder = NotificationCompat
            .Builder(this@AudioPlayService, AppConst.channelIdReadAloud)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setSubText(getString(R.string.audio))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(nTitle)
            .setContentText(nSubtitle)
            .setContentIntent(audioPlayerPendingIntent())
        builder.setLargeIcon(cover)
        builder.addAction(
            R.drawable.ic_skip_previous,
            getString(R.string.a11y_previous_chapter),
            servicePendingIntent<AudioPlayService>(IntentAction.prev)
        )
        if (pause) {
            builder.addAction(
                R.drawable.ic_play,
                getString(R.string.resume),
                servicePendingIntent<AudioPlayService>(IntentAction.resume)
            )
        } else {
            builder.addAction(
                R.drawable.ic_pause,
                getString(R.string.pause),
                servicePendingIntent<AudioPlayService>(IntentAction.pause)
            )
        }
        builder.addAction(
            R.drawable.ic_skip_next,
            getString(R.string.a11y_next_chapter),
            servicePendingIntent<AudioPlayService>(IntentAction.next)
        )
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.stop),
            servicePendingIntent<AudioPlayService>(ACTION_STOP_FROM_NOTIFICATION)
        )
        builder.addAction(
            R.drawable.ic_time_add_24dp,
            getString(R.string.set_timer),
            servicePendingIntent<AudioPlayService>(IntentAction.addTimer)
        )
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        builder.setStyle(choiceMediaStyle())
        return builder
    }

    private fun audioPlayerPendingIntent() = activityPendingIntent(
        MainActivity.createAudioPlayIntent(this, inBookshelf = AudioPlay.inBookshelf),
        ACTION_OPEN_AUDIO_PLAYER,
    )

    private fun choiceMediaStyle(): MediaStyle {
        val mediaStyle = MediaStyle().setShowActionsInCompactView(0, 1, 2)
        if (aloudSettingsGateway.currentSettings.systemMediaControlCompatibilityChange) {
            mediaStyle.setMediaSession(mediaSessionCompat.sessionToken)
        }
        return mediaStyle
    }

    private fun upAudioPlayNotification() {
        if (notificationUpdateJob?.isActive == true) return
        notificationUpdateJob = lifecycleScope.launch {
            // 切章时播放器可能在同一轮主线程内触发多个回调，合并通知刷新。
            yield()
            if (!isRun) return@launch
            try {
                notificationManager.notify(NotificationId.AudioPlayService, createNotification().build())
            } catch (e: Exception) {
                AppLog.put("创建音频播放通知出错,${e.localizedMessage}", e, true)
            }
        }
    }

    /**
     * 更新通知
     */
    override fun startForegroundNotification() {
        try {
            val notification = createNotification()
            startForeground(NotificationId.AudioPlayService, notification.build())
        } catch (e: Exception) {
            AppLog.put("创建音频播放通知出错,${e.localizedMessage}", e, true)
            //创建通知出错不结束服务就会崩溃,服务必须绑定通知
            stopSelf()
        }
    }

    /**
     * 请求音频焦点
     * @return 音频焦点
     */
    private fun requestFocus(): Boolean {
        if (aloudSettingsGateway.currentSettings.ignoreAudioFocus) {
            return true
        }
        return MediaHelp.requestFocus(mFocusRequest)
    }

    /**
     * 放弃音频焦点
     */
    private fun abandonFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, mFocusRequest)
    }

}
