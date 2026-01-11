package io.legado.app.ui.widget.components.exportComponents

import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.modalBottomSheet.GlobalModalBottomSheet

enum class FilePickerSheetMode {
    DIR, FILE, EXPORT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    mode: FilePickerSheetMode,
    onSelectSysDir: () -> Unit,
    onSelectSysFile: (Array<String>) -> Unit,
    onUpload: (() -> Unit)? = null,
    allowExtensions: Array<String>? = null,
) {
    GlobalModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = { it.surface }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                modifier = Modifier.padding(bottom = 16.dp),
                text = stringResource(R.string.select_operation),
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (mode) {
                    FilePickerSheetMode.DIR -> {
                        FilePickerOptionCard(
                            icon = Icons.Default.FolderOpen,
                            text = stringResource(R.string.sys_folder_picker),
                            onClick = onSelectSysDir
                        )
                    }
                    FilePickerSheetMode.FILE -> {
                        FilePickerOptionCard(
                            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                            text = stringResource(R.string.sys_file_picker),
                            onClick = { onSelectSysFile(typesOfExtensions(allowExtensions)) }
                        )
                    }
                    FilePickerSheetMode.EXPORT -> {
                        FilePickerOptionCard(
                            icon = Icons.Default.SaveAlt,
                            text = stringResource(R.string.save_to_local),
                            onClick = onSelectSysDir
                        )

                        if (onUpload != null) {
                            FilePickerOptionCard(
                                icon = Icons.Default.CloudUpload,
                                text = stringResource(R.string.upload_url),
                                onClick = onUpload
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.FilePickerOptionCard(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(100.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

private fun typesOfExtensions(allowExtensions: Array<String>?): Array<String> {
    val types = hashSetOf<String>()
    if (allowExtensions.isNullOrEmpty()) {
        types.add("*/*")
    } else {
        allowExtensions.forEach {
            when (it) {
                "*" -> types.add("*/*")
                "txt", "xml" -> types.add("text/*")
                else -> {
                    val mime = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(it)
                        ?: "application/octet-stream"
                    types.add(mime)
                }
            }
        }
    }
    return types.toTypedArray()
}