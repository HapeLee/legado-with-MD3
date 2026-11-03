package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.book.BookHelp
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadBook
import io.legado.app.model.cache.CacheBookDownloadState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ToggleChip
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * 阅读页「离线缓存」范围选择。
 *
 * 用户看到的是与目录一致的 1 基章号，下载队列使用 0 基章节 index，提交时统一换算。
 * 历史实现直接把输入当 index 用，且默认结束章填 `totalChapterNum`（越界一章），这里一并修正。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadSheet(
    onDismissRequest: () -> Unit,
    onDownload: (start: Int, end: Int) -> Unit,
) {
    val book = ReadBook.book ?: return
    val totalChapters = book.totalChapterNum.coerceAtLeast(0)
    val currentIndex = book.durChapterIndex

    fun chapterNumberText(chapterNumber: Int): String =
        chapterNumber.coerceIn(1, max(totalChapters, 1)).toString()

    var startText by remember(book.bookUrl) {
        mutableStateOf(chapterNumberText(currentIndex + 1))
    }
    var endText by remember(book.bookUrl) {
        mutableStateOf(chapterNumberText(totalChapters))
    }

    val startChapter = startText.toIntOrNull()
    val endChapter = endText.toIntOrNull()

    // 逐章判断正文文件是否已存在，必须离开主线程；弹窗只展示概要，不阻塞确认。
    val cachedCount by produceState<Int?>(initialValue = null, book.bookUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching { BookHelp.countCachedChapters(book) }.getOrNull()
        }
    }
    val downloadState by CacheBook.downloadStateFlow.collectAsState()
    val bookTaskState = downloadState.books[book.bookUrl]

    // 输入框清空时保持安静（只是不能确认），越界或倒序才报错。
    val startInvalid = startChapter != null && startChapter !in 1..totalChapters
    val endInvalid = endChapter != null && endChapter !in 1..totalChapters
    val orderInvalid = startChapter != null && endChapter != null && startChapter > endChapter
    val rangeError = when {
        totalChapters <= 0 -> stringResource(R.string.cache_range_empty_toc)
        startInvalid || endInvalid -> stringResource(R.string.cache_range_out_of_range, totalChapters)
        orderInvalid -> stringResource(R.string.cache_range_order_error)
        else -> null
    }
    val rangeSize = if (rangeError == null && startChapter != null && endChapter != null) {
        endChapter - startChapter + 1
    } else {
        null
    }

    AppAlertDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.offline_cache),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CachedSummary(
                    cachedCount = cachedCount,
                    totalChapters = totalChapters,
                )
                Spacer(modifier = Modifier.height(16.dp))
                AppText(
                    text = stringResource(R.string.cache_range_title),
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ToggleChip(
                        label = stringResource(R.string.cache_range_after_current),
                        selected = startChapter == currentIndex + 2 && endChapter == totalChapters,
                        enabled = totalChapters > 0 && currentIndex + 2 <= totalChapters,
                        onToggle = {
                            startText = chapterNumberText(currentIndex + 2)
                            endText = chapterNumberText(totalChapters)
                        },
                    )
                    ToggleChip(
                        label = stringResource(R.string.cache_range_from_current),
                        selected = startChapter == currentIndex + 1 && endChapter == totalChapters,
                        enabled = totalChapters > 0,
                        onToggle = {
                            startText = chapterNumberText(currentIndex + 1)
                            endText = chapterNumberText(totalChapters)
                        },
                    )
                    ToggleChip(
                        label = stringResource(R.string.cache_range_all),
                        selected = startChapter == 1 && endChapter == totalChapters,
                        enabled = totalChapters > 0,
                        onToggle = {
                            startText = chapterNumberText(1)
                            endText = chapterNumberText(totalChapters)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = startText,
                        onValueChange = { startText = it.digitsOnly() },
                        label = stringResource(R.string.start_chapter),
                        singleLine = true,
                        isError = startInvalid || orderInvalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppTextField(
                        value = endText,
                        onValueChange = { endText = it.digitsOnly() },
                        label = stringResource(R.string.end_chapter),
                        singleLine = true,
                        isError = endInvalid || orderInvalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                // Miuix 引擎不展示 isError/supportingText，提示统一画在外面。
                AppText(
                    text = rangeError
                        ?: rangeSize?.let { stringResource(R.string.cache_range_summary, it) }
                        ?: "",
                    modifier = Modifier.padding(top = 8.dp),
                    style = LegadoTheme.typography.bodySmall,
                    color = if (rangeError != null) {
                        LegadoTheme.colorScheme.error
                    } else {
                        LegadoTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (bookTaskState != null && bookTaskState.hasUnfinishedWork()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AppText(
                        text = stringResource(
                            R.string.cache_task_running,
                            bookTaskState.waitingCount,
                            bookTaskState.runningIndices.size,
                            bookTaskState.pausedIndices.size,
                            bookTaskState.failedIndices.size,
                        ),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmText = stringResource(R.string.start_cache),
        confirmEnabled = rangeError == null && startChapter != null && endChapter != null,
        onConfirm = {
            val start = startChapter
            val end = endChapter
            if (start != null && end != null) {
                onDownload(start - 1, end - 1)
            }
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismissRequest,
    )
}

@Composable
private fun CachedSummary(cachedCount: Int?, totalChapters: Int) {
    val text = when {
        totalChapters <= 0 -> stringResource(R.string.cache_range_empty_toc)
        cachedCount == null -> stringResource(R.string.cache_progress_loading)
        else -> stringResource(R.string.cache_progress_summary, cachedCount, totalChapters)
    }
    AppText(
        text = text,
        style = LegadoTheme.typography.bodyMedium,
        color = LegadoTheme.colorScheme.onSurface,
    )
    if (cachedCount != null && totalChapters > 0) {
        AppLinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            progress = (cachedCount.toFloat() / totalChapters).coerceIn(0f, 1f),
        )
    }
}

private fun String.digitsOnly(): String = filter { it.isDigit() }.take(7)

private fun CacheBookDownloadState.hasUnfinishedWork(): Boolean =
    waitingCount > 0 || runningIndices.isNotEmpty() ||
            pausedIndices.isNotEmpty() || failedIndices.isNotEmpty()
