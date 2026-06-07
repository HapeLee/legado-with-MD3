package io.legado.app.ui.widget.components.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.LogUtils
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLogSheet(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    var logs by remember(show) { mutableStateOf(loadAllLogs()) }
    var showDetail by remember { mutableStateOf<String?>(null) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.log),
        endAction = {
            MediumPlainButton(
                onClick = {
                    clearAllLogs()
                    logs = emptyList()
                },
                icon = Icons.Default.DeleteSweep
            )
        }
    ) {
        if (logs.isEmpty()) {
            EmptyMessage(message = "暂无日志")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(logs) { item ->
                    LogItem(item) {
                        showDetail = item.content
                    }
                }
            }
        }
    }

    LogDetailSheet(
        show = showDetail != null,
        title = "Log",
        content = showDetail.orEmpty(),
        onDismissRequest = { showDetail = null }
    )
}

private data class LogEntry(
    val time: Long,
    val message: String,
    val isCrash: Boolean = false,
) {
    val content: String get() = message
}

/**
 * 从本地日志文件加载所有日志（应用日志 + 崩溃日志）
 */
private fun loadAllLogs(): List<LogEntry> {
    val entries = mutableListOf<LogEntry>()
    loadAppLogFiles(entries)
    loadCrashLogFiles(entries)
    return entries.sortedByDescending { it.time }
}

/**
 * 从 externalCacheDir/logs/ 读取应用日志文件
 * 文件格式: appLog-{date}.txt, 每行格式: yy-MM-dd HH:mm:ss.SSS: message
 */
private fun loadAppLogFiles(entries: MutableList<LogEntry>) {
    val logDir = appCtx.externalCacheDir?.resolve("logs") ?: return
    if (!logDir.isDirectory) return
    logDir.listFiles()
        ?.filter { it.isFile && it.name.startsWith("appLog-") && it.name.endsWith(".txt") }
        ?.sortedByDescending { it.name }
        ?.forEach { file ->
            runCatching {
                file.readLines().forEach { line ->
                    parseLogLine(line)?.let { entries.add(it) }
            }
        }
    }
}

/**
 * 从 externalCacheDir/crash/ 读取崩溃日志文件
 */
private fun loadCrashLogFiles(entries: MutableList<LogEntry>) {
    val crashDir = appCtx.externalCacheDir?.resolve("crash") ?: return
    if (!crashDir.isDirectory) return
    crashDir.listFiles()
        ?.filter { it.isFile && it.name.startsWith("crash-") }
        ?.forEach { file ->
            runCatching {
                entries.add(
                    LogEntry(
                        time = file.lastModified(),
                        message = file.readText(),
                        isCrash = true,
                    )
                )
            }
        }
}

/**
 * 解析日志行: "yy-MM-dd HH:mm:ss.SSS: message"
 */
private fun parseLogLine(line: String): LogEntry? {
    if (line.isBlank()) return null
    // 格式: 25-06-07 12:34:56.789: message
    val colonIdx = line.indexOf(": ")
    if (colonIdx < 0 || colonIdx < 17) {
        // 没有时间戳前缀，作为多行消息附加到上一条（此处忽略）
        return null
    }
    val timeStr = line.substring(0, colonIdx)
    val message = line.substring(colonIdx + 2)
    val time = runCatching {
        logFileDateFormat.parse(timeStr)?.time
    }.getOrNull() ?: 0L
    return LogEntry(time = time, message = message)
}

private val logFileDateFormat = SimpleDateFormat("yy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

private fun clearAllLogs() {
    // 清除应用日志
    val logDir = appCtx.externalCacheDir?.resolve("logs")
    if (logDir?.isDirectory == true) {
        logDir.listFiles()?.forEach { it.delete() }
    }
    // 清除崩溃日志
    val crashDir = appCtx.externalCacheDir?.resolve("crash")
    if (crashDir?.isDirectory == true) {
        crashDir.listFiles()?.forEach { it.delete() }
    }
}

@Composable
private fun LogItem(
    item: LogEntry,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        AppText(
            text = buildString {
                if (item.isCrash) append("[崩溃] ")
                if (item.time > 0) append(LogUtils.logTimeFormat.format(Date(item.time)))
            },
            style = LegadoTheme.typography.labelMedium,
            color = if (item.isCrash) {
                LegadoTheme.colorScheme.error
            } else {
                LegadoTheme.colorScheme.outline
            }
        )
        AppText(
            text = if (item.isCrash) {
                item.message.lineSequence().firstOrNull().orEmpty()
            } else {
                item.message
            },
            style = LegadoTheme.typography.bodyMedium,
            color = if (item.isCrash) {
                LegadoTheme.colorScheme.error.copy(alpha = 0.8f)
            } else {
                LegadoTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 2,
        )
    }
}
