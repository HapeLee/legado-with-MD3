package io.legado.app.ui.book.readaloud

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.model.settings.ReadAloudTimerMode
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import kotlin.math.roundToInt

/**
 * 朗读定时设置（宿主无关）。
 *
 * 经典控制面板与听书播放界面共用这一张卡片，所以只接受原始值 + 回调，
 * 不依赖任一宿主的 UiState/Intent：两边各自把状态与意图映射进来，
 * 否则只能复制一份表单，两种定时模式互斥的规则迟早只在一边生效。
 */
data class ReadAloudTimerConfig(
    val mode: ReadAloudTimerMode,
    val minutes: Int,
    val chapters: Int,
    /** 分钟模式到点后是否读完本章再停；章节模式不需要它。 */
    val finishCurrentChapterAfterTimer: Boolean = false,
)

@Composable
fun ReadAloudTimerSheet(
    show: Boolean,
    config: ReadAloudTimerConfig,
    onDismissRequest: () -> Unit,
    onSetMode: (ReadAloudTimerMode) -> Unit,
    onSetMinutes: (Int) -> Unit,
    onSetChapters: (Int) -> Unit,
    onSetFinishCurrentChapterAfterTimer: (Boolean) -> Unit,
) {
    var minutesPreview by remember(config.minutes) {
        mutableFloatStateOf(config.minutes.toFloat())
    }
    var chaptersPreview by remember(config.chapters) {
        mutableFloatStateOf(config.chapters.toFloat())
    }
    // valueFormat 不是 @Composable 上下文，用 context 取文案（stringResource 在此不可用）
    val closeLabel = stringResource(R.string.close)
    val resources = LocalResources.current
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.set_timer),
    ) {
        // 两种定时互斥，切页签立即写入，不留「选了模式但没设值」的中间态
        CardTabRow(
            tabTitles = listOf(
                stringResource(R.string.read_aloud_timer_by_minute),
                stringResource(R.string.read_aloud_timer_by_chapter),
            ),
            selectedTabIndex = if (config.mode == ReadAloudTimerMode.Chapter) 1 else 0,
            onTabSelected = { index ->
                onSetMode(
                    if (index == 1) ReadAloudTimerMode.Chapter else ReadAloudTimerMode.Minute
                )
            },
        )
        Spacer(modifier = Modifier.padding(top = 12.dp))
        if (config.mode == ReadAloudTimerMode.Chapter) {
            val chapterPresets = listOf(0, 1, 2, 3, 5, 8, 10, 15, 20)
            val chapterDisplayEntries = chapterPresets.map { ch ->
                if (ch == 0) closeLabel
                else resources.getString(R.string.timer_chapters, ch)
            }.toTypedArray()
            val chapterEntryValues = chapterPresets.map { it.toString() }.toTypedArray()
            val currentChapters = chaptersPreview.roundToInt()
            val currentChaptersDisplay = if (currentChapters == 0) closeLabel
            else resources.getString(R.string.timer_chapters, currentChapters)

            TinyDropdownSettingItem(
                title = stringResource(R.string.timer_preset_duration),
                selectedValue = currentChapters.toString(),
                selectedDisplay = currentChaptersDisplay,
                displayEntries = chapterDisplayEntries,
                entryValues = chapterEntryValues,
                modifier = Modifier.padding(bottom = 4.dp),
                color = LegadoTheme.colorScheme.surfaceContainerHigh,
                onValueChange = { valueStr ->
                    val chapters = valueStr.toIntOrNull() ?: 0
                    chaptersPreview = chapters.toFloat()
                    onSetChapters(chapters)
                },
            )
            TinySliderSettingItem(
                title = stringResource(R.string.read_aloud_timer_by_chapter),
                description = stringResource(R.string.read_aloud_timer_chapter_summary),
                value = chaptersPreview.coerceIn(0f, PlaybackTimer.MAX_CHAPTERS.toFloat()),
                valueRange = 0f..PlaybackTimer.MAX_CHAPTERS.toFloat(),
                // 一章一档；0 保留给「关闭」
                steps = PlaybackTimer.MAX_CHAPTERS - 1,
                stepSize = 1f,
                valueFormat = { value ->
                    if (value == 0f) closeLabel
                    else resources.getString(R.string.timer_chapters, value.roundToInt())
                },
                // 拖动中只更新本地预览、松手才写设置：拖动中写会与外部 value 回流打架，滑块会跳
                onValueChange = { chaptersPreview = it },
                onValueChangeFinished = { onSetChapters(chaptersPreview.roundToInt()) },
            )
        } else {
            val minutePresets = listOf(0, 10, 15, 20, 25, 30, 45, 60, 90, 120, 180)
            val minuteDisplayEntries = minutePresets.map { min ->
                if (min == 0) closeLabel
                else resources.getString(R.string.timer_m, min)
            }.toTypedArray()
            val minuteEntryValues = minutePresets.map { it.toString() }.toTypedArray()
            val currentMinutes = minutesPreview.roundToInt()
            val currentMinutesDisplay = if (currentMinutes == 0) closeLabel
            else resources.getString(R.string.timer_m, currentMinutes)

            TinyDropdownSettingItem(
                title = stringResource(R.string.timer_preset_duration),
                selectedValue = currentMinutes.toString(),
                selectedDisplay = currentMinutesDisplay,
                displayEntries = minuteDisplayEntries,
                entryValues = minuteEntryValues,
                modifier = Modifier.padding(bottom = 4.dp),
                color = LegadoTheme.colorScheme.surfaceContainerHigh,
                onValueChange = { valueStr ->
                    val minutes = valueStr.toIntOrNull() ?: 0
                    minutesPreview = minutes.toFloat()
                    onSetMinutes(minutes)
                },
            )
            TinySliderSettingItem(
                title = stringResource(R.string.set_timer),
                description = stringResource(R.string.timer_m, PlaybackTimer.MAX_MINUTES),
                value = minutesPreview.coerceIn(0f, PlaybackTimer.MAX_MINUTES.toFloat()),
                valueRange = 0f..PlaybackTimer.MAX_MINUTES.toFloat(),
                // 一分钟一档；0 保留给「关闭」
                steps = PlaybackTimer.MAX_MINUTES - 1,
                stepSize = 1f,
                valueFormat = { value ->
                    if (value == 0f) closeLabel
                    else resources.getString(R.string.timer_m, value.roundToInt())
                },
                onValueChange = { minutesPreview = it },
                onValueChangeFinished = { onSetMinutes(minutesPreview.roundToInt()) },
            )
            // 「读完本章再停」是分钟模式的修饰项：章节模式本身就是停在章末，这里不显示
            TinySwitchSettingItem(
                title = stringResource(R.string.finish_current_chapter_after_timer),
                description = stringResource(R.string.finish_current_chapter_after_timer_summary),
                checked = config.finishCurrentChapterAfterTimer,
                modifier = Modifier.padding(vertical = 4.dp),
                color = LegadoTheme.colorScheme.surfaceContainerHigh,
                onCheckedChange = onSetFinishCurrentChapterAfterTimer,
            )
        }
    }
}
