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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.LogUtils
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLogSheet(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    var logs by remember { mutableStateOf(emptyList<LogEntry>()) }
    var showDetail by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(show) {
        if (show) {
            logs = loadAllLogs()
        }
    }

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
            EmptyMessage(message = stringResource(R.string.no_logs))
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
) {
    val content: String get() = message
}

private fun loadAllLogs(): List<LogEntry> {
    return AppLog.logs.map { (time, message, throwable) ->
        LogEntry(
            time = time,
            message = if (throwable == null) {
                message
            } else {
                "$message\n${throwable.stackTraceToString()}"
            }
        )
    }
}

private fun clearAllLogs() {
    AppLog.clear()
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
                if (item.time > 0) append(LogUtils.logTimeFormat.format(Date(item.time)))
            },
            style = LegadoTheme.typography.labelMedium,
            color = LegadoTheme.colorScheme.outline
        )
        AppText(
            text = item.message,
            style = LegadoTheme.typography.bodyMedium,
            color = LegadoTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 2,
        )
    }
}
