package io.legado.app.ui.widget.components.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalHasMiuixPopupHost
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.widget.components.AppGlassOverlayState
import io.legado.app.ui.widget.components.LocalAppGlassOverlay
import io.legado.app.ui.widget.components.coui.canUseCouiSettingMenu
import io.legado.app.ui.widget.components.topbar.LocalMiuixGlassTopBar
import io.legado.app.ui.widget.components.topbar.TopBarActionSurface
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.menuItem.MiuixGlassMenuHost
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.menuItem.isMiuixGlassMenuAvailable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup
import top.yukonga.miuix.kmp.glass.GlassIconButton as MiuixGlassIconButton
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.menu.OverlayIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.menu.WindowIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import ui.feice.mochi.basic.MochiDropdownDefaults
import ui.feice.mochi.basic.MochiDropdownMenu
import ui.feice.mochi.basic.MochiItem

/**
 * 命令菜单的统一入口：业务提供分组/命令与稳定 ID 回调 [onAction]。
 *
 * [trigger] 是纯展示内容（见 [AppMenuTriggerScope]），由分支包装成唯一可点控件。
 * Mochi 分支不渲染菜单按钮：入口包唯一可点节点并上报锚点矩形，弹层走 `MochiDropdownMenu`
 * （逐项禁用、独立勾选、分组小标题、分隔线和一层子菜单仍由库渲染），因此开合是「点击松手后」，
 * 没有按下即开和按住滑选。按钮那层壳按 [AppMenuAnchor] 分：`Icon`（顶栏）穿项目
 * `MochiBarIconButton`（= `TopBarActionButton` 在 Mochi 下的实现），与同排按钮同款圆底；
 * `Row`（设置行 / 自绘整行）只包 `Box(clickable)`，外观由 trigger 自己画。
 * COUI 分支走原生 [Menu] + `CouiMenuAnchor`，
 * `checked` / `dividerBefore` / 两级 `children` 直接映射，触点定位与按压预选由库处理。
 * Miuix 分支走原生 `Window/OverlayIconDropdownMenu`（有 `children` 时切 `Cascading` 变体），
 * HyperOS 4 走 [GlassIconDropdownMenu] + `GlassPopupAnchor` 的锚点变形；库自己渲染
 * `IconButton` 与 `expanded`，因此 [AppMenuTriggerScope.open] 在这些分支不被外部驱动。
 * Material 仍复用 [RoundDropdownMenu] + [RoundDropdownMenuItem] 的既有外观；该组件只有
 * `isSelected`，故 `checked` 与 `selected` 一样降级为高亮 + `semantics.selected`，不静默丢勾选态。
 *
 * 分段线口径：产品允许分组标题不保留文字，[AppAction.dividerBefore]、
 * [AppAction.groupTitleBefore] 与 [AppActionGroup.title] 由 [dividerBeforeAt] 统一降级为
 * 纯分段线，四套引擎都不渲染标题文本；多 group 时第 2 组起自动补线。
 * Material 无级联能力，子菜单显式平铺为普通项，不静默丢子项 ID。
 *
 * [embeddedExpanded] 非 `null` 时进入受控弹层模式：四引擎都只渲染弹层，不渲染 trigger 包装，
 * 开合由调用方经 [onDismissRequest] 收口；锚点取调用方放置本组件的容器矩形，因此该容器必须有尺寸，
 * 且容器上不能再叠加任何可命中节点（COUI 的 `couiMenuAnchor` 就在此列，会挡掉触发按钮的点击）。
 * 代价是失去 COUI 的触点锚定（菜单从手指/触点长出），改为从容器矩形下拉，与键盘、无障碍
 * 触发时的既有回退一致。与 [AppChoiceDropdown] 的同名参数同语义，用于「展开态要驱动页面其它行为」的菜单。
 */
