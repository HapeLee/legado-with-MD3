package io.legado.app.ui.widget.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

const val START_PAGE_OFFSET = 100

/**
 * @param modifier 外部修饰符
 * @param initialDate 初始显示的月份日期
 * @param onDateSelected 日期被选中时的回调
 */
@Composable
fun Calendar(
    modifier: Modifier = Modifier,
    initialDate: LocalDate = LocalDate.now(),
    onDateSelected: (LocalDate) -> Unit,
    selectedDate: LocalDate? = null,
    onClearDate: (() -> Unit)? = null
) {
    val baseMonth = remember(selectedDate) {
        selectedDate?.withDayOfMonth(1) ?: initialDate.withDayOfMonth(1)
    }

    val pagerState = rememberPagerState(initialPage = START_PAGE_OFFSET) {
        START_PAGE_OFFSET * 2
    }

    val coroutineScope = rememberCoroutineScope()

    val currentMonth by remember {
        derivedStateOf {
            val pageOffset = pagerState.currentPage - START_PAGE_OFFSET
            baseMonth.plusMonths(pageOffset.toLong())
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp).animateContentSize()) {
            MonthNavigation(
                currentMonth = currentMonth,
                selectedDate = selectedDate,
                onPreviousClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onNextClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                onClearClick = onClearDate
            )

            Spacer(modifier = Modifier.height(8.dp))

            DayOfWeekHeader()

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalAlignment = Alignment.Top
            ) { page ->
                val pageOffset = page - START_PAGE_OFFSET
                val monthForPage = baseMonth.plusMonths(pageOffset.toLong())

                MonthPageContent(
                    month = monthForPage,
                    selectedDate = selectedDate,
                    onDateClick = onDateSelected
                )
            }
        }
    }
}

@Composable
fun MonthPageContent(
    month: LocalDate,
    selectedDate: LocalDate?,
    onDateClick: (LocalDate) -> Unit
) {
    val datesInMonth = getDatesInMonth(month)
    val firstDayOfWeek = month.dayOfWeek.value % 7
    val offset = firstDayOfWeek

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier
            .fillMaxWidth(),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        items(offset) {
            Spacer(modifier = Modifier.size(36.dp))
        }

        items(datesInMonth) { date ->
            DateItem(
                date = date,
                isSelected = date == selectedDate,
                isToday = date == LocalDate.now(),
                isCurrentMonth = date.month == month.month,
                onClick = { onDateClick(date) }
            )
        }
    }
}

@Composable
fun MonthNavigation(
    currentMonth: LocalDate,
    selectedDate: LocalDate?,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onClearClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = currentMonth.year.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedTextLine(
                text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {

            if (selectedDate != null && onClearClick != null) {
                FilledTonalIconButton(onClick = onClearClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Date Selection",
                    )
                }
            }

            IconButton(onClick = onPreviousClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = "Previous Month",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onNextClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowForward,
                    contentDescription = "Next Month",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DayOfWeekHeader() {
    val dayNames = listOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
    ).map {
        it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        dayNames.forEach { dayName ->
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
@Composable
fun DateItem(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    isCurrentMonth: Boolean,
    onClick: () -> Unit
) {
    val buttonColors = when {
        isSelected -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
        isCurrentMonth -> ButtonDefaults.textButtonColors(
            contentColor = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        else -> ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }

    val borderStroke = if (isToday && !isSelected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Button(
        onClick = onClick,
        modifier = Modifier.size(36.dp).padding(4.dp),
        shape = CircleShape,
        colors = buttonColors,
        border = borderStroke,
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        )
    }
}

fun getDatesInMonth(month: LocalDate): List<LocalDate> {
    val firstDayOfMonth = month.withDayOfMonth(1)
    val lengthOfMonth = month.lengthOfMonth()
    return (0 until lengthOfMonth).map { firstDayOfMonth.plusDays(it.toLong()) }
}