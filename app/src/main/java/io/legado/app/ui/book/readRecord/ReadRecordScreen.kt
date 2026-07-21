package io.legado.app.ui.book.readRecord

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.hutool.core.date.DateUtil
import io.legado.app.R
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPaddingOnlyVertical
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.CollapsibleHeader
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.AppIconButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.heatmap.HeatmapCalendarEndAction
import io.legado.app.ui.widget.components.heatmap.HeatmapCalendarStartAction
import io.legado.app.ui.widget.components.heatmap.HeatmapConfig
import io.legado.app.ui.widget.components.heatmap.HeatmapLegend
import io.legado.app.ui.widget.components.heatmap.HeatmapMode
import io.legado.app.ui.widget.components.heatmap.HeatmapWeekColumn
import io.legado.app.ui.widget.components.heatmap.NoEarlierDataIndicator
import io.legado.app.ui.widget.components.heatmap.WeekdayLabelsColumn
import io.legado.app.ui.widget.components.heatmap.heatmapCalendarTitle
import io.legado.app.ui.widget.components.heatmap.rememberDateRange
import io.legado.app.ui.widget.components.heatmap.rememberDaysInRange
import io.legado.app.ui.widget.components.heatmap.rememberWeeks
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSwitchSettingItem
import io.legado.app.ui.widget.components.swipe.SwipeAction
import io.legado.app.ui.widget.components.swipe.SwipeActionContainer
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.StringUtils.formatFriendlyDate
import io.legado.app.utils.formatReadDuration
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

