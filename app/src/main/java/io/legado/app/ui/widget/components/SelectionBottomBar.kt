package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.ui.widget.components.menu.AppMenuAnchor
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.FloatingToolbar as MiuixFloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton

data class SelectionActions(
    val primaryAction: ActionItem,
    val secondaryActions: List<ActionItem> = emptyList(),
    val onClearSelection: (() -> Unit)? = null,
    val onSelectAll: () -> Unit,
    val onSelectInvert: () -> Unit,
)

data class ActionItem(
    val text: String,
    val icon: ImageVector = Icons.Default.ExpandMore,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectionBottomBar(
    modifier: Modifier = Modifier,
    /**
     * 紧凑模式：整体等比缩到 0.85（用于阅读主题弹窗这类"小面板"里；默认 false ⇒ 其他界面零变化）。
     * 用 scale 而不是逐项改尺寸：M3 浮动工具栏的容器高度/内边距不开放，只改按钮宽度会宽变高不变。
     */
    compact: Boolean = false,
    onSelectAll: () -> Unit,
    onSelectInvert: () -> Unit,
    primaryAction: ActionItem,
    secondaryActions: List<ActionItem>,
    /**
     * 前导动作（可选）：渲染在"全选/反选"之后、[primaryAction] 之前。
     * 默认 null ⇒ 现有调用点（书架/目录/搜索等）逐字不变。
     */
    leadingAction: ActionItem? = null,
) {
    val selectAllDescription = stringResource(R.string.select_all)
    val invertSelectionDescription = stringResource(R.string.invert_selection)
    val moreActionsDescription = stringResource(R.string.more_menu)
    val safeModifier = modifier
        .windowInsetsPadding(
            WindowInsets.navigationBars
                .union(WindowInsets.tappableElement)
                .union(WindowInsets.ime)
        )
        .scale(if (compact) 0.85f else 1f)

    when (LegadoTheme.composeEngine) {
        ComposeEngine.Miuix -> {
            MiuixFloatingToolbar(
                modifier = safeModifier
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    MediumPlainButton(
                        onClick = onSelectAll,
                        icon = Icons.Default.SelectAll,
                        contentDescription = selectAllDescription,
                    )
                    MediumPlainButton(
                        onClick = onSelectInvert,
                        icon = Icons.Default.Refresh,
                        contentDescription = invertSelectionDescription,
                    )

                    // 前导动作（可选）：反选之后、主操作之前
                    leadingAction?.let { action ->
                        MediumPlainButton(
                            onClick = action.onClick,
                            icon = action.icon,
                            contentDescription = action.text,
                        )
                    }

                    MiuixIconButton(
                        onClick = primaryAction.onClick,
                        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                        minWidth = 64.dp
                    ) {
                        MiuixIcon(
                            imageVector = primaryAction.icon,
                            contentDescription = primaryAction.text,
                            tint = MiuixTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (secondaryActions.isNotEmpty()) {
                        SelectionMoreMenu(secondaryActions, moreActionsDescription)
                    }
                }
            }
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            HorizontalFloatingToolbar(
                modifier = safeModifier,
                expanded = true,
                leadingContent = {
                    MediumPlainButton(
                        onClick = onSelectAll,
                        icon = Icons.Default.SelectAll,
                        contentDescription = selectAllDescription,
                    )
                    MediumPlainButton(
                        onClick = onSelectInvert,
                        icon = Icons.Default.Refresh,
                        contentDescription = invertSelectionDescription,
                    )
                    // 前导动作（可选）：反选之后、主操作之前
                    leadingAction?.let { action ->
                        MediumPlainButton(
                            onClick = action.onClick,
                            icon = action.icon,
                            contentDescription = action.text,
                        )
                    }
                },
                trailingContent = {
                    if (secondaryActions.isNotEmpty()) {
                        SelectionMoreMenu(secondaryActions, moreActionsDescription)
                    }
                },
                content = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        tooltip = { ProvideAppDensity { PlainTooltip { AppText(primaryAction.text) } } },
                        state = rememberTooltipState(),
                    ) {
                        FilledIconButton(
                            modifier = Modifier.width(64.dp),
                            onClick = primaryAction.onClick,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = LegadoTheme.colorScheme.secondaryContainer,
                                contentColor = LegadoTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            AppIcon(
                                imageVector = primaryAction.icon,
                                contentDescription = primaryAction.text
                            )
                        }
                    }
                }
            )
        }
    }
}

/**
 * 多选栏的「更多」菜单：保持 `ActionItem` 的 `onClick` API 不变，在组件内部按 index
 * 映射成稳定 ID 的 `AppAction`，交给四引擎原生入口渲染；调用方零改动。
 *
 * `AppMenuAnchor.Row` 是刻意的：Miuix 的玻璃 morph 只有 `MiuixRowActions` 走
 * `isMiuixGlassMenuAvailable()` + `MiuixGlassMenuHost` 才拿得到；Icon 锚点的玻璃门禁
 * 依赖 `LocalMiuixGlassTopBar`，在底栏处默认为 false。
 *
 * trigger 与栏上其它非主按钮同用 `MediumPlainButton`（40dp、
 * `IconButtonDefaults.extraSmallRoundShape`），所以 `triggerShape` 必须取同一形状，
 * 否则波纹会按 `CircleShape` 裁、和按钮轮廓对不上。
 * 菜单项刻意不带 `icon`：旧 `RoundDropdownMenuItem` 只用了 `text`，`ActionItem.icon`
 * 默认是 `ExpandMore` 占位符。
 */
@Composable
private fun SelectionMoreMenu(
    secondaryActions: List<ActionItem>,
    title: String,
) {
    val groups = remember(secondaryActions) {
        persistentListOf(
            AppActionGroup(
                id = "selection_more",
                actions = secondaryActions.mapIndexed { index, action ->
                    AppAction(id = index.toString(), label = action.text)
                }.toImmutableList(),
            ),
        )
    }
    AppActionMenu(
        groups = groups,
        onAction = { id -> secondaryActions.getOrNull(id.toIntOrNull() ?: -1)?.onClick() },
        anchor = AppMenuAnchor.Row,
        title = title,
        triggerShape = IconButtonDefaults.extraSmallRoundShape,
    ) {
        MediumPlainButton(
            onClick = null,
            icon = AppIcons.MoreVert,
            contentDescription = title,
            modifier = this.modifier,
        )
    }
}
