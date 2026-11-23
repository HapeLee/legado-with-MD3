package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.sheet.BottomSheet
import com.itos.coui.sheet.CouiSheetButton
import com.itos.coui.sheet.CouiSheetActionButton
import com.itos.coui.sheet.CouiSheetDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import com.kyant.capsule.ContinuousRoundedRectangle
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalHasMiuixPopupHost
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.LocalMochiBackdropRegistry
import io.legado.app.ui.theme.LocalLegadoThemeColors
import io.legado.app.ui.theme.ProvideAppContentColor
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.ProvidePopupThemeOverride
import io.legado.app.ui.theme.ThemeOverrideState
import io.legado.app.ui.theme.rememberMochiOverlayBackdrop
import io.legado.app.ui.theme.rememberPopupThemeOverride
import io.legado.app.ui.widget.components.LocalAppGlassOverlay
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.ui.widget.components.menu.AppMenuAnchor
import io.legado.app.ui.widget.components.mochi.MochiOverlayAccessibility
import io.legado.app.ui.widget.components.mochi.mochiOverlayContentModifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetContent
import ui.feice.mochi.basic.MochiSheetDetent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.effect.glass.LocalMochiBackdrop
import ui.feice.mochi.shape.MochiCapsule
import ui.feice.mochi.shape.MochiContinuity
import ui.feice.mochi.theme.MochiTheme

private val MochiModalTopShape = ContinuousRoundedRectangle(32.dp, 32.dp, 0.dp, 0.dp, MochiContinuity)

/**
 * 玻璃 / 窗口 sheet 的内容包装。窗口级 sheet 的内容继承调用点 locals、但不在 root host
 * 中，必须清掉宿主信号避免子菜单注册到画不到的地方；玻璃 sheet 与 root host 同根，保留
 * 信号，并由 ProvidePopupThemeOverride 重放宿主侧捕获的页面级色板覆盖。
 */
@Composable
private fun MiuixSheetLocals(
    useGlassContainer: Boolean,
    sheetContentColor: Color,
    themeOverride: State<ThemeOverrideState?>,
    content: @Composable () -> Unit,
) {
    // 玻璃 sheet 的内容由 root popup host 的独立 composition 执行，那里只继承 AppTheme
    // 的全局色板，看不到页面级 ProvideColorSchemeOverride，必须在这里重新提供。
    // 放在最外层，让下面的 ContentColor 与 host 信号保持更高优先级。
    ProvidePopupThemeOverride(themeOverride) {
        ProvideAppDensity {
            ProvideAppContentColor(sheetContentColor) {
                CompositionLocalProvider(
                    LocalHasMiuixPopupHost provides useGlassContainer,
                    LocalAppGlassOverlay provides LocalAppGlassOverlay.current.takeIf { useGlassContainer },
                    LocalMiuixBarBackdrop provides LocalMiuixBarBackdrop.current.takeIf { useGlassContainer },
                ) {
                    content()
                }
            }
        }
    }
}

enum class MochiSheetLayout {
    /** 按内容高度停靠，超过可用高度时停在全屏；滚动由调用方自己的容器负责。 */
    HalfScreen,

    /** 同 [HalfScreen]；保留名称以兼容已有调用方。 */
    FullScreen,

    /** 按内容高度停靠，超过可用高度时停在全屏；面板自带可滚正文，调用方不要再套滚动容器。 */
    Compact,

    /** 同 [Compact]，显式声明贴合内容；面板自带可滚正文，调用方不要再套滚动容器。 */
    FitContent,

    /** Use the modal window for callers that depend on its presentation contract. */
    Compatibility,
}

