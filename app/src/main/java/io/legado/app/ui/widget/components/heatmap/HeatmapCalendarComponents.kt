package io.legado.app.ui.widget.components.heatmap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.widget.components.button.AnimatedActionButton
import java.time.LocalDate

/**
 * 热力图日历顶部操作栏
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeatmapCalendarTopBar(
    currentMode: HeatmapMode,
    onModeChanged: (HeatmapMode) -> Unit,
    onClearDate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("时间线", style = MaterialTheme.typography.titleMedium)

        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedActionButton(
                checked = currentMode == HeatmapMode.TIME,
                onCheckedChange = {
                    onModeChanged(if (it) HeatmapMode.TIME else HeatmapMode.COUNT)
                },
                iconChecked = Icons.Default.AccessTime,
                iconUnchecked = Icons.Default.FormatListNumbered,
                activeText = "按时长",
                inactiveText = "按次数"
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedIconButton(
                onClick = onClearDate,
                shapes = IconButtonDefaults.shapes(),
                border = ButtonDefaults.outlinedButtonBorder()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "清除选择"
                )
            }
        }
    }
}

/**
 * 星期标签列（周一到周日）
 */
@Composable
fun WeekdayLabelsColumn(
    cellSize: Dp,
    cellSpacing: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(top = 20.dp, end = 8.dp)
    ) {
        val labels = listOf("一", "二", "三", "四", "五", "六", "日")

        labels.forEachIndexed { index, label ->
            if (index % 2 == 0) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(cellSize)
                )
            } else {
                Spacer(modifier = Modifier.height(cellSize))
            }

            if (index < 6) Spacer(modifier = Modifier.height(cellSpacing))
        }
    }
}

/**
 * 没有更早数据的提示组件
 */
@Composable
fun NoEarlierDataIndicator(
    cellSize: Dp,
    modifier: Modifier = Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(top = 24.dp, start = 8.dp, end = 16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(7) {
                Box(
                    modifier = Modifier
                        .size(cellSize)
                        .drawBehind {
                            val strokeWidth = 1.dp.toPx()
                            val stroke = Stroke(
                                width = strokeWidth,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                            )
                            val inset = strokeWidth / 2
                            drawRoundRect(
                                color = outlineColor,
                                style = stroke,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            "没有更早数据".forEach { char ->
                Text(
                    text = char.toString(),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

/**
 * 单个日历单元格
 */
@Composable
fun HeatmapCalendarCell(
    day: LocalDate,
    mode: HeatmapMode,
    dailyReadCounts: Map<LocalDate, Int>,
    dailyReadTimes: Map<LocalDate, Long>,
    isSelected: Boolean,
    config: HeatmapConfig,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val level = rememberHeatmapLevel(day, mode, dailyReadCounts, dailyReadTimes)
    val cellColor = heatmapColorForLevel(level)

    Box(
        modifier = modifier
            .size(config.cellSize)
            .clip(RoundedCornerShape(config.cornerRadius))
            .background(cellColor)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = RoundedCornerShape(config.cornerRadius)
            )
            .clickable { onDateSelected(day) }
    )
}

/**
 * 一周的日历列（包含月份标签）
 */
@Composable
fun HeatmapWeekColumn(
    week: List<LocalDate?>,
    mode: HeatmapMode,
    dailyReadCounts: Map<LocalDate, Int>,
    dailyReadTimes: Map<LocalDate, Long>,
    selectedDate: LocalDate?,
    config: HeatmapConfig,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstDayOfMonth = week.firstOrNull { it?.dayOfMonth == 1 }

    Box(modifier = modifier.width(config.cellSize)) {
        Column(
            modifier = Modifier.padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(config.cellSpacing)
        ) {
            week.forEach { day ->
                if (day == null) {
                    Spacer(modifier = Modifier.size(config.cellSize))
                } else {
                    HeatmapCalendarCell(
                        day = day,
                        mode = mode,
                        dailyReadCounts = dailyReadCounts,
                        dailyReadTimes = dailyReadTimes,
                        isSelected = day == selectedDate,
                        config = config,
                        onDateSelected = onDateSelected
                    )
                }
            }
        }

        // 月份标签
        if (firstDayOfMonth != null) {
            Text(
                text = "${firstDayOfMonth.monthValue}月",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .wrapContentWidth(unbounded = true)
            )
        }
    }
}

/**
 * 热力图图例
 */
@Composable
fun HeatmapLegend(
    mode: HeatmapMode,
    config: HeatmapConfig,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val legendUnit = if (mode == HeatmapMode.COUNT) "次" else "长"

        Text(
            "少($legendUnit)",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.width(4.dp))

        for (level in 0..4) {
            Box(
                modifier = Modifier
                    .size(config.legendSize)
                    .clip(RoundedCornerShape(2.dp))
                    .background(heatmapColorForLevel(level))
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        Text(
            "多($legendUnit)",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}