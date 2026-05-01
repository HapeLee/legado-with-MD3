package io.legado.app.ui.book.readRecord

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cn.hutool.core.date.DateUtil
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPaddingOnlyVertical
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.CollapsibleHeader
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.AppIconButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.cover.Cover
import io.legado.app.ui.widget.components.heatmap.HEATMAP_CALENDAR_TITLE
import io.legado.app.ui.widget.components.heatmap.HeatmapCalendarEndAction
import io.legado.app.ui.widget.components.heatmap.HeatmapCalendarStartAction
import io.legado.app.ui.widget.components.heatmap.HeatmapConfig
import io.legado.app.ui.widget.components.heatmap.HeatmapLegend
import io.legado.app.ui.widget.components.heatmap.HeatmapMode
import io.legado.app.ui.widget.components.heatmap.HeatmapWeekColumn
import io.legado.app.ui.widget.components.heatmap.NoEarlierDataIndicator
import io.legado.app.ui.widget.components.heatmap.WeekdayLabelsColumn
import io.legado.app.ui.widget.components.heatmap.rememberDateRange
import io.legado.app.ui.widget.components.heatmap.rememberDaysInRange
import io.legado.app.ui.widget.components.heatmap.rememberWeeks
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.swipe.SwipeAction
import io.legado.app.ui.widget.components.swipe.SwipeActionContainer
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.utils.formatReadDuration
import io.legado.app.utils.StringUtils.formatFriendlyDate
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReadRecordScreen(
    viewModel: ReadRecordViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onBookClick: (String, String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val state by viewModel.uiState.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var heatmapMode by remember { mutableStateOf(HeatmapMode.COUNT) }
    val listState = rememberLazyListState()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    var skipDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var mergeDialogData by remember { mutableStateOf<Pair<ReadRecord, List<ReadRecord>>?>(null) }
    val onConfirmDelete: (() -> Unit) -> Unit = { action ->
        if (skipDeleteConfirm) {
            action()
        } else {
            pendingDeleteAction = action
        }
    }
    val stickyDate by remember(displayMode, listState) {
        derivedStateOf {
            if (displayMode == DisplayMode.LATEST) return@derivedStateOf null
            val stickyKey = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { info ->
                    val key = info.key.toString()
                    key.startsWith("header_") ||
                        key.startsWith("timeline_header_") ||
                        key.startsWith("agg_item_") ||
                        key.startsWith("timeline_item_")
                }?.key?.toString() ?: return@derivedStateOf null

            when {
                stickyKey.startsWith("header_") -> stickyKey.removePrefix("header_")
                stickyKey.startsWith("timeline_header_") -> stickyKey.removePrefix("timeline_header_")
                stickyKey.startsWith("agg_item_") -> stickyKey.removePrefix("agg_item_").substringBefore("|")
                stickyKey.startsWith("timeline_item_") -> stickyKey.removePrefix("timeline_item_").substringBefore("|")
                else -> null
            }
        }
    }
    val floatingDate by remember(stickyDate, listState, displayMode) {
        derivedStateOf {
            if (displayMode == DisplayMode.LATEST) return@derivedStateOf null
            if (stickyDate == null) return@derivedStateOf null
            val shouldStick = listState.firstVisibleItemIndex > 1 ||
                listState.firstVisibleItemScrollOffset > 24
            if (shouldStick) stickyDate else null
        }
    }

    LaunchedEffect(state.searchKey) {
        if (state.searchKey.isNullOrBlank()) {
            listState.animateScrollToItem(0)
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                GlassMediumFlexibleTopAppBar(
                    title = "阅读记录",
                    subtitle = run {
                        val subTitle = when (displayMode) {
                            DisplayMode.AGGREGATE -> "汇总视图"
                            DisplayMode.TIMELINE -> "时间线视图"
                            DisplayMode.LATEST -> "最后阅读"
                        }
                        subTitle
                    },
                    navigationIcon = {
                        TopBarNavigationButton(onClick = onBackClick)
                    },
                    actions = {
                        AppIconButton(onClick = {
                            val newMode = when (displayMode) {
                                DisplayMode.AGGREGATE -> DisplayMode.TIMELINE
                                DisplayMode.TIMELINE -> DisplayMode.LATEST
                                DisplayMode.LATEST -> DisplayMode.AGGREGATE
                            }
                            viewModel.setDisplayMode(newMode)
                        }) {
                            val icon = when (displayMode) {
                                DisplayMode.AGGREGATE -> Icons.Default.Timeline
                                DisplayMode.TIMELINE -> Icons.Default.Schedule
                                DisplayMode.LATEST -> Icons.AutoMirrored.Filled.List
                            }
                            val description = if (displayMode == DisplayMode.AGGREGATE) "Switch to Timeline" else "Switch to Aggregate"
                            AppIcon(icon, description)
                        }
                        AppIconButton(onClick = { showCalendar = !showCalendar }) {
                            AppIcon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Toggle Calendar"
                            )
                        }
                        AppIconButton(onClick = { showSearch = !showSearch }) {
                            AppIcon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                    scrollBehavior = scrollBehavior
                )

                AnimatedVisibility(
                    modifier = Modifier.adaptiveHorizontalPadding(),
                    visible = showSearch
                ) {
                    SearchBar(
                        query = state.searchKey ?: "",
                        onQueryChange = { viewModel.setSearchKey(it) }
                    )
                }
            }
        }
    ) { padding ->
        val contentState = when {
            state.isLoading -> "LOADING"
            (displayMode == DisplayMode.AGGREGATE && state.groupedRecords.isEmpty()) ||
                (displayMode == DisplayMode.TIMELINE && state.timelineRecords.isEmpty()) ||
                (displayMode == DisplayMode.LATEST && state.latestRecords.isEmpty()) -> "EMPTY"
            else -> "CONTENT"
        }
        AnimatedContent(
            targetState = contentState,
            label = "MainContentAnimation"
        ) { targetState ->
            when (targetState) {
                "LOADING" -> {
                    EmptyMessage(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding()
                            ),
                        message = "加载中",
                        isLoading = true
                    )
                }

                "EMPTY" -> {
                    EmptyMessage(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding()
                            ),
                        message = "没有记录"
                    )
                }

                "CONTENT" -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = adaptiveContentPaddingOnlyVertical(
                                top = padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding() + 16.dp
                            )
                        ) {
                            item(key = "summary_card") {
                                SummarySection(state, viewModel)
                            }
                            renderListByMode(
                                displayMode = displayMode,
                                state = state,
                                viewModel = viewModel,
                                onBookClick = onBookClick,
                                onConfirmDelete = onConfirmDelete,
                                onMergeClick = { record ->
                                    scope.launch {
                                        val candidates = viewModel.getMergeCandidates(record)
                                        if (candidates.isEmpty()) {
                                            snackbarHostState.showSnackbar("没有可合并的同名记录")
                                        } else {
                                            mergeDialogData = record to candidates
                                        }
                                    }
                                }
                            )
                        }

                        TopFloatingStickyItem(
                            item = floatingDate,
                            modifier = Modifier.padding(
                                top = padding.calculateTopPadding() + 4.dp,
                                start = 8.dp
                            )
                        ) { date ->
                            val text = buildString {
                                append(formatFriendlyDate(date))
                                if (displayMode == DisplayMode.AGGREGATE) {
                                    val dailyTotal = state.groupedRecords[date]?.sumOf { it.readTime } ?: 0L
                                    append(" · ")
                                    append(formatDuring(dailyTotal))
                                }
                            }
                            TextCard(
                                text = text,
                                textStyle = LegadoTheme.typography.labelLarge,
                                backgroundColor = LegadoTheme.colorScheme.cardContainer,
                                contentColor = LegadoTheme.colorScheme.onCardContainer,
                                cornerRadius = 8.dp,
                                horizontalPadding = 8.dp,
                                verticalPadding = 8.dp
                            )
                        }
                    }
                }
            }
        }
    }

    var skipDeleteConfirmTemp by remember(pendingDeleteAction != null) { mutableStateOf(false) }

    AppAlertDialog(
        data = pendingDeleteAction,
        onDismissRequest = { pendingDeleteAction = null },
        title = "确认删除",
        content = { _ ->
            Column {
                AppText("确定要删除这条记录吗？")
                Spacer(modifier = Modifier.height(8.dp))
                CheckboxItem(
                    title = "不再提示",
                    checked = skipDeleteConfirmTemp,
                    onCheckedChange = { skipDeleteConfirmTemp = it }
                )
            }
        },
        confirmText = "删除",
        onConfirm = { action ->
            action.invoke()
            pendingDeleteAction = null
            skipDeleteConfirm = skipDeleteConfirmTemp
        },
        dismissText = "取消",
        onDismiss = {
            pendingDeleteAction = null
        }
    )

    AppModalBottomSheet(
        show = showCalendar,
        onDismissRequest = { showCalendar = false },
        title = HEATMAP_CALENDAR_TITLE,
        startAction = {
            HeatmapCalendarStartAction(
                currentMode = heatmapMode,
                onModeChanged = { heatmapMode = it }
            )
        },
        endAction = {
            HeatmapCalendarEndAction(
                onClearDate = {
                    viewModel.setSelectedDate(null)
                    showCalendar = false
                }
            )
        }
    ) {
        HeatmapCalendarSection(
            dailyReadCounts = state.dailyReadCounts,
            dailyReadTimes = state.dailyReadTimes,
            currentMode = heatmapMode,
            selectedDate = state.selectedDate,
            onDateSelected = { date ->
                viewModel.setSelectedDate(date)
                showCalendar = false
            }
        )
    }

    var selectedAuthors by remember(mergeDialogData != null) {
        mutableStateOf(
            mergeDialogData?.let { (_, candidates) ->
                candidates.map { it.bookAuthor }.toSet()
            } ?: emptySet()
        )
    }

    AppAlertDialog(
        data = mergeDialogData,
        onDismissRequest = { mergeDialogData = null },
        title = "合并阅读记录",
        content = { (targetRecord, candidates) ->
            Column {
                AppText("将以下作者的“${targetRecord.bookName}”合并到 ${targetRecord.bookAuthor.ifBlank { "未知作者" }}")
                Spacer(modifier = Modifier.height(8.dp))

                candidates.forEach { candidate ->
                    val author = candidate.bookAuthor.ifBlank { "未知作者" }
                    val isChecked = selectedAuthors.contains(candidate.bookAuthor)

                    CheckboxItem(
                        title = "$author（${formatDuring(candidate.readTime)}）",
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            selectedAuthors = if (checked) {
                                selectedAuthors + candidate.bookAuthor
                            } else {
                                selectedAuthors - candidate.bookAuthor
                            }
                        }
                    )
                }
            }
        },
        confirmText = "合并",
        onConfirm = { (targetRecord, candidates) ->
            viewModel.mergeReadRecords(
                targetRecord,
                candidates.filter { selectedAuthors.contains(it.bookAuthor) }
            )
            mergeDialogData = null
        },
        dismissText = "取消",
        onDismiss = { mergeDialogData = null }
    )
}

