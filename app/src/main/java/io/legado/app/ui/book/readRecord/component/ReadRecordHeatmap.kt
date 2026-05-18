package io.legado.app.ui.book.readRecord.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.heatmap.*
import io.legado.app.ui.widget.components.text.AppText
import java.time.LocalDate

@Composable
fun HeatmapCalendarSection(
    modifier: Modifier = Modifier,
    dailyReadCounts: Map<LocalDate, Int>,
    dailyReadTimes: Map<LocalDate, Long>,
    currentMode: HeatmapMode,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    config: HeatmapConfig = HeatmapConfig()
) {
    val (startDate, endDate) = rememberDateRange(dailyReadCounts, dailyReadTimes)
    val days = rememberDaysInRange(startDate, endDate)
    val weeks = rememberWeeks(days, startDate)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = HEATMAP_CALENDAR_TITLE,
                style = LegadoTheme.typography.titleSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            HeatmapLegend(mode = currentMode, config = config)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            WeekdayLabelsColumn(
                cellSize = config.cellSize,
                cellSpacing = config.cellSpacing
            )
            Spacer(modifier = Modifier.width(4.dp))
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(config.cellSpacing),
                reverseLayout = true
            ) {
                item {
                    HeatmapCalendarEndAction(
                        onClearDate = { onDateSelected(LocalDate.now()) }
                    )
                }
                items(weeks) { week ->
                    HeatmapWeekColumn(
                        week = week,
                        mode = currentMode,
                        dailyReadCounts = dailyReadCounts,
                        dailyReadTimes = dailyReadTimes,
                        selectedDate = selectedDate,
                        config = config,
                        onDateSelected = onDateSelected
                    )
                }
                item {
                    HeatmapCalendarStartAction(
                        currentMode = currentMode,
                        onModeChanged = {}
                    )
                }
                item { NoEarlierDataIndicator(cellSize = config.cellSize) }
            }
        }
    }
}
