package io.legado.app.ui.widget.components.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.topappbar.TopAppBarButton as CouiTopAppBarButton
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalHasMiuixPopupHost
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.coui.LocalAppCouiNativeToolbar
import io.legado.app.ui.widget.components.coui.canUseCouiSettingMenu
import io.legado.app.ui.widget.components.coui.rememberCouiCapsuleRenderer
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiDropdownDefaults
import ui.feice.mochi.basic.MochiDropdownMenu
import ui.feice.mochi.basic.MochiItem

/**
 * 单选菜单的统一入口：业务只提供选项、当前选中 [selectedId] 与稳定 ID 回调 [onSelect]。
 *
 * 触发器与整组条目一起交给主题分支组装，这是四套库保留原生效果的前提
 * （COUI 触点定位、Miuix 锚点变形、Mochi 锚点面板）。
 *
 * [trigger] 是纯展示内容（见 [AppMenuTriggerScope]），由分支包装成唯一可点控件；
 * 分支在包装节点上声明 [title] 的文本语义，保证按钮有稳定的无障碍名称。
 * Mochi 分支不套库按钮：入口包 `Box(clickable)`，菜单浮层只拿锚点矩形（`MochiDropdownMenu`），
 * 因此开合是「点击松手后」，没有按下即开和按住滑选。
 *
 * 当前阶段的边界：Mochi 走 fork 的 `MochiDropdownMenu`（只有锚点）；COUI 走原生 [Menu] +
 * `CouiMenuAnchor`（Android 14+ 且阴影渲染器可用，否则降级 M3）；Material 走 M3 [DropdownMenu]；
 * Miuix 默认复用已原生化的 [DropdownListSettingItem]（设置行形态，由库渲染整行，
 * [trigger] 不生效）。[compact] 用于紧凑/自定义 trigger：四套分支都由入口包装
 * [trigger]，Miuix 弹层走 `WindowDropdownPopup` / `OverlayDropdownPopup`，
 * 保留原生 spinner 动画与选中态，trigger 外观不变；
 * 该形态暂不接 HyperOS 4 玻璃锚点变形。行为基线是既有设置项，不含回退。
 *
 * [anchor] 与 [AppActionMenu] 同口径：`Icon`（顶栏 / 工具条图标）由分支套各自的原生图标按钮壳
 * ——Mochi 穿 `MochiBarIconButton`（= `TopBarActionButton` 的 Mochi 实现）、COUI 穿
 * `CouiTopAppBarButton`（无原生工具栏时退 `IconButton`）、Miuix 走 `Window/OverlayIconDropdownMenu`
 * 由库渲染 Icon 按钮、Material 退 `IconButton`。因此 Icon 形态与同排按钮一致，不需要新外观基线。
 * `Icon` 优先于 [compact]：Miuix 在 `Icon` 下不再渲染设置行。[anchor] 为 `Icon` 时
 * [summary] 无意义（设置行已不是渲染目标）。
 * Miuix 的 `Icon` 与 [compact] 一样暂不接 HyperOS 4 玻璃锚点变形。
 */
