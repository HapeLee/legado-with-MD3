package io.legado.app.help.readaloud.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import io.legado.app.data.entities.BgmSceneMark
import io.legado.app.help.readaloud.cast.BgmPoolStore
import io.legado.app.help.readaloud.cast.BgmSceneStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 朗读时的背景音乐轨：与 TTS 各自一条播放器并行跑，互不打断。
 *
 * 三条硬约束（都是被要求的）：
 * 1. **不抢音频焦点** —— 朗读服务已经持有 AUDIOFOCUS_GAIN，这里再要一次会把 TTS 挤掉；
 * 2. **章节标题不播** —— 只有朗读位置进入正文后才起乐（[syncTo] 的 onTitle 参数）；
 * 3. **只按场景换乐** —— 段序号来自正文段落，与正文 ♪ 胶囊、AI 分配场景同一套计数。
 *
 * 单曲循环：一个场景往往跨好几页，播完就停会让用户以为功能坏了。
 */
class ReadAloudBgmPlayer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var fadeJob: Job? = null

    /**
     * 取场景标记的那次挂起任务：必须单独记住，[release] 才取消得了它。
     *
     * 换章后第一次进度回调会走到这里去库里读标记（几十毫秒）。这期间用户点了「取消朗读」，
     * 服务 onDestroy 把 player 释放了，但这个任务还活着——它回来后会照常起一条新配乐，
     * 而且再没人能停它（服务已经没了），听起来就是「朗读停了 BGM 还在播」。
     */
    private var syncJob: Job? = null

    private var bookUrl = ""
    private var chapterIndex = -1
    /** 正文段落起始位置（章节语义坐标）→ 段序号，随章节装载刷新。 */
    private var positions: List<Pair<Int, Int>> = emptyList()
    private var marks: List<BgmSceneMark> = emptyList()
    private var marksVersion = -1
    /** 当前在播的场景（池名），null = 无音乐。 */
    private var currentMark: BgmSceneMark? = null
    private var currentPath: String? = null
    /** 在播这条配乐自己的音量，与本段音量相乘后才是播放器音量（拖滑杆时不必重新取库）。 */
    private var trackVolume = 1f
    /** 播放器当前应有的音量（渐变的两端都以它为准），已含总音量。 */
    private var targetVolume = 1f
    /**
     * 播放器此刻**真实**的音量（[ramp] 每一步、以及直接 setVolume 的那两处都会更新）。
     *
     * 每一次淡出都必须从这里起跳：拿 [targetVolume] 当起点会把一条已经淡到一半的曲子
     * 先顶响再淡，听起来就是「突然响一下」。
     */
    private var appliedVolume = 1f
    /** 上次读到的背景音乐总音量版本，用来发现「用户刚拖了朗读设置里的滑杆」。 */
    private var masterVersion = -1
    /** 朗读是否暂停：暂停时配乐一起停，恢复时把在播的那条接着起，不重新取库。 */
    private var paused = false
    /** 正在 prepare、还没交给 [player] 的那条：release 也要能把它放掉。 */
    private var pendingMedia: MediaPlayer? = null
    private var lastPosition = -1
    private var lastOnTitle = false

    @Volatile
    private var released = false

    /** 换章：记住坐标表并让场景缓存过期（下次 syncTo 重新取）。 */
    fun onChapter(bookUrl: String, chapterIndex: Int, positions: List<Pair<Int, Int>>) {
        this.bookUrl = bookUrl
        this.chapterIndex = chapterIndex
        this.positions = positions
        marksVersion = -1
        lastPosition = -1
    }

    /**
     * 朗读位置推进（章节语义坐标）。标题段传 [onTitle] = true → 静音。
     *
     * 每次进度回调都会进来，所以这里必须先按位置去重，否则每帧都会重算场景。
     */
    fun syncTo(chapterPosition: Int, onTitle: Boolean) {
        if (released || positions.isEmpty()) return
        // 总音量刚被拖过：位置没动也要重算一次，朗读设置里的滑杆才有即时反馈
        val volumeChanged = masterVersion != BgmPoolStore.volumeVersion
        if (!volumeChanged && chapterPosition == lastPosition && onTitle == lastOnTitle) return
        lastPosition = chapterPosition
        lastOnTitle = onTitle
        val ordinal = ordinalAt(chapterPosition)
        syncJob?.cancel()
        syncJob = scope.launch {
            if (marksVersion != BgmSceneStore.version) {
                marks = withContext(Dispatchers.IO) { BgmSceneStore.marks(bookUrl, chapterIndex) }
                marksVersion = BgmSceneStore.version
            }
            // 读库期间用户可能已经取消朗读：这里不拦，回来就会凭空起一条没人能停的配乐
            if (released) return@launch
            masterVersion = BgmPoolStore.volumeVersion
            val mark = if (onTitle || ordinal < 0) null else marks.lastOrNull { it.paragraphOrdinal <= ordinal }
            applyMark(mark)
        }
    }

    /** 朗读暂停：配乐一起停，但保住在播的这条（恢复时不必重新取库、不必重走缓入）。 */
    fun pause() {
        paused = true
        runCatching { if (player?.isPlaying == true) player?.pause() }
    }

    /** 朗读恢复：接着播暂停所在那条；已切出场景或已停止就没有音乐。 */
    fun resume() {
        paused = false
        if (released || currentPath == null) return
        runCatching { player?.start() }
    }

    fun release() {
        released = true
        // 只 cancel fadeJob 不够：syncJob 会在读库回来后重新起一条配乐，
        // 而服务已经销毁，再也没有人停它。整个作用域一起收掉。
        syncJob?.cancel()
        fadeJob?.cancel()
        scope.cancel()
        releaseQuietly(player)
        player = null
        releaseQuietly(pendingMedia)
        pendingMedia = null
        currentPath = null
    }

    /** 段序号：位置表按升序，取最后一个不晚于当前位置的段。 */
    private fun ordinalAt(chapterPosition: Int): Int {
        var index = positions.lastIndex
        while (index >= 0) {
            if (positions[index].first <= chapterPosition) return positions[index].second
            index--
        }
        return -1
    }

    private suspend fun applyMark(mark: BgmSceneMark?) {
        if (released) return
        if (mark == null) {
            stop()
            currentMark = null
            return
        }
        val sameScene = currentMark?.paragraphOrdinal == mark.paragraphOrdinal &&
            currentMark?.poolName == mark.poolName && currentMark?.trackName == mark.trackName
        if (sameScene) {
            // 同一场景：只可能改了音量，就地改播放器音量，不重新起播。
            //
            // 这里不能再附加「播放器已经就位」的条件：换曲要 900ms 淡出 + 一次 prepare，
            // 这期间进度回调会带着同一个场景再进来好几次，一旦放行就会 cancel 掉正在跑的
            // switchTo —— 淡出被硬切，上一条 MediaPlayer 也没人放掉。
            currentMark = mark
            setTargetVolume(levelOf(mark))
            return
        }
        val resolved = withContext(Dispatchers.IO) { BgmSceneStore.resolve(mark) } ?: run {
            stop()
            currentMark = mark
            return
        }
        if (released) return
        currentMark = mark
        trackVolume = resolved.trackVolume
        val target = levelOf(mark)
        if (resolved.path == currentPath && player != null) {
            setTargetVolume(target)
            if (!paused) {
                runCatching { player?.start() }
            }
            return
        }
        switchTo(resolved.path, target)
    }

    /**
     * 这一段的实际播放器音量 = 背景音乐总音量 × 在播曲目自身音量 × 本段音量。
     *
     * 三层各管一件事：总音量是「配乐相对人声整体多响」，曲目音量是「这首曲子偏响/偏闷」，
     * 段内音量是「这一场要压一点」。任何一层都能单独调，不用重跑另外两层。
     */
    private fun levelOf(mark: BgmSceneMark): Float =
        (BgmPoolStore.volume() * trackVolume * mark.volume.coerceIn(0f, 1f)).coerceIn(0f, 1f)

    /** 目标音量变了：正在渐变时交给渐变任务，否则立刻改，正在播的曲子音量跟着动。 */
    private fun setTargetVolume(volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
        if (fadeJob?.isActive != true) {
            appliedVolume = targetVolume
            runCatching { player?.setVolume(targetVolume, targetVolume) }
        }
    }

    /** 换曲：按「背景音乐池 → 设置」里的缓入/缓出独立开关做音量渐变，两个都不开就硬切。 */
    private fun switchTo(path: String, target: Float) {
        fadeJob?.cancel()
        // 上一首淡出的起点是它此刻的真实音量，用新目标当起点会让小声的曲子突然变大声
        val oldVolume = appliedVolume
        targetVolume = target.coerceIn(0f, 1f)
        val old = player
        player = null
        pendingMedia = old
        fadeJob = scope.launch {
            val fadeOut = BgmPoolStore.fadeOut()
            val fadeIn = BgmPoolStore.fadeIn()
            if (old != null) {
                // finally：淡出任务被取消（换场景、暂停、停止朗读）也必须把上一条停掉，
                // 否则它会带着 isLooping 一直放下去
                try {
                    if (fadeOut && runCatching { old.isPlaying }.getOrDefault(false)) {
                        ramp(old, FADE_MS, oldVolume, 0f)
                    }
                } finally {
                    releaseQuietly(old)
                    if (pendingMedia === old) pendingMedia = null
                }
            }
            if (released) return@launch
            val next = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
            }
            pendingMedia = next
            val ok = runCatching {
                next.setDataSource(path)
                next.isLooping = true
                next.prepare()
            }.isSuccess
            if (!ok || released) {
                releaseQuietly(next)
                if (pendingMedia === next) pendingMedia = null
                currentPath = null
                return@launch
            }
            player = next
            currentPath = path
            appliedVolume = if (fadeIn) 0f else targetVolume
            next.setVolume(appliedVolume, appliedVolume)
            // 朗读此时是暂停状态：只挂好播放器，起播交给 resume()
            if (paused) return@launch
            runCatching { next.start() }
            if (fadeIn) ramp(next, FADE_MS, 0f, targetVolume)
        }
    }

    private suspend fun ramp(media: MediaPlayer, durationMs: Long, from: Float, to: Float) {
        val steps = max(1, (durationMs / 50).toInt())
        for (step in 0..steps) {
            val value = min(1f, max(0f, from + (to - from) * step / steps))
            appliedVolume = value
            runCatching { media.setVolume(value, value) }
            if (step < steps) delay(50)
        }
    }

    /**
     * 停：把在播的这条按缓出走完再释放。
     *
     * 没有音乐在播就直接返回，**不要碰 fadeJob**：朗读位置离开场景之后，每一次进度回调都会
     * 再调一次这里，而进度回调大约 200ms 一次、淡出要 900ms——先 cancel 会让上一趟淡出
     * 走到一半就被 `releaseQuietly` 硬切，用户听到的就是「突然停」而不是「慢慢变小」。
     */
    private fun stop() {
        val media = player ?: return
        currentPath = null
        player = null
        fadeJob?.cancel()
        pendingMedia = media
        fadeJob = scope.launch {
            try {
                if (BgmPoolStore.fadeOut() && runCatching { media.isPlaying }.getOrDefault(false)) {
                    ramp(media, FADE_MS, appliedVolume, 0f)
                }
            } finally {
                releaseQuietly(media)
                if (pendingMedia === media) pendingMedia = null
            }
        }
    }

    /** 停 + 放： MediaPlayer 不 release 就一直在音频轨上挂着，isLooping 的曲子会永远放下去。 */
    private fun releaseQuietly(media: MediaPlayer?) {
        media ?: return
        runCatching { media.stop() }
        runCatching { media.release() }
    }

    private companion object {
        /** 渐变时长：场景切换处 0.9 秒，够听出「换了」又不会盖过人声。 */
        const val FADE_MS = 900L
    }
}
