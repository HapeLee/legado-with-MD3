package io.legado.app.ui.rss.article

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 视频订阅源的列表点击必须直接进播放页。
 *
 * 阅读页会把正文规则解析出的 m3u8 当 HTML 加载，用户先看到的是一页视频链接文本；
 * 议题 #2562 要的正是「点开就能播」，所以分流必须发生在列表点击处。
 */
class RssSortVideoEntryContractTest {

    @Test
    fun videoSourceOpensPlayerInsteadOfReader() {
        val source = codeOf(SCREEN)
        assertTrue(
            "列表点击未按订阅源类型分流",
            source.contains("isRssVideoSource(viewModel.rssSource?.type)"),
        )
        assertTrue(
            "视频源未跳到播放页",
            source.contains("onOpenVideoPlayer(article)"),
        )
    }

    private fun codeOf(path: String): String = sourceOf(path)
        .lineSequence()
        .filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
        }
        .joinToString("\n")

    private fun sourceOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val SCREEN = "io/legado/app/ui/rss/article/RssSortRouteScreen.kt"
    }
}