@Composable
fun AppActionMenu(
    groups: ImmutableList<AppActionGroup>,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    anchor: AppMenuAnchor = AppMenuAnchor.Icon,
    /**
     * `Row` 锚点的可访问名称。`Icon` 锚点由分支渲染按钮、名称取自 trigger 里的
     * contentDescription；`Row` 锚点只有一层 `Box(clickable)`，必须显式给出名称，
     * 否则 TalkBack 读不到这个「更多」按钮。
     */
    title: String? = null,
    /**
     * `Row` 锚点触发内容的形状。
     *
     * 入口只包一层可点节点（矩形），触发内容自己画圆角按钮时，M3 水波纹会从四个角溢出，
     * 观感就是「按钮没有圆角」。Row 触发的惯例是图标按钮 / 胶囊（`RoundedCornerShape(50)`），
     * 所以默认按圆形裁剪；方形卡片触发时传自己的形状。
     */
    triggerShape: Shape = CircleShape,
    /**
     * 受控弹层模式：非 `null` 时只渲染弹层、不渲染 trigger 包装，由调用方持有开合状态并
     * 把本组件放在锚点位置（通常是按钮所在的 Box + `Modifier.matchParentSize()`）。
     * 用于「展开态还要驱动外部行为」的菜单：播放页自动隐藏控制栏、工具按钮高亮等。
     * Miuix 的库 `Icon*DropdownMenu` 自持 `expanded`，受控时改走 [MiuixRowActions] 的 popup 形态。
     */
    embeddedExpanded: Boolean? = null,
    /** 受控模式下弹层请求关闭（含选中某项）时回调。 */
    onDismissRequest: () -> Unit = {},
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    val flat = remember(groups) { groups.flatMap { it.actions } }
    when (LegadoTheme.composeEngine) {
        ComposeEngine.Mochi -> MochiActions(
            groups, flat, onAction, modifier, enabled, anchor, triggerShape,
            embeddedExpanded, onDismissRequest, trigger,
        )

        ComposeEngine.Coui -> if (canUseCouiSettingMenu()) {
            CouiActions(
                groups, onAction, modifier, enabled, anchor, title, triggerShape,
                embeddedExpanded, onDismissRequest, trigger,
            )
        } else {
            LegacyActions(
                groups, onAction, modifier, enabled, anchor, title, triggerShape,
                embeddedExpanded, onDismissRequest, trigger,
            )
        }

        ComposeEngine.Miuix -> if (embeddedExpanded != null || anchor == AppMenuAnchor.Row) {
            // 受控模式优先：库的 `Icon*DropdownMenu` 自持 expanded，无法从外部开合。
            // 库的图标弹层也不支持受控，改走 popup 形态（与 AppChoiceDropdown 一致）。
            MiuixRowActions(
                groups, onAction, modifier, enabled, title, triggerShape,
                embeddedExpanded, onDismissRequest, trigger,
            )
        } else {
            MiuixActions(groups, onAction, modifier, enabled, trigger)
        }

        ComposeEngine.Material -> LegacyActions(
            groups, onAction, modifier, enabled, anchor, title, triggerShape,
            embeddedExpanded, onDismissRequest, trigger,
        )
    }
}

