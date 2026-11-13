package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.itos.coui.menu.ProvideCouiMenuBackdrop
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
import io.legado.app.ui.theme.rememberMochiOverlayBackdrop
import io.legado.app.ui.widget.components.LocalAppGlassOverlay
import io.legado.app.ui.widget.components.mochi.MochiOverlayAccessibility
import io.legado.app.ui.widget.components.mochi.mochiOverlayContentModifier
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
 * 只在 HyperOS 4 开关关闭时使用窗口级 sheet。它的内容虽继承 Scaffold 的 local，但并不在
 * root host 中，必须清掉宿主信号，避免子菜单注册到画不到的地方；玻璃 sheet 与 root host 同根，
 * 保留信号。
 */
@Composable
private fun MiuixSheetLocals(
    useGlassContainer: Boolean,
    sheetContentColor: Color,
    content: @Composable () -> Unit,
) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppModalBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    animateContentSize: Boolean = true,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    contentPaddingEnabled: Boolean = true,
    sheetGesturesEnabled: Boolean = true,
    mochiLayout: MochiSheetLayout = MochiSheetLayout.HalfScreen,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = LocalLegadoThemeColors.current.colorScheme
    val sheetContainerColor = LegadoTheme.colorScheme.surfaceContainer
    val sheetContentColor = LegadoTheme.colorScheme.onSurface
    val sheetDragHandleColor = LegadoTheme.colorScheme.onSurfaceVariant
    val composeEngine = LegadoTheme.composeEngine
    val isMochi = composeEngine == ComposeEngine.Mochi

    // Mochi attaches a sibling ComposeView to the Activity decor. Other windows need their own
    // modal host to retain window insets and the local back dispatcher.
    val inActivityWindow = LocalView.current.rootView === LocalActivity.current?.window?.decorView
    if (isMochi && mochiLayout != MochiSheetLayout.Compatibility && sheetGesturesEnabled && inActivityWindow) {
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
                            val hasHeader = !title.isNullOrEmpty() || startAction != null || endAction != null
                            if (hasHeader) {
                                MochiSheetHeader(
                                    title = title.orEmpty(),
                                    navigationIcon = startAction,
                                    actions = endAction,
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

    when (composeEngine) {
        ComposeEngine.Miuix -> {
            val themeSettings = LocalAppUiConfiguration.current.theme
            // 开关打开后只走玻璃；无玻璃设施的独立窗口（如朗读悬浮窗）才退回窗口面板。
            val useGlassContainer = themeSettings.useHyperOs4Components &&
                ThemeResolver.isBlurSupported() &&
                LocalAppGlassOverlay.current != null
            val backdrop = LocalMiuixBarBackdrop.current
            val insideMargin = if (contentPaddingEnabled) DpSize(16.dp, 0.dp) else DpSize(0.dp, 0.dp)
            val wrappedStartAction: (@Composable () -> Unit)? = startAction?.let { action ->
                {
                    MiuixSheetLocals(useGlassContainer, sheetContentColor) {
                        Box(
                            modifier = if (contentPaddingEnabled) Modifier
                            else Modifier.padding(start = 16.dp),
                        ) { action() }
                    }
                }
            }
            val wrappedEndAction: (@Composable () -> Unit)? = endAction?.let { action ->
                {
                    MiuixSheetLocals(useGlassContainer, sheetContentColor) {
                        Box(
                            modifier = if (contentPaddingEnabled) Modifier
                            else Modifier.padding(end = 16.dp),
                        ) { action() }
                    }
                }
            }
            val sheetBody: @Composable () -> Unit = {
                MiuixSheetLocals(useGlassContainer, sheetContentColor) {
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
                        content = content
                    )
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
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
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
                                    val hasHeader =
                                        !title.isNullOrEmpty() || startAction != null || endAction != null

                                    if (hasHeader) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (startAction != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterStart)
                                                        .let {
                                                            if (contentPaddingEnabled) it else it.padding(
                                                                start = 16.dp
                                                            )
                                                        }
                                                ) {
                                                    startAction()
                                                }
                                            }

                                            if (!title.isNullOrEmpty()) {
                                                Text(
                                                    text = title,
                                                    style = if (isMochi) MochiTheme.textStyles.navTitle
                                                        else LegadoTheme.typography.titleMediumEmphasized,
                                                    color = sheetContentColor,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 56.dp)
                                                )
                                            }

                                            if (endAction != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterEnd)
                                                        .let {
                                                            if (contentPaddingEnabled) it else it.padding(
                                                                end = 16.dp
                                                            )
                                                        }
                                                ) {
                                                    endAction()
                                                }
                                            }
                                        }
                                    }

                                    content()
                                }
                            }
                        }
                        // COUI 菜单的玻璃材质采样所属窗口的内容根；Sheet 是独立窗口，
                        // AppTheme 页面根的 ProvideCouiMenuBackdrop 覆盖不到这里。
                        if (composeEngine == ComposeEngine.Coui && ThemeResolver.isCouiSupported()) {
                            ProvideCouiMenuBackdrop { backdropModifier ->
                                Box(backdropModifier, propagateMinConstraints = true) {
                                    sheetBody()
                                }
                            }
                        } else {
                            sheetBody()
                        }
                    }
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
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    animateContentSize: Boolean = true,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    sheetGesturesEnabled: Boolean = true,
    mochiLayout: MochiSheetLayout = MochiSheetLayout.HalfScreen,
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
        startAction = startAction,
        endAction = endAction,
        animateContentSize = animateContentSize,
        contentWindowInsets = contentWindowInsets,
        sheetGesturesEnabled = sheetGesturesEnabled,
        mochiLayout = mochiLayout,
        content = {
            if (currentData != null) {
                content(currentData)
            }
        }
    )
}
