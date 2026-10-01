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
 * 三条都是被要求的：
 * 1. **不读总音量**——背景音乐那根滑杆只管背景音乐，音效「原本的音频多大声就多大声」；
 * 2. **不占主线程**——这个回调就在朗读播放器的主线程上跑，`prepare()` 是同步读文件的，
 *    一首几 MB 的音效能把主线程按住几百毫秒，表现就是「音效和 TTS 一起顿一下才响」。
 *    所以走 `prepareAsync`，读完了再在同一个 Handler 上起播。
 * 3. **到点就响**：[prime] 在句子开始时就先把播放器建好、把文件读完，[play] 只做 `start()`。
 *    否则「按媒体时钟排到命中处」这件事会被 prepare 那几百毫秒拖掉——用户听到的
 *    「读到匹配处两秒后才响」一半是排期基准错，一半就是这里。
 */
class ReadAloudEffectPlayer(private val context: Context) {

    /** MediaPlayer 的回调要挂在有 Looper 的线程上，服务的工作线程没有，统一丢主线程。 */
    private val handler = Handler(Looper.getMainLooper())
    private val active = ArrayList<MediaPlayer>()

    /** [prime] 备好、还没响的播放器：到点 [play] 只要 `start()`，不再等 prepare。 */
    private val ready = HashMap<String, MediaPlayer>()
    private var released = false

    /** 一条音频响一次；文件读不出来（被删/格式不支持）就静默跳过。 */
    fun play(path: String) {
        if (released || path.isBlank()) return
        handler.post { fire(path) }
    }

    /** 提前把这条音频读好（在句子起播时调用），到点 [play] 才不用等 prepare。 */
    fun prime(path: String) {
        if (released || path.isBlank()) return
        handler.post { if (!ready.containsKey(path)) start(path, parked = true) }
    }

    /** 到点起播：有备好的就直接 start，没有就现读（会慢几百毫秒）。 */
    private fun fire(path: String) {
        if (released) return
        ready.remove(path)?.let { media ->
            active += media
            runCatching { media.start() }.onFailure { retire(media) }
            return
        }
        start(path, parked = false)
    }

    private fun start(path: String, parked: Boolean) {
        if (released || path.isBlank()) return
        val media = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        if (!parked) active += media
        val done = Runnable { retire(media) }
        media.setOnCompletionListener { done.run() }
        media.setOnErrorListener { _, _, _ -> done.run(); true }
        media.setOnPreparedListener { player ->
            if (parked) {
                if (released) retire(player) else ready[path] = player
            } else if (released || !active.contains(player)) {
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
        val parkedPath = ready.entries.firstOrNull { it.value === media }?.key
        if (parkedPath != null) ready.remove(parkedPath)
        runCatching { media.release() }
    }

    /** 朗读服务 onDestroy 调：之后 [play] 不再起新的播放器。 */
    fun release() {
        released = true
        handler.post {
            val all = ArrayList(active) + ready.values
            active.clear()
            ready.clear()
            all.forEach { media -> runCatching { media.release() } }
        }
    }
}
