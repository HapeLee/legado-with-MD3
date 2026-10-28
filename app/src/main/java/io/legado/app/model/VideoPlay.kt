package io.legado.app.model

import android.annotation.SuppressLint
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.VideoContentRepository
import io.legado.app.data.repository.VideoMedia
import io.legado.app.help.CacheManager
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.exoplayer.ExoPlayerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import splitties.init.appCtx
import java.util.concurrent.ConcurrentHashMap

/** 视频播放会话对 UI 暴露的只读快照。 */
data class VideoPlayState(
    val bookName: String = "",
    val chapterTitle: String = "",
    /** 卷名列表；为空表示该书不分卷。 */
    val volumes: List<String> = emptyList(),
    val volumeIndex: Int = 0,
    val episodes: List<String> = emptyList(),
    val episodeIndex: Int = 0,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val playUrl: String? = null,
    val danmaku: String? = null,
)

/**
 * 视频播放会话模型：持有书籍/目录/卷集索引与播放器实例，负责解析播放地址、切集与进度落库。
 *
 * 播放器实例由本对象持有（进程内单例），[io.legado.app.service.VideoPlayService] 绑定同一个实例
 * 做前台保活、通知与媒体会话，UI（Compose 的 PlayerView）也绑定它；
 * 这样退出播放页后声音仍在，回到页面不需要重新缓冲。
 */
@SuppressLint("StaticFieldLeak")
@Suppress("unused")
object VideoPlay : CoroutineScope by MainScope() {

    private const val VIDEO_POS_PREFIX = "video_pos_"
    private const val VIDEO_POS_SAVE_TIME = 60 * 60 * 24 * 20 // 临时直链进度保留 20 天
    private const val CHAPTER_LINK_TTL = 30 * 60 * 1000L

    private val contentRepository get() = GlobalContext.get().get<VideoContentRepository>()

    private data class CachedMedia(val media: VideoMedia, val createdAt: Long)

    var book: Book? = null
        private set
    var bookSource: BookSource? = null
        private set
    var toc: List<BookChapter> = emptyList()
        private set
    var volumes: List<BookChapter> = emptyList()
        private set
    var episodes: List<BookChapter> = emptyList()
        private set
    var chapter: BookChapter? = null
        private set
    var durVolumeIndex: Int = 0
        private set
    var chapterInVolumeIndex: Int = 0
        private set
    var durChapterPos: Int = 0
    var inBookshelf: Boolean = true
        private set
    var videoTitle: String? = null
        private set
    var playUrl: String? = null
        private set
    var danmaku: String? = null
        private set
    var singleUrl: Boolean = false
        private set

    private val _state = MutableStateFlow(VideoPlayState())
    val state: StateFlow<VideoPlayState> = _state.asStateFlow()

    private val linkCache = ConcurrentHashMap<String, CachedMedia>()
    private val preloadingKeys = ConcurrentHashMap.newKeySet<String>()
    private val preloadMutex = Mutex()

    private var playerInstance: ExoPlayer? = null

