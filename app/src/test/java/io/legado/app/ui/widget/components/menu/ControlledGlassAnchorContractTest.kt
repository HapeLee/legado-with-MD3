package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 受控玻璃菜单锚点回归契约（第四十八轮；第四十九轮按交付决策调整）。
 *
 * 用户实测：Miuix HyperOS 4 下「发现 / 订阅」的源长按菜单弹窗固定在窗口左上角，
 * 关闭时抛 `IllegalArgumentException: Corner size in Px can't be negative(-0.33...)`。
 *
 * 两个现象同源：`Modifier.glassPopupAnchor` 同时写 `GlassPopupAnchor.containerBounds`
 * 与 `cornerRadius`。`AppActionMenu.MiuixRowActions` 支持 `embeddedExpanded` 后，
 * 受控分支跳过了带锚点的 trigger Box，却仍注册 `MiuixGlassMenuHost`；anchor 保持
 * `Rect.Zero` / `0.dp`，`GlassTransformPopup` 于是从窗口原点展开；收起插值的
 * `startRadius` 为 0 时越过 0 就得到负圆角。迁移前这两页走 `RoundDropdownMenu` 的
 * `GlassDropdownPopup`（真实父矩形 + `translate(-origin)`），所以不会这样。
 *
 * 唯一防线是 app 侧给受控容器挂 `glassPopupAnchor`：它写入正的起点圆角后，插值不会
 * 越界成负值。`modules/miuix` 是 gitlink submodule，库侧改动不会随主仓提交、交付会分叉，
 * 因此第四十九轮撤销了库侧钳制，vendored 源码保持原版。
 *
 * 静态契约只锁结构，**不证明玻璃材质、像素位置与收起动画**；后者已于 2026-11-13
 * 由用户在 OnePlus / SDK 37 真机复核通过。
 */
class ControlledGlassAnchorContractTest {

    /** AppActionMenu 受控容器必须自己发布锚点矩形与起点圆角。 */
    @Test
    fun controlledRowActionsPublishGlassAnchorBounds() {
        val body = sourceOf(APP_ACTION_MENU)
            .substringAfter("private fun MiuixRowActions(")
            .substringBefore("private fun MiuixActions(")
        assertTrue(
            "受控模式未把 glassPopupAnchor 挂到容器上：anchor 会保持 Rect.Zero / 0.dp",
            body.contains("val containerAnchor = if (glass && embeddedExpanded != null)"),
        )
        assertTrue(
            "容器未透传 containerAnchor，玻璃面板仍会从窗口左上角 morph",
            body.contains("Box(modifier.then(containerAnchor))"),
        )
        assertTrue(
            "受控容器锚点没有写正值 cornerRadius，收起插值会得到负圆角",
            body.contains("Modifier.glassPopupAnchor(anchor, cornerRadius = 12.dp)"),
        )
        assertFalse(
            "MiuixRowActions 仍把裸 modifier 交给容器（锚点只挂在非受控 trigger 上）",
            body.contains("Box(modifier) {"),
        )
    }

    /** 两个声明式入口的受控模式必须同口径，只修一个会留下另一条左上角路径。 */
    @Test
    fun bothControlledEntriesUseTheSameContainerAnchor() {
        listOf(APP_ACTION_MENU, APP_CHOICE_DROPDOWN).forEach { path ->
            val source = sourceOf(path)
            assertTrue("$path 受控模式没有容器锚点，玻璃 anchor 会退化", source.contains("containerAnchor"))
            assertTrue(
                "$path 的 containerAnchor 没有落到容器 Box 上",
                source.contains("Box(modifier.then(containerAnchor))"),
            )
        }
    }

    /**
     * vendored 库保持原版：负圆角由 app 侧的正起点圆角避免，而不是靠子仓补丁。
     * `modules/miuix` 是 gitlink，改子仓文件不会进主仓提交。
     */
    @Test
    fun glassPanelKeepsVendoredRadiusUnpatched() {
        // 只看 MeasuredCorner：同文件阴影 outset 本来就有 4 处 coerceAtLeast(0f)，
        // 按整个文件断言会误报。
        val corner = repoFileOf(GLASS_POPUP_SURFACE)
            .substringAfter("private class MeasuredCorner(")
        assertFalse(
            "vendored Miuix 的 MeasuredCorner 又被改了：submodule 改动不会随主仓提交，交付会分叉；请改 app 侧锚点",
            corner.contains("coerceAtLeast"),
        )
        assertTrue(
            "MeasuredCorner 应保持上游原版实现，负圆角由 app 侧的正起点圆角避免",
            corner.contains("with(density) { measured.cornerRadius.toPx() }"),
        )
        val transform = repoFileOf(GLASS_TRANSFORM_POPUP)
        assertTrue(
            "库仍按起点圆角插值：必须由 glassPopupAnchor 写入正值，收起时才不会越界成负圆角",
            transform.contains("cornerRadius = startRadius + (endRadius - startRadius) * sizeFraction"),
        )
    }

    private fun sourceOf(path: String): String = repoFileOf("app/src/main/java/$path")

    private fun repoFileOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val APP_ACTION_MENU = "io/legado/app/ui/widget/components/menu/AppActionMenu.kt"
        const val APP_CHOICE_DROPDOWN = "io/legado/app/ui/widget/components/menu/AppChoiceDropdown.kt"
        const val GLASS_POPUP_SURFACE =
            "modules/miuix/miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass/GlassPopupSurface.kt"
        const val GLASS_TRANSFORM_POPUP =
            "modules/miuix/miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass/GlassTransformPopup.kt"
    }
}
