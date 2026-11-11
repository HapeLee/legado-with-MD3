package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 触发按钮的「玻璃底」契约：同排按钮是玻璃胶囊时，⋮ 不能是唯一的裸按钮。
 *
 * 真机实测（2026-11-11）：COUI 下 ⋮ 没有玻璃底（相邻按钮有），Miuix 下同样缺失。
 * 原因是两个分支各自绕过了引擎的玻璃实现：
 * - COUI 用普通 `IconButton`，而 `TopBarActionButton` 走
 *   `rememberCouiCapsuleRenderer()` + `CouiTopAppBarButton`，玻璃底在后者里；
 * - Miuix 分支的玻璃开关只看 `isMiuixHyperOs4Enabled()`，而顶栏按钮看
 *   `LocalMiuixGlassTopBar`，两者不等价时 ⋮ 会落到库自带的普通 `IconButton`。
 *
 * JVM/Robolectric 无法渲染这两套玻璃材质，因此这里只锁「走哪条分支」的结构契约；
 * 玻璃观感与 Miuix 打开时的单帧锚点位置**仍需真机复核**。
 */
class MenuTriggerGlassContractTest {

    @Test
    fun couiTriggerUsesNativeCapsuleButton() {
        val source = sourceOf(APP_ACTION_MENU)
        val coui = source
            .substringAfter("private fun CouiActions(")
            .substringBefore("private fun MiuixActions(")
        assertTrue("COUI 分支未复用胶囊渲染器", coui.contains("rememberCouiCapsuleRenderer()"))
        assertTrue("COUI 分支未渲染原生顶栏按钮", coui.contains("CouiTopAppBarButton("))
        assertTrue("缺少原生工具条判定", coui.contains("LocalAppCouiNativeToolbar.current"))
        assertTrue("缺少 COUI 能力判定", coui.contains("ThemeResolver.isCouiSupported()"))
        assertTrue("非原生环境下丢弃了普通按钮兜底", coui.contains("IconButton("))
    }

    @Test
    fun miuixGlassGateFollowsTopBarSignal() {
        val source = sourceOf(APP_ACTION_MENU)
        val miuix = source
            .substringAfter("private fun MiuixActions(")
            .substringBefore("private fun MiuixGlassActions(")
        assertTrue(
            "Miuix 玻璃分支未跟随顶栏的玻璃信号，⋮ 会缺玻璃",
            miuix.contains("LocalMiuixGlassTopBar.current"),
        )
        assertFalse(
            "Miuix 玻璃分支仍只看 HyperOS 4 开关，与同排按钮判定不一致",
            miuix.contains("isMiuixHyperOs4Enabled()"),
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
        const val APP_ACTION_MENU = "io/legado/app/ui/widget/components/menu/AppActionMenu.kt"
    }
}
