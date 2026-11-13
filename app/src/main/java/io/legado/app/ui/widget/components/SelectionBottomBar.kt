package io.legado.app.ui.widget.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.floatingtoolbar.CouiFloatingToolbarDefaults
import com.itos.coui.floatingtoolbar.FloatingToolbar
import com.itos.coui.floatingtoolbar.FloatingToolbarItem
import com.itos.coui.menu.rememberCouiWindowBackdrop
import io.legado.app.R
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.coui.rememberCouiCapsuleRenderer
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.ui.widget.components.menu.AppMenuAnchor
import io.legado.app.ui.widget.components.menu.AppMenuTriggerScope
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

/**
 * COUI 原生胶囊操作栏的背景模糊半径（设备像素）。
 *
 * `CAPSULE_1` 系统胶囊材质在顶栏里不带模糊，因为顶栏背景本身已经渐进模糊；操作栏直接浮在正文上，
 * 零半径会让列表文字清晰透出。这档 20px 默认值已随 fork `pr/floating-toolbar` 合进
 * `CouiFloatingToolbarDefaults.style()`，这里不再显式补齐（数值相同，不会二次叠加）。
 */

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
    val safeModifier = modifier
        .windowInsetsPadding(
            WindowInsets.navigationBars
                .union(WindowInsets.tappableElement)
                .union(WindowInsets.ime)
        )
        .scale(if (compact) 0.85f else 1f)

    // COUI：走原生胶囊操作栏。它需要所属页面的背景采样源（AppTheme 的 ProvideCouiMenuBackdrop
    // 挂在窗口内容根上），采样源缺失或原生材质不可用时落回 M3 工具栏，操作与语义保持一致。
    if (LegadoTheme.composeEngine == ComposeEngine.Coui) {
        val backdrop = rememberCouiWindowBackdrop()
        if (backdrop != null && ThemeResolver.isCouiSupported()) {
            CouiSelectionBottomBar(
                modifier = safeModifier,
                backdrop = backdrop,
                onSelectAll = onSelectAll,
                onSelectInvert = onSelectInvert,
                primaryAction = primaryAction,
                secondaryActions = secondaryActions,
                leadingAction = leadingAction,
            )
            return
        }
    }

    when (LegadoTheme.composeEngine) {
        ComposeEngine.Miuix -> {
            val selectAllDescription = stringResource(R.string.select_all)
            val invertSelectionDescription = stringResource(R.string.invert_selection)
            val moreActionsDescription = stringResource(R.string.more_menu)
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
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> MaterialSelectionBottomBar(
            modifier = safeModifier,
            onSelectAll = onSelectAll,
            onSelectInvert = onSelectInvert,
            primaryAction = primaryAction,
            secondaryActions = secondaryActions,
            leadingAction = leadingAction,
        )
    }
}

/**
 * COUI 原生胶囊操作栏：整条栏共享一个玻璃表面、一个跟手高光与一次形变，操作项只承担点击。
 *
 * 与 M3 分支的差异是 COUI 组件的既有形态，不是遗漏：栏高 48dp、每项最小 48dp，且浮动栏内部
 * 不区分主次配色，因此主操作不再用 `secondaryContainer` 强调；点击顺序（全选 / 反选 / 前导 /
 * 主操作 / 更多）与无障碍名称和其它引擎一致。
 *
 * 原生材质创建失败（Shader 初始化异常）时不留下一条空栏，直接退回 M3 工具栏。
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun CouiSelectionBottomBar(
    modifier: Modifier,
    backdrop: CouiBackdrop,
    onSelectAll: () -> Unit,
    onSelectInvert: () -> Unit,
    primaryAction: ActionItem,
    secondaryActions: List<ActionItem>,
    leadingAction: ActionItem?,
) {
    val renderer = rememberCouiCapsuleRenderer()
    if (renderer == null) {
        MaterialSelectionBottomBar(
            modifier = modifier,
            onSelectAll = onSelectAll,
            onSelectInvert = onSelectInvert,
            primaryAction = primaryAction,
            secondaryActions = secondaryActions,
            leadingAction = leadingAction,
        )
        return
    }
    val selectAllDescription = stringResource(R.string.select_all)
    val invertSelectionDescription = stringResource(R.string.invert_selection)
    val moreActionsDescription = stringResource(R.string.more_menu)
    val style = CouiFloatingToolbarDefaults.style()
    FloatingToolbar(
        modifier = modifier,
        backdrop = backdrop,
        style = style,
        renderer = renderer,
    ) {
        FloatingToolbarItem(onClick = onSelectAll) {
            CouiToolbarActionIcon(Icons.Default.SelectAll, selectAllDescription)
        }
        FloatingToolbarItem(onClick = onSelectInvert) {
            CouiToolbarActionIcon(Icons.Default.Refresh, invertSelectionDescription)
        }

        // 前导动作（可选）：反选之后、主操作之前
        leadingAction?.let { action ->
            FloatingToolbarItem(onClick = action.onClick) {
                CouiToolbarActionIcon(action.icon, action.text)
            }
        }

        FloatingToolbarItem(onClick = primaryAction.onClick) {
            CouiToolbarActionIcon(primaryAction.icon, primaryAction.text)
        }

        if (secondaryActions.isNotEmpty()) {
            SelectionMoreMenu(
                secondaryActions = secondaryActions,
                title = moreActionsDescription,
                trigger = {
                    // 纯展示：锚点修饰符已由 AppActionMenu 的外层可点节点承担，这里不能再挂一次
                    // （COUI 的 couiMenuAnchor 内含 pointerInput，重复挂载会抢在点击之前命中）。
                    Box(
                        Modifier.size(CouiFloatingToolbarDefaults.itemSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            imageVector = AppIcons.MoreVert,
                            contentDescription = moreActionsDescription,
                            modifier = Modifier.size(CouiFloatingToolbarDefaults.iconSize),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun CouiToolbarActionIcon(icon: ImageVector, contentDescription: String) {
    AppIcon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(CouiFloatingToolbarDefaults.iconSize),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaterialSelectionBottomBar(
    modifier: Modifier,
    onSelectAll: () -> Unit,
    onSelectInvert: () -> Unit,
    primaryAction: ActionItem,
    secondaryActions: List<ActionItem>,
    leadingAction: ActionItem?,
) {
    val selectAllDescription = stringResource(R.string.select_all)
    val invertSelectionDescription = stringResource(R.string.invert_selection)
    val moreActionsDescription = stringResource(R.string.more_menu)
    HorizontalFloatingToolbar(
        modifier = modifier,
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

/**
 * 多选栏的「更多」菜单：保持 `ActionItem` 的 `onClick` API 不变，在组件内部按 index
 * 映射成稳定 ID 的 `AppAction`，交给四引擎原生入口渲染；调用方零改动。
 *
 * [trigger] 只负责画外观，可点节点由 `AppActionMenu(Row)` 统一提供；默认实现是 M3 medium 档
 * 图标按钮，COUI 分支传入胶囊操作栏里的那格图标。
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
    trigger: @Composable AppMenuTriggerScope.() -> Unit = {
        MediumPlainButton(
            onClick = null,
            icon = AppIcons.MoreVert,
            contentDescription = title,
            modifier = this.modifier,
        )
    },
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
        trigger = trigger,
    )
}