/**
 * 各引擎共用的模态面板入口。
 *
 * @param windowPresentation 是否必须用独立窗口（Dialog）呈现。Miuix 玻璃面板与 Mochi 面板都画在
 * 宿主 Activity 的层内，调用点如果本身位于独立窗口（如划词浮层 Popup），面板会落到该窗口之下
 * 并被它吃掉触摸；此时置 true 强制窗口级呈现，与非浮动引擎的 M3 Dialog 行为一致。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppModalBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    startSheetAction: SheetAction? = null,
    endSheetAction: SheetAction? = null,
    startSheetActions: ImmutableList<SheetAction> = persistentListOf(),
    endSheetActions: ImmutableList<SheetAction> = persistentListOf(),
    animateContentSize: Boolean = true,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    contentPaddingEnabled: Boolean = true,
    sheetGesturesEnabled: Boolean = true,
    mochiLayout: MochiSheetLayout = MochiSheetLayout.HalfScreen,
    windowPresentation: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = LocalLegadoThemeColors.current.colorScheme
    val sheetContainerColor = LegadoTheme.colorScheme.surfaceContainer
    val sheetContentColor = LegadoTheme.colorScheme.onSurface
    val sheetDragHandleColor = LegadoTheme.colorScheme.onSurfaceVariant
    val composeEngine = LegadoTheme.composeEngine
    val isMochi = composeEngine == ComposeEngine.Mochi
    // 头部操作只有声明式一种形态：同一份数据由各引擎渲染成自己的按钮。
    val startActions = resolveSheetActions(startSheetAction, startSheetActions)
    val endActions = resolveSheetActions(endSheetAction, endSheetActions)
    val startSlot = sheetActionSlot(startActions)
    val endSlot = sheetActionSlot(endActions)

    // Mochi attaches a sibling ComposeView to the Activity decor. Other windows need their own
    // modal host to retain window insets and the local back dispatcher.
    val inActivityWindow = LocalView.current.rootView === LocalActivity.current?.window?.decorView
    if (isMochi && !windowPresentation && mochiLayout != MochiSheetLayout.Compatibility && sheetGesturesEnabled && inActivityWindow) {
        MochiOverlayAccessibility(show)
        val backdrop = rememberMochiOverlayBackdrop(show)
        val registry = LocalMochiBackdropRegistry.current
        // 与 M3 / Miuix 一致：先按内容高度停靠，内容超过可用高度时停在全屏并让正文滚动。
        // Large 只作为可上拉的上限，初始停靠点始终是 FitContent。
        val state = rememberMochiSheetState(
            detents = listOf(MochiSheetDetent.FitContent, MochiSheetDetent.Large),
        )
        LaunchedEffect(state, show) {
            if (show) state.show() else state.hide()
        }
        CompositionLocalProvider(LocalMochiBackdrop provides backdrop) {
            MochiSheet(
                state = state,
                onDismiss = { if (show) onDismissRequest() },
                color = sheetContainerColor,
                hideNavigationBar = false,
            ) {
                if (registry != null) {
                    DisposableEffect(registry, state) {
                        registry.addSheet(state)
                        onDispose { registry.removeSheet(state) }
                    }
                }
                ProvideAppDensity {
                    ProvideAppContentColor(sheetContentColor) {
                        Column(mochiOverlayContentModifier(state::hide, title).fillMaxWidth()) {
                            val hasHeader = !title.isNullOrEmpty() || startSlot != null || endSlot != null
                            if (hasHeader) {
                                MochiSheetHeader(
                                    title = title.orEmpty(),
                                    navigationIcon = startSlot,
                                    actions = endSlot,
                                )
                            }
                            val bodyModifier = Modifier
                                .weight(1f, fill = false)
                                // The native detent already places the panel below the top safe area.
                                .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                .windowInsetsPadding(contentWindowInsets())
                                .imePadding()
                                .let { if (animateContentSize) it.animateContentSize() else it }
                                .then(modifier)
                            val scrollable = mochiLayout == MochiSheetLayout.Compact ||
                                mochiLayout == MochiSheetLayout.FitContent
                            if (scrollable && contentPaddingEnabled) {
                                MochiSheetContent(
                                    modifier = bodyModifier,
                                    verticalArrangement = Arrangement.Top,
                                    content = content,
                                )
                            } else {
                                Column(
                                    modifier = bodyModifier
                                        .fillMaxWidth()
                                        .padding(top = if (hasHeader) 8.dp else 16.dp)
                                        .let {
                                            if (contentPaddingEnabled) {
                                                it.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                            }
                                            else it
                                        },
                                    content = content,
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // COUI 有自己的面板：表面、材质、遮罩、拖拽与入退场都由库提供，不再套 M3 窗口。
    // 引擎解析已经要求 API 34，这里的守卫只是与其余 COUI 调用点同口径。
    if (composeEngine == ComposeEngine.Coui && ThemeResolver.isCouiSupported()) {
        CouiAppModalBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            title = title,
            startSlot = startSlot,
            endSlot = endSlot,
            startSheetAction = startActions.singleOrNull(),
            endSheetAction = endActions.singleOrNull(),
            // 库工具栏每侧只有一个按钮槽、按钮也挂不了菜单：多按钮或菜单按钮改由
            // App 自绘头部，但按钮本体仍是库按钮，只有自定义 Composable 槽才退回 App 按钮。
            useLibraryToolbar = declarativeSheetHeader(startActions, endActions),
            contentPaddingEnabled = contentPaddingEnabled,
            sheetGesturesEnabled = sheetGesturesEnabled,
            content = content,
        )
        return
    }

    when (composeEngine) {
        ComposeEngine.Miuix -> {
            // 在宿主 composition 里捕获页面级色板覆盖，交给内容侧带到 root popup host。
            val themeOverride = rememberPopupThemeOverride()
            val themeSettings = LocalAppUiConfiguration.current.theme
            // 开关打开后只走玻璃；无玻璃设施的独立窗口（如朗读悬浮窗）才退回窗口面板。
            // 玻璃面板画在宿主 Activity 的 Scaffold overlay 内，调用点若本身位于独立窗口
            // （例如划词浮层 Popup）时会被该窗口盖住并吃掉触摸，因此由调用方显式要求窗口级呈现。
            val useGlassContainer = !windowPresentation &&
                themeSettings.useHyperOs4Components &&
                ThemeResolver.isBlurSupported() &&
                LocalAppGlassOverlay.current != null
            val backdrop = LocalMiuixBarBackdrop.current
            val insideMargin = if (contentPaddingEnabled) DpSize(16.dp, 0.dp) else DpSize(0.dp, 0.dp)
            val wrappedStartAction: (@Composable () -> Unit)? = startSlot?.let { action ->
                {
                    MiuixSheetLocals(useGlassContainer, sheetContentColor, themeOverride) {
                        Box(
                            modifier = if (contentPaddingEnabled) Modifier
                            else Modifier.padding(start = 16.dp),
                        ) { action() }
                    }
                }
            }
            val wrappedEndAction: (@Composable () -> Unit)? = endSlot?.let { action ->
                {
                    MiuixSheetLocals(useGlassContainer, sheetContentColor, themeOverride) {
                        Box(
                            modifier = if (contentPaddingEnabled) Modifier
                            else Modifier.padding(end = 16.dp),
                        ) { action() }
                    }
                }
            }
            // 玻璃 sheet 的内容在 root dialog host 的独立组合里执行。用 State 持有最新
            // content，让该组合订阅宿主重组，避免只显示注册瞬间的快照；窗口分支同值幂等。
            val currentSheetContent by rememberUpdatedState(content)
            val sheetBody: @Composable () -> Unit = {
                MiuixSheetLocals(useGlassContainer, sheetContentColor, themeOverride) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .let {
                                if (contentPaddingEnabled) {
                                    it.padding(bottom = 24.dp)
                                } else {
                                    it.navigationBarsPadding()
                                }
                            }
                            .let { contentModifier ->
                                if (animateContentSize) contentModifier.animateContentSize() else contentModifier
                            },
                    ) {
                        currentSheetContent()
                    }
                }
            }
            if (useGlassContainer) {
                // A glass sheet registers in the Scaffold's root popup host. Its content and
                // nested dropdowns then share one coordinate root and one z-order.
                GlassBottomSheet(
                    onDismissRequest = onDismissRequest,
                    show = show,
                    backdrop = backdrop,
                    modifier = modifier,
                    title = title,
                    startAction = wrappedStartAction,
                    endAction = wrappedEndAction,
                    insideMargin = insideMargin,
                    containerColor = sheetContainerColor,
                    dragHandleColor = sheetDragHandleColor,
                    enableWindowDim = true,
                    content = sheetBody,
                )
            } else {
                WindowBottomSheet(
                    show = show,
                    modifier = modifier,
                    title = title,
                    startAction = wrappedStartAction,
                    endAction = wrappedEndAction,
                    insideMargin = insideMargin,
                    backgroundColor = sheetContainerColor,
                    dragHandleColor = sheetDragHandleColor,
                    onDismissRequest = onDismissRequest,
                    enableWindowDim = true,
                    content = sheetBody,
                )
            }
        }
        ComposeEngine.Oxygen, ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            if (show) {
                val sheetState = rememberBottomSheetState(
                    initialValue = Hidden,
                    enabledValues = setOf(Hidden, Expanded)
                )
                val density = LocalDensity.current
                val maxHeight = with(density) {
                    val containerHeight = LocalWindowInfo.current.containerSize.height.toDp()
                    containerHeight * 0.8f
                }

                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    typography = Typography(),
                    motionScheme = MotionScheme.expressive(),
                    shapes = Shapes()
                ) {
                    ModalBottomSheet(
                        onDismissRequest = onDismissRequest,
                        sheetState = sheetState,
                        shape = if (isMochi) MochiModalTopShape else BottomSheetDefaults.ExpandedShape,
                        containerColor = sheetContainerColor,
                        contentColor = sheetContentColor,
                        dragHandle = {
                            if (isMochi) {
                                Box(
                                    Modifier
                                        .padding(top = 6.dp, bottom = 13.dp)
                                        .size(width = 36.dp, height = 5.dp)
                                        .background(sheetDragHandleColor.copy(alpha = 0.35f), MochiCapsule)
                                )
                            } else {
                                BottomSheetDefaults.DragHandle(color = sheetDragHandleColor)
                            }
                        },
                        contentWindowInsets = contentWindowInsets,
                        sheetGesturesEnabled = sheetGesturesEnabled,
                    ) {
                        val sheetBody: @Composable () -> Unit = {
                            ProvideAppDensity {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .let {
                                            if (contentPaddingEnabled) {
                                                it.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                            } else {
                                                it
                                            }
                                        }
                                        .heightIn(max = maxHeight)
                                        .let { contentModifier ->
                                            if (animateContentSize) contentModifier.animateContentSize() else contentModifier
                                        }
                                        .then(modifier)
                                ) {
                                    SheetHeader(
                                        title = title,
                                        startAction = startSlot,
                                        endAction = endSlot,
                                        contentPaddingEnabled = contentPaddingEnabled,
                                    ) { text ->
                                        Text(
                                            text = text,
                                            style = if (isMochi) MochiTheme.textStyles.navTitle
                                                else LegadoTheme.typography.titleMediumEmphasized,
                                            color = sheetContentColor,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 56.dp)
                                        )
                                    }

                                    content()
                                }
                            }
                        }
                        // COUI 已在上面分流到自己的面板，这里的窗口不再需要 COUI 采样根。
                        sheetBody()
                    }
                }
            }
        }
    }
}

/** 各引擎共用的标题行：标题居中，两侧操作贴边；三者都没有时不占高度。 */
@Composable
private fun SheetHeader(
    title: String?,
    startAction: @Composable (() -> Unit)?,
    endAction: @Composable (() -> Unit)?,
    contentPaddingEnabled: Boolean,
    modifier: Modifier = Modifier,
    titleContent: @Composable (String) -> Unit,
) {
    if (title.isNullOrEmpty() && startAction == null && endAction == null) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (startAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .let { if (contentPaddingEnabled) it else it.padding(start = 16.dp) },
            ) {
                startAction()
            }
        }

        if (!title.isNullOrEmpty()) titleContent(title)

        if (endAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .let { if (contentPaddingEnabled) it else it.padding(end = 16.dp) },
            ) {
                endAction()
            }
        }
    }
}

