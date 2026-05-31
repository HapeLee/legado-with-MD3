package io.legado.app.ui.book.read.sheet

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

@Composable
fun ToolButtonConfigSheet(
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("tool_button_config", Context.MODE_PRIVATE)
    }
    var items by remember {
        mutableStateOf(loadToolButtonConfig(prefs, context))
    }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.config_btn),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    ToolButtonConfigItem(
                        item = item,
                        canMoveUp = index > 0 && item.enabled && items[index - 1].enabled,
                        canMoveDown = index < items.lastIndex && item.enabled
                                && items.getOrNull(index + 1)?.enabled == true,
                        onToggleEnabled = {
                            items = items.toMutableList().also { list ->
                                list[index] = item.copy(enabled = !item.enabled)
                                if (!item.enabled) {
                                    // Was just disabled — move to end
                                    val moved = list.removeAt(index)
                                    list.add(moved)
                                }
                            }
                        },
                        onMoveUp = {
                            items = items.toMutableList().also { list ->
                                list.add(index - 1, list.removeAt(index))
                            }
                        },
                        onMoveDown = {
                            items = items.toMutableList().also { list ->
                                list.add(index + 1, list.removeAt(index))
                            }
                        },
                    )
                }
            }

            TextButton(
                onClick = {
                    saveToolButtonConfig(prefs, items)
                    onSaved()
                    onDismissRequest()
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun ToolButtonConfigItem(
    item: ToolButtonEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleEnabled: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val (iconRes, name) = item.info
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (item.enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        if (item.enabled) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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
    }
}

private data class ToolButtonEntry(
    val id: String,
    val enabled: Boolean,
    val info: Pair<Int, String>,
)

private fun getAllButtonIds() = listOf(
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

private fun getButtonInfo(context: Context, id: String): Pair<Int, String> {
    return when (id) {
        "search" -> R.drawable.ic_search to context.getString(R.string.search_content)
        "auto_page" -> R.drawable.ic_auto_page to context.getString(R.string.auto_next_page)
        "catalog" -> R.drawable.ic_toc to context.getString(R.string.chapter_list)
        "read_aloud" -> R.drawable.ic_read_aloud to context.getString(R.string.read_aloud)
        "setting" -> R.drawable.ic_settings to context.getString(R.string.setting)
        "addBookmark" -> R.drawable.ic_bookmark to context.getString(R.string.bookmark)
        "theme" -> R.drawable.ic_daytime to context.getString(R.string.day_night_switch)
        "prev_chapter" -> R.drawable.ic_previous to context.getString(R.string.previous_chapter)
        "next_chapter" -> R.drawable.ic_next to context.getString(R.string.next_chapter)
        "translate" -> R.drawable.ic_translate to context.getString(R.string.translate)
        "replace" -> R.drawable.ic_find_replace to context.getString(R.string.replace_purify)
        "replace_badge" -> R.drawable.ic_find_replace to context.getString(R.string.replace_purify_badge)
        else -> R.drawable.ic_help to id
    }
}

private fun loadToolButtonConfig(
    prefs: android.content.SharedPreferences,
    context: Context,
): List<ToolButtonEntry> {
    val str = prefs.getString("tool_buttons", null)

    val rawList = if (str.isNullOrBlank()) {
        getAllButtonIds().mapIndexed { index, id ->
            Pair(id, index < 5)
        }
    } else {
        val saved = str.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size == 2) Pair(parts[0], parts[1].toBoolean()) else null
        }.toMutableList()
        val allIds = getAllButtonIds()
        for (id in allIds) {
            if (saved.none { it.first == id }) {
                saved.add(Pair(id, true))
            }
        }
        saved
    }

    return rawList.map { (id, enabled) ->
        ToolButtonEntry(id, enabled, getButtonInfo(context, id))
    }
}

private fun saveToolButtonConfig(
    prefs: android.content.SharedPreferences,
    list: List<ToolButtonEntry>,
) {
    val str = list.joinToString(";") { "${it.id},${it.enabled}" }
    prefs.edit().putString("tool_buttons", str).apply()
}
