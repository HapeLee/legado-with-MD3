package io.legado.app.ui.widget.components.heatmap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.legado.app.ui.theme.LegadoTheme
import java.time.LocalDate

/** 热力图等级上限：0 表示无记录，[HEATMAP_MAX_LEVEL] 表示当日数据最多。 */
const val HEATMAP_MAX_LEVEL = 4

/**
 * 各等级叠加在容器背景上的 primary 透明度。
 * 用透明度叠加而不是固定色值，热力图才会跟随主题 primary，
 * 并在浅色/深色、玻璃卡片等不同背景下保持单一色调递进。
 */
private val HEATMAP_LEVEL_ALPHAS = floatArrayOf(0.35f, 0.55f, 0.75f, 1f)

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
 * 热力图刻度：把每日数据映射到 0..[HEATMAP_MAX_LEVEL]。
 * 上限在数据变化时只算一次，单元格共享同一刻度，避免每格各自遍历整张表。
 */
@Immutable
data class HeatmapScale(
    val maxCount: Int,
    val maxTime: Long,
) {
    fun levelOf(
        day: LocalDate,
        mode: HeatmapMode,
        dailyReadCounts: Map<LocalDate, Int>,
        dailyReadTimes: Map<LocalDate, Long>
    ): Int {
        val fraction = if (mode == HeatmapMode.COUNT) {
            val value = dailyReadCounts[day] ?: 0
            value.toFloat() / maxCount
        } else {
            val value = dailyReadTimes[day] ?: 0L
            value.toFloat() / maxTime
        }

        return when {
            fraction <= 0f -> 0
            fraction < 0.25f -> 1
            fraction < 0.5f -> 2
            fraction < 0.75f -> 3
            else -> HEATMAP_MAX_LEVEL
        }
    }
}

@Composable
fun rememberHeatmapScale(
    dailyReadCounts: Map<LocalDate, Int>,
    dailyReadTimes: Map<LocalDate, Long>
): HeatmapScale {
    return remember(dailyReadCounts, dailyReadTimes) {
        HeatmapScale(
            maxCount = dailyReadCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1,
            maxTime = dailyReadTimes.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        )
    }
}

/**
 * 根据等级获取对应的颜色。
 * 0 级使用主题 onSurface 的低透明度作为空态底色，保证在弹窗、玻璃卡片等
 * 不同容器上都能看清网格；1..[HEATMAP_MAX_LEVEL] 全部由主题 primary 派生。
 */
@Composable
fun heatmapColorForLevel(
    level: Int,
    primary: Color = LegadoTheme.colorScheme.primary,
    emptyColor: Color = LegadoTheme.colorScheme.onSurface.copy(alpha = 0.08f)
): Color {
    if (level <= 0) return emptyColor
    val alpha = HEATMAP_LEVEL_ALPHAS[(level - 1).coerceAtMost(HEATMAP_LEVEL_ALPHAS.lastIndex)]
    return if (alpha >= 1f) primary else primary.copy(alpha = alpha)
}