    val player: ExoPlayer
        get() = playerInstance ?: ExoPlayerHelper.createHttpExoPlayer(appCtx)
            .also { created ->
                created.addListener(playerListener)
                playerInstance = created
            }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    isLoading = playbackState == Player.STATE_BUFFERING,
                    isPlaying = it.isPlaying && playbackState != Player.STATE_ENDED,
                )
            }
            // 自动连播：一集播完直接进入下一集，与上游一致
            if (playbackState == Player.STATE_ENDED && !isReleasing) {
                upDurIndex(1)
            }
        }
    }

    private var isReleasing = false

    // ——————————————————————————— 播放设置 ———————————————————————————

    /** 打开播放页后自动开始播放。 */
    var autoPlay: Boolean
        get() = AppConfigStore.getInt(PreferKey.videoAutoPlay) != 0
        set(value) = AppConfigStore.putInt(PreferKey.videoAutoPlay, if (value) 1 else 0)

    /** 竖屏视频：进入后直接全屏。 */
    var startFull: Boolean
        get() = AppConfigStore.getInt(PreferKey.videoStartFull) != 0
        set(value) = AppConfigStore.putInt(PreferKey.videoStartFull, if (value) 1 else 0)

    /** 长按倍速，单位 0.1 倍；默认 30 即 3.0 倍。 */
    var longPressSpeed: Int
        get() = AppConfigStore.getInt(PreferKey.videoLongPressSpeed) ?: 30
        set(value) = AppConfigStore.putInt(PreferKey.videoLongPressSpeed, value)

    /** 常驻倍速，单位 0.01 倍；默认 100 即 1.0 倍。 */
    var playSpeed: Float
        get() = (AppConfigStore.getInt(PreferKey.videoPlaySpeed) ?: 100) / 100f
        set(value) = AppConfigStore.putInt(PreferKey.videoPlaySpeed, (value * 100).toInt())

    var danmakuShow: Boolean
        get() = AppConfigStore.getInt(PreferKey.videoDanmakuShow) != 0
        set(value) = AppConfigStore.putInt(PreferKey.videoDanmakuShow, if (value) 1 else 0)

    // ——————————————————————————— 会话入口 ———————————————————————————

    /** 打开书架中的视频书：补齐详情/目录，恢复上次进度并开始播放。 */
    suspend fun openBook(bookUrl: String, autoPlay: Boolean = this.autoPlay) {
        singleUrl = false
        _state.update { it.copy(isLoading = true, error = null) }
        val prepared = withContext(Dispatchers.IO) {
            runCatching {
                val book = appDb.bookDao.getBook(bookUrl)
                    ?: error(appCtx.getString(R.string.book_not_found))
                val source = appDb.bookSourceDao.getBookSource(book.origin)
                    ?: error(appCtx.getString(R.string.source_not_found))
                // 书架直开视频书时可能还没有详情和目录（AddToBookshelfUseCase 只插书）
                if (book.tocUrl.isEmpty()) {
                    contentRepository.loadBookInfo(source, book)
                }
                var chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
                if (chapters.isEmpty()) {
                    chapters = contentRepository.loadChapters(source, book)
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*chapters.toTypedArray())
                    book.update()
                }
                Triple(book, source, chapters)
            }
        }
        prepared.onSuccess { (book, source, chapters) ->
            applyBook(book, source, chapters)
            playCurrent(autoPlay = autoPlay)
        }.onFailure {
            fail(it)
        }
    }

    /** 播放一条没有书籍上下文的直链（外部/调试入口），进度按地址记在缓存里。 */
    suspend fun openUrl(url: String, title: String?) {
        singleUrl = true
        book = null
        bookSource = null
        toc = emptyList()
        volumes = emptyList()
        episodes = emptyList()
        chapter = null
        videoTitle = title
        playUrl = url
        danmaku = null
        durChapterPos = withContext(Dispatchers.IO) {
            CacheManager.getInt(VIDEO_POS_PREFIX + url) ?: 0
        }
        _state.update {
            it.copy(
                bookName = title.orEmpty(),
                chapterTitle = "",
                volumes = emptyList(),
                episodes = emptyList(),
                episodeIndex = 0,
                isLoading = false,
                error = null,
                playUrl = url,
                danmaku = null,
            )
        }
        player.setMediaItem(MediaItem.fromUri(url), durChapterPos.toLong())
        player.prepare()
        if (autoPlay) player.play()
    }

    private fun applyBook(book: Book, source: BookSource, chapters: List<BookChapter>) {
        this.book = book
        bookSource = source
        toc = chapters
        volumes = chapters.filter { it.isVolume }
        inBookshelf = !book.isNotShelf
        durChapterPos = book.durChapterPos
        // 从全局章节索引反推「卷 + 卷内第几集」；索引落在卷标题上时顺延到下一集
        val startIndex = book.durChapterIndex.coerceIn(0, chapters.lastIndex.coerceAtLeast(0))
        val startChapter = chapters.getOrNull(startIndex)?.takeUnless { it.isVolume }
            ?: chapters.firstOrNull { !it.isVolume }
        locateChapter(startChapter)
        SourceCallBack.callBackBook(SourceCallBack.START_READ, source, book, startChapter)
    }

    /** 把当前集定位到 [target]，并同步卷索引与卷内索引。 */
    private fun locateChapter(target: BookChapter?) {
        chapter = target
        if (target == null) {
            durVolumeIndex = 0
            chapterInVolumeIndex = 0
            upEpisodes()
            return
        }
        if (volumes.isEmpty()) {
            durVolumeIndex = 0
            val flat = toc.filter { !it.isVolume }
            chapterInVolumeIndex = flat.indexOf(target).coerceAtLeast(0)
        } else {
            val volumeIdx = volumes.indexOfLast { it.index < target.index }.coerceAtLeast(0)
            durVolumeIndex = volumeIdx
            upEpisodes()
            chapterInVolumeIndex = episodes.indexOf(target).coerceAtLeast(0)
        }
        upEpisodes()
    }

    /** 按当前卷切出集列表：无卷时就是全部非卷章节。 */
    private fun upEpisodes() {
        episodes = if (volumes.isEmpty()) {
            toc.filter { !it.isVolume }
        } else {
            val start = (volumes.getOrNull(durVolumeIndex)?.index ?: -1) + 1
            val end = volumes.getOrNull(durVolumeIndex + 1)?.index ?: toc.size
            if (end > start) toc.subList(start.coerceAtLeast(0), end).filter { !it.isVolume }
            else emptyList()
        }
        _state.update {
            it.copy(
                volumes = volumes.map { volume -> volume.title },
                volumeIndex = durVolumeIndex,
                episodes = episodes.map { episode -> episode.title },
                episodeIndex = chapterInVolumeIndex,
            )
        }
    }

    // ——————————————————————————— 播放控制 ———————————————————————————

    suspend fun playCurrent(startPos: Int? = null, autoPlay: Boolean = this.autoPlay) {
        val book = book ?: return
        val source = bookSource ?: return
        val chapter = episodes.getOrNull(chapterInVolumeIndex)
        if (chapter == null) {
            fail(IllegalStateException(appCtx.getString(R.string.chapter_not_found)))
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        val position = (startPos ?: durChapterPos).toLong()
        val key = cacheKey(source, book, chapter)
        val cached = linkCache[key]
            ?.takeIf { System.currentTimeMillis() - it.createdAt <= CHAPTER_LINK_TTL }
            ?.media
        val media = cached ?: withContext(Dispatchers.IO) {
            runCatching { contentRepository.loadMedia(source, book, chapter) }.getOrNull()
        }
        if (media == null) {
            fail(IllegalStateException(appCtx.getString(R.string.video_link_not_found)))
            return
        }
        linkCache[key] = CachedMedia(media, System.currentTimeMillis())
        this.chapter = chapter
        playUrl = media.playUrl
        danmaku = media.danmaku
        videoTitle = chapter.title
        player.setMediaItem(media.mediaItem, position.coerceAtLeast(0))
        player.playbackParameters = player.playbackParameters.withSpeed(playSpeed)
        player.prepare()
        if (autoPlay) player.play()
        _state.update {
            it.copy(
                bookName = book.name,
                chapterTitle = chapter.title,
                isLoading = false,
                error = null,
                playUrl = media.playUrl,
                danmaku = media.danmaku,
            )
        }
        saveRead(0)
        preloadNextEpisode()
    }

    /** 上一集 / 下一集：跨卷自动跳到相邻卷的首/末集。 */
    fun upDurIndex(delta: Int) {
        if (singleUrl) return
        val current = episodes.getOrNull(chapterInVolumeIndex) ?: return
        saveRead(0)
        if (delta > 0) {
            if (chapterInVolumeIndex < episodes.lastIndex) {
                chapterInVolumeIndex += 1
            } else if (durVolumeIndex < volumes.lastIndex) {
                durVolumeIndex += 1
                upEpisodes()
                chapterInVolumeIndex = 0
            } else {
                return
            }
        } else {
            if (chapterInVolumeIndex > 0) {
                chapterInVolumeIndex -= 1
            } else if (durVolumeIndex > 0) {
                durVolumeIndex -= 1
                upEpisodes()
                chapterInVolumeIndex = episodes.lastIndex.coerceAtLeast(0)
            } else {
                return
            }
        }
        if (episodes.getOrNull(chapterInVolumeIndex) === current) return
        launch { playCurrent(startPos = 0) }
    }

    /** 选集：先选卷再选集，与上游的卷/集两级结构一致。 */
    fun selectEpisode(volumeIndex: Int, episodeIndex: Int) {
        if (singleUrl) return
        saveRead(0)
        durVolumeIndex = volumeIndex.coerceIn(0, volumes.lastIndex.coerceAtLeast(0))
        upEpisodes()
        val target = episodes.getOrNull(episodeIndex) ?: return
        chapterInVolumeIndex = episodes.indexOf(target)
        _state.update { it.copy(episodeIndex = chapterInVolumeIndex) }
        launch { playCurrent(startPos = 0) }
    }

    fun selectVolume(volumeIndex: Int) {
        if (singleUrl) return
        saveRead(0)
        durVolumeIndex = volumeIndex.coerceIn(0, volumes.lastIndex.coerceAtLeast(0))
        upEpisodes()
        chapterInVolumeIndex = 0
        _state.update { it.copy(episodeIndex = 0) }
        launch { playCurrent(startPos = 0) }
    }

    fun setSpeed(speed: Float) {
        playSpeed = speed
        player.playbackParameters = player.playbackParameters.withSpeed(speed)
    }

    fun togglePlay() {
        if (player.isPlaying) pause() else resume()
    }

    fun pause() {
        if (player.isPlaying) player.pause()
        saveRead()
    }

    fun resume() {
        player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
    }

    // ——————————————————————————— 进度与收尾 ———————————————————————————

    /** 落库进度；无书籍上下文时按播放地址记缓存（临时直链）。 */
    fun saveRead(pos: Int? = null) {
        val book = book
        val currentPos = pos ?: runCatching { player.currentPosition }.getOrNull()
            ?.coerceAtLeast(0)?.toInt() ?: durChapterPos
        durChapterPos = currentPos
        if (book == null) {
            playUrl?.let { url ->
                CacheManager.put(VIDEO_POS_PREFIX + url, currentPos, VIDEO_POS_SAVE_TIME)
            }
            return
        }
        val chapter = this.chapter
        val time = System.currentTimeMillis()
        val index = chapter?.let { toc.indexOf(it) }?.takeIf { it >= 0 } ?: book.durChapterIndex
        book.lastCheckCount = 0
        book.durChapterTime = time
        book.durChapterIndex = index
        book.durChapterPos = currentPos
        book.durChapterTitle = chapter?.title
        launch(Dispatchers.IO) {
            appDb.bookDao.update(book)
            SourceCallBack.callBackBook(
                SourceCallBack.SAVE_READ, bookSource, book, chapter, time.toString()
            )
        }
    }

    /** 结束会话：保存进度、回调书源、释放播放器与临时文件。 */
    fun stop() {
        saveRead()
        val book = book
        if (book != null && inBookshelf) {
            SourceCallBack.callBackBook(SourceCallBack.END_READ, bookSource, book, chapter)
        }
        isReleasing = true
        player.stop()
        player.clearMediaItems()
        linkCache.clear()
        preloadingKeys.clear()
        launch(Dispatchers.IO) { contentRepository.clearTempFiles() }
        _state.update { VideoPlayState() }
        isReleasing = false
    }

    /** 退出播放页：不再需要播放器实例时释放，服务停止后调用。 */
    fun release() {
        isReleasing = true
        runCatching { playerInstance?.release() }
        playerInstance = null
        isReleasing = false
    }

    private fun fail(throwable: Throwable) {
        val message = throwable.message ?: appCtx.getString(R.string.load_failed)
        _state.update { it.copy(isLoading = false, error = message) }
    }

    private fun cacheKey(source: BookSource, book: Book, chapter: BookChapter): String =
        "${source.bookSourceUrl}|${book.bookUrl}|${chapter.url}"

    /** 预解析下一集的播放地址，让切集不用等一次正文请求。 */
    private fun preloadNextEpisode() {
        val source = bookSource ?: return
        val book = book ?: return
        val next = episodes.getOrNull(chapterInVolumeIndex + 1) ?: return
        val key = cacheKey(source, book, next)
        val fresh = linkCache[key]
            ?.takeIf { System.currentTimeMillis() - it.createdAt <= CHAPTER_LINK_TTL }
        if (fresh != null || !preloadingKeys.add(key)) return
        launch(Dispatchers.IO) {
            preloadMutex.withLock {
                try {
                    if (linkCache[key]?.let {
                            System.currentTimeMillis() - it.createdAt <= CHAPTER_LINK_TTL
                        } == true
                    ) return@withLock
                    // 解析整份媒体（含 headers 编码），切集时可以直接复用
                    val media = contentRepository.loadMedia(source, book, next)
                        ?: return@withLock
                    linkCache[key] = CachedMedia(media, System.currentTimeMillis())
                } catch (_: Throwable) {
                    // 预解析失败不影响当前播放
                } finally {
                    preloadingKeys.remove(key)
                }
            }
        }
    }
}
