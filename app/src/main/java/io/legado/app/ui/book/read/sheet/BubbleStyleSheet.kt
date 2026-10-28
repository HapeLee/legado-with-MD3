package io.legado.app.ui.book.read.sheet

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.help.config.BubbleSettings
import io.legado.app.model.ImageProvider
import io.legado.app.ui.book.read.ConfigUpdateAction
import io.legado.app.ui.book.read.ReadConfigUpdateBus
import io.legado.app.ui.config.bubble.BubblePreviewCache
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import kotlinx.coroutines.launch

/**
 * 阅读页的气泡快速切换。只做「换一个立刻见效」这件事，导入/编辑仍回设置页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubbleStyleSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    /** 打开气泡管理页：由阅读页宿主 push 路由，返回时能回到阅读界面。 */
    onOpenManage: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val previewCache = remember { BubblePreviewCache() }
    var entries by remember { mutableStateOf<List<BubblePackageManager.Entry>>(emptyList()) }
    var activeDirName by remember { mutableStateOf(BubblePackageManager.activeDirName()) }
    var forceSoftware by remember { mutableStateOf(BubbleSettings.forceSoftwareParagraphBubble) }

    LaunchedEffect(show) {
        if (show) {
            entries = runCatching { BubblePackageManager.loadEntries() }.getOrDefault(emptyList())
            activeDirName = BubblePackageManager.activeDirName()
            forceSoftware = BubbleSettings.forceSoftwareParagraphBubble
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.bubble_config),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SwitchSettingItem(
                title = stringResource(R.string.bubble_force_software),
                description = stringResource(R.string.bubble_force_software_desc),
                checked = forceSoftware,
                onCheckedChange = { checked ->
                    forceSoftware = checked
                    scope.launch {
                        BubbleSettings.setForceSoftwareParagraphBubble(checked)
                        refreshReader()
                    }
                },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(entries, key = { it.dirName }) { entry ->
                    BubbleRow(
                        entry = entry,
                        active = entry.dirName == activeDirName,
                        previewCache = previewCache,
                        onClick = {
                            scope.launch {
                                runCatching { BubblePackageManager.apply(entry) }
                                activeDirName = BubblePackageManager.activeDirName()
                                refreshReader()
                                onDismissRequest()
                            }
                        },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onOpenManage()
                    },
                ) {
                    AppText(
                        text = stringResource(R.string.bubble_manage_entry),
                        color = LegadoTheme.colorScheme.primary,
                        style = LegadoTheme.typography.titleMediumEmphasized,
                    )
                }
            }
            Spacer(modifier = Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun BubbleRow(
    entry: BubblePackageManager.Entry,
    active: Boolean,
    previewCache: BubblePreviewCache,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BubbleThumb(entry = entry, previewCache = previewCache)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = entry.config.name,
                color = if (active) {
                    LegadoTheme.colorScheme.primary
                } else {
                    LegadoTheme.colorScheme.onSurface
                },
                style = LegadoTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = if (active) {
                    stringResource(R.string.bubble_applied)
                } else if (entry.source == BubblePackageManager.Source.BUILTIN) {
                    stringResource(R.string.bubble_source_builtin)
                } else {
                    stringResource(R.string.bubble_source_local)
                },
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                style = LegadoTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun BubbleThumb(
    entry: BubblePackageManager.Entry,
    previewCache: BubblePreviewCache,
) {
    val isNight = LegadoTheme.isDark
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = entry.dirName,
        key2 = entry.config.updatedAt,
        key3 = isNight,
    ) {
        value = previewCache.load(entry = entry, sidePx = 120, isNightTheme = isNight)
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(LegadoTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private suspend fun refreshReader() {
    ImageProvider.clear()
    ReadConfigUpdateBus.post(
        setOf(
            ConfigUpdateAction.RefreshInlineImages,
            ConfigUpdateAction.ReloadContent,
        )
    )
}