/** 一侧的声明式操作：单数与复数槽合并，复数优先。空列表代表该侧什么都不显示。 */
private fun resolveSheetActions(
    single: SheetAction?,
    many: ImmutableList<SheetAction>,
): List<SheetAction> = when {
    many.isNotEmpty() -> many
    single != null -> listOf(single)
    else -> emptyList()
}

/** 声明式操作渲染成按钮；空列表时该侧不占位。 */
private fun sheetActionSlot(
    actions: List<SheetAction>,
): (@Composable () -> Unit)? = when {
    actions.isEmpty() -> null
    else -> ({ SheetActions(actions) })
}

/**
 * COUI 的库工具栏只有在「每侧最多一颗、且都不带菜单」时才成立：
 * 工具栏每侧只有一个按钮槽，库按钮也没有菜单能力。其余情况由 App 自绘头部，按钮仍按引擎渲染。
 */
private fun declarativeSheetHeader(
    startActions: List<SheetAction>,
    endActions: List<SheetAction>,
): Boolean = startActions.size <= 1 && endActions.size <= 1 &&
    startActions.none { it.menu.isNotEmpty() } &&
    endActions.none { it.menu.isNotEmpty() }

/**
 * 一至多颗声明式操作。单颗不加额外间距，多颗按 4dp 紧凑排列——与各引擎顶栏按钮的间距口径一致。
 */
