package io.legado.app.ui.widget.components

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.FileDoc

@Composable
fun FontSelectSheet(
    show: Boolean = true,
    title: String,
    fontFolderUri: Uri?,
    selectedFontName: String?,
    showPreview: Boolean,
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
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        startAction = startAction,
        endAction = {
            SmallPlainButton(
                onClick = onOpenFolderPicker,
                icon = folderIcon,
                contentDescription = folderContentDescription,
            )
        },
    ) {
        FontSelectGrid(
            fontFolderUri = fontFolderUri,
            selectedFontName = selectedFontName,
            showPreview = showPreview,
            onSelectFont = { doc ->
                onSelectFont(doc)
                onDismissRequest()
            },
            onSelectSystemTypeface = onSelectSystemTypeface?.let { callback ->
                { index ->
                    callback(index)
                    onDismissRequest()
                }
            },
            systemTypefaces = systemTypefaces,
            emptyText = emptyText,
        )
    }
}
