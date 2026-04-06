package io.legado.app.ui.widget.components.filePicker

import android.webkit.MimeTypeMap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.widget.components.modalBottomSheet.OptionCard
import io.legado.app.ui.widget.components.modalBottomSheet.OptionSheet

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
    OptionSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title
    ) {
        onSelectSysDir?.let {
            OptionCard(
                icon = Icons.Default.FolderOpen,
                text = stringResource(R.string.sys_folder_picker),
                onClick = it
            )
        }

        onSelectSysFile?.let {
            OptionCard(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                text = stringResource(R.string.sys_file_picker),
                onClick = { it(typesOfExtensions(allowExtensions)) }
            )
        }

        onSelectSysFiles?.let {
            OptionCard(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                text = "多选项目",
                onClick = { it(typesOfExtensions(allowExtensions)) }
            )
        }

        onManualInput?.let {
            OptionCard(
                icon = Icons.Default.EditNote,
                text = stringResource(R.string.manual_input),
                onClick = it
            )
        }

        onUpload?.let {
            OptionCard(
                icon = Icons.Default.CloudUpload,
                text = stringResource(R.string.upload_url),
                onClick = it
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