@Composable
fun AppChoiceDropdown(
    options: ImmutableList<AppChoiceOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    /** 当前设备/产物不提供的档位：仍列出但不可选中。 */
    unavailableIds: Set<String> = emptySet(),
    onUnavailableSelected: ((String) -> Unit)? = null,
    /**
     * 选中后先关菜单、等浮层从 DecorView 摘掉再回调。切换根主题这类会立刻重建 composition
     * 的操作必须开启，否则 Mochi 的 `InWindowOverlay` 子视图会在测量期间被移除。
     */
    commitAfterDismiss: Boolean = false,
    /** 紧凑图标按钮形态：让 Miuix 也用 [trigger] 而不是渲染整行设置项。 */
    compact: Boolean = false,
    /** 顶栏 / 工具条图标形态；见 KDoc 的 [anchor] 说明。 */
    anchor: AppMenuAnchor = AppMenuAnchor.Row,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    when (LegadoTheme.composeEngine) {
        ComposeEngine.Mochi -> MochiChoice(
            options, selectedId, onSelect, title, modifier, enabled,
            unavailableIds, onUnavailableSelected, commitAfterDismiss, anchor, trigger,
        )

        ComposeEngine.Coui -> if (canUseCouiSettingMenu()) {
            CouiChoice(
                options, selectedId, onSelect, title, modifier, enabled,
                unavailableIds, onUnavailableSelected, anchor, trigger,
            )
        } else {
            MaterialChoice(
                options, selectedId, onSelect, title, modifier, enabled,
                unavailableIds, onUnavailableSelected, anchor, trigger,
            )
        }

        ComposeEngine.Material -> MaterialChoice(
            options, selectedId, onSelect, title, modifier, enabled,
            unavailableIds, onUnavailableSelected, anchor, trigger,
        )

        ComposeEngine.Miuix -> when {
            // Icon 形态必须让库渲染图标按钮，不能落到设置行。
            anchor == AppMenuAnchor.Icon -> MiuixIconChoice(
                options, selectedId, onSelect, modifier, enabled,
                unavailableIds, onUnavailableSelected, trigger,
            )

            compact -> MiuixCompactChoice(
                options, selectedId, onSelect, title, modifier, enabled,
                unavailableIds, onUnavailableSelected, trigger,
            )

            // Miuix 的 DropdownPreference 已经是原生菜单实现，由库渲染整行。
            else -> DropdownListSettingItem(
                title = title,
                selectedValue = selectedId ?: "",
                displayEntries = options.map { it.label }.toTypedArray(),
                entryValues = options.map { it.id }.toTypedArray(),
                description = summary,
                enabled = enabled,
                unavailableValues = options.filterNot { it.enabled }.map { it.id }.toSet() + unavailableIds,
                onUnavailableSelected = onUnavailableSelected,
                onValueChange = onSelect,
            )
        }
    }
}

/**
 * Miuix 紧凑单选：入口自己用 `Box(clickable)` 包装 [trigger]，弹层交给原生
 * `WindowDropdownPopup` / `OverlayDropdownPopup`（有 popup host 用后者）。
 * 与另外三套分支一样由入口持有开合状态，因此带文字、方卡或任意尺寸的自定义 trigger
 * 都能零外观变化迁移；库负责 spinner 动画、[DropdownItem.selected] 勾选态与逐项禁用。
 * 定位由 `ListPopupLayout` 依调用点自动完成，不需要显式锚点 bounds；
 * HyperOS 4 玻璃锚点变形留待后续。
 */
@Composable
private fun MiuixCompactChoice(
    options: ImmutableList<AppChoiceOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    title: String,
    modifier: Modifier,
    enabled: Boolean,
    unavailableIds: Set<String>,
    onUnavailableSelected: ((String) -> Unit)?,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val entries = rememberChoiceEntries(options, selectedId, unavailableIds, onUnavailableSelected, onSelect)
    val scope = remember { AppMenuTriggerScopeImpl(Modifier) { expanded = true } }
    val dropdownColors = DropdownDefaults.dropdownColors()
    Box(modifier) {
        Box(
            scope.modifier
                .clickable(enabled = enabled) { scope.open() }
                .semantics { text = AnnotatedString(title) },
        ) { scope.trigger() }
        if (LocalHasMiuixPopupHost.current) {
            OverlayDropdownPopup(
                entries = entries,
                show = expanded && enabled,
                onDismiss = { expanded = false },
                onDismissFinished = {},
                maxHeight = null,
                dropdownColors = dropdownColors,
                renderInRootScaffold = true,
                collapseOnSelection = true,
            )
        } else {
            WindowDropdownPopup(
                entries = entries,
                show = expanded && enabled,
                onDismiss = { expanded = false },
                onDismissFinished = {},
                maxHeight = null,
                dropdownColors = dropdownColors,
                collapseOnSelection = true,
            )
        }
    }
}

/**
 * Miuix 图标单选（顶栏 / 工具条）：库自己渲染图标按钮与开合，[trigger] 只承载纯展示内容，
 * 因此 `expanded` 由库自持、[AppMenuTriggerScope.open] 在此分支是 no-op。
 * 与 [MiuixCompactChoice] 共用同一份 [rememberChoiceEntries]，选中态仍由
 * [DropdownItem.selected] 表达。HyperOS 4 玻璃锚点变形留待后续（与 compact 同一条遗留）。
 */
