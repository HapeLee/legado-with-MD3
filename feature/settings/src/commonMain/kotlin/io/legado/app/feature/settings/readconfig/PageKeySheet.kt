package io.legado.app.feature.settings.readconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.custom_page_key
import io.legado.app.feature.settings.res.next_page_key
import io.legado.app.feature.settings.res.ok
import io.legado.app.feature.settings.res.page_key_set_help
import io.legado.app.feature.settings.res.prev_page_key
import io.legado.app.feature.settings.res.reset
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import org.jetbrains.compose.resources.stringResource

// M5-11b：从 `:app` 的 `ui/config/readConfig` 迁来。**本片同时确立共享层的按键处理写法**
// —— 迁移前它用的是 Android 专用 API，共享层第一次遇到，需要定一个方案（该方案随后
// 会在阅读器栈的其它页反复用到）。
//
// | 迁移前（Android 专用） | 迁移后（跨端） |
// |---|---|
// | `event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN` | `event.type == KeyEventType.KeyDown` |
// | `event.nativeKeyEvent.keyCode` | `event.key.nativeKeyCode.toInt()` |
// | `android.view.KeyEvent.KEYCODE_BACK / KEYCODE_DEL` | 下方两个局部常量 |
//
// 为什么是 `nativeKeyCode` 而不是 CMP 的 `Key.*` 常量：这个设置存的是**数字 keyCode 的
// 逗号分隔串**（如 `"21,22"`），阅读器那边按数字匹配。所以必须拿到数字，不能换成语义 Key
// （换了就等于改了存储格式与匹配逻辑）。
//
// ⚠️ `nativeKeyCode` 的取值**按平台而异**：Android 上就是 `android.view.KeyEvent` 的
// keyCode（这正是本设置要的）；桌面/其它端是各自的编码。本设置本质是手机上的翻页键配置，
// 所以只在 Android 上有意义 —— 与迁移前一致，不是本片引入的差异。

private const val KEYCODE_BACK = 4
private const val KEYCODE_DEL = 67

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageKeySheet(
    show: Boolean,
    prevKeys: String,
    nextKeys: String,
    onDismissRequest: () -> Unit,
    onConfirm: (prevKeys: String, nextKeys: String) -> Unit
) {
    var prevKeysDraft by remember { mutableStateOf(prevKeys) }
    var nextKeysDraft by remember { mutableStateOf(nextKeys) }

    LaunchedEffect(show, prevKeys, nextKeys) {
        if (show) {
            prevKeysDraft = prevKeys
            nextKeysDraft = nextKeys
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.custom_page_key)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppTextField(
                value = prevKeysDraft,
                onValueChange = { prevKeysDraft = it },
                label = stringResource(Res.string.prev_page_key),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            val keyCode = event.key.nativeKeyCode.toInt()
                            if (keyCode != KEYCODE_BACK && keyCode != KEYCODE_DEL) {
                                prevKeysDraft = if (prevKeysDraft.isEmpty() || prevKeysDraft.endsWith(",")) {
                                    prevKeysDraft + keyCode.toString()
                                } else {
                                    "$prevKeysDraft,$keyCode"
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    },
                singleLine = true
            )

            AppTextField(
                value = nextKeysDraft,
                onValueChange = { nextKeysDraft = it },
                label = stringResource(Res.string.next_page_key),
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            val keyCode = event.key.nativeKeyCode.toInt()
                            if (keyCode != KEYCODE_BACK && keyCode != KEYCODE_DEL) {
                                nextKeysDraft = if (nextKeysDraft.isEmpty() || nextKeysDraft.endsWith(",")) {
                                    nextKeysDraft + keyCode.toString()
                                } else {
                                    "$nextKeysDraft,$keyCode"
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    },
                singleLine = true
            )

            AppText(
                text = stringResource(Res.string.page_key_set_help),
                style = LegadoTheme.typography.bodyMedium
            )

            ConfirmDismissButtonsRow(
                modifier = Modifier.fillMaxWidth(),
                onDismiss = {
                    prevKeysDraft = ""
                    nextKeysDraft = ""
                },
                onConfirm = {
                    onConfirm(prevKeysDraft, nextKeysDraft)
                },
                dismissText = stringResource(Res.string.reset),
                confirmText = stringResource(Res.string.ok)
            )
        }
    }
}
