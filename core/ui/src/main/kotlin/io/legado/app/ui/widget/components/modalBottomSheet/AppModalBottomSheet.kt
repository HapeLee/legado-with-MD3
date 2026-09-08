package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalLegadoThemeColors
import io.legado.app.ui.theme.ProvideAppContentColor
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.menuItem.LocalUseMiuixWindowPopup
import top.yukonga.miuix.kmp.window.WindowBottomSheet

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
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = LocalLegadoThemeColors.current.colorScheme
    val sheetContainerColor = LegadoTheme.colorScheme.surfaceContainer
    val sheetContentColor = LegadoTheme.colorScheme.onSurface
    val sheetDragHandleColor = LegadoTheme.colorScheme.onSurfaceVariant

    if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
        WindowBottomSheet(
            show = show,
            modifier = modifier,
            title = title,
            startAction = startAction?.let { action ->
                {
                    ProvideAppDensity {
                        ProvideAppContentColor(sheetContentColor) {
                            CompositionLocalProvider(LocalUseMiuixWindowPopup provides true) {
                                Box(
                                    modifier = if (contentPaddingEnabled) Modifier
                                    else Modifier.padding(start = 16.dp),
                                ) { action() }
                            }
                        }
                    }
                }
            },
            endAction = endAction?.let { action ->
                {
                    ProvideAppDensity {
                        ProvideAppContentColor(sheetContentColor) {
                            CompositionLocalProvider(LocalUseMiuixWindowPopup provides true) {
                                Box(
                                    modifier = if (contentPaddingEnabled) Modifier
                                    else Modifier.padding(end = 16.dp),
                                ) { action() }
                            }
                        }
                    }
                }
            },
            insideMargin = if (contentPaddingEnabled) DpSize(16.dp, 0.dp) else DpSize(0.dp, 0.dp),
            backgroundColor = sheetContainerColor,
            dragHandleColor = sheetDragHandleColor,
            onDismissRequest = onDismissRequest,
            enableWindowDim = true,
        ) {
            ProvideAppDensity {
                ProvideAppContentColor(sheetContentColor) {
                    CompositionLocalProvider(LocalUseMiuixWindowPopup provides true) {
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
            }
        }
    } else {
        if (show) {
            val sheetState = rememberBottomSheetState(
                initialValue = Hidden,
                enabledValues = setOf(Hidden, Expanded)
            )
            val density = LocalDensity.current
            val maxHeight = with(density) {
                LocalWindowInfo.current.containerSize.height.toDp() * 0.8f
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
                    containerColor = sheetContainerColor,
                    contentColor = sheetContentColor,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = sheetDragHandleColor) },
                    contentWindowInsets = contentWindowInsets,
                    sheetGesturesEnabled = sheetGesturesEnabled,
                ) {
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
                                            style = LegadoTheme.typography.titleMediumEmphasized,
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
        content = {
            if (currentData != null) {
                content(currentData)
            }
        }
    )
}
