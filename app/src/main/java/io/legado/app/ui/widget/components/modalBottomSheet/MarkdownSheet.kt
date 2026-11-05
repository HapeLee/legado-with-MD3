package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.widget.components.text.MarkdownBlock

/**
 * 纯文本说明的底部面板：标题 + Markdown 正文。
 *
 * 说明需要分档、分点或强调时用它代替对话框，正文可滚动且可选中复制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownSheet(
    show: Boolean,
    title: String,
    content: String,
    onDismissRequest: () -> Unit,
    endAction: @Composable (() -> Unit)? = null,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        endAction = endAction,
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                MarkdownBlock(
                    content = content,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.heightIn(min = 16.dp))
            }
        }
    }
}