@Composable
private fun MochiActions(
    groups: ImmutableList<AppActionGroup>,
    flat: List<AppAction>,
    onAction: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    anchor: AppMenuAnchor,
    triggerShape: Shape,
    embeddedExpanded: Boolean?,
    onDismissRequest: () -> Unit,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isOpen = embeddedExpanded ?: expanded
    val scope = remember { AppMenuTriggerScopeImpl(Modifier) { expanded = true } }
    // AppAction.selected 在 Mochi 没有单独的单选通道：只要这份菜单里用过 selected，就整列按
    // 独立勾选圈（checked）画，和 COUI / Miuix 的勾、Material 的 isSelected 对齐。
    // 没给 selected 的普通命令项 checked 仍是 null，不画圈、不占列。
    val usesSelection = remember(groups) {
        groups.any { group -> group.actions.any { it.selected } }
    }
    val items = remember(groups, usesSelection) {
        buildList {
            groups.forEachIndexed { groupIndex, group ->
                group.actions.forEachIndexed { actionIndex, action ->
                    val iconSlot: (@Composable (Color) -> Unit)? = action.icon?.let { vector ->
                        { tint -> AppIcon(imageVector = vector, contentDescription = null, tint = tint) }
                    }
                    add(
                        MochiItem(
                            label = action.label,
                            destructive = action.destructive,
                            icon = iconSlot,
                            id = action.id,
                            enabled = action.enabled,
                            checked = action.checked
                                ?: if (usesSelection) action.selected else null,
                            dividerBefore = group.dividerBeforeAt(actionIndex, groupIndex),
                            children = action.children.map { child ->
                                MochiItem(
                                    label = child.label,
                                    destructive = child.destructive,
                                    id = child.id,
                                    enabled = child.enabled,
                                    checked = child.checked
                                        ?: if (usesSelection) child.selected else null,
                                )
                            }.toImmutableList(),
                        )
                    )
                }
            }
        }.toImmutableList()
    }
    val onItemClick: (Int) -> Unit = { index -> onAction(flat[index].id) }
    val onChildClick: (Int, Int) -> Unit = { parent, child ->
        onAction(flat[parent].children[child].id)
    }
    var anchorBounds by remember { mutableStateOf(IntRect.Zero) }
    // 入口包唯一可点节点 + 锚点上报，Mochi 只出菜单浮层，开合是「点击松手后」。
    // Icon 锚点（顶栏）穿项目的 Mochi 顶栏图标按钮那层壳——也就是 `TopBarActionButton`
    // 在 Mochi 下的实现，否则 ⋯ 是一颗没有底色的裸图标，和同排搜索按钮不一致。
    // Row 锚点（设置行 / 自绘整行）仍由 trigger 自己画，入口只负责可点与锚点。
    // 受控模式没有 trigger 节点，锚点直接取调用方给的外层容器（与 MochiChoice 一致）。
    Box(
        modifier.then(
            if (embeddedExpanded != null) {
                Modifier.onGloballyPositioned {
                    anchorBounds = it.boundsInWindow().roundToIntRect()
                }
            } else {
                Modifier
            }
        )
    ) {
        if (embeddedExpanded == null) {
            if (anchor == AppMenuAnchor.Icon) {
                TopBarActionSurface(
                    onClick = scope::open,
                    modifier = Modifier.onGloballyPositioned {
                        anchorBounds = it.boundsInWindow().roundToIntRect()
                    },
                    enabled = enabled,
                    contentKey = "app-menu",
                ) { scope.trigger() }
            } else {
                Box(
                    scope.modifier
                        .onGloballyPositioned { anchorBounds = it.boundsInWindow().roundToIntRect() }
                        .clipTriggerShape(triggerShape)
                        .clickable(enabled = enabled) { scope.open() },
                ) { scope.trigger() }
            }
        }
        MochiDropdownMenu(
            anchorBounds = anchorBounds,
            expanded = isOpen && enabled,
            onExpandedChange = { open ->
                if (embeddedExpanded == null) expanded = open
                if (!open) onDismissRequest()
            },
            items = items,
            onItemClick = onItemClick,
            onChildClick = onChildClick,
            menuWidth = if (anchor == AppMenuAnchor.Icon) {
                MochiDropdownDefaults.IconMenuWidth
            } else {
                MochiDropdownDefaults.MenuWidth
            },
        )
    }
}

