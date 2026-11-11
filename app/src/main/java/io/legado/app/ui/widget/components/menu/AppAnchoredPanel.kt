package io.legado.app.ui.widget.components.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.menuItem.GlassMenuAnchor
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.mochi.AppMochiPopover
import ui.feice.mochi.basic.LocalMochiDismiss

/**
 * 任意内容弹层的统一入口：带搜索框、复杂行或长列表的面板走这里。
 *
 * 触发器默认由组件渲染，[trigger] 为纯展示内容（见 [AppMenuTriggerScope]），
 * 由分支包装成唯一可点控件。Mochi 分支和其余三套一样是 `Box(clickable)`，
 * 弹层只拿锚点（[AppMochiPopover] 的分离式锚点，按调用方 Box 的 bounds 定位），
 * 不再套库的 `MochiPopoverButton`：按钮外观由 [trigger] 决定，开合是点击松手之后。
 * 面板内容通过 [LocalMochiDismiss] 取得关闭回调；其余三套沿用现有 [RoundDropdownMenu]
 * 的弹层容器（含 Miuix 玻璃宿主所需的 [glassAnchor]）。
 *
 * [externalTrigger] 是**兼容路径**：调用方自己渲染可点按钮（如顶栏的 `TopBarActionButton`），
 * 通过 [AppMenuTriggerScope.open] / [AppMenuTriggerScope.modifier] 接入，本入口只接管弹层分派。
 * 用于保留既有按钮外观的顶栏溢出菜单；该路径不再包可点节点，因此 [trigger] 里的按钮必须自己
 * 调 [AppMenuTriggerScope.open]，且 [width] 不生效。
 */
@Composable
fun AppAnchoredPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 280.dp,
    glassAnchor: GlassMenuAnchor? = null,
    externalTrigger: Boolean = false,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    if (externalTrigger) {
        ExternalTriggerPanel(expanded, onExpandedChange, modifier, glassAnchor, trigger, content)
        return
    }
    when (LegadoTheme.composeEngine) {
        ComposeEngine.Mochi -> {
            val scope = remember(onExpandedChange) {
                AppMenuTriggerScopeImpl(Modifier) { onExpandedChange(true) }
            }
            // 与 CouiChoice / CouiActions 同构：入口包唯一可点节点，面板只拿锚点
            Box(modifier) {
                Box(
                    scope.modifier.clickable { scope.open() },
                ) { scope.trigger() }
                AppMochiPopover(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) },
                    width = width,
                ) {
                    val dismiss = LocalMochiDismiss.current
                    content(dismiss)
                }
            }
        }

        ComposeEngine.Material, ComposeEngine.Coui, ComposeEngine.Miuix -> {
            val scope = remember(onExpandedChange) {
                AppMenuTriggerScopeImpl(Modifier) { onExpandedChange(true) }
            }
            Box {
                Box(
                    scope.modifier.clickable { onExpandedChange(true) },
                ) { scope.trigger() }
                RoundDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) },
                    modifier = modifier,
                    glassAnchor = glassAnchor,
                    content = content,
                )
            }
        }
    }
}

/** 兼容路径：调用方按钮 + 统一弹层分派，结构与收编前的 `TopBarOverflowMenu` 一致。 */
@Composable
private fun ExternalTriggerPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
    glassAnchor: GlassMenuAnchor?,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val scope = remember(onExpandedChange) {
        AppMenuTriggerScopeImpl(Modifier) { onExpandedChange(true) }
    }
    Box {
        scope.trigger()
        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = modifier,
            glassAnchor = glassAnchor,
            content = content,
        )
    }
}