data class TimelineItem(
    val session: ReadRecordSession,
    val showHeader: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReadRecordRouteScreen(
    viewModel: ReadRecordViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onBookClick: (String, String) -> Unit,
    onSummaryClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReadRecordScreen(
        state = state,
        onIntent = viewModel::onIntent,
        loadBookCover = viewModel::getBookCover,
        loadChapterTitle = viewModel::getChapterTitle,
        loadMergeCandidates = viewModel::getMergeCandidates,
        onBackClick = onBackClick,
        onBookClick = onBookClick,
        onSummaryClick = onSummaryClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReadRecordScreen(
    state: ReadRecordUiState,
    onIntent: (ReadRecordIntent) -> Unit,
    loadBookCover: suspend (String, String) -> String?,
    loadChapterTitle: suspend (String, String, Long) -> String?,
    loadMergeCandidates: suspend (ReadRecord) -> List<ReadRecord>,
    onBackClick: () -> Unit,
    onBookClick: (String, String) -> Unit,
    onSummaryClick: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val displayMode = state.displayMode
    val readRecordEnabled = state.readRecordEnabled
    var showSearch by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var heatmapMode by remember { mutableStateOf(HeatmapMode.COUNT) }
    var selectedItemKeys by remember { mutableStateOf(emptySet<String>()) }
    val listState = rememberLazyListState()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val inSelectionMode = selectedItemKeys.isNotEmpty()

    var skipDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingDeleteCount by remember { mutableStateOf(1) }
    var mergeDialogData by remember { mutableStateOf<Pair<ReadRecord, List<ReadRecord>>?>(null) }
    val onConfirmDelete: (Int, () -> Unit) -> Unit = { count, action ->
        if (skipDeleteConfirm) {
            action()
        } else {
            pendingDeleteCount = count
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

    BackHandler(enabled = inSelectionMode) {
        selectedItemKeys = emptySet()
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                GlassMediumFlexibleTopAppBar(
                    title = if (inSelectionMode) {
                        stringResource(R.string.selected_count, selectedItemKeys.size)
                    } else {
                        stringResource(R.string.read_record)
                    },
                    subtitle = run {
                        if (inSelectionMode) return@run stringResource(R.string.long_press_select_mode)
                        val subTitle = when (displayMode) {
                            DisplayMode.AGGREGATE -> stringResource(R.string.read_record_view_aggregate)
                            DisplayMode.TIMELINE -> stringResource(R.string.read_record_view_timeline)
                            DisplayMode.LATEST -> stringResource(R.string.read_record_view_latest)
                        }
                        subTitle
                    },
                    navigationIcon = {
                        TopBarNavigationButton(
                            onClick = {
                                if (inSelectionMode) {
                                    selectedItemKeys = emptySet()
                                } else {
                                    onBackClick()
                                }
                            }
                        )
                    },
                    actions = {
                        if (inSelectionMode) {
                            AppIconButton(
                                onClick = {
                                    val action = {
                                        deleteSelectedReadRecords(
                                            state = state,
                                            selectedItemKeys = selectedItemKeys,
                                            onIntent = onIntent
                                        )
                                        selectedItemKeys = emptySet()
                                    }
                                    onConfirmDelete(selectedItemKeys.size, action)
                                }
                            ) {
                                AppIcon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete_selected)
                                )
                            }
                        } else {
                            AppIconButton(onClick = {
                                val newMode = when (displayMode) {
                                    DisplayMode.AGGREGATE -> DisplayMode.TIMELINE
                                    DisplayMode.TIMELINE -> DisplayMode.LATEST
                                    DisplayMode.LATEST -> DisplayMode.AGGREGATE
                                }
                                onIntent(ReadRecordIntent.SetDisplayMode(newMode))
                                selectedItemKeys = emptySet()
                            }) {
                                val icon = when (displayMode) {
                                    DisplayMode.AGGREGATE -> Icons.Default.Timeline
                                    DisplayMode.TIMELINE -> Icons.Default.Schedule
                                    DisplayMode.LATEST -> Icons.AutoMirrored.Filled.List
                                }
                                val description = when (displayMode) {
                                    DisplayMode.AGGREGATE -> stringResource(R.string.a11y_switch_to_timeline_view)
                                    DisplayMode.TIMELINE -> stringResource(R.string.a11y_switch_to_latest_view)
                                    DisplayMode.LATEST -> stringResource(R.string.a11y_switch_to_aggregate_view)
                                }
                                AppIcon(icon, description)
                            }
                            AppIconButton(onClick = { showSearch = !showSearch }) {
                                AppIcon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                            }
                            AppIconButton(onClick = { showActionsSheet = true }) {
                                AppIcon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_actions)
                                )
                            }
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
                        onQueryChange = { onIntent(ReadRecordIntent.Search(it)) }
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
                        message = stringResource(R.string.loading),
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
                        message = stringResource(R.string.no_read_record)
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
                                SummarySection(state, loadBookCover, onSummaryClick)
                            }
                            renderListByMode(
                                displayMode = displayMode,
                                state = state,
                                onIntent = onIntent,
                                loadBookCover = loadBookCover,
                                loadChapterTitle = loadChapterTitle,
                                onBookClick = onBookClick,
                                onConfirmDelete = onConfirmDelete,
                                selectedItemKeys = selectedItemKeys,
                                inSelectionMode = inSelectionMode,
                                onToggleSelection = { key ->
                                    selectedItemKeys = if (selectedItemKeys.contains(key)) {
                                        selectedItemKeys - key
                                    } else {
                                        selectedItemKeys + key
                                    }
                                },
                                onEnterSelection = { key ->
                                    selectedItemKeys = selectedItemKeys + key
                                },
                                onMergeClick = { record ->
                                    scope.launch {
                                        val candidates = loadMergeCandidates(record)
                                        if (candidates.isEmpty()) {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.no_merge_candidates)
                                            )
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

    ReadRecordActionsSheet(
        show = showActionsSheet,
        showCalendar = showCalendar,
        readRecordEnabled = readRecordEnabled,
        onDismissRequest = { showActionsSheet = false },
        onToggleCalendar = {
            showCalendar = !showCalendar
            showActionsSheet = false
        },
        onReadRecordEnabledChange = { checked ->
            onIntent(ReadRecordIntent.SetEnabled(checked))
        },
        onClearReadRecords = {
            showActionsSheet = false
            onConfirmDelete(-1) {
                onIntent(ReadRecordIntent.ClearRecords)
                selectedItemKeys = emptySet()
            }
        }
    )

    var skipDeleteConfirmTemp by remember(pendingDeleteAction != null) { mutableStateOf(false) }

    AppAlertDialog(
        data = pendingDeleteAction,
        onDismissRequest = { pendingDeleteAction = null },
        title = stringResource(R.string.confirm_delete_read_record),
        content = { _ ->
            Column {
                AppText(
                    if (pendingDeleteCount == -1) {
                        stringResource(R.string.clear_read_records_message)
                    } else if (pendingDeleteCount > 1) {
                        stringResource(R.string.delete_selected_read_records_message, pendingDeleteCount)
                    } else {
                        stringResource(R.string.delete_read_record_message)
                    }
                )
                if (pendingDeleteCount != -1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CheckboxItem(
                        title = stringResource(R.string.do_not_remind_again),
                        checked = skipDeleteConfirmTemp,
                        onCheckedChange = { skipDeleteConfirmTemp = it }
                    )
                }
            }
        },
        confirmText = stringResource(R.string.delete),
        onConfirm = { action ->
            action.invoke()
            pendingDeleteAction = null
            if (pendingDeleteCount != -1) {
                skipDeleteConfirm = skipDeleteConfirmTemp
            }
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = {
            pendingDeleteAction = null
        }
    )

    AppModalBottomSheet(
        show = showCalendar,
        onDismissRequest = { showCalendar = false },
        title = heatmapCalendarTitle(),
        startAction = {
            HeatmapCalendarStartAction(
                currentMode = heatmapMode,
                onModeChanged = { heatmapMode = it }
            )
        },
        endAction = {
            HeatmapCalendarEndAction(
                onClearDate = {
                    onIntent(ReadRecordIntent.SelectDate(null))
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
                onIntent(ReadRecordIntent.SelectDate(date))
                showCalendar = false
            }
        )
    }

    var selectedMergeKeys by remember(mergeDialogData != null) {
        mutableStateOf(
            mergeDialogData?.let { (targetRecord, candidates) ->
                candidates
                    .filter { it.bookName == targetRecord.bookName }
                    .map { it.mergeKey() }
                    .toSet()
            } ?: emptySet()
        )
    }

    AppAlertDialog(
        data = mergeDialogData,
        onDismissRequest = { mergeDialogData = null },
        title = stringResource(R.string.merge_read_records),
        content = { (targetRecord, candidates) ->
            val unknownAuthor = stringResource(R.string.unknown_author)
            Column {
                AppText(
                    stringResource(
                        R.string.merge_read_records_message,
                        targetRecord.bookName,
                        targetRecord.bookAuthor.ifBlank { unknownAuthor }
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(candidates, key = { it.mergeKey() }) { candidate ->
                        val author = candidate.bookAuthor.ifBlank { unknownAuthor }
                        val candidateKey = candidate.mergeKey()
                        val isChecked = selectedMergeKeys.contains(candidateKey)

                        CheckboxItem(
                            title = stringResource(
                                R.string.merge_read_record_candidate,
                                candidate.bookName,
                                author,
                                formatDuring(candidate.readTime)
                            ),
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                selectedMergeKeys = if (checked) {
                                    selectedMergeKeys + candidateKey
                                } else {
                                    selectedMergeKeys - candidateKey
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmText = stringResource(R.string.merge),
        onConfirm = { (targetRecord, candidates) ->
            onIntent(
                ReadRecordIntent.MergeRecords(
                    targetRecord,
                    candidates.filter { selectedMergeKeys.contains(it.mergeKey()) },
                )
            )
            mergeDialogData = null
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { mergeDialogData = null }
    )
}

@Composable
private fun ReadRecordActionsSheet(
    show: Boolean,
    showCalendar: Boolean,
    readRecordEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onToggleCalendar: () -> Unit,
    onReadRecordEnabledChange: (Boolean) -> Unit,
    onClearReadRecords: () -> Unit
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.read_record_options)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactClickableSettingItem(
                title = stringResource(
                    if (showCalendar) R.string.hide_read_calendar else R.string.show_read_calendar
                ),
                imageVector = Icons.Default.CalendarMonth,
                onClick = onToggleCalendar
            )
            CompactSwitchSettingItem(
                title = stringResource(R.string.enable_read_record),
                checked = readRecordEnabled,
                description = stringResource(R.string.enable_read_record_summary),
                imageVector = Icons.Default.Schedule,
                onCheckedChange = onReadRecordEnabledChange
            )
            CompactClickableSettingItem(
                title = stringResource(R.string.clear_read_records),
                description = stringResource(R.string.clear_read_records_summary),
                imageVector = Icons.Default.Delete,
                color = LegadoTheme.colorScheme.error,
                onClick = onClearReadRecords
            )
        }
    }
}

private fun ReadRecord.mergeKey(): String {
    return "$deviceId|$bookName|$bookAuthor"
}

private fun ReadRecordDetail.selectionKey(): String {
    return "detail|$deviceId|$bookName|$bookAuthor|$date"
}

private fun ReadRecordSession.selectionKey(): String {
    return "session|$id"
}

private fun ReadRecord.selectionKey(): String {
    return "record|$deviceId|$bookName|$bookAuthor"
}

private fun deleteSelectedReadRecords(
    state: ReadRecordUiState,
    selectedItemKeys: Set<String>,
    onIntent: (ReadRecordIntent) -> Unit,
) {
    state.groupedRecords.values.flatten()
        .filter { selectedItemKeys.contains(it.selectionKey()) }
        .forEach { onIntent(ReadRecordIntent.DeleteDetail(it)) }
    state.timelineRecords.values.flatten()
        .filter { selectedItemKeys.contains(it.selectionKey()) }
        .forEach { onIntent(ReadRecordIntent.DeleteSession(it)) }
    state.latestRecords
        .filter { selectedItemKeys.contains(it.selectionKey()) }
        .forEach { onIntent(ReadRecordIntent.DeleteRecord(it)) }
}

@Composable
private fun Modifier.selectionBackground(isSelected: Boolean): Modifier {
    return if (isSelected) {
        background(LegadoTheme.colorScheme.primary.copy(alpha = 0.12f))
    } else {
        this
    }
}

@Composable
private fun SelectionCheckmark(
    inSelectionMode: Boolean,
    isSelected: Boolean
) {
    AnimatedVisibility(visible = inSelectionMode) {
        AppText(
            text = if (isSelected) "✓" else "",
            modifier = Modifier.width(24.dp),
            color = LegadoTheme.colorScheme.primary,
            style = LegadoTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SummarySection(
    state: ReadRecordUiState,
    loadBookCover: suspend (String, String) -> String?,
    onSummaryClick: () -> Unit
) {
    val selectedDate = state.selectedDate

    if (selectedDate != null) {
        val dateKey = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dailyDetails = state.groupedRecords[dateKey] ?: emptyList()

        if (dailyDetails.isNotEmpty()) {
            val distinctBooks = dailyDetails.map { it.bookName to it.bookAuthor }.distinct()
            val dailyTime = dailyDetails.sumOf { it.readTime }

            ReadingSummaryCard(
                title = stringResource(
                    R.string.read_record_date_overview,
                    selectedDate.monthValue,
                    selectedDate.dayOfMonth
                ),
                bookCount = distinctBooks.size,
                totalTimeMillis = dailyTime,
                bookNamesForCover = distinctBooks.take(3),
                loadBookCover = loadBookCover,
                onClick = onSummaryClick
            )
        }
    } else {
        val allBooksCount = state.latestRecords.size
        val totalTime = state.totalReadTime

        if (allBooksCount > 0) {
            ReadingSummaryCard(
                title = stringResource(R.string.read_record_total_achievement),
                bookCount = allBooksCount,
                totalTimeMillis = totalTime,
                bookNamesForCover = state.latestRecords.take(5).map { it.bookName to it.bookAuthor },
                loadBookCover = loadBookCover,
                onClick = onSummaryClick
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
    onDateSelected: ((LocalDate) -> Unit)?,
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WeekdayLabelsColumn(
                cellSize = config.interactiveCellSize,
                cellSpacing = config.cellSpacing
            )

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(config.cellSpacing),
                modifier = Modifier
                    .weight(1f)
                    .fadingEdge(listState, config.gradientWidth)
            ) {
                val firstReadDate = listOfNotNull(
                    dailyReadCounts.filterValues { it > 0 }.keys.minOrNull(),
                    dailyReadTimes.filterValues { it > 0L }.keys.minOrNull()
                ).minOrNull()

                if (firstReadDate != null) {
                    item {
                        NoEarlierDataIndicator(
                            cellSize = config.cellSize,
                            touchTargetSize = config.interactiveCellSize,
                        )
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
    onIntent: (ReadRecordIntent) -> Unit,
    loadBookCover: suspend (String, String) -> String?,
    loadChapterTitle: suspend (String, String, Long) -> String?,
    onBookClick: (String, String) -> Unit,
    onConfirmDelete: (Int, () -> Unit) -> Unit,
    selectedItemKeys: Set<String>,
    inSelectionMode: Boolean,
    onToggleSelection: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
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
                    key = { "agg_item_${date}|${it.deviceId}_${it.bookName}_${it.bookAuthor}_${it.date}" }
                ) { detail ->
                    val itemKey = detail.selectionKey()
                    val isSelected = selectedItemKeys.contains(itemKey)
                    val itemContent: @Composable (Modifier) -> Unit = { modifier ->
                        ReadRecordItem(
                            detail,
                            loadBookCover,
                            onClick = {
                                if (inSelectionMode) {
                                    onToggleSelection(itemKey)
                                } else {
                                    onBookClick(detail.bookName, detail.bookAuthor)
                                }
                            },
                            onLongClick = { onEnterSelection(itemKey) },
                            inSelectionMode = inSelectionMode,
                            isSelected = isSelected,
                            modifier = modifier
                        )
                    }
                    val deleteActionDescription = stringResource(R.string.del_read_record)
                    if (inSelectionMode) {
                        itemContent(Modifier.animateItem())
                    } else {
                        SwipeActionContainer(
                            modifier = Modifier.animateItem(),
                            startAction = SwipeAction(
                                icon = Icons.Default.Delete,
                                background = LegadoTheme.colorScheme.error,
                                onSwipe = {
                                    onConfirmDelete(1) {
                                        onIntent(ReadRecordIntent.DeleteDetail(detail))
                                    }
                                },
                                contentDescription = deleteActionDescription
                            )
                        ) {
                            itemContent(Modifier)
                        }
                    }
                }
            }
        }

        DisplayMode.TIMELINE -> {
            state.timelineRecords.forEach { (date, sessions) ->
                item(key = "timeline_header_$date") { DateHeader(date) }
                items(items = sessions, key = { "timeline_item_${date}|${it.id}" }) { session ->
                    val itemKey = session.selectionKey()
                    val isSelected = selectedItemKeys.contains(itemKey)
                    val itemContent: @Composable (Modifier) -> Unit = { modifier ->
                        TimelineSessionItem(
                            item = TimelineItem(session, true),
                            onBookClick = { bookName, bookAuthor ->
                                if (inSelectionMode) {
                                    onToggleSelection(itemKey)
                                } else {
                                    onBookClick(bookName, bookAuthor)
                                }
                            },
                            onLongClick = { onEnterSelection(itemKey) },
                            inSelectionMode = inSelectionMode,
                            isSelected = isSelected,
                            loadBookCover = loadBookCover,
                            loadChapterTitle = loadChapterTitle,
                            modifier = modifier
                        )
                    }
                    val deleteActionDescription = stringResource(R.string.del_read_record)
                    if (inSelectionMode) {
                        itemContent(Modifier.animateItem())
                    } else {
                        SwipeActionContainer(
                            modifier = Modifier.animateItem(),
                            startAction = SwipeAction(
                                icon = Icons.Default.Delete,
                                background = LegadoTheme.colorScheme.error,
                                onSwipe = {
                                    onConfirmDelete(1) {
                                        onIntent(ReadRecordIntent.DeleteSession(session))
                                    }
                                },
                                contentDescription = deleteActionDescription
                            )
                        ) {
                            itemContent(Modifier)
                        }
                    }
                }
            }
        }

        DisplayMode.LATEST -> {
            items(items = state.latestRecords, key = { "${it.deviceId}_${it.bookName}_${it.bookAuthor}" }) { record ->
                val itemKey = record.selectionKey()
                val isSelected = selectedItemKeys.contains(itemKey)
                val itemContent: @Composable (Modifier) -> Unit = { modifier ->
                    LatestReadItem(
                        record = record,
                        loadBookCover = loadBookCover,
                        onClick = {
                            if (inSelectionMode) {
                                onToggleSelection(itemKey)
                            } else {
                                onBookClick(record.bookName, record.bookAuthor)
                            }
                        },
                        onLongClick = { onEnterSelection(itemKey) },
                        inSelectionMode = inSelectionMode,
                        isSelected = isSelected,
                        modifier = modifier
                    )
                }
                val deleteActionDescription = stringResource(R.string.del_read_record)
                val mergeActionDescription = stringResource(R.string.a11y_merge_same_name_read_records)
                if (inSelectionMode) {
                    itemContent(Modifier.animateItem())
                } else {
                    SwipeActionContainer(
                        modifier = Modifier.animateItem(),
                        startAction = SwipeAction(
                            icon = Icons.Default.Delete,
                            background = LegadoTheme.colorScheme.error,
                            onSwipe = {
                                onConfirmDelete(1) {
                                    onIntent(ReadRecordIntent.DeleteRecord(record))
                                }
                            },
                            contentDescription = deleteActionDescription
                        ),
                        endAction = SwipeAction(
                            icon = Icons.Default.Merge,
                            background = LegadoTheme.colorScheme.primary,
                            onSwipe = {
                                onMergeClick(record)
                            },
                            contentDescription = mergeActionDescription
                        )
                    ) {
                        itemContent(Modifier)
                    }
                }
            }
        }
    }
}

@Composable
fun LatestReadItem(
    record: ReadRecord,
    loadBookCover: suspend (String, String) -> String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    inSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    var coverPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(record.bookName, record.bookAuthor) {
        coverPath = loadBookCover(record.bookName, record.bookAuthor)
    }
    val unknownAuthor = stringResource(R.string.unknown_author)
    val author = record.bookAuthor.ifBlank { unknownAuthor }
    val lastReadText = DateUtil.format(Date(record.lastRead), "yyyy-MM-dd HH:mm")
    val itemDescription = stringResource(
        R.string.a11y_read_record_latest_item,
        record.bookName,
        author,
        formatDuring(record.readTime),
        lastReadText
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectionBackground(isSelected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = itemDescription
                role = Role.Button
            }
            .adaptiveHorizontalPadding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoilBookCover(
            name = record.bookName,
            author = record.bookAuthor,
            path = coverPath,
            modifier = Modifier.width(44.dp)
        )

        SelectionCheckmark(inSelectionMode, isSelected)

        Spacer(modifier = Modifier.width(if (inSelectionMode) 8.dp else 16.dp))

        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = record.bookName,
                style = LegadoTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AppText(
                text = author,
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
                        append(lastReadText)
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
    loadBookCover: suspend (String, String) -> String?,
    loadChapterTitle: suspend (String, String, Long) -> String?,
    onBookClick: (String, String) -> Unit,
    onLongClick: () -> Unit = {},
    inSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val session = item.session
    var coverPath by remember { mutableStateOf<String?>(null) }
    val loadingText = stringResource(R.string.loading)
    val fallbackChapterTitle = stringResource(R.string.chapter_index_format, session.words)
    var chapterTitle by remember { mutableStateOf<String?>(loadingText) }

    LaunchedEffect(session.bookName, session.bookAuthor, session.words, fallbackChapterTitle) {
        coverPath = loadBookCover(session.bookName, session.bookAuthor)
        val title = loadChapterTitle(session.bookName, session.bookAuthor, session.words)
        chapterTitle = title ?: fallbackChapterTitle
    }

    val endTimeText = DateUtil.format(Date(session.endTime), "HH:mm")
    val unknownAuthor = stringResource(R.string.unknown_author)
    val author = session.bookAuthor.ifBlank { unknownAuthor }
    val duration = formatDuring(session.endTime - session.startTime)
    val itemDescription = stringResource(
        R.string.a11y_read_record_timeline_item,
        session.bookName,
        author,
        chapterTitle.orEmpty(),
        duration,
        endTimeText
    )

    val nodeRadius = 4.dp
    val lineWidth = 2.dp
    val timelineX = 24.dp
    val contentPaddingStart = 32.dp

    val lineColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val nodeColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .selectionBackground(isSelected)
            .combinedClickable(
                onClick = { onBookClick(session.bookName, session.bookAuthor) },
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = itemDescription
                role = Role.Button
            }
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
                CoilBookCover(
                    name = session.bookName,
                    author = session.bookAuthor,
                    path = coverPath,
                    modifier = Modifier.width(44.dp)
                )
                SelectionCheckmark(inSelectionMode, isSelected)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    AppText(
                        text = session.bookName,
                        style = LegadoTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AppText(
                        text = author,
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
    loadBookCover: suspend (String, String) -> String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    inSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    var coverPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(detail.bookName, detail.bookAuthor) {
        coverPath = loadBookCover(detail.bookName, detail.bookAuthor)
    }
    val unknownAuthor = stringResource(R.string.unknown_author)
    val author = detail.bookAuthor.ifBlank { unknownAuthor }
    val itemDescription = stringResource(
        R.string.a11y_read_record_detail_item,
        detail.bookName,
        author,
        formatDuring(detail.readTime)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectionBackground(isSelected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = itemDescription
                role = Role.Button
            }
            .adaptiveHorizontalPadding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoilBookCover(
            name = detail.bookName,
            author = detail.bookAuthor,
            path = coverPath,
            modifier = Modifier.width(44.dp)
        )

        SelectionCheckmark(inSelectionMode, isSelected)

        Spacer(modifier = Modifier.width(if (inSelectionMode) 8.dp else 16.dp))

        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = detail.bookName,
                style = LegadoTheme.typography.titleMedium,
                maxLines = 1
            )
            AppText(
                text = author,
                style = LegadoTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppText(
                text = stringResource(R.string.reading_time_with_value, formatDuring(detail.readTime)),
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
        modifier = Modifier.adaptiveHorizontalPadding(),
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
                    text = stringResource(R.string.read_duration_done, formatDuring(total)),
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
    loadBookCover: suspend (String, String) -> String?,
    onClick: () -> Unit
) {

    val coverPaths by produceState(initialValue = emptyList(), key1 = bookNamesForCover) {
        value = bookNamesForCover.map { (name, author) ->
            loadBookCover(name, author)
        }
    }

    val totalDurationMinutes = totalTimeMillis / 60000
    val cardDescription = stringResource(
        R.string.a11y_reading_summary_card,
        title,
        bookCount,
        formatDuring(totalTimeMillis)
    )

    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = cardDescription
                role = Role.Button
            }
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
                    text = stringResource(R.string.read_books_prefix),
                        style = LegadoTheme.typography.titleMedium
                    )
                    AppText(
                        text = "$bookCount",
                        style = LegadoTheme.typography.headlineMedium,
                        color = LegadoTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                AppText(
                    text = stringResource(R.string.read_books_suffix),
                        style = LegadoTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val hours = totalDurationMinutes / 60
                val minutes = totalDurationMinutes % 60
                val timeString = if (hours > 0) {
                    stringResource(R.string.hours_minutes_format, hours, minutes)
                } else {
                    stringResource(R.string.minutes_format, minutes)
                }

                AppText(
                    text = stringResource(R.string.total_reading_time_format, timeString),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant
                )
            }

            if (bookNamesForCover.isNotEmpty()) {
                val combined = bookNamesForCover.zip(coverPaths)
                BookStackView(books = combined)
            }
        }
    }
}

@Composable
fun BookStackView(books: List<Pair<Pair<String, String>, String?>>) {
    val xOffsetStep = 12.dp
    val stackWidth = 44.dp + (xOffsetStep * (books.size - 1).coerceAtLeast(0))

    Box(
        modifier = Modifier
            .width(stackWidth)
            .height(64.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        books.forEachIndexed { index, (info, path) ->
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
                    CoilBookCover(
                        name = info.first,
                        author = info.second,
                        path = path,
                        modifier = Modifier.width(44.dp)
                    )
                }
            }
        }
    }
}

fun formatDuring(mss: Long): String {
    return formatReadDuration(mss)
}
