package io.legado.app.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
import io.legado.app.utils.LogUtils
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLogSheet(
    onDismissRequest: () -> Unit
) {
    var logs by remember { mutableStateOf(AppLog.logs) }
    var showStackTrace by remember { mutableStateOf<String?>(null) }

    GlassModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.log),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                AppLog.clear()
                logs = emptyList()
            }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
            }
        }

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

    showStackTrace?.let { stackTrace ->
        AlertDialog(
            onDismissRequest = { showStackTrace = null },
            title = { Text("Log") },
            text = {
                SelectionContainer {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(text = stackTrace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStackTrace = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
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
        Text(
            text = LogUtils.logTimeFormat.format(Date(item.first)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = item.second,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
