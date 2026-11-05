package io.legado.app.ui.widget.components.alert

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.text.MarkdownBlock

/**
 * 用对话框展示一段 Markdown 说明，适合"点一下看解释"这类短内容。
 *
 * 与 [io.legado.app.ui.widget.components.modalBottomSheet.MarkdownSheet] 只差载体：对话框更轻，
 * 不会在已有的底部面板上再叠一层面板，返回手势也只剩一层。
 */
@Composable
fun MarkdownDialog(
    show: Boolean,
    title: String,
    content: String,
    onDismissRequest: () -> Unit,
    confirmText: String = stringResource(R.string.close),
    style: TextStyle = LegadoTheme.typography.bodyMedium,
) {
    AppAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        confirmText = confirmText,
        onConfirm = onDismissRequest,
        content = {
            SelectionContainer {
                MarkdownBlock(
                    content = content,
                    modifier = Modifier.fillMaxWidth(),
                    style = style,
                )
            }
        }
    )
}
