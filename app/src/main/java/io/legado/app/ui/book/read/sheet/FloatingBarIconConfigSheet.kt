package io.legado.app.ui.book.read.sheet

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookButtonConfigItem
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.widget.components.ConfigListEntry
import io.legado.app.ui.widget.components.ReorderableConfigList
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

@Composable
fun FloatingBarIconConfigSheet(
    show: Boolean,
    items: List<ReadBookButtonConfigItem>,
    customIcons: Map<String, String>,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    ButtonIconConfigSheet(
        show = show,
        title = stringResource(R.string.title_bar_icons),
        items = items,
        customIcons = customIcons,
        onDismissRequest = onDismissRequest,
        onSaved = { onIntent(ReadBookIntent.SaveTitleBarButtonConfig(it)) },
        onSelectIcon = { id -> onIntent(ReadBookIntent.OpenTitleBarCustomIconPicker(id)) },
        onClearIcon = { id ->
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleBarCustomIcon(id, "")))
        },
    )
}

@Composable
internal fun BottomBarIconSheet(
    show: Boolean,
    items: List<ReadBookButtonConfigItem>,
    customIcons: Map<String, String>,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    ButtonIconConfigSheet(
        show = show,
        title = stringResource(R.string.config_btn),
        items = items,
        customIcons = customIcons,
        onDismissRequest = onDismissRequest,
        onSaved = { onIntent(ReadBookIntent.SaveMenuButtonConfig(it)) },
        onSelectIcon = { id -> onIntent(ReadBookIntent.OpenMenuCustomIconPicker(id)) },
        onClearIcon = { id ->
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuCustomIcon(id, "")))
        },
    )
}

@Composable
private fun ButtonIconConfigSheet(
    show: Boolean,
    title: String,
    items: List<ReadBookButtonConfigItem>,
    customIcons: Map<String, String>,
    onDismissRequest: () -> Unit,
    onSaved: (List<ReadBookButtonConfigItem>) -> Unit,
    onSelectIcon: (String) -> Unit,
    onClearIcon: (String) -> Unit,
) {
    val context = LocalContext.current
    val entries = remember(items) { buildButtonIconEntries(items, context) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            ReorderableConfigList(
                initialEntries = entries,
                customIcons = customIcons,
                onSelectIcon = onSelectIcon,
                onClearIcon = onClearIcon,
                onDismiss = onDismissRequest,
                onConfirm = { confirmed ->
                    onSaved(confirmed.map { ReadBookButtonConfigItem(it.id, it.enabled) })
                    onDismissRequest()
                },
                reorderEnabledFirst = true,
            )
        }
    }
}

private fun buildButtonIconEntries(
    items: List<ReadBookButtonConfigItem>,
    context: Context,
): List<ConfigListEntry> {
    val infoMap = readMenuButtonInfos(context).associateBy { it.id }
    return items.mapNotNull { item ->
        val id = item.id
        infoMap[id]?.let { info ->
            ConfigListEntry(id, item.enabled, info.icon, info.label)
        }
    }
}
