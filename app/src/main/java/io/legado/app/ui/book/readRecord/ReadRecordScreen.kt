package io.legado.app.ui.book.readRecord

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cn.hutool.core.date.DateUtil
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.ui.widget.components.AnimatedTextLine
import io.legado.app.ui.widget.components.Calendar
import io.legado.app.ui.widget.components.Cover
import io.legado.app.ui.widget.components.EmptyMessageView
import io.legado.app.ui.widget.components.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.SearchBarSection
import io.legado.app.ui.widget.components.SectionHeader
import io.legado.app.ui.widget.components.button.AlertButton
import io.legado.app.ui.widget.components.button.SmallTopBarButton
import io.legado.app.ui.widget.components.swipe.SwipeAction
import io.legado.app.ui.widget.components.swipe.SwipeActionContainer
import io.legado.app.utils.StringUtils.formatFriendlyDate
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
    val state by viewModel.uiState.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var skipDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var mergeTargetRecord by remember { mutableStateOf<ReadRecord?>(null) }
    val onConfirmDelete: (() -> Unit) -> Unit = { action ->
        if (skipDeleteConfirm) {
            action()
        } else {
            pendingDeleteAction = action
        }
    }

    LaunchedEffect(state.searchKey) {
        if (state.searchKey.isNullOrBlank()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                GlassMediumFlexibleTopAppBar(
                    title = {
                        Text(
                            text = "阅读记录"
                        )
                    },
                    subtitle = {
                        val subTitle = when (displayMode) {
                            DisplayMode.AGGREGATE -> "汇总视图"
                            DisplayMode.TIMELINE -> "时间线视图"
                            DisplayMode.LATEST -> "最后阅读"
                        }
                        AnimatedTextLine(
                            text = subTitle
                        )
                    },
                    navigationIcon = {
                        SmallTopBarButton(onClick = onBackClick)
                    },
                    actions = {
                        IconButton(onClick = {
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
                            Icon(icon, description)
                        }
                        IconButton(onClick = { showCalendar = !showCalendar }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Toggle Calendar")
                        }
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                    scrollBehavior = scrollBehavior
                )

                AnimatedVisibility(visible = showSearch) {
                    SearchBarSection(
                        query = state.searchKey ?: "",
                        onQueryChange = { viewModel.setSearchKey(it) }
                    )
                }
                AnimatedVisibility(visible = showCalendar) {
                    CalendarSection(
                        selectedDate = state.selectedDate,
                        onDateSelected = { date ->
                            viewModel.setSelectedDate(date)
                            showCalendar = false
                        },
                        onClearDate = {
                            viewModel.setSelectedDate(null)
                            showCalendar = false
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
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
                        EmptyMessageView(
                            modifier = Modifier.fillMaxSize(),
                            message = "加载中",
                            isLoading = true
                        )
                    }

                    "EMPTY" -> {
                        EmptyMessageView(
                            modifier = Modifier.fillMaxSize(),
                            message = "没有记录"
                        )
                    }

                    "CONTENT" -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .nestedScroll(scrollBehavior.nestedScrollConnection)
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
                                onMergeClick = { mergeTargetRecord = it }
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingDeleteAction != null) {
        var skipDeleteConfirmTemp by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                pendingDeleteAction = null
            },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("确定要删除这条记录吗？")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                color = if (skipDeleteConfirmTemp) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                shape = MaterialTheme.shapes.small
                            )
                            .clickable { skipDeleteConfirmTemp = !skipDeleteConfirmTemp }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = skipDeleteConfirmTemp, onCheckedChange = null)
                        Text(
                            text = "不再提示",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                AlertButton(
                    onClick = {
                        pendingDeleteAction?.invoke()
                        pendingDeleteAction = null
                        skipDeleteConfirm = skipDeleteConfirmTemp
                    },
                    text = "删除"
                )
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDeleteAction = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    mergeTargetRecord?.let { targetRecord ->
        val candidates by produceState(
            initialValue = emptyList<ReadRecord>(),
            key1 = targetRecord.bookName,
            key2 = targetRecord.bookAuthor
        ) {
            value = viewModel.getMergeCandidates(targetRecord)
        }
        var selectedAuthors by remember(targetRecord.bookName, targetRecord.bookAuthor, candidates) {
            mutableStateOf(candidates.map { it.bookAuthor }.toSet())
        }

        AlertDialog(
            onDismissRequest = { mergeTargetRecord = null },
            title = { Text("合并阅读记录") },
            text = {
                if (candidates.isEmpty()) {
                    Text("没有可合并的同名记录")
                } else {
                    Column {
                        Text("将以下作者的“${targetRecord.bookName}”合并到 ${targetRecord.bookAuthor.ifBlank { "未知作者" }}")
                        Spacer(modifier = Modifier.height(8.dp))
                        candidates.forEach { candidate ->
                            val author = candidate.bookAuthor.ifBlank { "未知作者" }
                            val isChecked = selectedAuthors.contains(candidate.bookAuthor)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedAuthors =
                                            if (isChecked) selectedAuthors - candidate.bookAuthor
                                            else selectedAuthors + candidate.bookAuthor
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isChecked, onCheckedChange = null)
                                Text(
                                    text = "$author（${formatDuring(candidate.readTime)}）",
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (candidates.isNotEmpty()) {
                            viewModel.mergeReadRecords(
                                targetRecord,
                                candidates.filter { selectedAuthors.contains(it.bookAuthor) }
                            )
                        }
                        mergeTargetRecord = null
                    }
                ) {
                    Text("合并")
                }
            },
            dismissButton = {
                TextButton(onClick = { mergeTargetRecord = null }) {
                    Text("取消")
                }
            }
        )
    }
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
                stickyHeader(key = "header_$date") {
                    DateHeader(date, details.sumOf { it.readTime })
                }
                items(items = details, key = { "${it.bookName}_${it.bookAuthor}_${it.date}" }) { detail ->
                    SwipeActionContainer(
                        modifier = Modifier.animateItem(),
                        startAction = SwipeAction(
                            icon = Icons.Default.Delete,
                            background = MaterialTheme.colorScheme.error,
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
                stickyHeader(key = "timeline_header_$date") { DateHeader(date) }
                items(items = sessions, key = { it.id }) { session ->
                    SwipeActionContainer(
                        modifier = Modifier.animateItem(),
                        startAction = SwipeAction(
                            icon = Icons.Default.Delete,
                            background = MaterialTheme.colorScheme.error,
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
                        background = MaterialTheme.colorScheme.error,
                        onSwipe = {
                            onConfirmDelete { viewModel.deleteReadRecord(record) }
                        }
                    )
                ) {
                        LatestReadItem(
                            record = record,
                            viewModel = viewModel,
                            onClick = { onBookClick(record.bookName, record.bookAuthor) },
                            onMergeClick = { onMergeClick(record) }
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
    onMergeClick: () -> Unit,
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(coverPath)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.bookName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2
            )
            Text(
                text = record.bookAuthor.ifBlank { "未知作者" },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "总时长: ${formatDuring(record.readTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = "最后阅读: ${DateUtil.format(Date(record.lastRead), "yyyy-MM-dd HH:mm")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        TextButton(onClick = onMergeClick) {
            Text("合并")
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
                Text(
                    text = endTimeText,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(coverPath)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = session.bookName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = session.bookAuthor.ifBlank { "未知作者" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = chapterTitle.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(coverPath)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detail.bookName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2
            )
            Text(
                text = detail.bookAuthor.ifBlank { "未知作者" },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "阅读时长: ${formatDuring(detail.readTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun DateHeader(
    date: String,
    dailyTotalTime: Long? = null
) {
    val dateText = formatFriendlyDate(date)
    SectionHeader(
        titleContent = {
            Column {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                dailyTotalTime?.let { total ->
                    Text(
                        text = "已读 ${formatDuring(total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        detailContent = null,
        horizontalArrangement = Arrangement.Start
    )
}

@Composable
fun CalendarSection(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onClearDate: () -> Unit
) {
    val effectiveInitialDate = selectedDate ?: LocalDate.now()
    Calendar(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        initialDate = effectiveInitialDate,
        selectedDate = selectedDate,
        onDateSelected = onDateSelected,
        onClearDate = onClearDate
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

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "已读 ",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "$bookCount",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = " 本书",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val hours = totalDurationMinutes / 60
                val minutes = totalDurationMinutes % 60
                val timeString = if (hours > 0) "${hours}小时${minutes}分钟" else "${minutes}分钟"

                Text(
                    text = "共阅读 $timeString",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val days = mss / (1000 * 60 * 60 * 24)
    val hours = mss % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
    val minutes = mss % (1000 * 60 * 60) / (1000 * 60)
    val seconds = mss % (1000 * 60) / 1000
    val d = if (days > 0) "${days}天" else ""
    val h = if (hours > 0) "${hours}小时" else ""
    val m = if (minutes > 0) "${minutes}分钟" else ""
    val s = if (seconds > 0) "${seconds}秒" else ""
    return if ("$d$h$m$s".isBlank()) "0秒" else "$d$h$m$s"
}
