package io.legado.app.feature.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.feature.about.res.Res
import io.legado.app.feature.about.res.clear
import io.legado.app.feature.about.res.crash_log
import io.legado.app.feature.about.res.no_crash_logs
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import org.jetbrains.compose.resources.stringResource

/**
 * 崩溃日志列表弹层（M5-1c-2 从 `:app` 的 `ui/widget/components/log/CrashLogSheet.kt` 搬来）。
 *
 * **为什么是搬进 Feature 而不是原地上提 `:core:designsystem`**：它的唯一调用方是 `AboutOverlays`，
 * 搬前 `grep CrashLogSheet` 只命中 `AboutScreen.kt`。按 AGENTS.md「不为架构完整创建无调用方抽象」，
 * 它属于 about 私有 UI（`internal`），不该进共享原子组件层。
 *
 * 唯一的内容变化：`List<FileDoc>` → `List<CrashLogEntry>`。`FileDoc` 是 Android 的 SAF 封装
 * （暴露 `Uri`），AGENTS.md 禁止共享契约暴露 `Uri`；共享层只认「id + 展示名」，回读时由
 * 平台实现把 id 还原成 `FileDoc`（见 `AndroidAboutDiagnostics`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CrashLogSheet(
    show: Boolean,
    logFiles: List<CrashLogEntry>,
    onDismissRequest: () -> Unit,
    onReadFile: (CrashLogEntry) -> Unit,
    onClear: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        endAction = {
            MediumTonalButton(
                onClick = onClear,
                icon = Icons.Default.DeleteSweep,
                contentDescription = stringResource(Res.string.clear)
            )
        },
        title = stringResource(Res.string.crash_log),
    ) {
        if (logFiles.isEmpty()) {
            EmptyMessage(message = stringResource(Res.string.no_crash_logs))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(logFiles) { entry ->
                    AppText(
                        text = entry.name,
                        style = LegadoTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onReadFile(entry) }
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}