@Composable
private fun SheetActions(actions: List<SheetAction>) {
    if (actions.size == 1) {
        SheetActionButton(actions[0])
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { SheetActionButton(it) }
    }
}

@Composable
private fun SheetActionButton(action: SheetAction) {
    val hasMenu = action.menu.isNotEmpty()
    var menuExpanded by remember { mutableStateOf(false) }
    val onClick: () -> Unit = if (hasMenu) {
        { menuExpanded = true }
    } else {
        action.onClick ?: {}
    }
    Box(contentAlignment = Alignment.Center) {
        if (LegadoTheme.composeEngine == ComposeEngine.Coui && ThemeResolver.isCouiSupported()) {
            // COUI 按钮只有一款尺寸，`compact` / `selected` 属于该引擎的已知降级。
            CouiSheetActionButton(action.toCouiSheetButton(onClick))
        } else if (action.compact) {
            SmallTonalButton(
                onClick = onClick,
                modifier = action.modifier,
                enabled = action.enabled,
                selected = action.selected,
                icon = action.icon,
                text = action.text,
                contentDescription = action.contentDescription,
            )
        } else {
            MediumTonalButton(
                onClick = onClick,
                modifier = action.modifier,
                enabled = action.enabled,
                selected = action.selected,
                icon = action.icon,
                text = action.text,
                contentDescription = action.contentDescription,
            )
        }
        if (hasMenu) {
            // 菜单绑在按钮上：受控弹层撑满按钮矩形，四引擎各自的原生弹层与定位沿用既有实现。
            AppActionMenu(
                groups = remember(action.menu) { action.menu.toAppActionGroups() },
                onAction = { id ->
                    menuExpanded = false
                    action.menu.firstOrNull { it.id == id }?.onClick?.invoke()
                },
                anchor = AppMenuAnchor.Row,
                title = action.contentDescription ?: action.text,
                embeddedExpanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.matchParentSize(),
            ) {}
        }
    }
}

