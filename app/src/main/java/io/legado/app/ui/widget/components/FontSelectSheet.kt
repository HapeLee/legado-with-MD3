package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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

/**
 * Font file sort type for [FontSelectSheet].
 */
enum class FontSortType {
    NAME,
    SIZE,
    MODIFIED_TIME,
}

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
                DocumentFile.fromSingleUri(context, it.toUri())?.name
            }.getOrNull()
        }
    }
    var showTypefaceMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortType by remember { mutableStateOf(FontSortType.NAME) }
    var sortDescending by remember { mutableStateOf(false) }

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
            Row {
                // Sort button (second from right) - clicking same type toggles direction,
                // switching to a different type resets to ascending
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_by_name)) },
                        leadingIcon = {
                            if (sortType == FontSortType.NAME) Icon(
                                if (sortDescending) Icons.Default.ArrowDownward
                                else Icons.Default.ArrowUpward,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            if (sortType == FontSortType.NAME) {
                                sortDescending = !sortDescending
                            } else {
                                sortType = FontSortType.NAME
                                sortDescending = false
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_by_size)) },
                        leadingIcon = {
                            if (sortType == FontSortType.SIZE) Icon(
                                if (sortDescending) Icons.Default.ArrowDownward
                                else Icons.Default.ArrowUpward,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            if (sortType == FontSortType.SIZE) {
                                sortDescending = !sortDescending
                            } else {
                                sortType = FontSortType.SIZE
                                sortDescending = false
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_by_time)) },
                        leadingIcon = {
                            if (sortType == FontSortType.MODIFIED_TIME) Icon(
                                if (sortDescending) Icons.Default.ArrowDownward
                                else Icons.Default.ArrowUpward,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            if (sortType == FontSortType.MODIFIED_TIME) {
                                sortDescending = !sortDescending
                            } else {
                                sortType = FontSortType.MODIFIED_TIME
                                sortDescending = false
                            }
                        },
                    )
                }
                SmallPlainButton(
                    onClick = { showSortMenu = !showSortMenu },
                    icon = Icons.Default.Sort,
                    contentDescription = stringResource(R.string.sort),
                )

                // Folder picker button (rightmost)
                SmallPlainButton(
                    onClick = onOpenFolderPicker,
                    icon = folderIcon,
                    contentDescription = folderContentDescription
                        ?: stringResource(R.string.select_folder),
                )
            }
        },
    ) {
        FontSelectGrid(
            folderState = folderState,
            selectedFontName = selectedFontName,
            sortType = sortType,
            sortDescending = sortDescending,
            onSelectFont = { doc ->
                onSelectFont(doc)
                onDismissRequest()
            },
            emptyText = emptyText,
        )
    }
}