/** COUI 原生命令菜单：分组用 `dividerBefore`，勾选与子菜单由 [CouiMenuItem] 直接表达。 */
@Composable
private fun CouiActions(
    groups: ImmutableList<AppActionGroup>,
    onAction: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    anchor: AppMenuAnchor,
    title: String?,
    triggerShape: Shape,
    embeddedExpanded: Boolean?,
    onDismissRequest: () -> Unit,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isOpen = embeddedExpanded ?: expanded
    val dismissMenu = {
        expanded = false
        onDismissRequest()
    }
    val couiAnchor = rememberCouiMenuAnchor()
    // COUI 的 CouiMenuItem 没有文字色，destructive 只能落到图标上（文字保持菜单默认色）。
    val destructiveTint = LegadoTheme.colorScheme.error
    val items = remember(groups, destructiveTint) {
        buildList {
            groups.forEachIndexed { groupIndex, group ->
                group.actions.forEachIndexed { actionIndex, action ->
                    add(
                        CouiMenuItem(
                            value = action.id,
                            text = action.label,
                            icon = action.icon,
                            selected = action.selected,
                            enabled = action.enabled,
                            checked = action.checked,
                            dividerBefore = group.dividerBeforeAt(actionIndex, groupIndex),
                            iconTint = if (action.destructive) destructiveTint else null,
                            children = action.children.map { child ->
                                CouiMenuItem(
                                    value = child.id,
                                    text = child.label,
                                    icon = child.icon,
                                    selected = child.selected,
                                    enabled = child.enabled,
                                    checked = child.checked,
                                    iconTint = if (child.destructive) destructiveTint else null,
                                )
                            },
                        )
                    )
                }
            }
        }
    }
    val scope = remember(couiAnchor) {
        AppMenuTriggerScopeImpl(
            modifier = Modifier.couiMenuAnchor(couiAnchor),
            onOpen = { couiAnchor.open { expanded = true } },
        )
    }
    // 受控模式**不能**把 couiMenuAnchor 挂到容器上：该修饰符内含 pointerInput，作为覆盖在
    // 调用方触发按钮之上的兄弟节点会成为命中的唯一节点，按钮再也收不到点击（菜单永远打不开）。
    // 定位也不需要它：COUI 的触点锚点只在 open() 记下触点后才产生坐标，受控模式没有触点，
    // 弹层按 Popup 的父容器矩形定位（COUI `Menu.anchor` 文档写明「不提供时沿用父布局定位」），
    // 而容器就是调用方放本组件的位置——正是下拉应有的锚点；反过来传 anchor 只会拿到 null 坐标。
    Box(modifier) {
        if (embeddedExpanded != null) {
            // 受控模式：弹层锚在调用方给定的位置，入口不包可点节点。
        } else if (anchor == AppMenuAnchor.Row) {
            // 列表项 / 卡片内：壳由 trigger 自己画，入口只包一个可点节点。
            // 必须按 trigger 的形状裁剪：否则按压底色/水波纹是方角，观感上就是「按钮没有圆角」。
            Box(
                scope.modifier
                    .clipTriggerShape(triggerShape)
                    .clickable(enabled = enabled) { scope.open() }
                    .semantics { title?.let { text = AnnotatedString(it) } },
            ) { scope.trigger() }
        } else {
            // 按钮壳归顶栏：与同排 TopBarActionButton 共用尺寸、容器底色、液体玻璃与合并态，
            // 否则 ⋮ 会是整排里唯一一颗裸 IconButton。
            TopBarActionSurface(
                onClick = scope::open,
                modifier = scope.modifier,
                enabled = enabled,
            ) { scope.trigger() }
        }
        Menu(
            expanded = isOpen && enabled,
            onDismissRequest = dismissMenu,
            items = items,
            onItemClick = { item ->
                // COUI 的 Menu 不会在选中后自动回调 onDismissRequest；受控模式下只改
                // 内部 expanded 关不掉，必须显式通知调用方（非受控时 onDismissRequest 为空实现）。
                dismissMenu()
                onAction(item.value)
            },
            // 受控模式没有触点锚点；传 anchor 只会得到 null 坐标，见上面对容器修饰符的说明。
            anchor = if (embeddedExpanded == null) couiAnchor else null,
        )
    }
}

/**
 * Miuix 列表项命令菜单（`Row` 锚点）：壳由 [trigger] 自己画，弹层走
 * `WindowDropdownPopup` / `OverlayDropdownPopup`。
 *
 * 库的 `Icon*DropdownMenu` 会把 content 包进它自己的图标按钮，列表项 / 卡片里那层壳不对，
 * 因此 `Row` 锚点必须绕开它；条目构造仍是同一份 [toMiuixEntries]，语义不因锚点变化。
 * 锚定方式与 `AppChoiceDropdown` 的紧凑形态一致（同一个 `Box` 内的 popup）。
 */