private fun ImmutableList<SheetMenuAction>.toAppActionGroups(): ImmutableList<AppActionGroup> =
    persistentListOf(
        AppActionGroup(
            id = "sheet_action_menu",
            actions = map { item ->
                AppAction(
                    id = item.id,
                    label = item.label,
                    enabled = item.enabled,
                    checked = item.checked,
                    icon = item.icon,
                )
            }.toImmutableList(),
        ),
    )

/**
 * COUI 的按钮槽要求 label 与 icon 二选一：文字优先，只有图标时把 contentDescription
 * 交给 Icon；`selected` 在 COUI 按钮上没有对应状态，属于已知差异。
 */
internal fun SheetAction.toCouiSheetButton(
    onClick: (() -> Unit)? = null,
): CouiSheetButton {
    val iconVector = icon
    return CouiSheetButton(
        onClick = onClick ?: this.onClick ?: {},
        label = text ?: if (iconVector == null) contentDescription.orEmpty() else null,
        icon = if (text == null && iconVector != null) {
            ({ Icon(iconVector, contentDescription = contentDescription) })
        } else {
            null
        },
        enabled = enabled,
        modifier = modifier,
    )
}

/**
 * COUI 面板：表面、材质、遮罩、下拉关闭和入退场都由 [BottomSheet] 负责。
 *
 * 标题与操作走库工具栏（`titleContent` + `leadingButton`/`trailingButton`）时，能得到
 * COUI 的居中标题、胶囊材质、按压动效和按下防误触。工具栏每侧只有一个按钮槽、库按钮
 * 也挂不了菜单，因此只有「每侧最多一颗、且都不带菜单」时整条头部交给库（[useLibraryToolbar]）；
 * 其余情况由 [SheetHeader] 自绘头部，按钮仍由 [SheetActionButton] 渲染成库按钮，
 * 并补回工具栏的 `toolbarTopSpacing + toolbarHeight` 几何，避免正文顶到 45dp 圆角。
 */
