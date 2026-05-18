package io.legado.app.ui.widget.components.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.FileDoc

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogSheet(
    show: Boolean,
    logFiles: List<FileDoc>,
    onDismissRequest: () -> Unit,
    onReadFile: (FileDoc) -> Unit,
    onClear: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        endAction = {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
            }
        },
        title = stringResource(R.string.crash_log),
    ) {
        if (logFiles.isEmpty()) {
            EmptyMessage(message = "暂无崩溃日志")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(logFiles) { fileDoc ->
                    AppText(
                        text = fileDoc.name,
                        style = LegadoTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onReadFile(fileDoc) }
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}
