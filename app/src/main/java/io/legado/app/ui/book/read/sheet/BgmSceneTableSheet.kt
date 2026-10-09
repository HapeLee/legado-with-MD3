package io.legado.app.ui.book.read.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.readaloud.cast.BgmSceneStore
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.CastFieldSpec
import io.legado.app.ui.widget.components.CastFieldStack
import io.legado.app.ui.widget.components.CastImeScope
import io.legado.app.ui.widget.components.CastOption
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.AppIconButton
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本章背景音乐总览：一屏看完、一屏改完本章所有配乐区间。
 *
 * 一行 = 一个区间（这条标记起，到下一条标记之前），因为朗读时整段区间播同一首，
 * 只报起点看不出「这段音乐铺了多远」。
 *
 * 与段首配乐悬浮窗的区别只在收尾：这里改完**不关窗、不弹 toast**（总览的意义就是连着改
 * 好几段），所以走 UpdateBgmScene / DeleteBgmScene 而不是胶囊那两条。
 * 音量滑杆常驻行内，松手才提交（拖动中每像素写库会把配乐轨的音量跟着抖动）。
 */
@Composable
fun BgmSceneTableSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val book = ReadBook.book
    val bookUrl = book?.bookUrl
    val chapterIndex = ReadBook.durChapterIndex
    var refreshKey by remember { mutableStateOf(0) }
    val rows by produceState(
        initialValue = emptyList<BgmSceneStore.OverviewRow>(),
        key1 = bookUrl,
        key2 = chapterIndex,
        // 本屏自己的改动 + 别处的（AI 重新分配、悬浮窗、删除分配）：只看 refreshKey 会让
        // 这一屏在别人改完后还是旧表，表现为「删除分配对场景没作用」
        key3 = "$refreshKey#${BgmSceneStore.version}",
    ) {
        value = if (book == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { BgmSceneStore.overview(book, chapterIndex) }
        }
    }
    var editingOrdinal by remember { mutableStateOf<Int?>(null) }

    // 正在展开某一段的编辑器时，返回键先收起编辑器而不是关掉整个弹层
    BackHandler(enabled = show && editingOrdinal != null) { editingOrdinal = null }

    AppModalBottomSheet(
        show = show && book != null,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.cast_bgm_scene_table),
    ) {
        CastImeScope {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // 弹层内容边距统一 16.dp：与其它听书弹层同一档，底部留白够最后一行离开拖拽条
                    .padding(bottom = 16.dp),
            ) {
                AppText(
                    text = stringResource(R.string.cast_bgm_scene_table_summary),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (rows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyMessage(messageResId = R.string.cast_bgm_scene_table_empty)
                    }
                }
                rows.forEach { row ->
                    BgmSceneTableRow(
                        bookUrl = bookUrl.orEmpty(),
                        chapterIndex = chapterIndex,
                        row = row,
                        editing = editingOrdinal == row.ordinal,
                        onToggleEdit = {
                            editingOrdinal =
                                if (editingOrdinal == row.ordinal) null else row.ordinal
                        },
                        onIntent = onIntent,
                        onSaved = {
                            editingOrdinal = null
                            refreshKey++
                        },
                        onChanged = { refreshKey++ },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/**
 * 一行：区间范围 + 池/曲目 + 正文开头，音量滑杆常驻，点范围那行展开池与曲目的编辑。
 */
@Composable
private fun BgmSceneTableRow(
    bookUrl: String,
    chapterIndex: Int,
    row: BgmSceneStore.OverviewRow,
    editing: Boolean,
    onToggleEdit: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
    onSaved: () -> Unit,
    onChanged: () -> Unit,
) {
    // 记住用户拖到的位置：行数据只在提交后（refreshKey++）才会带回新音量
    var volumeDraft by remember(row.ordinal, row.volume) { mutableStateOf(row.volume) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleEdit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = if (row.endOrdinal > row.ordinal + 1) {
                    stringResource(R.string.cast_bgm_scene_range, row.ordinal + 1, row.endOrdinal)
                } else {
                    stringResource(R.string.cast_bgm_scene_row, row.ordinal + 1)
                },
                style = LegadoTheme.typography.bodyMedium,
            )
            AppIcon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(16.dp),
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            AppText(
                text = row.musicLabel.ifBlank { stringResource(R.string.cast_bgm_scene_none) },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
            AppIconButton(onClick = {
                onIntent(ReadBookIntent.DeleteBgmScene(row.ordinal))
                onChanged()
            }) {
                AppIcon(
                    imageVector = AppIcons.Delete,
                    contentDescription = stringResource(R.string.cast_bgm_scene_clear),
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (row.preview.isNotBlank()) {
            AppText(
                text = row.preview,
                modifier = Modifier.padding(start = 8.dp),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppSlider(
                    value = volumeDraft,
                    onValueChange = { volumeDraft = it },
                    onValueChangeFinished = {
                        onIntent(
                            ReadBookIntent.UpdateBgmScene(
                                paragraphIndex = row.ordinal,
                                poolName = row.poolName,
                                trackName = row.trackName,
                                volume = volumeDraft,
                            ),
                        )
                        onChanged()
                    },
                    valueRange = 0f..1f,
                    steps = 19,
                    modifier = Modifier.weight(1f),
                )
                AppText(
                    text = "${(volumeDraft * 100).toInt()}%",
                    modifier = Modifier.padding(start = 8.dp),
                    style = LegadoTheme.typography.bodySmall,
                )
            }
        } else {
            BgmSceneRowEditor(
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                row = row,
                onSave = { pool, track, volume ->
                    onIntent(
                        ReadBookIntent.UpdateBgmScene(
                            paragraphIndex = row.ordinal,
                            poolName = pool,
                            trackName = track,
                            volume = volume,
                        ),
                    )
                    onSaved()
                },
                onCancel = onToggleEdit,
            )
        }
    }
}

/** 展开后的池/曲目编辑 + 音量：候选列表与段首配乐悬浮窗同源（sheetData）。 */
@Composable
private fun BgmSceneRowEditor(
    bookUrl: String,
    chapterIndex: Int,
    row: BgmSceneStore.OverviewRow,
    onSave: (String, String, Float) -> Unit,
    onCancel: () -> Unit,
) {
    val data by produceState<BgmSceneStore.SheetData?>(
        initialValue = null,
        key1 = row.ordinal,
    ) {
        value = withContext(Dispatchers.IO) {
            BgmSceneStore.sheetData(bookUrl, chapterIndex, row.ordinal)
        }
    }
    val sheet = data ?: return
    var pool by remember(row.ordinal) { mutableStateOf(row.poolName) }
    var track by remember(row.ordinal) { mutableStateOf(row.trackName) }
    var volume by remember(row.ordinal) { mutableStateOf(row.volume) }
    var expandedRow by remember(row.ordinal) { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CastFieldStack(
            specs = listOf(
                CastFieldSpec(
                    id = "pool",
                    label = stringResource(R.string.cast_bgm_pool),
                    value = pool,
                    options = sheet.pools.map { CastOption(it, it) },
                    expanded = expandedRow == "pool",
                    onValueChange = { pool = it },
                    onSelected = {
                        pool = it.key
                        // 换池后已指定的那首若不在新池里，清掉避免「播不到」
                        if (track.isNotBlank() && it.key != row.poolName) track = ""
                    },
                    onExpand = { open -> expandedRow = if (open) "pool" else null },
                ),
                CastFieldSpec(
                    id = "track",
                    label = stringResource(R.string.cast_bgm_track),
                    value = track,
                    options = sheet.tracks.map { CastOption(it, it) },
                    expanded = expandedRow == "track",
                    onValueChange = { track = it },
                    onSelected = { track = it.key },
                    onExpand = { open -> expandedRow = if (open) "track" else null },
                ),
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                text = stringResource(R.string.cast_bgm_segment_volume),
                style = LegadoTheme.typography.bodyMedium,
            )
            AppSlider(
                value = volume,
                onValueChange = { volume = it },
                valueRange = 0f..1f,
                steps = 19,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            AppText(
                text = "${(volume * 100).toInt()}%",
                modifier = Modifier.padding(start = 8.dp),
                style = LegadoTheme.typography.bodyMedium,
            )
        }
        AppText(
            text = stringResource(R.string.cast_bgm_segment_volume_hint),
            style = LegadoTheme.typography.bodySmall,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
        ConfirmDismissButtonsRow(
            onDismiss = onCancel,
            onConfirm = { onSave(pool.trim(), track.trim(), volume) },
            dismissText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.ok),
            confirmEnabled = pool.isNotBlank() || track.isNotBlank(),
        )
    }
}