@Composable
fun SummarySection(
    state: ReadRecordUiState,
    viewModel: ReadRecordViewModel
) {
    val selectedDate = state.selectedDate

    if (selectedDate != null) {
        val dateKey = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dailyDetails = state.groupedRecords[dateKey] ?: emptyList()

        if (dailyDetails.isNotEmpty()) {
            val distinctBooks = dailyDetails.map { it.bookName to it.bookAuthor }.distinct()
            val dailyTime = dailyDetails.sumOf { it.readTime }

            ReadingSummaryCard(
                title = selectedDate.format(DateTimeFormatter.ofPattern("M月d日阅读概览")),
                bookCount = distinctBooks.size,
                totalTimeMillis = dailyTime,
                bookNamesForCover = distinctBooks.take(3),
                viewModel = viewModel,
                onClick = { }
            )
        }
    } else {
        val allBooksCount = state.latestRecords.size
        val totalTime = state.totalReadTime

        if (allBooksCount > 0) {
            ReadingSummaryCard(
                title = "累计阅读成就",
                bookCount = allBooksCount,
                totalTimeMillis = totalTime,
                bookNamesForCover = state.latestRecords.take(5).map { it.bookName to it.bookAuthor },
                viewModel = viewModel,
                onClick = {  }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    val listState = rememberLazyListState()

    LaunchedEffect(weeks) {
        if (weeks.isNotEmpty()) {
            listState.scrollToItem(weeks.size - 1)
        }
    }

    val showLeftGradient by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val showRightGradient by remember {
        derivedStateOf {
            listState.canScrollForward
        }
    }

    val leftAlpha by animateFloatAsState(
        targetValue = if (showLeftGradient) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "LeftGradientAlpha"
    )

    val rightAlpha by animateFloatAsState(
        targetValue = if (showRightGradient) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "RightGradientAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WeekdayLabelsColumn(
                cellSize = config.cellSize,
                cellSpacing = config.cellSpacing
            )

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(config.cellSpacing),
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()

                        val width = size.width
                        val gradientWidthPx = config.gradientWidth.toPx()
                        val leftStop = gradientWidthPx / width
                        val rightStop = 1f - (gradientWidthPx / width)

                        val colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 1f - leftAlpha),
                            leftStop to Color.Black,
                            rightStop to Color.Black,
                            1f to Color.Black.copy(alpha = 1f - rightAlpha)
                        )

                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = colorStops,
                                startX = 0f,
                                endX = width
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                val firstReadDate = listOfNotNull(
                    dailyReadCounts.filterValues { it > 0 }.keys.minOrNull(),
                    dailyReadTimes.filterValues { it > 0L }.keys.minOrNull()
                ).minOrNull()

                if (firstReadDate != null) {
                    item {
                        NoEarlierDataIndicator(cellSize = config.cellSize)
                    }
                }

                items(weeks.size) { weekIndex ->
                    HeatmapWeekColumn(
                        week = weeks[weekIndex],
                        mode = currentMode,
                        dailyReadCounts = dailyReadCounts,
                        dailyReadTimes = dailyReadTimes,
                        selectedDate = selectedDate,
                        config = config,
                        onDateSelected = onDateSelected
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HeatmapLegend(
            mode = currentMode,
            config = config
        )
    }
}

fun LazyListScope.renderListByMode(
    displayMode: DisplayMode,
    state: ReadRecordUiState,
    viewModel: ReadRecordViewModel,
    onBookClick: (String, String) -> Unit,
    onConfirmDelete: (() -> Unit) -> Unit,
    onMergeClick: (ReadRecord) -> Unit
) {

    when (displayMode) {
        DisplayMode.AGGREGATE -> {
            state.groupedRecords.forEach { (date, details) ->
                item(key = "header_$date") {
                    DateHeader(date, details.sumOf { it.readTime })
                }
                items(
                    items = details,
                    key = { "agg_item_${date}|${it.bookName}_${it.bookAuthor}_${it.date}" }
                ) { detail ->
                    SwipeActionContainer(
                        modifier = Modifier.animateItem(),
                        startAction = SwipeAction(
                            icon = Icons.Default.Delete,
                            background = LegadoTheme.colorScheme.error,
                            onSwipe = {
                                onConfirmDelete { viewModel.deleteDetail(detail) }
                            }
                        )
                    ) {
                        ReadRecordItem(
                            detail,
                            viewModel,
                            onClick = { onBookClick(detail.bookName, detail.bookAuthor) })
                    }
                }
            }
        }

        DisplayMode.TIMELINE -> {
            state.timelineRecords.forEach { (date, sessions) ->
                item(key = "timeline_header_$date") { DateHeader(date) }
                items(items = sessions, key = { "timeline_item_${date}|${it.id}" }) { session ->
                    SwipeActionContainer(
                        modifier = Modifier.animateItem(),
                        startAction = SwipeAction(
                            icon = Icons.Default.Delete,
                            background = LegadoTheme.colorScheme.error,
                            onSwipe = {
                                onConfirmDelete { viewModel.deleteSession(session) }
                            }
                        )
                    ) {
                        TimelineSessionItem(
                            item = TimelineItem(session, true),
                            onBookClick = onBookClick,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        DisplayMode.LATEST -> {
            items(items = state.latestRecords, key = { "${it.bookName}_${it.bookAuthor}" }) { record ->
                SwipeActionContainer(
                    modifier = Modifier.animateItem(),
                    startAction = SwipeAction(
                        icon = Icons.Default.Delete,
                        background = LegadoTheme.colorScheme.error,
                        onSwipe = {
                            onConfirmDelete { viewModel.deleteReadRecord(record) }
                        }
                    ),
                    endAction = SwipeAction(
                        icon = Icons.Default.Merge,
                        background = LegadoTheme.colorScheme.primary,
                        onSwipe = {
                            onMergeClick(record)
                        }
                    )
                ) {
                        LatestReadItem(
                            record = record,
                            viewModel = viewModel,
                            onClick = { onBookClick(record.bookName, record.bookAuthor) }
                        )
                    }
                }
            }
    }
}

@Composable
fun LatestReadItem(
    record: ReadRecord,
    viewModel: ReadRecordViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var coverPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(record.bookName, record.bookAuthor) {
        coverPath = viewModel.getBookCover(record.bookName, record.bookAuthor)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .adaptiveHorizontalPadding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(coverPath)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = record.bookName,
                style = LegadoTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AppText(
                text = record.bookAuthor.ifBlank { "未知作者" },
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppText(
                modifier = Modifier
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 2000,
                        initialDelayMillis = 1000
                    ),
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.outline)) {
                        append(formatDuring(record.readTime))
                        append(" • ")
                    }
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("${DateUtil.format(Date(record.lastRead), "yyyy-MM-dd HH:mm")}")
                    }
                },
                style = LegadoTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TimelineSessionItem(
    item: TimelineItem,
    viewModel: ReadRecordViewModel,
    onBookClick: (String, String) -> Unit
) {
    val session = item.session
    var coverPath by remember { mutableStateOf<String?>(null) }
    var chapterTitle by remember { mutableStateOf<String?>("加载中...") }

    LaunchedEffect(session.bookName, session.bookAuthor) {
        coverPath = viewModel.getBookCover(session.bookName, session.bookAuthor)
        val title = viewModel.getChapterTitle(session.bookName, session.bookAuthor, session.words)
        chapterTitle = title ?: "第 ${session.words} 章"
    }

    val endTimeText = DateUtil.format(Date(session.endTime), "HH:mm")

    val nodeRadius = 4.dp
    val lineWidth = 2.dp
    val timelineX = 24.dp
    val contentPaddingStart = 32.dp

    val lineColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val nodeColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBookClick(session.bookName, session.bookAuthor) }
            .drawBehind {
                val x = timelineX.toPx()
                val h = size.height
                val cy = h / 2f

                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = lineWidth.toPx()
                )

                drawCircle(
                    color = nodeColor,
                    radius = nodeRadius.toPx(),
                    center = Offset(x, cy)
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = contentPaddingStart, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(48.dp),
                verticalArrangement = Arrangement.Center
            ) {
                AppText(
                    text = endTimeText,
                    style = LegadoTheme.typography.bodySmall
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(coverPath)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    AppText(
                        text = session.bookName,
                        style = LegadoTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AppText(
                        text = session.bookAuthor.ifBlank { "未知作者" },
                        style = LegadoTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(
                        text = chapterTitle.orEmpty(),
                        style = LegadoTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ReadRecordItem(
    detail: ReadRecordDetail,
    viewModel: ReadRecordViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var coverPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(detail.bookName, detail.bookAuthor) {
        coverPath = viewModel.getBookCover(detail.bookName, detail.bookAuthor)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .adaptiveHorizontalPadding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(coverPath)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = detail.bookName,
                style = LegadoTheme.typography.titleMedium,
                maxLines = 1
            )
            AppText(
                text = detail.bookAuthor.ifBlank { "未知作者" },
                style = LegadoTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppText(
                text = "阅读时长: ${formatDuring(detail.readTime)}",
                color = MaterialTheme.colorScheme.outline,
                style = LegadoTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun DateHeader(
    date: String,
    dailyTotalTime: Long? = null
) {
    CollapsibleHeader(
        showIcon = false,
        isCollapsed = false,
        onToggle = { },
        title = "",
        titleContent = {
            val dateText = formatFriendlyDate(date)
            AppText(
                text = dateText,
                style = LegadoTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = LegadoTheme.colorScheme.primary
            )
            dailyTotalTime?.let { total ->
                AppText(
                    text = "已读 ${formatDuring(total)}",
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurface
                )
            }
        }
    )
}

@Composable
fun ReadingSummaryCard(
    title: String,
    bookCount: Int,
    totalTimeMillis: Long,
    bookNamesForCover: List<Pair<String, String>>,
    viewModel: ReadRecordViewModel,
    onClick: () -> Unit
) {

    val coverPaths by produceState(initialValue = emptyList(), key1 = bookNamesForCover) {
        value = bookNamesForCover.map { (name, author) ->
            viewModel.getBookCover(name, author)
        }
    }

    val totalDurationMinutes = totalTimeMillis / 60000

    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .adaptiveHorizontalPadding(vertical = 8.dp),
        containerColor = LegadoTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(1f)) {

                AppText(
                    text = title,
                    style = LegadoTheme.typography.labelLarge,
                    color = LegadoTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    AppText(
                        text = "已读 ",
                        style = LegadoTheme.typography.titleMedium
                    )
                    AppText(
                        text = "$bookCount",
                        style = LegadoTheme.typography.headlineMedium,
                        color = LegadoTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    AppText(
                        text = " 本书",
                        style = LegadoTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val hours = totalDurationMinutes / 60
                val minutes = totalDurationMinutes % 60
                val timeString = if (hours > 0) "${hours}小时${minutes}分钟" else "${minutes}分钟"

                AppText(
                    text = "共阅读 $timeString",
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant
                )
            }

            if (bookNamesForCover.isNotEmpty()) {
                BookStackView(coverPaths = coverPaths)
            }
        }
    }
}

@Composable
fun BookStackView(coverPaths: List<String?>) {
    val xOffsetStep = 12.dp
    val stackWidth = 48.dp + (xOffsetStep * (coverPaths.size - 1).coerceAtLeast(0))

    Box(
        modifier = Modifier
            .width(stackWidth)
            .height(72.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        coverPaths.forEachIndexed { index, path ->
            Box(
                modifier = Modifier
                    .padding(start = xOffsetStep * index)
                    .zIndex(index.toFloat())
                    .rotate(if (index % 2 == 0) 3f else -3f)
            ) {
                Surface(
                    shadowElevation = 4.dp,
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Transparent
                ) {
                    Cover(path = path)
                }
            }
        }
    }
}

fun formatDuring(mss: Long): String {
    return formatReadDuration(mss)
}
