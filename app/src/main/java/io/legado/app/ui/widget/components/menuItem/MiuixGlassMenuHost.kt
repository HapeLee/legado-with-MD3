package io.legado.app.ui.widget.components.menuItem

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.widget.components.LocalAppGlassOverlay
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor

/**
 * 把 Miuix 玻璃菜单注册到 [io.legado.app.ui.widget.components.AppGlassOverlayState] 的坐标根。
 *
 * 玻璃面板要拿 `anchor.containerBounds` 当 morph 起点，必须在 root `BoxScope` 里绘制；
 * 注册在页面内部会得到错误坐标（面板从行首长出来，或干脆不动画）。按钮外壳留给调用方，
 * 因此同一个 host 能覆盖三种锚点形态：
 * - 顶栏 / 图标：`MiuixGlassIconButton` + `glassPopupAnchor` + `glassPopupAnchorContent`；
 * - 列表行 / 取值 Chip：自绘按钮 + `glassPopupAnchor`，`anchorContent` 留空，避免过渡期
 *   在自绘内容上再叠一份副本；
 * - 受控弹层：锚点挂在调用方给的 `matchParentSize` 容器上。
 *
 * 没有锚点节点、只能拿父节点 bounds 的调用方（[RoundDropdownMenu] 的玻璃路径）仍然走
 * `GlassDropdownPopup`：面板同样是玻璃，但没有从按钮长出来的 morph。
 *
 * 这条只负责 Miuix 玻璃。COUI 的原生 `Menu`、Mochi 的 `MochiDropdownMenu`、Material 的
 * `DropdownMenu` 各自在自己的入口里完成锚点，不走这里——新增组件库时照这个 host 的形态
 * 提供等价实现即可，入口不需要再按库写死面板。
 */
@Composable
internal fun MiuixGlassMenuHost(
    show: Boolean,
    onDismissRequest: () -> Unit,
    anchor: GlassPopupAnchor,
    entries: List<DropdownEntry>,
    anchorContent: @Composable () -> Unit = {},
) {
    val glassOverlay = LocalAppGlassOverlay.current ?: return
    val backdrop = LocalMiuixBarBackdrop.current
    val localContext = currentCompositionLocalContext
    val key = remember { Any() }
    val popupContent = remember(glassOverlay) {
        mutableStateOf<@Composable BoxScope.() -> Unit>({})
    }
    SideEffect {
        popupContent.value = {
            CompositionLocalProvider(localContext) {
                GlassIconDropdownMenu(
                    show = show,
                    onDismissRequest = onDismissRequest,
                    anchor = anchor,
                    anchorContent = anchorContent,
                    entries = entries,
                    backdrop = backdrop,
                    collapseOnSelection = true,
                )
            }
        }
    }
    DisposableEffect(glassOverlay, key) {
        glassOverlay.register(key) { popupContent.value(this) }
        onDispose { glassOverlay.remove(key) }
    }
}
