package io.legado.app.help.readaloud.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/**
 * 朗读时的第三条音轨：正则角色里「命中不念、改放音频」放的那一下。
 *
 * 与背景音乐轨同样的硬约束：**不申请音频焦点**（朗读服务已经持有 AUDIOFOCUS_GAIN，
 * 这里再要一次会把 TTS 挤掉，见 [ReadAloudBgmPlayer] 的注释）。差别是它不循环、不渐变：
 * 一次触发响一次，同一个朗读单元上挂了几条就同时响几条，响完自己释放。
 *
 * 两条都是被要求的：
 * 1. **不读总音量**——背景音乐那根滑杆只管背景音乐，音效「原本的音频多大声就多大声」；
 * 2. **不占主线程**——这个回调就在朗读播放器的主要件线程上跑，`prepare()` 是同步读文件的，
 *    一首几 MB 的音效能把主线程按住几百毫秒，表现就是「音效和 TTS 一起顿一下才响」。
 *    所以走 `prepareAsync`，读完了再在同一个 Handler 上起播。
 */
class ReadAloudEffectPlayer(private val context: Context) {

    /** MediaPlayer 的回调要挂在有 Looper 的线程上，服务的工作线程没有，统一丢主线程。 */
    private val handler = Handler(Looper.getMainLooper())
    private val active = ArrayList<MediaPlayer>()
    private var released = false

    /** [paths] 里一条路径响一次；文件读不出来（被删/格式不支持）就静默跳过。 */
    fun play(paths: List<String>) {
        if (released || paths.isEmpty()) return
        handler.post { paths.forEach { start(it) } }
    }

    fun play(path: String) = play(listOf(path))

    private fun start(path: String) {
        if (released || path.isBlank()) return
        val media = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        active += media
        val done = Runnable { retire(media) }
        media.setOnCompletionListener { done.run() }
        media.setOnErrorListener { _, _, _ -> done.run(); true }
        media.setOnPreparedListener { player ->
            if (released || !active.contains(player)) {
                retire(player)
            } else {
                runCatching { player.start() }.onFailure { retire(player) }
            }
        }
        val ok = runCatching {
            media.setDataSource(path)
            media.prepareAsync()
        }.isSuccess
        if (!ok) retire(media)
    }

    private fun retire(media: MediaPlayer) {
        active.remove(media)
        runCatching { media.release() }
    }

    /** 朗读服务 onDestroy 调：之后 [play] 不再起新的播放器。 */
    fun release() {
        released = true
        handler.post {
            val all = ArrayList(active)
            active.clear()
            all.forEach { media -> runCatching { media.release() } }
        }
    }
}