@Composable
private fun MiuixRowActions(
    groups: ImmutableList<AppActionGroup>,
    onAction: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    title: String?,
    triggerShape: Shape,
    embeddedExpanded: Boolean?,
    onDismissRequest: () -> Unit,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isOpen = embeddedExpanded ?: expanded
    val dismissMenu = {
        expanded = false
        onDismissRequest()
    }
    val scope = remember { AppMenuTriggerScopeImpl(Modifier) { expanded = true } }
    val glass = isMiuixGlassMenuAvailable()
    val anchor = rememberGlassPopupAnchor()
    // 受控时显式通知调用方收口：常规 popup 有 collapseOnSelection，玻璃弹层不保证。
    val entries = remember(groups, onAction, embeddedExpanded != null) {
        groups.toMiuixEntries { id ->
            if (embeddedExpanded != null) onDismissRequest()
            onAction(id)
        }
    }
    // `glassPopupAnchor` 同时写入 containerBounds 与 cornerRadius。受控模式没有 trigger 节点，
    // 不挂在调用方容器上，anchor 就保留 Rect.Zero / 0.dp：玻璃面板从窗口原点 morph（左上角），
    // 收起插值 `startRadius + (endRadius - startRadius) * fraction` 越过 0 时还会得到负圆角。
    // 与 AppChoiceDropdown 的受控 containerAnchor 保持同一口径；12dp 对齐方卡 / 尾部区域。
    val containerAnchor = if (glass && embeddedExpanded != null) {
        Modifier.glassPopupAnchor(anchor, cornerRadius = 12.dp)
    } else {
        Modifier
    }
    Box(modifier.then(containerAnchor)) {
        if (embeddedExpanded == null) {
            Box(
                scope.modifier
                    // morph 起点圆角对齐 `SmallPlainButton`（32dp 圆形）。
                    .then(if (glass) Modifier.glassPopupAnchor(anchor, cornerRadius = 16.dp) else Modifier)
                    .clipTriggerShape(triggerShape)
                    .clickable(enabled = enabled) { scope.open() }
                    .semantics { title?.let { text = AnnotatedString(it) } },
            ) { scope.trigger() }
        }
        if (glass) {
            // HyperOS 4：Row 锚点也走原生玻璃菜单——面板从按钮矩形 morph 出来，
            // 行、级联子菜单、勾选态全部由库画。否则同一页会出现「顶栏 ⋮ 是玻璃、
            // 列表 ⋮ 还是 OS3 面板」的错位。
            // `anchorContent` 必须是 trigger 内容：`glassPopupAnchor` 只把原按钮瞬间
            // 置为透明，过渡中移动/缩放的那一份图标由弹层自己画；留空就成了「图标直接
            // 消失再出现」。这里不给 trigger 挂 `glassPopupAnchorContent`——弹层自己
            // 画面板（没有 `anchor.surface`），库需要复制整个控件而不是只复制图标。
            MiuixGlassMenuHost(
                show = isOpen && enabled,
                onDismissRequest = dismissMenu,
                anchor = anchor,
                entries = entries,
                // 受控模式没有 trigger 节点（内容由调用方渲染），玻璃过渡只能靠面板自身 morph。
                anchorContent = if (embeddedExpanded == null) {
                    { scope.trigger() }
                } else {
                    {}
                },
            )
        } else {
            val dropdownColors = DropdownDefaults.dropdownColors()
            if (LocalHasMiuixPopupHost.current) {
                OverlayDropdownPopup(
                    entries = entries,
                    show = isOpen && enabled,
                    onDismiss = dismissMenu,
                    onDismissFinished = {},
                    maxHeight = null,
                    dropdownColors = dropdownColors,
                    renderInRootScaffold = true,
                    collapseOnSelection = true,
                )
            } else {
                WindowDropdownPopup(
                    entries = entries,
                    show = isOpen && enabled,
                    onDismiss = dismissMenu,
                    onDismissFinished = {},
                    maxHeight = null,
                    dropdownColors = dropdownColors,
                    collapseOnSelection = true,
                )
            }
        }
    }
}

/**
 * Miuix 原生命令菜单：整组条目与触发器一起交给库渲染。
 *
 * 有 popup host（Scaffold 页面）走 `Overlay*`、window 级弹层走 `Window*`，与既有
 * `DropdownPreference` 的选路一致；含 [AppAction.children] 时切 `Cascading` 变体，
 * 由库渲染返回头与子菜单。HyperOS 4 玻璃场景改走 [MiuixGlassActions] 取得锚点变形。
 *
 * 能力降级（Miuix 的 `DropdownItem` 只有 `selected`，没有分组标题/危险色/独立勾选）：
 * `checked == true` 与 `selected` 都映射为 `selected`（库画勾选图标），分组标题与
 * `dividerBefore` 退化为组间分隔线，`destructive` 不体现。不静默丢条目或伪造勾选态。
 * 常规分支的 `expanded` 由库自持，[AppMenuTriggerScope.open] 是 no-op；需要外部驱动
 * 开合的场景应使用 `AppAnchoredPanel`。
 */
