package io.legado.app.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.alert.AppAlertDialog
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
        onDismissRequest = onDismissRequest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = stringResource(R.string.log),
                style = LegadoTheme.typography.titleLarge,
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


    AppAlertDialog(
        data = showStackTrace,
        onDismissRequest = { showStackTrace = null },
        title = "Log",
        content = { stackTrace ->
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AppText(
                        text = stackTrace,
                        style = LegadoTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { showStackTrace = null }
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