@Composable
private fun CouiAppModalBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    title: String?,
    startSlot: (@Composable () -> Unit)?,
    endSlot: (@Composable () -> Unit)?,
    startSheetAction: SheetAction?,
    endSheetAction: SheetAction?,
    useLibraryToolbar: Boolean,
    contentPaddingEnabled: Boolean,
    sheetGesturesEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 面板自己播放退场：先在窗口内把 visible 落成 false，调用方随后关闭 show 时同值幂等。
    val visible = remember { mutableStateOf(show) }
    LaunchedEffect(show) { visible.value = show }
    val dark = CouiTheme.isDark
    // 页面根提供的窗口采样源；不能在本窗口里新建，否则会把面板自身录进材质。
    val windowBackdrop = rememberCouiWindowBackdrop()
    val material = if (CouiDialogDefaults.isMaterialSupported() && windowBackdrop != null) {
        CouiSheetDefaults.material(dark)
    } else {
        null
    }
    // 与 M3 分支同口径：正文自己决定滚动，面板不再套一层 couiVerticalScroll。
    // 内层 LazyColumn/LazyVerticalGrid 拿到无限高度会直接抛 IllegalStateException。
    val maxHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.8f
    }
    val sheetTitle = title?.takeIf { it.isNotEmpty() }
    val hasHeader = sheetTitle != null || startSlot != null || endSlot != null
    val libraryToolbar = useLibraryToolbar && hasHeader
    BottomSheet(
        visible = visible,
        onDismissRequest = {
            visible.value = false
            onDismissRequest()
        },
        dark = dark,
        draggable = sheetGesturesEnabled,
        scrollableContent = false,
        backdrop = windowBackdrop,
        material = material,
        leadingButton = if (libraryToolbar) startSheetAction?.toCouiSheetButton() else null,
        titleContent = if (libraryToolbar && sheetTitle != null) {
            ({ CouiText(text = sheetTitle, kind = CouiTextKind.SheetTitle) })
        } else {
            null
        },
        trailingButton = if (libraryToolbar) endSheetAction?.toCouiSheetButton() else null,
    ) {
        ProvideAppDensity {
            ProvideAppContentColor(LegadoTheme.colorScheme.onSurface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = when {
                                libraryToolbar -> 0.dp
                                hasHeader -> CouiSheetDefaults.toolbarTopSpacing
                                else -> 16.dp
                            },
                        )
                        .let {
                            if (contentPaddingEnabled) {
                                it.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            } else {
                                it
                            }
                        }
                        .heightIn(max = maxHeight)
                        .then(modifier),
                ) {
                    if (!libraryToolbar) {
                        SheetHeader(
                            title = title,
                            startAction = startSlot,
                            endAction = endSlot,
                            contentPaddingEnabled = contentPaddingEnabled,
                            // COUI 工具栏的 50dp 高度；标题和操作在其中垂直居中。
                            modifier = Modifier.heightIn(min = CouiSheetDefaults.toolbarHeight),
                        ) { text ->
                            CouiText(
                                text = text,
                                kind = CouiTextKind.SheetTitle,
                                color = LegadoTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 56.dp),
                            )
                        }
                    }

                    content()
                }
            }
        }
    }
}

/**
 * 专为 nullable 数据设计的 AppModalBottomSheet 重载。
 * 当 [data] 不为 null 时显示弹窗；当 [data] 变为 null 时，自动缓存最后一次数据并播放退出动画。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> AppModalBottomSheet(
    data: T?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    startSheetAction: SheetAction? = null,
    endSheetAction: SheetAction? = null,
    startSheetActions: ImmutableList<SheetAction> = persistentListOf(),
    endSheetActions: ImmutableList<SheetAction> = persistentListOf(),
    animateContentSize: Boolean = true,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    sheetGesturesEnabled: Boolean = true,
    mochiLayout: MochiSheetLayout = MochiSheetLayout.HalfScreen,
    windowPresentation: Boolean = false,
    content: @Composable ColumnScope.(T) -> Unit
) {
    var cachedData by remember { mutableStateOf(data) }

    if (data != null) {
        cachedData = data
    }

    val currentData = cachedData
    AppModalBottomSheet(
        show = data != null,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = title,
        startSheetAction = startSheetAction,
        endSheetAction = endSheetAction,
        startSheetActions = startSheetActions,
        endSheetActions = endSheetActions,
        animateContentSize = animateContentSize,
        contentWindowInsets = contentWindowInsets,
        sheetGesturesEnabled = sheetGesturesEnabled,
        mochiLayout = mochiLayout,
        windowPresentation = windowPresentation,
        content = {
            if (currentData != null) {
                content(currentData)
            }
        }
    )
}