@Composable
private fun MiuixActions(
    groups: ImmutableList<AppActionGroup>,
    onAction: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    val entries = remember(groups, onAction) { groups.toMiuixEntries(onAction) }
    val hasChildren = remember(groups) {
        groups.any { group -> group.actions.any { it.children.isNotEmpty() } }
    }
    val glassOverlay = LocalAppGlassOverlay.current
    val hasPopupHost = LocalHasMiuixPopupHost.current
    // 跟随顶栏自己的玻璃信号（与 TopBarActionButton / TopBarOverflowMenu 同一判定）：
    // 同排按钮都带玻璃时，⋮ 也必须走玻璃分支，否则会落到库自带的普通 IconButton，
    // 出现「整排玻璃、只有 ⋮ 没有」的错位观感。
    val glassBar = LocalMiuixGlassTopBar.current
    val glassHost = glassOverlay?.takeIf { glassBar && hasPopupHost }
    if (glassHost != null) {
        MiuixGlassActions(glassHost, entries, modifier, enabled, trigger)
        return
    }

    // 库自己渲染 IconButton 与 expanded，scope 只承载纯展示 trigger。
    val scope = remember { AppMenuTriggerScopeImpl(Modifier) {} }
    val menuTrigger: @Composable () -> Unit = { scope.trigger() }
    when {
        hasPopupHost && hasChildren -> OverlayIconCascadingDropdownMenu(
            entries = entries,
            modifier = modifier,
            enabled = enabled,
            collapseOnSelection = true,
            content = menuTrigger,
        )

        hasPopupHost -> OverlayIconDropdownMenu(
            entries = entries,
            modifier = modifier,
            enabled = enabled,
            collapseOnSelection = true,
            content = menuTrigger,
        )

        hasChildren -> WindowIconCascadingDropdownMenu(
            entries = entries,
            modifier = modifier,
            enabled = enabled,
            collapseOnSelection = true,
            content = menuTrigger,
        )

        else -> WindowIconDropdownMenu(
            entries = entries,
            modifier = modifier,
            enabled = enabled,
            collapseOnSelection = true,
            content = menuTrigger,
        )
    }
}

/**
 * HyperOS 4 玻璃命令菜单。面板必须注册进 [AppGlassOverlayState] 的 root `BoxScope`，
 * 才能和顶栏按钮共享坐标根；`glassPopupAnchor` 只挂在按钮最外层节点，否则库的
 * `GlassButtonSurface` 取不到锚点，且图标会重复上报 `containerBounds`。
 */
@Composable
private fun MiuixGlassActions(
    glassOverlay: AppGlassOverlayState,
    entries: List<DropdownEntry>,
    modifier: Modifier,
    enabled: Boolean,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberGlassPopupAnchor()
    val backdrop = LocalMiuixBarBackdrop.current
    val scope = remember { AppMenuTriggerScopeImpl(Modifier) {} }
    MiuixGlassMenuHost(
        show = expanded && enabled,
        onDismissRequest = { expanded = false },
        anchor = anchor,
        entries = entries,
        anchorContent = { scope.trigger() },
    )
    MiuixGlassIconButton(
        onClick = { if (enabled) expanded = true },
        modifier = modifier
            .glassPopupAnchor(
                anchor,
                cornerRadius = GlassTopAppBarDefaults.ButtonSize / 2,
                floating = true,
            )
            .semantics { role = Role.Button },
        backdrop = backdrop,
    ) {
        // `GlassTransformPopup` 用 `anchor.contentBounds` 定位过渡里那份图标副本；
        // 不挂这个修饰符时它会退回整颗按钮的 containerBounds，副本被放在按钮左上角，
        // 展开首帧 / 收起末帧就能看到图标往左上「飞」一下（玻璃底本身不动）。
        Box(Modifier.glassPopupAnchorContent(anchor)) { scope.trigger() }
    }
}

