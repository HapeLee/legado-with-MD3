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
 * 「Markdown 内容 + 底部弹层」通用组件（M5-1c-pre 从 `:app` 的 `ui/about/AboutSheets.kt` 上提）。
 *
 * 上提的理由是**调用方分布**，不是复用洁癖：它原本与 about 私有的 `UpdateSheet` 同居一个文件，
 * 但实际有 6 个包外调用方——`BookSourceDebugScreen` / `BookSourceEditScreen` /
 * `SourceLoginSheets` / `MainActivity` / `RssSourceDebugScreen` / `RssSourceEditScreen`。
 * 若随 `AboutSheets.kt` 一起搬进 `:feature:about`，book / rss / login 的屏幕就会反向依赖
 * about Feature（`AGENTS.md`：Feature 之间不直接依赖实现）。
 *
 * 判定它「能上提」的机械依据与 M5-1b 的 `MarkdownBlock` 相同：文件内零 `android.*` / 零 `R.`，
 * 依赖闭包到此为止（`AppModalBottomSheet` + `MarkdownBlock` + foundation 的 `SelectionContainer`）。
 * `MarkdownBlock` 已在 M5-1b 进本模块，所以这里只剩纯粹的组合。
 *
 * ⚠️ 包名**改了**（`io.legado.app.ui.about` → `...ui.widget.components.modalBottomSheet`，与
 * `AppModalBottomSheet` / `OptionSheet` 同包）。这与 M5-1a/1b「保包名换目录」的做法不同是有意的：
 * 一个名叫 `about` 的包出现在 `:core:designsystem` 里语义就是错的，改名换 6 处 import 是更小的代价。
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
