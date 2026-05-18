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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import io.legado.app.ui.widget.components.button.MediumIconButton
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
    var logs by remember { mutableStateOf(AppLog.logs) }
    var showStackTrace by remember { mutableStateOf<String?>(null) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.log),
        endAction = {
            MediumIconButton(
                onClick = {
                    AppLog.clear()
                    logs = emptyList()
                },
                imageVector = Icons.Default.DeleteSweep
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
                        item.third?.let {
                            showStackTrace = it.stackTraceToString()
                        }
                    }
                }
            }
        }
    }

    LogDetailSheet(
        show = showStackTrace != null,
        title = "Log",
        content = showStackTrace.orEmpty(),
        onDismissRequest = { showStackTrace = null }
    )
}

@Composable
private fun LogItem(
    item: Triple<Long, String, Throwable?>,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        AppText(
            text = LogUtils.logTimeFormat.format(Date(item.first)),
            style = LegadoTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        AppText(
            text = item.second,
            style = LegadoTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
