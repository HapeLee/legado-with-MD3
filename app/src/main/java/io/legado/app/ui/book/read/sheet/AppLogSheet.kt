package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppLogSheet(
    onDismissRequest: () -> Unit,
    onShowStackTrace: (String) -> Unit,
) {
    var logs by remember { mutableStateOf(AppLog.logs) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.log),
        endAction = {
            TextButton(
                onClick = {
                    AppLog.clear()
                    logs = emptyList()
                },
            ) {
                Text(stringResource(R.string.clear))
            }
        },
    ) {
        if (logs.isEmpty()) {
            Text(
                text = stringResource(R.string.empty),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn {
                items(logs) { (timestamp, message, throwable) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                throwable?.let {
                                    onShowStackTrace(it.stackTraceToString())
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = timeFormat.format(Date(timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
