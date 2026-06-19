package io.legado.app.ui.widget.components

import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.loadFontFiles
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.utils.FileDoc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val fontGridHeight = 360.dp


sealed interface FontFolderState {
    data object Loading : FontFolderState
    data class Loaded(val uri: Uri?) : FontFolderState
}

/**
 * Shared font selection grid with search support.
 *
 * @param folderState 字体文件夹加载状态，见 [FontFolderState]
 * @param selectedFontName currently selected font name (for check mark), null to hide
 * @param onSelectFont called when a font file is selected
 * @param emptyText text to show when no fonts found
 */
@Composable
fun FontSelectGrid(
    folderState: FontFolderState,
    selectedFontName: String?,
    onSelectFont: (FileDoc) -> Unit,
    emptyText: String? = null,
) {
    val context = LocalContext.current
    var fontItems by remember { mutableStateOf<List<FileDoc>>(emptyList()) }
    var filesLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(folderState) {
        if (folderState is FontFolderState.Loaded) {
            filesLoading = true
            try {
                fontItems = withContext(Dispatchers.IO) {
                    loadFontFiles(context, folderState.uri)
                }
            } finally {
                filesLoading = false
            }
        }
    }

    val filteredItems = remember(fontItems, searchQuery) {
        if (searchQuery.isBlank()) fontItems
        else fontItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val showLoading = folderState is FontFolderState.Loading || filesLoading

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // Search
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = stringResource(R.string.search_placeholder),
            autoFocus = false,
        )

        Spacer(Modifier.height(4.dp))

        // Font grid
        if (showLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fontGridHeight),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fontGridHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyText ?: stringResource(R.string.empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(fontGridHeight),
            ) {
                items(filteredItems, key = { it.name }) { item ->
                    FontItem(
                        item = item,
                        isSelected = item.name == selectedFontName,
                        onClick = { onSelectFont(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FontItem(
    item: FileDoc,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val fontFamily by rememberItemFontFamily(item.uri)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LegadoTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(48.dp)
        ) {
            Text(
                text = item.name,
                style = LegadoTheme.typography.bodyMedium.copy(fontFamily = fontFamily),
                color = LegadoTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberItemFontFamily(uri: Uri): State<FontFamily?> {
    val context = LocalContext.current
    return produceState<FontFamily?>(initialValue = null, uri) {
        val parsed = withContext(Dispatchers.IO) {
            runCatching {
                val typeface: Typeface? = if (uri.scheme == "content") {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        Typeface.Builder(it.fileDescriptor).build()
                    }
                } else {
                    uri.path?.let { Typeface.createFromFile(it) }
                }
                typeface?.let { FontFamily(it) }
            }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        }
        if (parsed != null) value = parsed
    }
}
