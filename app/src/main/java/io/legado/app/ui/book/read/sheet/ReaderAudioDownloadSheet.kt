package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.ReadAloudAudioDownload
import io.legado.app.help.readaloud.playback.ReadAloudAudioStore
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SectionTitle
import io.legado.app.ui.widget.components.ValueStepper
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.settingItem.TinySettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 听书下载：把选定章节的朗读音频整章合成到本地，之后点朗读直接播本地文件、不联网。
 *
 * 音频是**按朗读那一条链路**合成的（同一套音色路由、同一个文件名算法），所以下载过的章节
 * 朗读时能找到同名文件；章内每句一个文件，文件名清单记在库里，删除才能精确到章。
 *
 * 弹层用项目通用的 [AppModalBottomSheet]，内容用通用的设置项组件：
 * 范围用页签 + 步进器（不再有输入框抢焦点的 IME 问题），进度和已下载各一段。
 */
@Composable
fun ReaderAudioDownloadSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    val book = ReadBook.book
    val bookUrl = book?.bookUrl
    val chapterCount = (book?.totalChapterNum ?: 0).coerceAtLeast(1)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress by ReadAloudAudioStore.progress.collectAsState()
    val running = progress.running
    // 当前章每次重组重读：弹层常挂在阅读界面上，存快照会把章号钉在进阅读器那一章
    val currentChapterIndex = ReadBook.durChapterIndex
    val cancelHintMsg = stringResource(R.string.read_aloud_audio_download_cancel_hint)
    val deletedCountMsg = stringResource(R.string.read_aloud_audio_download_deleted)

    var byRange by remember { mutableStateOf(false) }
    var startChapter by remember(currentChapterIndex) { mutableIntStateOf(currentChapterIndex + 1) }
    var endChapter by remember(currentChapterIndex) { mutableIntStateOf(currentChapterIndex + 1) }

    // 已下载列表是库里的事实：打开弹层、每完成一章、下载收尾都重查一次，
    // 只靠 bookUrl 做 key 的话，下载完了列表还停在打开那一刻。
    var rows by remember { mutableStateOf<List<ReadAloudAudioDownload>?>(null) }
    var listVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(show, running, progress.chapterDone) {
        if (show) listVersion++
    }
    LaunchedEffect(bookUrl, listVersion) {
        val url = bookUrl ?: return@LaunchedEffect
        rows = withContext(Dispatchers.IO) { ReadAloudAudioStore.list(url) }
    }

    val downloaded = rows.orEmpty()
    val currentChapterRow = rows?.firstOrNull { it.chapterIndex == currentChapterIndex }
    // 起止章按目录里的章号填（从 1 开始），换成朗读用的 0 基下标。
    // totalChapterNum 可能还没加载完，按目录末尾收一下，别把越界的章号递给服务。
    val lastChapterIndex = chapterCount - 1
    val range: Pair<Int, Int>? = if (byRange) {
        if (endChapter < startChapter) {
            null
        } else {
            (startChapter - 1).coerceIn(0, lastChapterIndex) to
                    (endChapter - 1).coerceIn(0, lastChapterIndex)
        }
    } else {
        currentChapterIndex to currentChapterIndex
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.read_aloud_audio_download_entry),
        startAction = {
            MediumTonalButton(
                onClick = onDismissRequest,
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppText(
                text = stringResource(R.string.read_aloud_audio_download_summary),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            if (bookUrl == null) {
                AppText(
                    text = stringResource(R.string.book_not_found),
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CardTabRow(
                    tabTitles = listOf(
                        stringResource(R.string.read_aloud_audio_download_this_chapter),
                        stringResource(R.string.read_aloud_audio_download_by_range),
                    ),
                    selectedTabIndex = if (byRange) 1 else 0,
                    onTabSelected = { byRange = it == 1 },
                )

                if (byRange) {
                    RangeRow(
                        title = stringResource(R.string.read_aloud_audio_download_start_chapter),
                        value = startChapter,
                        chapterCount = chapterCount,
                        onValueChange = { startChapter = it },
                    )
                    RangeRow(
                        title = stringResource(R.string.read_aloud_audio_download_end_chapter),
                        value = endChapter,
                        chapterCount = chapterCount,
                        onValueChange = { endChapter = it },
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
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                } else {
                    // 本章状态：选「只下本章」时先看得到这句，再决定按不按下载
                    TinySettingItem(
                        title = currentChapterRow?.title
                            ?: stringResource(R.string.read_aloud_audio_download_this_chapter),
                        description = when {
                            rows == null -> null
                            currentChapterRow == null ->
                                stringResource(R.string.read_aloud_audio_download_chapter_none)

                            else -> stringResource(
                                R.string.read_aloud_audio_download_chapter_hint,
                                currentChapterRow.sentenceCount,
                            )
                        },
                        color = if (currentChapterRow != null) {
                            LegadoTheme.colorScheme.primaryContainer
                        } else {
                            LegadoTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediumTonalButton(
                        onClick = {
                            if (range != null) {
                                ReadAloud.downloadAudio(
                                    context, bookUrl, range.first, range.second,
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = range != null && !running,
                        icon = Icons.Default.Download,
                        text = stringResource(R.string.read_aloud_audio_download_start),
                    )
                    if (running) {
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

                if (running || progress.chapterTotal > 0) {
                    SectionTitle(
                        stringResource(R.string.read_aloud_audio_download_section_progress),
                    )
                    DownloadProgressCard(progress = progress, running = running)
                }

                // rows == null 表示还没查完库：这时整段都不画，
                // 否则会先闪一句「0 章 · 0 句 / 还没下载过」这种假话
                if (rows != null) {
                    if (downloaded.isEmpty()) {
                        // 空列表不摆「0 章 · 0 句」的表头，按通用空态提示
                        EmptyMessage(
                            message = stringResource(R.string.read_aloud_audio_download_empty),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    } else {
                        SectionTitle(
                            stringResource(
                                R.string.read_aloud_audio_download_downloaded_count,
                                downloaded.size,
                                downloaded.sumOf { it.sentenceCount },
                            ),
                        )
                        downloaded.forEach { row ->
                            DownloadedRow(
                                row = row,
                                isCurrent = row.chapterIndex == currentChapterIndex,
                                onDelete = {
                                    scope.launch {
                                        // 删文件 + 删记录都是阻塞 IO，不能占着主线程查库
                                        val deleted = withContext(Dispatchers.IO) {
                                            ReadAloudAudioStore.delete(bookUrl, row.chapterIndex)
                                        }
                                        context.toastOnUi(deletedCountMsg.format(deleted))
                                        listVersion++
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

/** 起止章一行：标题 + 步进器。章数上千也不必滚键盘，输入框在这个弹层里只会抢焦点。 */
@Composable
private fun RangeRow(
    title: String,
    value: Int,
    chapterCount: Int,
    onValueChange: (Int) -> Unit,
) {
    TinySettingItem(
        title = title,
        color = LegadoTheme.colorScheme.surfaceContainerLow,
        trailingContent = {
            ValueStepper(
                value = value.toFloat(),
                displayValue = value.toFloat(),
                valueRange = 1f..chapterCount.toFloat(),
                onValueChange = { onValueChange(it.roundToInt().coerceIn(1, chapterCount)) },
            )
        },
    )
}

/** 进度卡片：章名一行，章 / 句两条进度各自一行，失败句数只在有时才出现。 */
@Composable
private fun DownloadProgressCard(
    progress: ReadAloudAudioStore.Progress,
    running: Boolean,
) {
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
        contentColor = LegadoTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (progress.currentChapter.isNotBlank()) {
                AppText(
                    text = progress.currentChapter,
                    style = LegadoTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProgressLine(
                label = stringResource(R.string.read_aloud_audio_download_chapter_bar),
                text = "${progress.chapterDone}/${progress.chapterTotal}",
                fraction = progress.chapterFraction,
                running = running,
            )
            ProgressLine(
                label = stringResource(R.string.read_aloud_audio_download_sentence_bar),
                text = "${progress.sentenceDone}/${progress.sentenceTotal}",
                fraction = progress.sentenceFraction,
                running = running,
            )
            if (progress.failed > 0) {
                // 失败的那几句不落进下载区：宁可少下，也不能下一句永远播空白的占位
                AppText(
                    text = stringResource(
                        R.string.read_aloud_audio_download_failed_count,
                        progress.failed,
                    ),
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 一行进度：标签 + 计数在上，进度条在下，长章名不会把数字挤走。 */
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
            )
        }
        AppLinearProgressIndicator(
            // 一句都还没合成完时给不确定进度：有进度条在动，用户才知道不是卡住了
            progress = if (running && fraction <= 0f) null else fraction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        )
    }
}

/** 已下载的一章：标题 + 章号/句数 + 删除。当前章点亮，长列表里一眼找到自己在读的那章。 */
@Composable
private fun DownloadedRow(
    row: ReadAloudAudioDownload,
    isCurrent: Boolean,
    onDelete: () -> Unit,
) {
    val resources = LocalResources.current
    TinySettingItem(
        title = row.title,
        description = resources.getString(R.string.chapter_index_format, row.chapterIndex + 1) +
                " · " + resources.getString(
            R.string.read_aloud_audio_download_sentences_count,
            row.sentenceCount,
        ),
        color = if (isCurrent) {
            LegadoTheme.colorScheme.primaryContainer
        } else {
            LegadoTheme.colorScheme.surfaceContainerLow
        },
        trailingContent = {
            MediumTonalButton(
                onClick = onDelete,
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.read_aloud_audio_download_delete),
            )
        },
    )
}
