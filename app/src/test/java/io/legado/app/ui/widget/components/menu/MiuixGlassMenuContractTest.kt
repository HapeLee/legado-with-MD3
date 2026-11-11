package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Miuix 玻璃菜单的覆盖面契约。
 *
 * 判定口径只能有一份（[isMiuixGlassMenuAvailable]）：它绑在 `AppScaffold` 建立的
 * 玻璃设施上（`LocalAppGlassOverlay` 只在「Miuix + HyperOS 4 组件 + SDK 33+ + 原生采样」
 * 时才有值）。各菜单分支若各自内联条件，就会出现「顶栏 ⋮ 是玻璃、列表 ⋮ 还是普通面板」
 * ——真机上的观感就是「OS4 组件开着却弹出 OS3 弹窗、也没有大圆角」。
 *
 * 静态契约只保证结构与判定来源，**不证明玻璃材质、圆角与锚点变形的实际渲染**。
 */
class MiuixGlassMenuContractTest {

    @Test
    fun glassAvailabilityIsDefinedOnce() {
        val owner = sourceOf(ROUND_MENU)
        assertTrue(
            "玻璃可用性判定未集中在 RoundDropdownMenu.kt",
            owner.contains("internal fun isMiuixGlassMenuAvailable()"),
        )
        listOf(ACTION_MENU, CHOICE).forEach { path ->
            val source = sourceOf(path)
            assertTrue("$path 未使用共享的玻璃判定", source.contains("isMiuixGlassMenuAvailable()"))
            assertFalse(
                "$path 内联了玻璃开关判定，会与 AppScaffold 的设施漂移",
                source.contains("useHyperOs4Components"),
            )
        }
    }

    /** Row 锚点的命令菜单（列表项 ⋮）在玻璃可用时也必须换成原生玻璃菜单。 */
    @Test
    fun rowAnchoredActionMenuUsesNativeGlassMenu() {
        val source = sourceOf(ACTION_MENU)
        assertTrue("缺玻璃分支", source.contains("val glass = isMiuixGlassMenuAvailable()"))
        assertTrue("未注册到玻璃坐标根", source.contains("MiuixGlassMenuHost("))
        assertTrue("trigger 未挂锚点，面板无法从按钮 morph", source.contains("Modifier.glassPopupAnchor(anchor"))
        // `glassPopupAnchor` 只把原控件瞬间置透明，过渡中移动的图标副本由弹层画；
        // 不把 trigger 交给 anchorContent，按钮图标就会「直接消失再出现」。
        assertTrue("未把 trigger 交给 anchorContent", source.contains("anchorContent = { scope.trigger() }"))
        // 玻璃菜单自带级联子菜单，不能再按 hasChildren 回退到普通 popup。
        assertFalse("玻璃分支仍按 hasChildren 回退", source.contains("!hasChildren"))
    }

    /** 紧凑 / 受控单选（设置行、书源类型卡）同口径。 */
    @Test
    fun compactChoiceUsesNativeGlassMenu() {
        val source = sourceOf(CHOICE)
        assertTrue("缺玻璃分支", source.contains("val glass = isMiuixGlassMenuAvailable()"))
        assertTrue("未注册到玻璃坐标根", source.contains("MiuixGlassMenuHost("))
        assertTrue("trigger 未挂锚点", source.contains("Modifier.glassPopupAnchor(anchor"))
        // 非受控时必须交出 trigger 副本，否则图标只会在开合瞬间闪现。
        assertTrue("未按受控与否交出 trigger 副本", source.contains("anchorContent = if (embeddedExpanded == null)"))
        // 受控模式没有 trigger，锚点必须回落到调用方容器。
        assertTrue("受控模式锚点未挂在容器上", source.contains("containerAnchor"))
    }

    /** 坐标根注册只允许有一份实现，否则各入口的锚点坐标系会各自漂移。 */
    @Test
    fun overlayRegistrationLivesInHost() {
        val host = sourceOf(HOST)
        assertTrue("宿主未注册到玻璃坐标根", host.contains("glassOverlay.register(key)"))
        listOf(ACTION_MENU, CHOICE).forEach { path ->
            assertFalse("$path 自己又注册了一份玻璃弹层", sourceOf(path).contains("glassOverlay.register("))
        }
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
        const val ROUND_MENU = "io/legado/app/ui/widget/components/menuItem/RoundDropdownMenu.kt"
        const val HOST = "io/legado/app/ui/widget/components/menuItem/MiuixGlassMenuHost.kt"
        const val ACTION_MENU = "io/legado/app/ui/widget/components/menu/AppActionMenu.kt"
        const val CHOICE = "io/legado/app/ui/widget/components/menu/AppChoiceDropdown.kt"
    }
}
