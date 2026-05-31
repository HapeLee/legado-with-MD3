package io.legado.app.ui.widget.components

import android.graphics.Typeface
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
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.help.loadFontFiles
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.utils.FileDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared font selection grid with search and system typeface support.
 *
 * @param fontFolderUri URI of the font folder to load from
 * @param selectedFontName currently selected font name (for check mark), null to hide
 * @param showPreview true to render font preview with real typeface, false for plain text
 * @param onSelectFont called when a font file is selected
 * @param onSelectSystemTypeface called when a system typeface is selected (index-based), null to hide system typeface button
 * @param systemTypefaces list of system typeface names, null to hide
 * @param emptyText text to show when no fonts found
 */
@Composable
fun FontSelectGrid(
    fontFolderUri: android.net.Uri?,
    selectedFontName: String?,
    showPreview: Boolean,
    onSelectFont: (FileDoc) -> Unit,
    onSelectSystemTypeface: ((Int) -> Unit)? = null,
    systemTypefaces: Array<String>? = null,
    emptyText: String? = null,
) {
    val context = LocalContext.current
    var fontItems by remember { mutableStateOf<List<FileDoc>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showTypefaceMenu by remember { mutableStateOf(false) }

    LaunchedEffect(fontFolderUri) {
        isLoading = true
        fontItems = withContext(Dispatchers.IO) {
            loadFontFiles(context, fontFolderUri)
        }
        isLoading = false
    }

    val filteredItems = remember(fontItems, searchQuery) {
        if (searchQuery.isBlank()) fontItems
        else fontItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        // Search + system typeface
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_content)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (systemTypefaces != null && onSelectSystemTypeface != null) {
                Box {
                    SmallPlainButton(
                        onClick = { showTypefaceMenu = true },
                        icon = Icons.Default.TextFields,
                    )
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
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Font grid
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
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
                modifier = Modifier.height(360.dp),
            ) {
                items(filteredItems, key = { it.name }) { item ->
                    FontItem(
                        item = item,
                        isSelected = item.name == selectedFontName,
                        showPreview = showPreview,
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
    showPreview: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showPreview) {
                val context = LocalContext.current
                AndroidView(
                    factory = { ctx ->
                        android.widget.TextView(ctx).apply {
                            text = item.name
                            textSize = 14f
                            gravity = android.view.Gravity.CENTER
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            runCatching {
                                val uri = item.uri
                                val typeface: Typeface? = if (uri.scheme == "content") {
                                    ctx.contentResolver.openFileDescriptor(uri, "r")?.use {
                                        Typeface.Builder(it.fileDescriptor).build()
                                    }
                                } else {
                                    uri.path?.let { Typeface.createFromFile(it) }
                                }
                                this.typeface = typeface
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
