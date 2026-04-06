package io.legado.app.ui.config.themeConfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.help.config.OldThemeConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.GSON
import io.legado.app.utils.getClipText
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeListDialog(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var listVersion by remember { mutableIntStateOf(0) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    val themeList = remember(listVersion) { OldThemeConfig.configList.toList() }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = stringResource(R.string.theme_list),
                    style = LegadoTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val clipText = context.getClipText()
                        if (clipText != null && OldThemeConfig.addConfig(clipText)) {
                            listVersion++
                        } else {
                            context.toastOnUi("Import failed")
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = stringResource(R.string.import_theme)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(themeList, key = { _, item -> item.themeName }) { index, item ->
                    GlassCard(
                        onClick = {
                            OldThemeConfig.applyConfig(context, item)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        containerColor = if (item.primaryColor.toColorInt() == context.primaryColor) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .background(
                                        color = Color(
                                            item.primaryColor.toColorInt()
                                        ),
                                        shape = MaterialTheme.shapes.large
                                    )
                                    .padding(8.dp)
                            )
                            AppText(
                                text = item.themeName,
                                style = LegadoTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val json = GSON.toJson(item)
                                    context.share(json, "主题分享")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.share)
                                )
                            }
                            IconButton(onClick = { deleteIndex = index }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    AppAlertDialog(
        data = deleteIndex,
        onDismissRequest = { deleteIndex = null },
        title = stringResource(R.string.delete),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { index ->
            OldThemeConfig.delConfig(index)
            listVersion++
            deleteIndex = null
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { deleteIndex = null }
    )
}
