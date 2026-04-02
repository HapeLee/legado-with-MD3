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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.rememberOpaqueColorScheme
import top.yukonga.miuix.kmp.extra.WindowBottomSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppModalBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    containerColor: Color = LegadoTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
        WindowBottomSheet(
            show = show,
            modifier = modifier,
            title = title,
            startAction = startAction,
            endAction = endAction,
            insideMargin = DpSize(16.dp, 12.dp),
            backgroundColor = containerColor,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissRequest,
            enableWindowDim = true,
            allowDismiss = true
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    } else {
        if (show) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val colorScheme = rememberOpaqueColorScheme()
            val density = LocalDensity.current
            val maxHeight = with(density) {
                LocalWindowInfo.current.containerSize.height.toDp() * 0.8f
            }

            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                sheetState = sheetState,
                containerColor = containerColor,
                contentColor = colorScheme.onSurface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
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
                            .padding(horizontal = 16.dp)
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
                                        color = colorScheme.onSurface,
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