@Composable
private fun MiuixIconChoice(
    options: ImmutableList<AppChoiceOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    unavailableIds: Set<String>,
    onUnavailableSelected: ((String) -> Unit)?,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    val entries = rememberChoiceEntries(options, selectedId, unavailableIds, onUnavailableSelected, onSelect)
    val scope = remember { AppMenuTriggerScopeImpl(Modifier) {} }
    val menuTrigger: @Composable () -> Unit = { scope.trigger() }
    if (LocalHasMiuixPopupHost.current) {
        OverlayIconDropdownMenu(
            entries = entries,
            modifier = modifier,
            enabled = enabled,
            collapseOnSelection = true,
            content = menuTrigger,
        )
    } else {
        WindowIconDropdownMenu(
            entries = entries,
            modifier = modifier,
            enabled = enabled,
            collapseOnSelection = true,
            content = menuTrigger,
        )
    }
}

/** `dividerBefore` 在 Miuix 原生弹层里表达为 [DropdownEntry] 切换（库负责画组间线）。 */
@Composable
private fun rememberChoiceEntries(
    options: ImmutableList<AppChoiceOption>,
    selectedId: String?,
    unavailableIds: Set<String>,
    onUnavailableSelected: ((String) -> Unit)?,
    onSelect: (String) -> Unit,
): List<DropdownEntry> = remember(options, selectedId, unavailableIds, onUnavailableSelected, onSelect) {
    buildList {
        var bucket = mutableListOf<DropdownItem>()
        fun flush() {
            if (bucket.isNotEmpty()) {
                add(DropdownEntry(bucket))
                bucket = mutableListOf()
            }
        }
        options.forEach { option ->
            if (option.dividerBefore) flush()
            val blocked = option.id in unavailableIds && onUnavailableSelected == null
            bucket += DropdownItem(
                text = option.label,
                enabled = option.enabled && !blocked,
                selected = option.id == selectedId,
                onClick = {
                    if (option.id in unavailableIds) onUnavailableSelected?.invoke(option.id)
                    else onSelect(option.id)
                },
            )
        }
        flush()
    }
}

@Composable
private fun MochiChoice(
    options: ImmutableList<AppChoiceOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    title: String,
    modifier: Modifier,
    enabled: Boolean,
    unavailableIds: Set<String>,
    onUnavailableSelected: ((String) -> Unit)?,
    commitAfterDismiss: Boolean,
    anchor: AppMenuAnchor,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = remember(options, unavailableIds, onUnavailableSelected) {
        options.map { option ->
            // 无提示回调的不可用档位真正禁用；有回调时保持可点并由回调说明原因。
            val blocked = option.id in unavailableIds && onUnavailableSelected == null
            val iconSlot: (@Composable (Color) -> Unit)? = option.icon?.let { vector ->
                { tint -> AppIcon(imageVector = vector, contentDescription = null, tint = tint) }
            }
            MochiItem(
                label = option.label,
                id = option.id,
                enabled = option.enabled && !blocked,
                icon = iconSlot,
                dividerBefore = option.dividerBefore,
            )
        }.toImmutableList()
    }
    var anchorBounds by remember { mutableStateOf(IntRect.Zero) }
    val scope = remember { AppMenuTriggerScopeImpl(Modifier) { expanded = true } }
    // 与 CouiChoice 同构：入口包唯一可点节点，Mochi 只把菜单浮层挂在锚点矩形上（不再套库按钮）。
    // 开合变成「点击松手后」，按下不预展开，也没有按住滑选。
    Box(modifier) {
        if (anchor == AppMenuAnchor.Icon) {
            // Icon 锚点穿 `MochiBarIconButton`（= `TopBarActionButton` 的 Mochi 实现），
            // 与同排顶栏按钮同款圆底；按钮名称由 trigger 里的 contentDescription 提供。
            MochiBarIconButton(
                onClick = scope::open,
                modifier = Modifier.onGloballyPositioned {
                    anchorBounds = it.boundsInWindow().roundToIntRect()
                },
                enabled = enabled,
                contentKey = "app-choice",
            ) { scope.trigger() }
        } else {
            Box(
                scope.modifier
                    .onGloballyPositioned { anchorBounds = it.boundsInWindow().roundToIntRect() }
                    .clickable(enabled = enabled) { scope.open() }
                    // 可点节点的 semantics 不合并后代，文本留在 trigger 子节点里；
                    // 在这里直接声明标题文本，TalkBack 才能读到名称。
                    .semantics { text = AnnotatedString(title) },
            ) { scope.trigger() }
        }
        MochiDropdownMenu(
            anchorBounds = anchorBounds,
            expanded = expanded && enabled,
            onExpandedChange = { expanded = it },
            items = items,
            onItemClick = { index ->
                val id = options[index].id
                if (id in unavailableIds) onUnavailableSelected?.invoke(id) else onSelect(id)
            },
            selectedIndex = options.indexOfFirst { it.id == selectedId },
            menuWidth = if (anchor == AppMenuAnchor.Icon) {
                MochiDropdownDefaults.IconMenuWidth
            } else {
                MochiDropdownDefaults.MenuWidth
            },
            commitAfterDismiss = commitAfterDismiss,
        )
    }
}

