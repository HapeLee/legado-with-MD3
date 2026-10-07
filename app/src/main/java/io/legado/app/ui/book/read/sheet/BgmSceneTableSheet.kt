package io.legado.app.ui.book.read.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.readaloud.cast.BgmSceneStore
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookSheet
import io.legado.app.ui.book.read.ReadMenuConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.CastFieldSpec
import io.legado.app.ui.widget.components.CastFieldStack
import io.legado.app.ui.widget.components.CastImeScope
import io.legado.app.ui.widget.components.CastOption
import io.legado.app.ui.widget.components.castCardMaxHeight
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
    menuConfig: ReadMenuConfig? = null,
) {
    // show 一撤整棵树就没了，退场动画没有地方播：多留 180ms 让淡出跑完
    val opened = show
    if (!rememberSheetAlive(opened)) return
    val scrimAlpha = rememberSheetScrimAlpha(opened)
    val book = ReadBook.book ?: return
    val bookUrl = book.bookUrl
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
        value = withContext(Dispatchers.IO) { BgmSceneStore.overview(book, chapterIndex) }
    }
    var editingOrdinal by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = show && editingOrdinal != null) { editingOrdinal = null }

    CastImeScope {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f * scrimAlpha))
                .safeDrawingPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (editingOrdinal != null) editingOrdinal = null else onDismissRequest()
                },
            contentAlignment = Alignment.Center,
        ) {
            CastSheetCard(
                menuConfig = menuConfig,
                visible = opened,
                modifier = Modifier
                    .fillMaxWidth()
                    // 只吞点击、不抢焦点：clickable 会让正在输入的框失焦→键盘先收再弹
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .padding(horizontal = 24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = castCardMaxHeight(0.78f))
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                        Text(
                            text = stringResource(R.string.cast_bgm_scene_table),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 48.dp),
                            style = LegadoTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = stringResource(R.string.cast_bgm_scene_table_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                    if (rows.isEmpty()) {
                        Text(
                            text = stringResource(R.string.cast_bgm_scene_table_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        )
                    }
                    rows.forEach { row ->
                        BgmSceneTableRow(
                            bookUrl = bookUrl,
                            chapterIndex = chapterIndex,
                            row = row,
                            editing = editingOrdinal == row.ordinal,
                            onToggleEdit = {
                                editingOrdinal = if (editingOrdinal == row.ordinal) null else row.ordinal
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
            Text(
                text = if (row.endOrdinal > row.ordinal + 1) {
                    stringResource(R.string.cast_bgm_scene_range, row.ordinal + 1, row.endOrdinal)
                } else {
                    stringResource(R.string.cast_bgm_scene_row, row.ordinal + 1)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .width(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = row.musicLabel.ifBlank { stringResource(R.string.cast_bgm_scene_none) },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
            IconButton(onClick = {
                onIntent(ReadBookIntent.DeleteBgmScene(row.ordinal))
                onChanged()
            }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cast_bgm_scene_clear),
                )
            }
        }
        if (row.preview.isNotBlank()) {
            Text(
                text = row.preview,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
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
                Text(
                    text = "${(volumeDraft * 100).toInt()}%",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
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
            Text(
                text = stringResource(R.string.cast_bgm_segment_volume),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = volume,
                onValueChange = { volume = it },
                valueRange = 0f..1f,
                steps = 19,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            Text(
                text = "${(volume * 100).toInt()}%",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = stringResource(R.string.cast_bgm_segment_volume_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.width(4.dp))
            TextButton(
                enabled = pool.isNotBlank() || track.isNotBlank(),
                onClick = { onSave(pool.trim(), track.trim(), volume) },
            ) {
                Text(stringResource(R.string.ok))
            }
        }
    }
}
