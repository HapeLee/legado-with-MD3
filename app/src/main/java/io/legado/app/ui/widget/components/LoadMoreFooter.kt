package io.legado.app.ui.widget.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.sendToClip

@Composable
fun LoadMoreFooter(
    isLoading: Boolean,
    errorMsg: String?,
    isEnd: Boolean,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    var showFullError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isLoading, errorMsg, isEnd) {
        if (!isLoading && errorMsg == null && !isEnd) {
            onRetry()
        }
    }

    AppAlertDialog(
        data = showFullError,
        onDismissRequest = { showFullError = null },
        title = "错误详情",
        textProvider = { this },
        confirmText = "复制",
        onConfirm = { error ->
            context.sendToClip(error)
            showFullError = null
        },
        dismissText = "关闭",
        onDismiss = { showFullError = null }
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .adaptiveHorizontalPadding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {

        AnimatedContent(
            targetState = Triple(isLoading, errorMsg, isEnd),
            label = "LoadMoreFooter"
        ) { (loading, error, end) ->

            when {
                error != null -> {

                    GlassCard(
                        onClick = { showFullError = error },
                        containerColor = LegadoTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            // 信息区域
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        all = 16.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {

                                AppIcon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = LegadoTheme.colorScheme.error
                                )

                                AppText(
                                    text = error,
                                    color = LegadoTheme.colorScheme.error,
                                    style = LegadoTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            HorizontalDivider(
                                color = LegadoTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            // 操作区域
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onRetry)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    AppIcon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = LegadoTheme.colorScheme.error
                                    )

                                    AppText(
                                        text = "重新加载",
                                        color = LegadoTheme.colorScheme.error,
                                        style = LegadoTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                loading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        AppContainedLoadingIndicator()

                        AppText(
                            text = "正在加载…",
                            color = LegadoTheme.colorScheme.outline,
                            style = LegadoTheme.typography.bodySmall
                        )
                    }
                }

                end -> {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth(),
                        containerColor = LegadoTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        all = 16.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {

                                AppIcon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = LegadoTheme.colorScheme.onSurface
                                )

                                AppText(
                                    text = "已经到底了~",
                                    color = LegadoTheme.colorScheme.onSurface,
                                    style = LegadoTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                else -> {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth(),
                        containerColor = LegadoTheme.colorScheme.surfaceContainer,
                        onClick = onRetry
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        all = 16.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {

                                AppIcon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = LegadoTheme.colorScheme.onSurface
                                )

                                AppText(
                                    text = "加载更多",
                                    color = LegadoTheme.colorScheme.onSurface,
                                    style = LegadoTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
