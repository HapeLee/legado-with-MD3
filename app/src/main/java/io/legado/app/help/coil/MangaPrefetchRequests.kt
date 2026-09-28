package io.legado.app.help.coil

import coil3.ColorImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okio.Buffer
import java.io.Closeable

/** 当前章持续准备原图，视口变化只重排等待项；两个后台任务不限制可见页/显式下载。主线程调用。 */
internal class MangaPrefetchRequests(private val scope: CoroutineScope) : Closeable {
    private var wanted = emptyList<Pair<String, Int>>()
    private var prepare: (suspend (Pair<String, Int>) -> Unit)? = null
    private val settled = mutableSetOf<Pair<String, Int>>()
    private val active = mutableMapOf<Pair<String, Int>, Job>()
    private var closed = false

    fun update(keys: List<Pair<String, Int>>, start: suspend (Pair<String, Int>) -> Unit) {
        if (closed) return
        wanted = keys.distinct()
        prepare = start
        val keep = wanted.toSet()
        settled.retainAll(keep)
        active.keys.filterNot(keep::contains).forEach { active.remove(it)?.cancel() }
        next()
    }

    private fun next() {
        val start = prepare ?: return
        while (!closed && active.size < 2) {
            val key = wanted.firstOrNull { it !in settled && it !in active } ?: return
            lateinit var job: Job
            job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    start(key)
                    if (active[key] === job) settled += key
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // 失败不阻塞后续页，也不循环重试；显式重试代次或重新进入章节会重新准备。
                    if (active[key] === job) settled += key
                } finally {
                    if (active[key] === job) {
                        active.remove(key); next()
                    }
                }
            }
            active[key] = job
            job.start()
        }
    }

    override fun close() {
        closed = true
        val jobs = active.values.toList()
        active.clear()
        jobs.forEach(Job::cancel)
        settled.clear()
        wanted = emptyList()
        prepare = null
    }
}

/** 与 Glide downloadOnly 一样只准备数据；结果不进入内存缓存，也不通知页面 Ready。 */
internal fun ImageRequest.asMangaPrefetch(): ImageRequest = newBuilder()
    .apply { extras[CoverExtras.MangaDataPrefetch] = true }
    .listener(null)
    .target(null)
    .memoryCachePolicy(CachePolicy.DISABLED)
    .transformations(emptyList())
    .decoderFactory { result, options, _ ->
        Decoder {
            // 带 owner 的在线原图已经完整下载和校验，无需再读整个文件。
            // 不走原图链路的流必须读到 EOF 才能完成缓存写入。
            if (options.extras[CoverExtras.MangaFileOwner] == null) {
                val source = result.source.source()
                val buffer = Buffer()
                val context = currentCoroutineContext()
                while (true) {
                    context.ensureActive()
                    if (source.read(buffer, 16 * 1024L) == -1L) break
                    buffer.clear()
                }
            }
            DecodeResult(ColorImage(0), isSampled = false)
        }
    }
    .build()
