package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.alert.MarkdownDialog
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.text.AppText

/**
 * 说明卡片：一句说明 + 一个可选的小按钮（默认「关闭」）。
 *
 * 独立提示与分组内说明共用这一套排版，圆角、内边距、文字样式与按钮都可替换；需要"点开看详情"
 * 时用 [InfoNote]。
 */
@Composable
fun InfoCard(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LegadoTheme.typography.labelLargeEmphasized,
    textColor: Color = Color.Unspecified,
    cornerRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    containerColor: Color = LegadoTheme.colorScheme.surfaceContainerLow,
    /** 为 null 时不渲染按钮。 */
    actionIcon: ImageVector? = AppIcons.Close,
    actionDescription: String? = null,
    actionSelected: Boolean = false,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    GlassCard(
        containerColor = containerColor,
        cornerRadius = cornerRadius,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(contentPadding)
        ) {
            AppText(
                text = text,
                style = textStyle,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            if (onAction != null && actionIcon != null) SmallPlainButton(
                icon = actionIcon,
                contentDescription = actionDescription,
                selected = actionSelected,
                enabled = actionEnabled,
                onClick = onAction
            )
        }
    }
}

/**
 * 分组内顶部的说明：次要文字 + Info 按钮，点开用 [MarkdownDialog] 展示 [detail]。
 *
 * 对比 [InfoCard] 只是换了默认值：更小的圆角与内边距、次要文字色，按钮固定为详情入口。
 * 详情用 Markdown 写，分档与代价说明才能排版成列表而不是一坨换行。
 */
@Composable
fun InfoNote(
    text: String,
    detail: String,
    detailTitle: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LegadoTheme.typography.bodySmall,
    textColor: Color = LegadoTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = LegadoTheme.colorScheme.surfaceContainerLow,
    cornerRadius: Dp = 2.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    var showDetail by rememberSaveable { mutableStateOf(false) }
    InfoCard(
        text = text,
        modifier = modifier,
        textStyle = textStyle,
        textColor = textColor,
        cornerRadius = cornerRadius,
        contentPadding = contentPadding,
        containerColor = containerColor,
        actionIcon = AppIcons.Info,
        actionDescription = stringResource(R.string.details_of, detailTitle),
        onAction = { showDetail = true }
    )
    MarkdownDialog(
        show = showDetail,
        title = detailTitle,
        content = detail,
        onDismissRequest = { showDetail = false }
    )
}
