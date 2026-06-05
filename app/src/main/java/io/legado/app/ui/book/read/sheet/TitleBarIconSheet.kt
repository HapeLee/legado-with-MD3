package io.legado.app.ui.book.read.sheet

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

@Composable
fun TitleBarIconSheet(
    customIcons: Map<String, String>,
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("title_bar_icons", Context.MODE_PRIVATE)
    }
    var items by remember {
        mutableStateOf(loadTitleBarIconConfig(prefs, context))
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        items = items.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.title_bar_icons),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(items, key = { it.id }) { item ->
                    ReorderableItem(reorderableState, key = item.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                        NormalCard(
                            elevation = elevation,
                            cornerRadius = 12.dp,
                            containerColor = LegadoTheme.colorScheme.surfaceContainerLow
                        ) {
                            TitleBarIconItem(
                                item = item,
                                customIcon = customIcons[item.id],
                                onToggleEnabled = {
                                    items = items.toMutableList().also { list ->
                                        val index = list.indexOfFirst { it.id == item.id }
                                        list[index] = item.copy(enabled = !item.enabled)
                                        if (!item.enabled) {
                                            val moved = list.removeAt(index)
                                            list.add(moved)
                                        }
                                    }
                                },
                                onSelectIcon = {
                                    onIntent(ReadBookIntent.OpenTitleBarCustomIconPicker(item.id))
                                },
                                onClearIcon = {
                                    customIcons[item.id]?.let { File(it).delete() }
                                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleBarCustomIcon(item.id, "")))
                                },
                                dragHandleModifier = Modifier.draggableHandle(),
                            )
                        }
                    }
                }
            }

            ConfirmDismissButtonsRow(
                onDismiss = onDismissRequest,
                onConfirm = {
                    saveTitleBarIconConfig(prefs, items)
                    onSaved()
                    onDismissRequest()
                },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.action_save),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TitleBarIconItem(
    item: TitleBarIconEntry,
    customIcon: String?,
    onToggleEnabled: () -> Unit,
    onSelectIcon: () -> Unit,
    onClearIcon: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
) {
    val (iconRes, name) = item.info
    val alpha = if (item.enabled) 1f else 0.38f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp),
        ) {
            if (!customIcon.isNullOrBlank()) {
                AsyncImage(
                    model = customIcon,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    alpha = alpha,
                )
            } else {
                Icon(
                    imageVector = iconRes,
                    contentDescription = name,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )

        if (item.enabled) {
            // Custom icon button
            if (!customIcon.isNullOrBlank()) {
                IconButton(
                    onClick = onClearIcon,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                SmallTonalButton(
                    onClick = onSelectIcon,
                    icon = Icons.Default.Add,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        // Visibility toggle
        IconButton(
            onClick = onToggleEnabled,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = if (item.enabled) {
                    Icons.Default.Visibility
                } else {
                    Icons.Default.VisibilityOff
                },
                contentDescription = null,
                tint = if (item.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }

        // Drag handle
        IconButton(
            modifier = dragHandleModifier.size(36.dp),
            onClick = {},
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private data class TitleBarIconEntry(
    val id: String,
    val enabled: Boolean,
    val info: Pair<ImageVector, String>,
)

private fun getAllTitleBarIconIds() = listOf(
    "search",
    "auto_page",
    "catalog",
    "read_aloud",
    "setting",
    "addBookmark",
    "theme",
    "prev_chapter",
    "next_chapter",
    "replace",
    "replace_badge",
    "translate",
)

private fun getTitleBarIconInfo(context: Context, id: String): Pair<ImageVector, String> {
    return when (id) {
        "search" -> Icons.Default.Search to context.getString(R.string.search_content)
        "auto_page" -> Icons.Default.PlayArrow to context.getString(R.string.auto_next_page)
        "catalog" -> Icons.AutoMirrored.Filled.List to context.getString(R.string.chapter_list)
        "read_aloud" -> Icons.Default.RecordVoiceOver to context.getString(R.string.read_aloud)
        "setting" -> Icons.Default.Settings to context.getString(R.string.setting)
        "addBookmark" -> Icons.Default.Bookmark to context.getString(R.string.bookmark)
        "theme" -> Icons.Default.Brightness6 to context.getString(R.string.day_night_switch)
        "prev_chapter" -> Icons.Default.SkipPrevious to context.getString(R.string.previous_chapter)
        "next_chapter" -> Icons.Default.SkipNext to context.getString(R.string.next_chapter)
        "translate" -> Icons.Default.Translate to context.getString(R.string.translate)
        "replace" -> Icons.Default.FindReplace to context.getString(R.string.replace_purify)
        "replace_badge" -> Icons.Default.AutoAwesome to context.getString(R.string.replace_purify_badge)
        else -> Icons.AutoMirrored.Filled.HelpOutline to id
    }
}

private fun loadTitleBarIconConfig(
    prefs: SharedPreferences,
    context: Context,
): List<TitleBarIconEntry> {
    val str = prefs.getString("icons", null)

    val rawList = if (str.isNullOrBlank()) {
        getAllTitleBarIconIds().mapIndexed { index, id ->
            Pair(id, index < 5)
        }
    } else {
        val saved = str.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size == 2) Pair(parts[0], parts[1].toBoolean()) else null
        }.toMutableList()
        val allIds = getAllTitleBarIconIds()
        for (id in allIds) {
            if (saved.none { it.first == id }) {
                saved.add(Pair(id, true))
            }
        }
        saved
    }

    return rawList.map { (id, enabled) ->
        TitleBarIconEntry(id, enabled, getTitleBarIconInfo(context, id))
    }
}

private fun saveTitleBarIconConfig(
    prefs: SharedPreferences,
    list: List<TitleBarIconEntry>,
) {
    val str = list.joinToString(";") { "${it.id},${it.enabled}" }
    prefs.edit().putString("icons", str).apply()
}
