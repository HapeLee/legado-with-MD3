package io.legado.app.service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * HttpTTS 朗读缓存的保留期边界。
 *
 * 「音频缓存保留时间」= 0 是即听即焚：缓存目录里不该长期留下音频。两个目录都归这条设置管：
 * `httpTTS`（文件合成按 md5 落的 `.mp3`，听书下载也先合成到这里）和 `httpTTS_cache`
 * （流式朗读经 Media3 `CacheDataSource`/`CacheDataSink` 写的分片）。
 *
 * 触发点比覆盖面更容易失守：`removeCacheFile()` 只挂在 `onDestroy`，进程被杀、划掉最近任务、
 * 崩溃都跳过它，所以起手清扫和下载即删才是这个语义真正成立的地方。
 * `filesDir/readAloudAudio`（听书下载区）是用户显式下载的，永远不进自动清理。
 */
class HttpTtsCacheRetentionTest {

    @Test
    fun `stream cache folder is the single name shared by the player cache and the cleaner`() {
        val source = mainSourceFile("io/legado/app/service/HttpReadAloudService.kt").readText()

        // 目录名只在一处定义（streamCacheFolder），SimpleCache 与清理都引它，避免又写出第三个目录。
        assertEqualsBetween(
            "stream cache dir literal count",
            1,
            Regex("\"httpTTS_cache\"").findAll(source).count(),
        )
        val cacheDeclaration = section(source, "private val cache by lazy {", "\n    }")
        assertTrue(
            "SimpleCache must be built on streamCacheFolder",
            cacheDeclaration.contains("streamCacheFolder"),
        )
    }

    @Test
    fun `retention cleaner covers both cache folders`() {
        val source = mainSourceFile("io/legado/app/service/HttpReadAloudService.kt").readText()
        val cleaner = section(source, "private fun removeCacheFile()", "\n    }")

        assertTrue(cleaner.contains("ttsFolderPath"))
        assertTrue(
            "streamCacheFolder must be swept by the retention policy",
            cleaner.contains("streamCacheFolder"),
        )
        assertTrue(
            "the sweep must stay behind the keepTime == 0 (即听即焚) gate",
            cleaner.contains("keepTime == 0L"),
        )
    }

    @Test
    fun `retention cleaner never touches the audio download area`() {
        val source = mainSourceFile("io/legado/app/service/HttpReadAloudService.kt").readText()
        val cleaner = withoutComments(section(source, "private fun removeCacheFile()", "\n    }"))

        assertTrue(
            "readAloudAudio is user-requested 听书下载 and must never be auto-cleaned",
            !cleaner.contains("readAloudAudio"),
        )
    }

    @Test
    fun `burn after read sweeps leftovers when the service starts`() {
        val source = mainSourceFile("io/legado/app/service/HttpReadAloudService.kt").readText()

        // onDestroy 不是唯一触发点：进程被杀时它根本不会跑，残留要等下一次起手清扫。
        assertTrue(
            "onCreate must sweep leftovers from a session that never got destroyed",
            section(source, "override fun onCreate() {", "\n    }")
                .contains("sweepBurnAfterReadLeftovers()"),
        )
        val sweep = section(source, "private fun sweepBurnAfterReadLeftovers()", "\n    }")
        assertTrue(
            "清扫只在即听即焚下动手：保留一段时间时开播前删掉当前章缓存会逼出整章重复合成",
            sweep.contains("if (!cacheBurnAfterRead) return"),
        )
        assertTrue(sweep.contains("ttsFolderPath"))
        assertTrue(sweep.contains("streamCacheFolder"))
    }

    @Test
    fun `downloaded sentences drop their cache copy right away`() {
        val source = mainSourceFile("io/legado/app/service/HttpReadAloudService.kt").readText()
        val finish = section(source, "private suspend fun finishAudioDownloadSentence(", "\n    }")

        // 一次下载能合成整本书的音频到缓存目录；等不到服务销毁的清扫，占用会一路涨。
        assertTrue(
            "saveFrom 之后缓存副本没有别的用途（播放先认下载区），即听即焚下当场删",
            finish.contains("cacheBurnAfterRead"),
        )
        assertTrue(
            "删除必须发生在落进下载区之后",
            finish.indexOf("saveFrom(") < finish.indexOf("cacheBurnAfterRead"),
        )
    }

    private companion object {
        /** 只留代码：跨目录语义的说明注释会提到下载区路径名，断言管的是行为不是文案。 */
        fun withoutComments(text: String): String =
            text.lines().joinToString("\n") { line ->
                line.substringBefore("//")
            }

        fun assertEqualsBetween(label: String, expected: Int, actual: Int) {
            assertTrue("$label: expected $expected, got $actual", expected == actual)
        }

        fun section(source: String, startMarker: String, endMarker: String): String {
            val start = source.indexOf(startMarker)
            assertTrue("$startMarker not found", start >= 0)
            val rest = source.substring(start)
            val end = rest.indexOf(endMarker, startMarker.length)
            return if (end < 0) rest else rest.substring(0, end)
        }

        fun mainSourceFile(relativePath: String): File {
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                for (prefix in listOf("src/main/java", "app/src/main/java")) {
                    val candidate = File(directory, "$prefix/$relativePath")
                    if (candidate.isFile) return candidate
                }
                directory = directory.parentFile
            }
            error("从 ${File("").absolutePath} 向上找不到 $relativePath")
        }
    }
}
