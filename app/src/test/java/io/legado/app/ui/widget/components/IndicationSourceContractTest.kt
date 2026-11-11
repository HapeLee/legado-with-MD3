package io.legado.app.ui.widget.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 按压反馈的来源契约：组件不得写死 M3 `ripple(...)`，一律读 `LocalIndication`。
 * 这样主题层按引擎注入 Indication 时，才能覆盖到项目自己的按钮组件，
 * 而不是只覆盖那些不带参数、默认吃 `LocalIndication` 的 `Box(clickable)`。
 */
class IndicationSourceContractTest {
    @Test
    fun seriesAndTopBarButtonsUseThemeIndication() {
        listOf(SERIES_BUTTON, TOP_BAR_BUTTON).forEach { path ->
            val source = sourceOf(path)
            assertFalse("$path 仍写死 ripple(...)，主题注入无法生效", source.contains("ripple("))
            assertTrue("$path 未读取 LocalIndication", source.contains("LocalIndication.current"))
        }
    }

    @Test
    fun navigationBarKeepsExplicitUnboundedRipple() {
        assertTrue(
            "导航项的 32dp 无界反馈是刻意保留的例外，不要一并解耦",
            sourceOf(NAVIGATION_BAR).contains("ripple(bounded = false, radius = 32.dp)"),
        )
    }

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
        const val SERIES_BUTTON = "io/legado/app/ui/widget/components/button/series/SeriesIconButton.kt"
        const val TOP_BAR_BUTTON = "io/legado/app/ui/widget/components/topbar/TopBarButton.kt"
        const val NAVIGATION_BAR = "io/legado/app/ui/widget/components/navigation/AppNavigationBar.kt"
    }
}
