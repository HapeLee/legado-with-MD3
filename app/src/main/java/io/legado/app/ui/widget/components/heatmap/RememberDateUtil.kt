package io.legado.app.ui.widget.components.heatmap

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import java.time.LocalDate

/**
 * 计算日期范围：从最早有数据的日期前1个月到最晚有数据的日期后1个月
 */
@Composable
fun rememberDateRange(
    dailyReadCounts: Map<LocalDate, Int>,
    dailyReadTimes: Map<LocalDate, Long>
): Pair<LocalDate, LocalDate> {
    return remember(dailyReadCounts, dailyReadTimes) {
        val firstReadDate = listOfNotNull(
            dailyReadCounts.filterValues { it > 0 }.keys.minOrNull(),
            dailyReadTimes.filterValues { it > 0L }.keys.minOrNull()
        ).minOrNull()

        val lastReadDate = listOfNotNull(
            dailyReadCounts.filterValues { it > 0 }.keys.maxOrNull(),
            dailyReadTimes.filterValues { it > 0L }.keys.maxOrNull()
        ).maxOrNull()

        val startDate = firstReadDate?.minusMonths(1) ?: LocalDate.now()
        val endDate = lastReadDate ?: startDate

        startDate to endDate
    }
}

/**
 * 生成日期范围内的所有日期列表
 */
@Composable
fun rememberDaysInRange(startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
    return remember(startDate, endDate) {
        val list = mutableListOf<LocalDate>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            list.add(current)
            current = current.plusDays(1)
        }
        list
    }
}

/**
 * 将日期列表按周分组（补全第一周的空白天数）
 */
@Composable
fun rememberWeeks(days: List<LocalDate>, startDate: LocalDate): List<List<LocalDate?>> {
    return remember(days, startDate) {
        val firstDayOfWeekOffset = startDate.dayOfWeek.value - 1 // 周一为1
        val padded = List(firstDayOfWeekOffset) { null } + days
        padded.chunked(7)
    }
}

/**
 * 计算日期对应的热力图等级（0-4）
 */
@Composable
fun rememberHeatmapLevel(
    day: LocalDate,
    mode: HeatmapMode,
    dailyReadCounts: Map<LocalDate, Int>,
    dailyReadTimes: Map<LocalDate, Long>
): Int {
    val maxCount by remember(dailyReadCounts) {
        derivedStateOf { dailyReadCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1 }
    }
    val maxTime by remember(dailyReadTimes) {
        derivedStateOf { dailyReadTimes.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L }
    }

    return remember(day, mode, maxCount, maxTime) {
        val fraction = if (mode == HeatmapMode.COUNT) {
            val value = dailyReadCounts[day] ?: 0
            value.toFloat() / maxCount
        } else {
            val value = dailyReadTimes[day] ?: 0L
            value.toFloat() / maxTime
        }

        when {
            fraction == 0f -> 0
            fraction < 0.25f -> 1
            fraction < 0.5f -> 2
            fraction < 0.75f -> 3
            else -> 4
        }
    }
}

/**
 * 根据等级获取对应的颜色
 */
@Composable
fun heatmapColorForLevel(
    level: Int,
    primary: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = MaterialTheme.colorScheme.surfaceVariant
): Color {
    return when (level) {
        0 -> emptyColor
        1 -> primary.copy(alpha = 0.35f)
        2 -> primary.copy(alpha = 0.55f)
        3 -> primary.copy(alpha = 0.75f)
        else -> primary
    }
}