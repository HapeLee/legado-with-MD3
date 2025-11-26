package io.legado.app.ui.book.readRecord

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.legado.app.data.appDb
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import cn.hutool.core.date.DateUtil
import io.legado.app.base.BaseComposeActivity
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.ui.widget.components.AnimatedTextLine
import io.legado.app.ui.widget.components.Cover
import io.legado.app.ui.widget.components.EmptyMessageView
import io.legado.app.ui.widget.components.SearchBarSection
import io.legado.app.utils.StringUtils.formatFriendlyDate
import org.koin.androidx.compose.koinViewModel

// 包含绘制时间线所需的上下文信息
data class TimelineItem(
    val session: ReadRecordSession,
    // 是否显示封面和标题
    val showHeader: Boolean
)

class ReadRecordActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        MaterialTheme {
            val viewModel: ReadRecordViewModel = koinViewModel()
            ReadRecordScreen(
                viewModel = viewModel,
                onBackClick = { finish() },
                onBookClick = { bookName ->
                    lifecycleScope.launch {
                        val book = withContext(Dispatchers.IO) {
                            appDb.bookDao.findByName(bookName).firstOrNull()
                        }
                        if (book != null) startActivityForBook(book)
                    }
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadRecordScreen(
    viewModel: ReadRecordViewModel,
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(showSearch) {
        if (!showSearch) {
            viewModel.loadData("")
        }
    }

    LaunchedEffect(searchText) {
        if (showSearch) {
            kotlinx.coroutines.delay(100L)
            viewModel.loadData(searchText)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                MediumTopAppBar(
                    title = {
                        val (mainTitle, subTitle) = when (displayMode) {
                            DisplayMode.AGGREGATE -> "阅读记录" to "汇总视图"
                            DisplayMode.TIMELINE -> "阅读记录" to "时间线视图"
                            DisplayMode.LATEST -> "阅读记录" to "最后阅读"
                        }
                        Column {
                            Text(
                                text = mainTitle,
                                style = MaterialTheme.typography.titleLarge,
                            )

                            AnimatedTextLine(
                                text = subTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
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
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                    scrollBehavior = scrollBehavior
                )

                AnimatedVisibility(visible = showSearch) {
                    SearchBarSection(
                        query = searchText,
                        onQueryChange = { searchText = it }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            TotalTimeHeader(state.totalReadTime)
            val isEmpty = when (displayMode) {
                DisplayMode.AGGREGATE -> state.groupedRecords.isEmpty()
                DisplayMode.TIMELINE -> state.timelineRecords.isEmpty()
                DisplayMode.LATEST -> state.latestRecords.isEmpty()
            }
            Crossfade(
                targetState = isEmpty,
                animationSpec = tween(durationMillis = 500),
                label = "ContentCrossfade"
            ) { isListEmpty ->
                if (isListEmpty){
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyMessageView(
                            message = "没有记录"
                        )
                    }
                } else {
                    LazyColumn {
                        when(displayMode){
                            DisplayMode.AGGREGATE -> {
                                state.groupedRecords.forEach { (date, details) ->

                                    val dailyTotalTime = details.sumOf { it.readTime }

                                    stickyHeader {
                                        DateHeader(date, dailyTotalTime)
                                    }

                                    items(
                                        items = details,
                                        key = { it.bookName + it.readTime.toString() }
                                    ) { detail ->
                                        ReadRecordItem(
                                            detail = detail,
                                            viewModel = viewModel,
                                            onClick = { onBookClick(detail.bookName) },
                                            onDelete = { viewModel.deleteDetail(detail) },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                            DisplayMode.TIMELINE -> {
                                state.timelineRecords.forEach { (date, sessions) ->
                                    val dailyTotalTime = sessions.sumOf { it.endTime - it.startTime }
                                    stickyHeader { DateHeader(date, dailyTotalTime) }

                                    val timelineItems = sessions.mapIndexed { index, session ->
                                        val previousSession = sessions.getOrNull(index - 1)

                                        val showHeader = index == 0 || session.bookName != previousSession?.bookName
                                        TimelineItem(session, showHeader)
                                    }

                                    items(items = timelineItems, key = { it.session.id }) { item ->
                                        TimelineSessionItem(
                                            item = item,
                                            onBookClick = onBookClick,
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                            DisplayMode.LATEST -> {
                                items(items = state.latestRecords, key = { it.bookName + it.deviceId }) { record ->
                                    LatestReadItem(
                                        record = record,
                                        viewModel = viewModel,
                                        onClick = { onBookClick(record.bookName) },
                                        modifier = Modifier.animateItem()
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

@Composable
fun LatestReadItem(
    record: ReadRecord,
    viewModel: ReadRecordViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var coverPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(record.bookName) {
        coverPath = viewModel.getBookCover(record.bookName)
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "总时长: ${formatDuring(record.readTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = "最后阅读: ${DateUtil.format(java.util.Date(record.lastRead), "yyyy-MM-dd HH:mm")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun TimelineSessionItem(
    item: TimelineItem,
    viewModel: ReadRecordViewModel,
    onBookClick: (String) -> Unit
) {
    val session = item.session
    var coverPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session.bookName) {
        coverPath = viewModel.getBookCover(session.bookName)
    }

    val startTimeText = DateUtil.format(java.util.Date(session.startTime), "HH:mm")
    val endTimeText = DateUtil.format(java.util.Date(session.endTime), "HH:mm")
    val duration = session.endTime - session.startTime

    val nodeRadius = 4.dp
    val lineWidth = 2.dp
    val timelineX = 24.dp
    val contentPaddingStart = 32.dp

    val lineColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val nodeColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
            .clickable { onBookClick(session.bookName) }
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

            Column(modifier = Modifier.weight(1f)) {
                if (item.showHeader) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cover(coverPath)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = session.bookName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    "时长: ${formatDuring(duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                // Text(
                //     "字数: ${session.words}",
                //     style = MaterialTheme.typography.bodySmall,
                //     color = MaterialTheme.colorScheme.onSurfaceVariant
                // )
            }
        }
    }
}

@Composable
fun ReadRecordItem(
    detail: ReadRecordDetail,
    viewModel: ReadRecordViewModel,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var coverPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(detail.bookName) {
        coverPath = viewModel.getBookCover(detail.bookName)
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "阅读时长: ${formatDuring(detail.readTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, "Delete", tint = Color.LightGray)
        }
    }
}

@Composable
fun DateHeader(
    date: String,
    dailyTotalTime: Long
) {
    val dateText = formatFriendlyDate(date)
    val totalTimeText = "已读 ${formatDuring(dailyTotalTime)}"
    Surface(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = totalTimeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun TotalTimeHeader(time: Long) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "阅读时长",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = formatDuring(time),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
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