/** COUI 原生菜单：触点由 [couiMenuAnchor] 观察，[Menu] 用它定位弹层。 */
@Composable
private fun CouiChoice(
    options: ImmutableList<AppChoiceOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    title: String,
    modifier: Modifier,
    enabled: Boolean,
    unavailableIds: Set<String>,
    onUnavailableSelected: ((String) -> Unit)?,
    anchor: AppMenuAnchor,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val couiAnchor = rememberCouiMenuAnchor()
    val items = remember(options, selectedId, unavailableIds, onUnavailableSelected) {
        options.map { option ->
            val blocked = option.id in unavailableIds && onUnavailableSelected == null
            CouiMenuItem(
                value = option.id,
                text = option.label,
                icon = option.icon,
                selected = option.id == selectedId,
                enabled = option.enabled && !blocked,
                dividerBefore = option.dividerBefore,
            )
        }
    }
    val scope = remember(couiAnchor) {
        AppMenuTriggerScopeImpl(
            modifier = Modifier.couiMenuAnchor(couiAnchor),
            onOpen = { couiAnchor.open { expanded = true } },
        )
    }
    Box(modifier) {
        if (anchor == AppMenuAnchor.Icon) {
            // 跟随同排 TopBarActionButton：只有 COUI 原生胶囊顶栏才带那层玻璃底，
            // 否则 `⋯` 会成为整排里唯一没有玻璃的按钮。
            val capsule = rememberCouiCapsuleRenderer()
            if (ThemeResolver.isCouiSupported() && LocalAppCouiNativeToolbar.current &&
                capsule != null
            ) {
                CouiTopAppBarButton(
                    modifier = scope.modifier.semantics { role = Role.Button },
                    onClick = { if (enabled) scope.open() },
                    text = false,
                    renderer = capsule,
                ) { scope.trigger() }
            } else {
                IconButton(
                    onClick = scope::open,
                    enabled = enabled,
                    modifier = scope.modifier,
                ) { scope.trigger() }
            }
        } else {
            Box(
                scope.modifier
                    .clickable(enabled = enabled) { scope.open() }
                    .semantics { text = AnnotatedString(title) },
            ) { scope.trigger() }
        }
        Menu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            items = items,
            onItemClick = { item ->
                expanded = false
                if (item.value in unavailableIds) onUnavailableSelected?.invoke(item.value)
                else onSelect(item.value)
            },
            anchor = couiAnchor,
        )
    }
}

@Composable
private fun MaterialChoice(
    options: ImmutableList<AppChoiceOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    title: String,
    modifier: Modifier,
    enabled: Boolean,
    unavailableIds: Set<String>,
    onUnavailableSelected: ((String) -> Unit)?,
    anchor: AppMenuAnchor,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val scope = remember { AppMenuTriggerScopeImpl(Modifier) { expanded = true } }
    Box(modifier) {
        if (anchor == AppMenuAnchor.Icon) {
            // 与 `AppActionMenu` 的 Material 分支同形：图标按钮由入口提供，trigger 只画内容。
            IconButton(
                onClick = scope::open,
                enabled = enabled,
                modifier = scope.modifier,
            ) { scope.trigger() }
        } else {
            Box(
                scope.modifier
                    .clickable(enabled = enabled) { scope.open() }
                    .semantics { text = AnnotatedString(title) },
            ) { scope.trigger() }
        }
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                if (option.dividerBefore) PillDivider()
                val blocked = option.id in unavailableIds && onUnavailableSelected == null
                RoundDropdownMenuItem(
                    text = option.label,
                    enabled = option.enabled && !blocked,
                    isSelected = option.id == selectedId,
                    onClick = {
                        expanded = false
                        if (option.id in unavailableIds) {
                            onUnavailableSelected?.invoke(option.id)
                        } else {
                            onSelect(option.id)
                        }
                    },
                )
            }
        }
    }
}
