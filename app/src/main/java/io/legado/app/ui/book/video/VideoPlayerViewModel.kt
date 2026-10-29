package io.legado.app.ui.book.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.IntentAction
import io.legado.app.data.entities.RssArticle
import io.legado.app.model.VideoPlay
import io.legado.app.service.VideoPlayService
import io.legado.app.utils.startService
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

/**
 * 播放页的状态宿主：把 [VideoPlay] 的会话状态映射成 UiState，用户动作分发给模型与服务。
 *
 * 播放器实例不在 ViewModel 里：退出播放页只是离开 UI，播放由 VideoPlayService 继续托管；
 * 这里只在清除时补一次进度保存。
 */
class VideoPlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<VideoPlayerEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var initializedBookUrl: String? = null

    init {
        viewModelScope.launch {
            VideoPlay.state.collect { state ->
                _uiState.update { current ->
                    current.copy(
                        bookName = state.bookName,
                        chapterTitle = state.chapterTitle,
                        volumes = state.volumes.toPersistentList(),
                        volumeIndex = state.volumeIndex,
                        episodes = state.episodes.toPersistentList(),
                        episodeIndex = state.episodeIndex,
                        isLoading = state.isLoading,
                        isPlaying = state.isPlaying,
                        error = state.error,
                        showDanmaku = VideoPlay.danmakuShow,
                    )
                }
            }
        }
        _uiState.update {
            it.copy(
                speed = VideoPlay.playSpeed,
                autoPlay = VideoPlay.autoPlay,
                startFull = VideoPlay.startFull,
                longPressSpeed = VideoPlay.longPressSpeed,
                showDanmaku = VideoPlay.danmakuShow,
                floatingEnabled = VideoPlay.floatingEnabled,
            )
        }
    }

    fun init(bookUrl: String) {
        if (initializedBookUrl == bookUrl) return
        initializedBookUrl = bookUrl
        // 播放器由服务托管：先起服务，避免退出页面后播放被系统回收
        appCtx.startService<VideoPlayService> { action = IntentAction.play }
        viewModelScope.launch {
            VideoPlay.openBook(bookUrl)
        }
    }

    /** RSS 文章里的视频：定位信息足够，正文由 [VideoPlay] 按源规则重新解析。 */
    fun initRss(article: RssArticle) {
        val key = "rss:${article.origin}|${article.link}|${article.sort}"
        if (initializedBookUrl == key) return
        initializedBookUrl = key
        appCtx.startService<VideoPlayService> { action = IntentAction.play }
        viewModelScope.launch {
            VideoPlay.openRssArticle(article)
        }
    }

    fun onIntent(intent: VideoPlayerIntent) {
        when (intent) {
            VideoPlayerIntent.TogglePlay -> VideoPlay.togglePlay()
            VideoPlayerIntent.Pause -> {
                VideoPlay.pause()
                VideoPlay.saveRead()
            }

            is VideoPlayerIntent.SeekTo -> VideoPlay.seekTo(intent.positionMs)
            VideoPlayerIntent.PreviousEpisode -> VideoPlay.upDurIndex(-1)
            VideoPlayerIntent.NextEpisode -> VideoPlay.upDurIndex(1)
            is VideoPlayerIntent.SelectVolume -> {
                VideoPlay.selectVolume(intent.index)
                _uiState.update { it.copy(sheet = null) }
            }

            is VideoPlayerIntent.SelectEpisode -> {
                VideoPlay.selectEpisode(_uiState.value.volumeIndex, intent.index)
                _uiState.update { it.copy(sheet = null) }
            }

            is VideoPlayerIntent.SetSpeed -> {
                VideoPlay.setSpeed(intent.speed)
                _uiState.update { it.copy(speed = intent.speed, sheet = null) }
            }

            VideoPlayerIntent.ToggleFullscreen ->
                _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }

            VideoPlayerIntent.Retry -> {
                val url = initializedBookUrl
                if (url != null) {
                    initializedBookUrl = null
                    init(url)
                }
            }

            is VideoPlayerIntent.OpenSheet -> _uiState.update { it.copy(sheet = intent.sheet) }
            VideoPlayerIntent.CloseSheet -> _uiState.update { it.copy(sheet = null) }

            is VideoPlayerIntent.SetAutoPlay -> {
                VideoPlay.autoPlay = intent.enabled
                _uiState.update { it.copy(autoPlay = intent.enabled) }
            }

            is VideoPlayerIntent.SetStartFull -> {
                VideoPlay.startFull = intent.enabled
                _uiState.update { it.copy(startFull = intent.enabled) }
            }

            is VideoPlayerIntent.SetLongPressSpeed -> {
                VideoPlay.longPressSpeed = intent.value
                _uiState.update { it.copy(longPressSpeed = intent.value) }
            }

            VideoPlayerIntent.ToggleDanmaku -> {
                val show = !VideoPlay.danmakuShow
                VideoPlay.danmakuShow = show
                _uiState.update { it.copy(showDanmaku = show) }
            }

            VideoPlayerIntent.ToggleFloating ->
                appCtx.startService<VideoPlayService> {
                    action = IntentAction.toggleVideoFloating
                }

            is VideoPlayerIntent.SetFloatingEnabled -> {
                VideoPlay.floatingEnabled = intent.enabled
                _uiState.update { it.copy(floatingEnabled = intent.enabled) }
            }

            VideoPlayerIntent.CopyPlayUrl -> copyPlayUrl()
            VideoPlayerIntent.OpenInExternalPlayer -> openExternal()
            VideoPlayerIntent.OpenSourceLogin -> emitSource(
                { VideoPlayerEffect.OpenSourceLogin(it) }
            )

            VideoPlayerIntent.OpenSourceEdit -> emitSource(
                { VideoPlayerEffect.OpenSourceEdit(it) }
            )

            VideoPlayerIntent.RunCustomButton ->
                _effects.tryEmit(VideoPlayerEffect.RunCustomButton)
            is VideoPlayerIntent.LongPressSpeed -> {
                val speed = if (intent.active) {
                    VideoPlay.longPressSpeed / 10f
                } else {
                    VideoPlay.playSpeed
                }
                VideoPlay.player.playbackParameters =
                    VideoPlay.player.playbackParameters.withSpeed(speed)
            }
        }
    }

    /** 书源相关动作都要求当前会话有书源（RSS/直链没有）。 */
    private fun emitSource(effect: (String) -> VideoPlayerEffect) {
        val sourceUrl = VideoPlay.bookSource?.bookSourceUrl
        if (sourceUrl.isNullOrBlank()) {
            _effects.tryEmit(
                VideoPlayerEffect.ShowToast(appCtx.getString(R.string.source_not_found))
            )
        } else {
            _effects.tryEmit(effect(sourceUrl))
        }
    }

    private fun copyPlayUrl() {
        val url = VideoPlay.playUrl
        if (url.isNullOrBlank()) {
            _effects.tryEmit(
                VideoPlayerEffect.ShowToast(appCtx.getString(R.string.video_link_not_found))
            )
            return
        }
        // 书源可以接管复制行为，需要 Activity，交给宿主执行
        _effects.tryEmit(VideoPlayerEffect.CopyPlayUrl(url))
    }

    private fun openExternal() {
        val url = VideoPlay.playUrl
        if (url.isNullOrBlank()) {
            _effects.tryEmit(
                VideoPlayerEffect.ShowToast(appCtx.getString(R.string.video_link_not_found))
            )
            return
        }
        _effects.tryEmit(
            VideoPlayerEffect.OpenExternalPlayer(url, VideoPlay.videoTitle ?: VideoPlay.book?.name)
        )
    }

    override fun onCleared() {
        // 离开播放页不等于停止播放：只把进度落库，播放交给服务继续
        VideoPlay.saveRead()
        super.onCleared()
    }
}
