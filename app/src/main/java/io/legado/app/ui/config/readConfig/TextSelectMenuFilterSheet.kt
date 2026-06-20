package io.legado.app.ui.config.readConfig

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AdaptiveSwitch
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

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
        endAction = {
            SmallPlainButton(
                icon = Icons.Default.Check,
                onClick = {
                    onFilterChanged(pendingFilterString)
                    onDismissRequest()
                }
            )
        },
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
                                        style = LegadoTheme.typography.titleMedium
                                    )
                                }
                                AdaptiveSwitch(
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
        }
    }
}

data class TextSelectMenuAppInfo(
    val title: String,
    val componentName: String,
    val icon: android.graphics.drawable.Drawable?
)
