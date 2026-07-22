package io.legado.app.ui.widget.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.R
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.FileDoc
import io.legado.app.utils.isContentScheme
import java.io.File

@Composable
fun FontSelectSheet(
    show: Boolean = true,
    title: String,
    folderState: FontFolderState,
    selectedFontPath: String?,
    onDismissRequest: () -> Unit,
    onSelectFont: (FileDoc) -> Unit,
    onOpenFolderPicker: () -> Unit,
    startAction: (@Composable () -> Unit)? = null,
    folderIcon: ImageVector = Icons.Default.FolderOpen,
    folderContentDescription: String? = null,
    onSelectSystemTypeface: ((Int) -> Unit)? = null,
    systemTypefaces: Array<String>? = null,
    emptyText: String? = null,
) {
    val context = LocalContext.current
    val selectedFontName = remember(selectedFontPath) {
        selectedFontPath?.let {
            runCatching {
                val uri = it.toUri()
                if (uri.isContentScheme()) {
                    DocumentFile.fromSingleUri(context, uri)?.name
                } else {
                    File(uri.path ?: it).name
                }
            }.getOrNull()
        }
    }
    var showTypefaceMenu by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        startAction = {
            startAction?.invoke()
            if (systemTypefaces != null && onSelectSystemTypeface != null) {
                DropdownMenu(
                    expanded = showTypefaceMenu,
                    onDismissRequest = { showTypefaceMenu = false },
                ) {
                    systemTypefaces.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onSelectSystemTypeface(index)
                                showTypefaceMenu = false
                                onDismissRequest()
                            },
                        )
                    }
                }
                SmallPlainButton(
                    onClick = { showTypefaceMenu = true },
                    icon = Icons.Default.TextFields,
                    contentDescription = stringResource(R.string.select_font),
                )
            }
        },
        endAction = {
            SmallPlainButton(
                onClick = onOpenFolderPicker,
                icon = folderIcon,
                contentDescription = folderContentDescription
                    ?: stringResource(R.string.select_folder),
            )
        },
    ) {
        FontSelectGrid(
            folderState = folderState,
            selectedFontName = selectedFontName,
            onSelectFont = { doc ->
                onSelectFont(doc)
                onDismissRequest()
            },
            emptyText = emptyText,
        )
    }
}
