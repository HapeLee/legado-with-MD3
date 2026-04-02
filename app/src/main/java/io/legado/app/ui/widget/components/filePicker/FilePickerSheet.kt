package io.legado.app.ui.widget.components.filePicker

import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

enum class FilePickerSheetMode {
    DIR, FILE, EXPORT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String = stringResource(R.string.select_operation),
    onSelectSysDir: (() -> Unit)? = null,
    onSelectSysFile: ((Array<String>) -> Unit)? = null,
    onSelectSysFiles: ((Array<String>) -> Unit)? = null,
    onManualInput: (() -> Unit)? = null,
    onUpload: (() -> Unit)? = null,
    allowExtensions: Array<String>? = null,
) {
    AppModalBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        containerColor = LegadoTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 2
            ) {

                onSelectSysDir?.let {
                    FilePickerOptionCard(
                        icon = Icons.Default.FolderOpen,
                        text = stringResource(R.string.sys_folder_picker),
                        onClick = it
                    )
                }

                onSelectSysFile?.let {
                    FilePickerOptionCard(
                        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                        text = stringResource(R.string.sys_file_picker),
                        onClick = { it(typesOfExtensions(allowExtensions)) }
                    )
                }

                onSelectSysFiles?.let {
                    FilePickerOptionCard(
                        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                        text = "多选项目",
                        onClick = { it(typesOfExtensions(allowExtensions)) }
                    )
                }

                onManualInput?.let {
                    FilePickerOptionCard(
                        icon = Icons.Default.EditNote,
                        text = stringResource(R.string.manual_input),
                        onClick = it
                    )
                }

                onUpload?.let {
                    FilePickerOptionCard(
                        icon = Icons.Default.CloudUpload,
                        text = stringResource(R.string.upload_url),
                        onClick = it
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
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
        color = LegadoTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = LegadoTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppText(
                text = text,
                style = LegadoTheme.typography.labelMedium,
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