private fun List<AppActionGroup>.toMiuixEntries(
    onAction: (String) -> Unit,
): List<DropdownEntry> = buildList {
    // buildList 的 MutableList receiver 会抢走隐式 forEach，必须显式指向扩展 receiver。
    this@toMiuixEntries.forEachIndexed { groupIndex, group ->
        var bucket = mutableListOf<DropdownItem>()
        fun flush() {
            if (bucket.isNotEmpty()) {
                add(DropdownEntry(bucket))
                bucket = mutableListOf()
            }
        }
        group.actions.forEachIndexed { actionIndex, action ->
            if (group.dividerBeforeAt(actionIndex, groupIndex)) flush()
            bucket += action.toMiuixItem(onAction)
        }
        flush()
    }
}

private fun AppAction.toMiuixItem(onAction: (String) -> Unit): DropdownItem {
    val iconVector = icon
    val leafClick: (() -> Unit)? = if (children.isEmpty()) {
        { onAction(id) }
    } else null
    return DropdownItem(
        text = label,
        enabled = enabled,
        selected = selected || checked == true,
        onClick = leafClick,
        icon = iconVector?.let { vector ->
            { m -> AppIcon(imageVector = vector, contentDescription = null, modifier = m) }
        },
        children = children.takeIf { it.isNotEmpty() }?.map { child ->
            DropdownItem(
                text = child.label,
                enabled = child.enabled,
                selected = child.selected || child.checked == true,
                onClick = { onAction(child.id) },
            )
        },
    )
}

@Composable
private fun LegacyActions(
    groups: ImmutableList<AppActionGroup>,
    onAction: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    anchor: AppMenuAnchor,
    title: String?,
    triggerShape: Shape,
    embeddedExpanded: Boolean?,
    onDismissRequest: () -> Unit,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isOpen = embeddedExpanded ?: expanded
    val dismissMenu = {
        expanded = false
        onDismissRequest()
    }
    val scope = remember { AppMenuTriggerScopeImpl(Modifier) { expanded = true } }
    Box(modifier) {
        if (embeddedExpanded != null) {
            // 受控模式：弹层锚在调用方给定的位置，入口不包可点节点。
        } else if (anchor == AppMenuAnchor.Row) {
            Box(
                scope.modifier
                    .clipTriggerShape(triggerShape)
                    .clickable(enabled = enabled) { scope.open() }
                    .semantics { title?.let { text = AnnotatedString(it) } },
            ) { scope.trigger() }
        } else {
            // 按钮壳归顶栏：与同排 TopBarActionButton 共用尺寸、容器底色、液体玻璃与合并态，
            // 否则 ⋮ 会是整排里唯一一颗裸 IconButton。
            TopBarActionSurface(
                onClick = scope::open,
                modifier = scope.modifier,
                enabled = enabled,
            ) { scope.trigger() }
        }
        RoundDropdownMenu(
            expanded = isOpen && enabled,
            onDismissRequest = dismissMenu,
        ) { dismiss ->
            groups.forEachIndexed { groupIndex, group ->
                group.actions.forEachIndexed { actionIndex, action ->
                    if (group.dividerBeforeAt(actionIndex, groupIndex)) PillDivider()
                    RoundDropdownMenuItem(
                        text = action.label,
                        // destructive 必须落成文字色：本分支的 contentColor 同时染文字与 leadingIcon。
                        color = if (action.destructive) LegadoTheme.colorScheme.error
                        else Color.Unspecified,
                        enabled = action.enabled,
                        isSelected = action.selected || action.checked == true,
                        modifier = Modifier.semantics {
                            selected = action.selected || action.checked == true
                        },
                        leadingIcon = action.icon?.let { vector ->
                            { AppIcon(imageVector = vector, contentDescription = null) }
                        },
                        onClick = {
                            dismiss()
                            onAction(action.id)
                        },
                    )
                    // Material 没有级联弹层：子项平铺为缩进项，保证 ID 与回调不被静默丢弃。
                    action.children.forEach { child ->
                        RoundDropdownMenuItem(
                            text = child.label,
                            color = if (child.destructive) LegadoTheme.colorScheme.error
                            else Color.Unspecified,
                            enabled = child.enabled,
                            isSelected = child.selected || child.checked == true,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .semantics { selected = child.selected || child.checked == true },
                            onClick = {
                                dismiss()
                                onAction(child.id)
                            },
                        )
                    }
                }
            }
        }
    }
}
