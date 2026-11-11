package io.legado.app.ui.widget.components.topbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.widget.components.LocalAppGlassOverlay
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.ui.widget.components.menu.AppAnchoredPanel
import io.legado.app.ui.widget.components.menuItem.GlassMenuAnchor
import kotlinx.collections.immutable.ImmutableList
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults as NativeGlassTopAppBarDefaults

@Composable
internal fun TopBarOverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val anchor = if (LocalMiuixGlassTopBar.current && LocalAppGlassOverlay.current != null) {
        rememberGlassPopupAnchor()
    } else null
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val glassAnchor = anchor?.let {
        GlassMenuAnchor(it, bounds) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(AppIcons.MoreVert, contentDescription = null)
            }
        }
    }

    // 弹层统一由 AppAnchoredPanel 分派；externalTrigger 保留顶栏按钮的四引擎外观，
    // 按钮通过 scope.open() 打开面板，并自己维护玻璃锚点的 bounds。
    AppAnchoredPanel(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        glassAnchor = glassAnchor,
        externalTrigger = true,
        trigger = {
            TopBarActionButton(
                onClick = { open() },
                imageVector = AppIcons.MoreVert,
                contentDescription = stringResource(R.string.more_menu),
                modifier = this.modifier.then(
                    if (anchor != null) {
                        Modifier
                            .glassPopupAnchor(
                                anchor,
                                cornerRadius = NativeGlassTopAppBarDefaults.ButtonSize / 2,
                                floating = true,
                            )
                            .onGloballyPositioned { bounds = it.boundsInRoot() }
                    } else Modifier
                ),
            )
        },
        content = content,
    )
}

/**
 * 声明式顶栏溢出菜单：整组条目与按钮一起交给 [AppActionMenu] 的四引擎原生分支。
 *
 * 只用于「静态命令 + 分段线」这类可声明的菜单；含搜索框、伪子菜单、动态长列表或需要
 * 自绘行的复杂面板仍走 [TopBarOverflowMenu] 的 content 路径。与 `TopBarOverflowMenu` 的
 * externalTrigger 兼容路径不同，这里的 MoreVert 按钮由入口渲染（和书架、两个编辑页一致），
 * 因此四引擎下顶栏按钮外观会有变化，需真机复核。
 */
@Composable
internal fun TopBarActionMenu(
    groups: ImmutableList<AppActionGroup>,
    onAction: (String) -> Unit,
) {
    AppActionMenu(groups = groups, onAction = onAction) {
        AppIcon(
            imageVector = AppIcons.MoreVert,
            contentDescription = stringResource(R.string.more_menu),
            modifier = this.modifier,
        )
    }
}
