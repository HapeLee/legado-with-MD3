package io.legado.app.ui.widget.components.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.CustomTipPlaceholder
import io.legado.app.ui.widget.components.alert.AppAlertDialog

/**
 * 自定义页眉/页脚模板编辑弹窗。
 *
 * 用户在输入框中输入模板，点击 chip 可在**光标位置**插入预定义占位符。
 * 点击"确定"前会先校验所有 `{Xxx}` 是否为预定义占位符；任何未知占位符都会使
 * 确定按钮处于禁用状态，并显示错误提示。
 *
 * @param show 是否显示弹窗
 * @param initialTemplate 初始模板字符串
 * @param onConfirm 用户点击确定时回调，参数为校验通过后的模板字符串
 * @param onDismissRequest 关闭弹窗回调（点击取消或外部）
 */
@Composable
fun CustomTipDialog(
    show: Boolean,
    initialTemplate: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // 使用 TextFieldValue 携带光标位置，chip 点击时在光标处插入 token。
    var template by remember(show, initialTemplate) {
        mutableStateOf(TextFieldValue(initialTemplate, TextRange(initialTemplate.length)))
    }

    // 缓存成不可变 List，避免每次重组时调用 values() 触发数组克隆
    val placeholders = remember { CustomTipPlaceholder.values().toList() }

    // 校验：所有 {Xxx} 必须是预定义占位符
    val isValid by remember(template) {
        derivedStateOf { CustomTipPlaceholder.isValid(template.text) }
    }

    AppAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.custom_tip),
        content = {
            Column {
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    placeholder = { Text(stringResource(R.string.custom_tip_template_hint)) },
                    isError = !isValid && template.text.isNotEmpty(),
                    supportingText = {
                        if (!isValid && template.text.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.custom_tip_invalid),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.custom_tip_template_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(placeholders, key = { it.token }) { ph ->
                        AssistChip(
                            label = { Text(stringResource(ph.labelRes)) },
                            onClick = { template = template.insertAtCursor(ph.token) },
                        )
                    }
                }
            }
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = if (isValid) {
            { onConfirm(template.text) }
        } else null,
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismissRequest,
    )
}

/**
 * 在 [TextFieldValue] 当前光标位置插入 [token]，并把光标移到插入文本末尾。
 */
private fun TextFieldValue.insertAtCursor(token: String): TextFieldValue {
    val cursor = selection.end.coerceIn(0, text.length)
    val newText = text.substring(0, cursor) + token + text.substring(cursor)
    val newCursor = cursor + token.length
    return TextFieldValue(newText, TextRange(newCursor))
}
