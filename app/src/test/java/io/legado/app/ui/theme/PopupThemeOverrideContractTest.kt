package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 玻璃 sheet / dialog 的内容由 root popup host 的独立 composition 执行，只继承 AppTheme
 * 的全局色板。这些契约锁住「宿主侧捕获页面级覆盖 → 内容侧重放」这条链不被拆掉。
 */
class PopupThemeOverrideContractTest {

    @Test
    fun `page level color scheme override is captured on host and replayed in popup content`() {
        val overrideSource = projectPath(
            "app/src/main/java/io/legado/app/ui/theme/ThemeColorSchemeOverride.kt",
        ).readText()
        assertTrue(
            "ProvideColorSchemeOverride 必须记录当前覆盖，供宿主侧捕获",
            overrideSource.contains("LocalThemeOverride provides overrideState"),
        )

        val bridge = projectPath(
            "app/src/main/java/io/legado/app/ui/theme/PopupThemeOverride.kt",
        ).readText()
        assertTrue(
            "捕获必须在调用点 composition 读取 local，并以 State 传递，避免色板动画固化首帧",
            bridge.contains("rememberUpdatedState(LocalThemeOverride.current)") &&
                bridge.contains("State<ThemeOverrideState?>"),
        )

        val sheet = projectPath(
            "app/src/main/java/io/legado/app/ui/widget/components/modalBottomSheet/AppModalBottomSheet.kt",
        ).readText()
        val dialog = projectPath(
            "app/src/main/java/io/legado/app/ui/widget/components/alert/AppAlertDialog.kt",
        ).readText()
        listOf("sheet" to sheet, "dialog" to dialog).forEach { (name, source) ->
            assertTrue(
                "$name 必须在宿主侧捕获页面级色板，并在内容侧重放",
                source.contains("rememberPopupThemeOverride()") &&
                    source.contains("ProvidePopupThemeOverride(themeOverride)"),
            )
        }

        val locals = sheet.substringAfter("private fun MiuixSheetLocals(")
            .substringBefore("enum class MochiSheetLayout")
        val overrideIndex = locals.indexOf("ProvidePopupThemeOverride")
        val contentColorIndex = locals.indexOf("ProvideAppContentColor")
        assertTrue(
            "主题覆盖必须包在 ContentColor / host 信号之外，让后者保持更高优先级",
            overrideIndex >= 0 && contentColorIndex > overrideIndex,
        )
    }

    private fun projectPath(relativePath: String): File {
        val candidates = listOf(relativePath, relativePath.removePrefix("app/"))
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            candidates.forEach { candidatePath ->
                val candidate = File(directory, candidatePath)
                if (candidate.exists()) return candidate
            }
            directory = directory.parentFile
        }
        error("cannot locate $relativePath")
    }
}
