package io.legado.app.ui.config.readConfig

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.series.SmallTonalButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSelectMenuFilterSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onFilterChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    // 查询所有注册了 ACTION_PROCESS_TEXT 的第三方 Activity
    var apps by remember { mutableStateOf<List<TextSelectMenuAppInfo>>(emptyList()) }
    LaunchedEffect(show) {
        if (show) {
            val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val intent = Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                pm.queryIntentActivities(intent, 0).map { resolveInfo ->
                    val title = resolveInfo.loadLabel(pm).toString()
                    val componentName = "${resolveInfo.activityInfo.packageName}/${resolveInfo.activityInfo.name}"
                    val icon = kotlin.runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
                    TextSelectMenuAppInfo(title, componentName, icon)
                }
            }
            apps = list
        } else {
            apps = emptyList()
        }
    }

    var pendingFilterString by remember(show) {
        mutableStateOf(ReadConfig.textSelectMenuFilter)
    }

    val disabledSet = remember(pendingFilterString) {
        pendingFilterString.split(",").filter { it.isNotEmpty() }.toSet()
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.text_select_menu_filter),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {


            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AppText(text = stringResource(R.string.no_apps_found))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(apps) { app ->
                        val isEnabled = !disabledSet.contains(app.componentName)
                        NormalCard(
                            onClick = {
                                val nextSet = if (isEnabled) {
                                    disabledSet + app.componentName
                                } else {
                                    disabledSet - app.componentName
                                }
                                pendingFilterString = nextSet.joinToString(",")
                            },
                            cornerRadius = 12.dp,
                            containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = app.icon,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                ) {
                                    AppText(
                                        text = app.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        val nextSet = if (checked) {
                                            disabledSet - app.componentName
                                        } else {
                                            disabledSet + app.componentName
                                        }
                                        pendingFilterString = nextSet.joinToString(",")
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { pendingFilterString = "" }) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = stringResource(R.string.show_all),
                            tint = LegadoTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        val allNames = apps.joinToString(",") { it.componentName }
                        pendingFilterString = allNames
                    }) {
                        Icon(
                            imageVector = Icons.Default.Deselect,
                            contentDescription = stringResource(R.string.hide_all),
                            tint = LegadoTheme.colorScheme.error
                        )
                    }
                }
                ConfirmDismissButtonsRow(
                    modifier = Modifier.weight(1f),
                    onDismiss = onDismissRequest,
                    onConfirm = {
                        onFilterChanged(pendingFilterString)
                        onDismissRequest()
                    },
                    dismissText = stringResource(R.string.cancel),
                    confirmText = stringResource(R.string.action_save)
                )
            }
        }
    }
}

data class TextSelectMenuAppInfo(
    val title: String,
    val componentName: String,
    val icon: android.graphics.drawable.Drawable?
)
