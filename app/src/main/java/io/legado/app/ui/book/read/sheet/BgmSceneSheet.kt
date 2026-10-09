package io.legado.app.ui.book.read.sheet

import android.media.MediaPlayer
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.readaloud.cast.BgmSceneStore
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookSheet
import io.legado.app.ui.book.read.ReadMenuConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.CastFieldSpec
import io.legado.app.ui.widget.components.CastFieldStack
import io.legado.app.ui.widget.components.CastImeScope
import io.legado.app.ui.widget.components.CastOption
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.castCardMaxHeight
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 段首配乐悬浮窗：点击正文 ♪ 胶囊后设定「这一段起播哪个背景音乐池 / 哪一首」。
 *
 * 与分配角色窗口同一套结构（主窗口内居中卡片、候选列表内嵌、共用一个真实输入框），
 * 因为这两个约束都是踩出来的：独立 Dialog 窗口在 HyperOS 上会在切输入框时反复收弹键盘。
 *
 * 只写 `bgm_scene_marks`：正文文本一个字符都不动，朗读文本也不含这个标记。
 */
@Composable
fun BgmSceneSheet(
    show: Boolean,
    paragraphIndex: Int,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
    menuConfig: ReadMenuConfig? = null,
) {
    // 关掉后宿主立刻把 paragraphIndex 清成 -1：先记住它，再让整棵树多活 180ms，
    // 否则退场动画一帧都播不出来。
    val opened = show && paragraphIndex >= 0
    val paragraphAt = rememberSheetArg(opened, paragraphIndex)
    if (!rememberSheetAlive(opened) || paragraphAt < 0) return
    val book = ReadBook.book ?: return
    val bookUrl = book.bookUrl
    val chapterIndex = ReadBook.durChapterIndex
    val loadKey = "$bookUrl#$chapterIndex#$paragraphAt"
    val data by produceState<BgmSceneStore.SheetData?>(initialValue = null, loadKey) {
        value = withContext(Dispatchers.IO) {
            BgmSceneStore.sheetData(bookUrl, chapterIndex, paragraphAt)
        }
    }
    val sheet = data ?: return

    var pool by remember(loadKey) { mutableStateOf(sheet.pool) }
    var track by remember(loadKey) { mutableStateOf(sheet.track) }
    var volume by remember(loadKey) { mutableStateOf(sheet.volume) }
    var expandedRow by remember(loadKey) { mutableStateOf<String?>(null) }

    BackHandler(enabled = show) {
        if (expandedRow != null) expandedRow = null else onDismissRequest()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playing by remember(loadKey) { mutableStateOf(false) }
    val player = remember { MediaPlayer() }
    DisposableEffect(player) {
        onDispose { runCatching { player.release() } }
    }

    val scrimAlpha = rememberSheetScrimAlpha(opened)
    CastImeScope {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 遮罩跟着卡片一起淡入淡出，否则黑底是硬蹦出来的
                .background(LegadoTheme.colorScheme.scrim.copy(alpha = 0.42f * scrimAlpha))
                .safeDrawingPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (expandedRow != null) expandedRow = null else onDismissRequest()
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
                        .heightIn(max = castCardMaxHeight(0.72f))
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MediumTonalButton(
                            onClick = onDismissRequest,
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                        AppText(
                            text = stringResource(R.string.cast_bgm_scene_title),
                            modifier = Modifier.weight(1f),
                            style = LegadoTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                        // 改完这一段通常想接着看本章还有哪几段配了乐
                        MediumPlainButton(
                            onClick = {
                                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.BgmSceneTable))
                            },
                            text = stringResource(R.string.cast_bgm_scene_overview),
                            tint = LegadoTheme.colorScheme.primary,
                        )
                    }
                    AppText(
                        text = stringResource(R.string.cast_bgm_scene_hint),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sheet.pools.isEmpty()) {
                        AppText(
                            text = stringResource(R.string.cast_bgm_scene_no_pool),
                            style = LegadoTheme.typography.bodyMedium,
                            color = LegadoTheme.colorScheme.error,
                        )
                    }

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
                                    if (track.isNotBlank() && it.key != sheet.pool) track = ""
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
                    // 留空 = 朗读时从池里随机取，这句必须在界面上说明，否则用户会以为没生效
                    AppText(
                        text = stringResource(R.string.cast_bgm_scene_follow_hint),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                    // 本段音量：与配乐自身音量相乘，只压这一段，不动整条曲子
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MediumTonalButton(
                            enabled = pool.isNotBlank() || track.isNotBlank(),
                            onClick = {
                                if (playing) {
                                    playing = false
                                    runCatching { runCatching { player.stop() }.getOrNull(); player.reset() }
                                    return@MediumTonalButton
                                }
                                val wantPool = pool
                                val wantTrack = track
                                val wantVolume = volume
                                scope.launch {
                                    val resolved = withContext(Dispatchers.IO) {
                                        BgmSceneStore.resolve(
                                            bookUrl, chapterIndex, paragraphIndex,
                                            wantPool, wantTrack, wantVolume,
                                        )
                                    }
                                    if (resolved == null || !File(resolved.path).exists()) {
                                        context.toastOnUi(R.string.cast_bgm_play_failed)
                                        return@launch
                                    }
                                    playing = true
                                    val ok = runCatching {
                                        player.reset()
                                        player.setDataSource(resolved.path)
                                        player.prepare()
                                        // 试听就是实际音量：曲目自身音量 × 本段音量
                                        val level = resolved.trackVolume * wantVolume
                                        player.setVolume(level, level)
                                        player.setOnCompletionListener { playing = false }
                                        player.start()
                                    }.isSuccess
                                    if (!ok) {
                                        playing = false
                                        context.toastOnUi(R.string.cast_bgm_play_failed)
                                    }
                                }
                            },
                            icon = if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = stringResource(
                                if (playing) R.string.cast_bgm_stop else R.string.cast_bgm_play,
                            ),
                        )
                        AppText(
                            text = stringResource(R.string.cast_bgm_scene_follow),
                            style = LegadoTheme.typography.bodyMedium,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (sheet.assigned) {
                            MediumPlainButton(
                                onClick = {
                                    playing = false
                                    runCatching { runCatching { player.stop() }.getOrNull(); player.reset() }
                                    onIntent(ReadBookIntent.ClearBgmScene(paragraphIndex))
                                },
                                text = stringResource(R.string.cast_bgm_scene_clear),
                                tint = LegadoTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        MediumPlainButton(
                            onClick = onDismissRequest,
                            text = stringResource(R.string.cancel),
                        )
                        Spacer(Modifier.width(4.dp))
                        MediumTonalButton(
                            enabled = pool.isNotBlank() || track.isNotBlank(),
                            onClick = {
                                playing = false
                                runCatching { runCatching { player.stop() }.getOrNull(); player.reset() }
                                onIntent(
                                    ReadBookIntent.SetBgmScene(
                                        paragraphIndex = paragraphIndex,
                                        poolName = pool.trim(),
                                        trackName = track.trim(),
                                        volume = volume,
                                    ),
                                )
                            },
                            text = stringResource(R.string.ok),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
