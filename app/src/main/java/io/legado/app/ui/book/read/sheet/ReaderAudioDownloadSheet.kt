package io.legado.app.ui.book.read.sheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.ReadAloudAudioDownload
import io.legado.app.help.readaloud.playback.ReadAloudAudioStore
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadMenuConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.CastFieldSpec
import io.legado.app.ui.widget.components.CastFieldStack
import io.legado.app.ui.widget.components.CastImeScope
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.castCardMaxHeight
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 听书下载：把选定章节的朗读音频整章合成到本地，之后点朗读直接播本地文件、不联网。
 *
 * 音频是**按朗读那一条链路**合成的（同一套音色路由、同一个文件名算法），所以下载过的章节
 * 朗读时能找到同名文件；章内每句一个文件，文件名清单记在库里，删除才能精确到章。
 *
 * 排版按「范围 → 进度 → 已下载」三节走，每节一个圆角容器；节里只放该节的东西，
 * 不再靠一串 start=8dp 的边距去对齐。
 */
@Composable
fun ReaderAudioDownloadSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    menuConfig: ReadMenuConfig? = null,
) {
    // show 一撤整棵树就没了，退场动画没有地方播：多留 180ms 让淡出跑完
    val opened = show
    if (!rememberSheetAlive(opened)) return
    val scrimAlpha = rememberSheetScrimAlpha(opened)
    val book = ReadBook.book ?: return
    val bookUrl = book.bookUrl
    val context = LocalContext.current
    val cancelHintMsg = stringResource(R.string.read_aloud_audio_download_cancel_hint)
    val deletedCountMsg = stringResource(R.string.read_aloud_audio_download_deleted)
    val scope = rememberCoroutineScope()
    val chapterCount = book.totalChapterNum.coerceAtLeast(1)
    val progress by ReadAloudAudioStore.progress.collectAsState()
    var refreshKey by remember { mutableStateOf(0) }
    // null = 还没查完库。第一帧就断言「本章没下载过」会闪一句假话。
    val rows by produceState(
        initialValue = null as List<ReadAloudAudioDownload>?,
        key1 = bookUrl,
        key2 = refreshKey,
    ) {
        value = withContext(Dispatchers.IO) { ReadAloudAudioStore.list(bookUrl) }
    }
    val downloaded = rows.orEmpty()
    val currentChapterRow = rows?.firstOrNull { it.chapterIndex == ReadBook.durChapterIndex }
    var byRange by remember { mutableStateOf(false) }
    var startText by remember { mutableStateOf((ReadBook.durChapterIndex + 1).toString()) }
    var endText by remember { mutableStateOf((ReadBook.durChapterIndex + 1).toString()) }
    val range = downloadRange(byRange, startText, endText, chapterCount)

    CastImeScope {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LegadoTheme.colorScheme.scrim.copy(alpha = 0.42f * scrimAlpha))
                .safeDrawingPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismissRequest() },
            contentAlignment = Alignment.Center,
        ) {
            CastSheetCard(
                menuConfig = menuConfig,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .padding(horizontal = 20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = castCardMaxHeight(0.8f))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                        AppText(
                            text = stringResource(R.string.read_aloud_audio_download_entry),
                            modifier = Modifier.padding(start = 4.dp),
                            style = LegadoTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                    }
                    AppText(
                        text = stringResource(R.string.read_aloud_audio_download_summary),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )

                    SheetSection(stringResource(R.string.read_aloud_audio_download_section_range)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MediumTonalButton(
                                onClick = { byRange = false },
                                modifier = Modifier.weight(1f),
                                selected = !byRange,
                                text = stringResource(
                                    R.string.read_aloud_audio_download_this_chapter,
                                ),
                            )
                            MediumTonalButton(
                                onClick = { byRange = true },
                                modifier = Modifier.weight(1f),
                                selected = byRange,
                                text = stringResource(
                                    R.string.read_aloud_audio_download_by_range,
                                ),
                            )
                        }
                        // 本章状态放在两个按钮下面：选「只下本章」时先看得到这句，再决定按不按下载
                        if (rows != null) {
                            AppText(
                                text = if (currentChapterRow == null) {
                                    stringResource(R.string.read_aloud_audio_download_chapter_none)
                                } else {
                                    stringResource(
                                        R.string.read_aloud_audio_download_chapter_hint,
                                        currentChapterRow.sentenceCount,
                                    )
                                },
                                style = LegadoTheme.typography.labelSmall,
                                color = if (currentChapterRow == null) {
                                    LegadoTheme.colorScheme.onSurfaceVariant
                                } else {
                                    LegadoTheme.colorScheme.primary
                                },
                            )
                        }
                        if (byRange) {
                            CastFieldStack(
                                specs = listOf(
                                    CastFieldSpec(
                                        id = "startChapter",
                                        label = stringResource(
                                            R.string.read_aloud_audio_download_start_chapter,
                                        ),
                                        value = startText,
                                        onValueChange = { startText = it },
                                    ),
                                    CastFieldSpec(
                                        id = "endChapter",
                                        label = stringResource(
                                            R.string.read_aloud_audio_download_end_chapter,
                                        ),
                                        value = endText,
                                        onValueChange = { endText = it },
                                    ),
                                ),
                            )
                            AppText(
                                text = if (range == null) {
                                    stringResource(R.string.read_aloud_audio_download_invalid_range)
                                } else {
                                    stringResource(
                                        R.string.read_aloud_audio_download_range_count,
                                        range.second - range.first + 1,
                                    )
                                },
                                style = LegadoTheme.typography.labelSmall,
                                color = if (range == null) {
                                    LegadoTheme.colorScheme.error
                                } else {
                                    LegadoTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MediumTonalButton(
                                onClick = {
                                    if (range != null) {
                                        ReadAloud.downloadAudio(
                                            context, bookUrl, range.first, range.second,
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = range != null && !progress.running,
                                icon = Icons.Default.Download,
                                text = stringResource(R.string.read_aloud_audio_download_start),
                            )
                            if (progress.running) {
                                MediumTonalButton(
                                    onClick = {
                                        ReadAloud.cancelDownloadAudio(context)
                                        context.toastOnUi(cancelHintMsg)
                                    },
                                    icon = Icons.Default.Close,
                                    text = stringResource(R.string.read_aloud_audio_download_cancel),
                                )
                            }
                        }
                    }

                    if (progress.running || progress.chapterTotal > 0) {
                        SheetSection(
                            stringResource(R.string.read_aloud_audio_download_section_progress),
                        ) {
                            if (progress.currentChapter.isNotBlank()) {
                                AppText(
                                    text = progress.currentChapter,
                                    style = LegadoTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            ProgressLine(
                                label = stringResource(
                                    R.string.read_aloud_audio_download_chapter_bar,
                                ),
                                text = "${progress.chapterDone}/${progress.chapterTotal}",
                                fraction = progress.chapterFraction,
                                running = progress.running,
                            )
                            ProgressLine(
                                label = stringResource(
                                    R.string.read_aloud_audio_download_sentence_bar,
                                ),
                                text = "${progress.sentenceDone}/${progress.sentenceTotal}",
                                fraction = progress.sentenceFraction,
                                running = progress.running,
                            )
                            if (progress.failed > 0) {
                                // 失败的那几句不落进下载区：宁可少下，也不能下一句永远播空白的占位
                                AppText(
                                    text = stringResource(
                                        R.string.read_aloud_audio_download_failed_count,
                                        progress.failed,
                                    ),
                                    style = LegadoTheme.typography.bodySmall,
                                    color = LegadoTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    SheetSection(
                        stringResource(
                            R.string.read_aloud_audio_download_downloaded_count,
                            downloaded.size,
                            downloaded.sumOf { it.sentenceCount },
                        ),
                    ) {
                        if (downloaded.isEmpty()) {
                            AppText(
                                text = stringResource(R.string.read_aloud_audio_download_empty),
                                style = LegadoTheme.typography.bodyMedium,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        downloaded.forEach { row ->
                            DownloadedRow(
                                row = row,
                                isCurrent = row.chapterIndex == ReadBook.durChapterIndex,
                                onDelete = {
                                    scope.launch {
                                        val deleted = ReadAloudAudioStore.delete(
                                            bookUrl, row.chapterIndex,
                                        )
                                        context.toastOnUi(deletedCountMsg.format(deleted))
                                        refreshKey++
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 起止章按目录里的章号填（从 1 开始），返回朗读用的章节下标区间。 */
private fun downloadRange(
    byRange: Boolean,
    startText: String,
    endText: String,
    chapterCount: Int,
): Pair<Int, Int>? {
    if (!byRange) {
        return ReadBook.durChapterIndex to ReadBook.durChapterIndex
    }
    val start = startText.trim().toIntOrNull() ?: return null
    val end = endText.trim().toIntOrNull() ?: return null
    if (start <= 0 || end <= 0 || end < start) return null
    val last = (chapterCount - 1).coerceAtLeast(0)
    return (start - 1).coerceAtMost(last) to (end - 1).coerceAtMost(last)
}

/** 一节：小标题 + 一个圆角容器，容器里纵向排开。三节同一套间距，不再各写各的边距。 */
@Composable
private fun SheetSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AppText(
            text = title,
            style = LegadoTheme.typography.labelLarge,
            color = LegadoTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(LegadoTheme.colorScheme.surfaceContainerHigh)
                .padding(14.dp)
                // 开关范围输入、进度出现、删掉一行——这一节的长短每次都变，让它长出来缩回去都是平滑的
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** 一行进度：标签 + 计数在上，圆角进度条在下，长章名不会把数字挤走。 */
@Composable
private fun ProgressLine(
    label: String,
    text: String,
    fraction: Float,
    running: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                text = label,
                modifier = Modifier.weight(1f),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            AppText(
                text = text,
                style = LegadoTheme.typography.labelMedium,
                textAlign = TextAlign.End,
            )
        }
        if (running && fraction <= 0f) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** 已下载的一章：章号徽标 + 标题 + 句数 + 删除。当前章的徽标点亮，长列表里一眼找到自己在读的那章。 */
@Composable
private fun DownloadedRow(
    row: ReadAloudAudioDownload,
    isCurrent: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isCurrent) {
                        LegadoTheme.colorScheme.primaryContainer
                    } else {
                        LegadoTheme.colorScheme.surfaceContainerHighest
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = (row.chapterIndex + 1).toString(),
                style = LegadoTheme.typography.labelSmall,
                color = if (isCurrent) {
                    LegadoTheme.colorScheme.onPrimaryContainer
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        AppText(
            text = row.title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            style = LegadoTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AppText(
            text = stringResource(
                R.string.read_aloud_audio_download_sentences_count,
                row.sentenceCount,
            ),
            style = LegadoTheme.typography.labelSmall,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.read_aloud_audio_download_delete),
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
