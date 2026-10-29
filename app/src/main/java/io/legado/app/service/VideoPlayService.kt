package io.legado.app.service

import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.Player
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.model.VideoPlay
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.servicePendingIntent
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import splitties.systemservices.notificationManager

/**
 * 视频播放的前台服务：只做保活、通知、媒体会话与命令分发，
 * 播放器实例与会话状态仍在 [VideoPlay] 上，UI 与悬浮窗都绑定同一个实例。
 */
class VideoPlayService : BaseService() {

    private var mediaSessionCompat: android.support.v4.media.session.MediaSessionCompat? = null

    private val serviceScope = kotlinx.coroutines.MainScope()

    private var floatingWindow: VideoFloatingWindow? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            upMediaSessionPlaybackState(
                if (isPlaying) {
                    android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
                } else {
                    android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
                }
            )
            upNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        VideoPlay.player.addListener(playerListener)
        initMediaSession()
        // 章节/加载状态变化也要刷新通知文案
        floatingWindow = VideoFloatingWindow(this)
        serviceScope.launch {
            VideoPlay.state
                .map { listOf(it.bookName, it.chapterTitle, it.error) }
                .distinctUntilChanged()
                .collect { upNotification() }
        }
        // 离开应用后把画面搬到悬浮小窗，回到应用内再交还给播放页
        serviceScope.launch {
            combine(
                io.legado.app.help.LifecycleHelp.appVisible,
                VideoPlay.state.map { it.isPlaying },
            ) { appVisible, playing -> !appVisible && playing }
                .distinctUntilChanged()
                .collect { shouldFloat ->
                    if (shouldFloat && VideoPlay.floatingEnabled) {
                        floatingWindow?.show()
                    } else {
                        floatingWindow?.close()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                IntentAction.play, IntentAction.resume -> VideoPlay.resume()
                IntentAction.pause -> VideoPlay.pause()
                IntentAction.prev -> VideoPlay.upDurIndex(-1)
                IntentAction.next -> VideoPlay.upDurIndex(1)
                IntentAction.adjustProgress -> VideoPlay.seekTo(
                    intent.getLongExtra("position", 0L)
                )

                IntentAction.adjustSpeed -> VideoPlay.setSpeed(
                    intent.getFloatExtra("adjust", 1f)
                )

                IntentAction.stop -> stopSelf()
                IntentAction.toggleVideoFloating -> floatingWindow?.toggle()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun startForegroundNotification() {
        try {
            startForeground(NotificationId.VideoPlayService, createNotification().build())
        } catch (e: Exception) {
            io.legado.app.constant.AppLog.put(
                "创建视频播放通知出错,${e.localizedMessage}", e, true
            )
            stopSelf()
        }
    }

    private fun initMediaSession() {
        val session = android.support.v4.media.session.MediaSessionCompat(this, "videoPlay")
        session.setCallback(object : android.support.v4.media.session.MediaSessionCompat.Callback() {
            override fun onPlay() {
                VideoPlay.resume()
            }

            override fun onPause() {
                VideoPlay.pause()
            }

            override fun onSkipToNext() {
                VideoPlay.upDurIndex(1)
            }

            override fun onSkipToPrevious() {
                VideoPlay.upDurIndex(-1)
            }

            override fun onSeekTo(pos: Long) {
                VideoPlay.seekTo(pos)
            }

            override fun onStop() {
                stopSelf()
            }
        })
        session.isActive = true
        mediaSessionCompat = session
        upMediaSessionPlaybackState(
            android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
        )
    }

    private fun upMediaSessionPlaybackState(state: Int) {
        mediaSessionCompat?.setPlaybackState(
            android.support.v4.media.session.PlaybackStateCompat.Builder()
                .setActions(
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, VideoPlay.player.currentPosition, 1f)
                .build()
        )
    }

    private fun createNotification(): NotificationCompat.Builder {
        val state = VideoPlay.state.value
        val title = listOf(state.bookName, state.chapterTitle)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { getString(R.string.video) }
        val playing = VideoPlay.player.isPlaying
        return NotificationCompat.Builder(this, AppConst.channelIdReadAloud)
            .setSmallIcon(R.drawable.ic_play)
            .setSubText(getString(R.string.video))
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setContentTitle(title)
            .setContentIntent(
                activityPendingIntent(
                    MainActivity.createVideoPlayIntent(
                        this,
                        bookUrl = VideoPlay.book?.bookUrl,
                    ),
                    "activity",
                )
            )
            .addAction(
                R.drawable.ic_skip_previous,
                getString(R.string.previous_chapter),
                servicePendingIntent<VideoPlayService>(IntentAction.prev),
            )
            .addAction(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                getString(if (playing) R.string.pause else R.string.resume),
                servicePendingIntent<VideoPlayService>(
                    if (playing) IntentAction.pause else IntentAction.resume
                ),
            )
            .addAction(
                R.drawable.ic_skip_next,
                getString(R.string.next_chapter),
                servicePendingIntent<VideoPlayService>(IntentAction.next),
            )
            .addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.stop),
                servicePendingIntent<VideoPlayService>(IntentAction.stop),
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(1, 2)
                    .setMediaSession(mediaSessionCompat?.sessionToken)
            )
    }

    private fun upNotification() {
        runCatching {
            notificationManager.notify(
                NotificationId.VideoPlayService, createNotification().build()
            )
        }.onFailure {
            io.legado.app.constant.AppLog.put(
                "刷新视频播放通知出错,${it.localizedMessage}", it
            )
        }
    }

    override fun onDestroy() {
        serviceScope.coroutineContext.cancel()
        floatingWindow?.close()
        floatingWindow = null
        runCatching { VideoPlay.player.removeListener(playerListener) }
        VideoPlay.stop()
        VideoPlay.release()
        mediaSessionCompat?.release()
        mediaSessionCompat = null
        notificationManager.cancel(NotificationId.VideoPlayService)
        super.onDestroy()
    }
}
