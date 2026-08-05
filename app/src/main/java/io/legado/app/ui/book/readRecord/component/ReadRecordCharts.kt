package io.legado.app.ui.book.readRecord.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.book.readRecord.ReadPeriod
import io.legado.app.ui.book.readRecord.ReadRecordFormatter
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.text.AppText
import java.time.LocalDate

@Composable
fun ReadingTimeBarChartCard(
    data: List<Pair<LocalDate, Long>>,
    period: ReadPeriod,
    modifier: Modifier = Modifier
) {
    val rawMaxTime = data.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L

    // 向上取整逻辑：根据时长跨度选择合适的对齐单位
    val roundedMaxTime = when {
        rawMaxTime < 60_000 -> 60_000L // 不足1分钟取1分钟
        rawMaxTime < 10 * 60_000 -> ((rawMaxTime + 59_999) / 60_000) * 60_000L // 10分钟内按1分钟对齐
        rawMaxTime < 60 * 60_000 -> ((rawMaxTime + 5 * 60_000 - 1) / (5 * 60_000)) * 5 * 60_000L // 1小时内按5分钟对齐
        rawMaxTime < 12 * 3600_000 -> ((rawMaxTime + 3600_000 - 1) / 3600_000) * 3600_000L // 12小时内按1小时对齐
        else -> ((rawMaxTime + 4 * 3600_000 - 1) / (4 * 3600_000)) * 4 * 3600_000L // 超过12小时按4小时对齐
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .adaptiveHorizontalPadding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AppText("阅读时长分布", style = LegadoTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                // Y-Axis
                Column(
                    modifier = Modifier
                        .width(32.dp)
                        .fillMaxHeight()
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    AppText(
                        text = ReadRecordFormatter.formatDuration(roundedMaxTime),
                        style = LegadoTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                    AppText(
                        text = "0",
                        style = LegadoTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Chart
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    data.forEachIndexed { index, (date, time) ->
                        val targetHeightFactor = time.toFloat() / roundedMaxTime
                        val heightFactor by animateFloatAsState(
                            targetValue = targetHeightFactor,
                            animationSpec = tween(durationMillis = 320, delayMillis = index * 20),
                            label = "BarHeight"
                        )

                        val showLabel = when (period) {
                            ReadPeriod.DAY -> true
                            ReadPeriod.WEEK -> true
                            ReadPeriod.MONTH -> date.dayOfMonth == 1 || date.dayOfMonth == 15 || index == data.lastIndex
                            ReadPeriod.YEAR -> true
                            else -> false
                        }

                        val labelText = when (period) {
                            ReadPeriod.YEAR -> "${date.monthValue}月"
                            ReadPeriod.WEEK -> when (date.dayOfWeek.value) {
                                1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; 7 -> "日"; else -> ""
                            }
                            else -> date.dayOfMonth.toString()
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(max = 16.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(if (period == ReadPeriod.MONTH) 0.8f else 0.6f)
                                        .fillMaxHeight(heightFactor.coerceAtLeast(0.01f))
                                        .padding(horizontal = 1.dp)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(
                                            if (time > 0) LegadoTheme.colorScheme.primary
                                            else LegadoTheme.colorScheme.surfaceVariant
                                        )
                                )
                            }

                            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.TopCenter) {
                                if (showLabel) {
                                    AppText(
                                        text = labelText,
                                        style = LegadoTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible,
                                        modifier = Modifier.wrapContentWidth(unbounded = true)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
