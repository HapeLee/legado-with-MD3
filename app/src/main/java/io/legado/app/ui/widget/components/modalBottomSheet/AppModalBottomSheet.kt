package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberModalBottomSheetState
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
                    ProvideAppContentColor(sheetContentColor) {
                        CompositionLocalProvider(LocalUseMiuixWindowPopup provides true) {
                            action()
                        }
                    }
                }
            },
            endAction = endAction?.let { action ->
                {
                    ProvideAppContentColor(sheetContentColor) {
                        CompositionLocalProvider(LocalUseMiuixWindowPopup provides true) {
                            action()
                        }
                    }
                }
            },
            insideMargin = DpSize(16.dp, 0.dp),
            backgroundColor = sheetContainerColor,
            dragHandleColor = sheetDragHandleColor,
            onDismissRequest = onDismissRequest,
            enableWindowDim = true,
            allowDismiss = true
        ) {
            ProvideAppContentColor(sheetContentColor) {
                CompositionLocalProvider(LocalUseMiuixWindowPopup provides true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .animateContentSize(),
                        content = content
                    )
                }
            }
        }
    } else {
        if (show) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val density = LocalDensity.current
            val maxHeight = with(density) {
                LocalWindowInfo.current.containerSize.height.toDp() * 0.8f
            }

            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                sheetState = sheetState,
                containerColor = sheetContainerColor,
                contentColor = sheetContentColor,
                dragHandle = { BottomSheetDefaults.DragHandle(color = sheetDragHandleColor) }
            ) {
                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    typography = Typography(),
                    motionScheme = MotionScheme.expressive(),
                    shapes = Shapes()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 0.dp)
                            .heightIn(max = maxHeight)
                            .animateContentSize()
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
                                    Box(modifier = Modifier.align(Alignment.CenterStart)) {
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
                                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
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

/**
 * 专为 nullable 数据设计的 AppModalBottomSheet 重载。
 * 当 [data] 不为 null 时显示弹窗；当 [data] 变为 null 时，自动缓存最后一次数据并播放退出动画。
 */
@Composable
fun <T> AppModalBottomSheet(
    data: T?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
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
        content = {
            if (currentData != null) {
                content(currentData)
            }
        }
    )
}
