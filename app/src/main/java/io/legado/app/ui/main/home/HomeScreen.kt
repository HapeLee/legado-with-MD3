package io.legado.app.ui.main.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.ui.config.backupConfig.BackupOptionSheet
import io.legado.app.ui.config.backupConfig.RestoreOptionSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.image.cover.BookshelfCover
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.takePersistablePermissionSafely
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun HomeRouteScreen(
    onOpenBook: (Book) -> Unit,
    onOpenBackupSettings: () -> Unit,
    onNavigateToReadRecord: () -> Unit,
    onNavigateToReadRecordOverview: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingBackupDestination by remember {
        mutableStateOf<HomeBackupDestination?>(null)
    }
    val backupDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val destination = pendingBackupDestination
        pendingBackupDestination = null
        if (uri != null && destination != null) {
            uri.takePersistablePermissionSafely(context)
            val path = if (uri.isContentScheme()) {
                uri.toString()
            } else {
                uri.path.orEmpty()
            }
            if (path.isNotEmpty()) {
                viewModel.onIntent(
                    HomeIntent.BackupDirectorySelected(
                        destination = destination,
                        path = path,
                    )
                )
            }
        }
    }
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            viewModel.onIntent(HomeIntent.RestoreLocalFileSelected(it.toString()))
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is HomeEffect.OpenBook -> onOpenBook(effect.book)
                HomeEffect.OpenBackupSettings -> onOpenBackupSettings()
                is HomeEffect.SelectBackupDirectory -> {
                    pendingBackupDestination = effect.destination
                    runCatching { backupDirectoryLauncher.launch(null) }
                }

                is HomeEffect.RequestBackupStoragePermission -> {
                    PermissionsCompat.Builder()
                        .addPermissions(*Permissions.Group.STORAGE)
                        .rationale(R.string.tip_perm_request_storage)
                        .onGranted {
                            viewModel.onIntent(
                                HomeIntent.BackupDirectorySelected(
                                    destination = effect.destination,
                                    path = effect.path,
                                )
                            )
                        }
                        .request()
                }

                HomeEffect.SelectRestoreFile -> {
                    restoreFileLauncher.launch(arrayOf("application/zip"))
                }

                is HomeEffect.ShowMessage -> {
                    val message = buildString {
                        append(context.getString(effect.messageRes))
                        effect.detail?.takeIf { it.isNotBlank() }?.let {
                            append('\n')
                            append(it)
                        }
                    }
                    context.toastOnUi(message)
                }
            }
        }
    }

    HomeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateToReadRecord = onNavigateToReadRecord,
        onNavigateToReadRecordOverview = onNavigateToReadRecordOverview,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    onNavigateToReadRecord: () -> Unit = {},
    onNavigateToReadRecordOverview: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.home),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "recent_book") {
                RecentBookCard(
                    book = state.recentBook,
                    onClick = { onIntent(HomeIntent.RecentBookClick) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }

            item(key = "statistics") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatisticCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = stringResource(R.string.home_total_read_books),
                        value = state.totalReadBooks.toString(),
                        unit = stringResource(R.string.unit_books),
                        onClick = onNavigateToReadRecord,
                    )
                    StatisticCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AccessTime,
                        title = stringResource(R.string.home_total_reading_time),
                        value = String.format("%.1f", state.totalReadTimeMillis / 3_600_000.0),
                        unit = stringResource(R.string.unit_hours),
                        onClick = onNavigateToReadRecordOverview,
                    )
                }
            }

            item(key = "daily_goal") {
                ReadingGoalCard(
                    todayReadTimeMillis = state.todayReadTimeMillis,
                    goalMinutes = state.dailyGoalMinutes,
                    onClick = { onIntent(HomeIntent.ReadingGoalClick) },
                )
            }

            item(key = "webdav") {
                WebDavBackupCard(
                    latestBackup = state.latestBackup,
                    isLoading = state.isBackupLoading,
                    isActionRunning = state.isBackupActionRunning,
                    onBackup = { onIntent(HomeIntent.BackupClick) },
                    onRestore = { onIntent(HomeIntent.RestoreClick) },
                    onOpenSettings = { onIntent(HomeIntent.BackupSettingsClick) },
                )
            }
        }
    }

    HomeDialogs(
        dialog = state.activeDialog,
        onIntent = onIntent,
    )
    HomeSheets(
        sheet = state.activeSheet,
        onIntent = onIntent,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RecentBookCard(
    book: HomeRecentBookUi?,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    NormalCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        onClick = if (book?.bookUrl != null) onClick else null,
        cornerRadius = 20.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            book?.chapterProgress?.let { progress ->
                RecentReadingProgress(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = LegadoTheme.colorScheme.primary,
                    )
                    AppText(
                        text = stringResource(R.string.home_recent_reading),
                        style = LegadoTheme.typography.titleMediumEmphasized,
                    )
                }

                if (book == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        AppText(
                            text = stringResource(R.string.home_no_recent_reading),
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookshelfCover(
                            name = book.name,
                            author = book.author,
                            path = book.coverPath,
                            sourceOrigin = book.origin,
                            modifier = Modifier
                                .width(72.dp)
                                .aspectRatio(5f / 7f),
                            coverModifier = Modifier.fillMaxSize(),
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedCoverKey = book.bookUrl?.let { "home_recent_$it" },
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AppText(
                                text = book.name,
                                style = LegadoTheme.typography.titleMediumEmphasized,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            AppText(
                                text = book.author,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val chapterTitle = book.chapterTitle?.takeIf { it.isNotBlank() }
                            if (chapterTitle != null || book.chapterProgress != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    chapterTitle?.let { chapter ->
                                        AppText(
                                            text = chapter,
                                            modifier = Modifier
                                                .weight(1f)
                                                .basicMarquee(),
                                            style = LegadoTheme.typography.bodySmall,
                                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip,
                                        )
                                    } ?: Spacer(modifier = Modifier.weight(1f))
                                    book.chapterProgress?.let { progress ->
                                        val percent = remember(progress) {
                                            (progress * 100f)
                                                .roundToInt()
                                                .coerceIn(0, 100)
                                        }
                                        TextCard(
                                            text = "$percent%",
                                            backgroundColor = LegadoTheme.colorScheme.secondaryContainer,
                                            contentColor = LegadoTheme.colorScheme.onSecondaryContainer,
                                            cornerRadius = 6.dp,
                                            horizontalPadding = 6.dp,
                                            verticalPadding = 2.dp,
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
}

@Composable
private fun RecentReadingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "RecentReadingProgress",
    )
    val progressColor = LegadoTheme.colorScheme.primary
    val trackColor = LegadoTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Canvas(
        modifier = modifier.height(48.dp),
    ) {
        val progressWidth = size.width * animatedProgress
        val barHeight = 3.dp.toPx()
        val glowHeight = size.height - barHeight
        val stripHeight = 1.dp.toPx().coerceAtLeast(1f)
        val maxDiffusion = 48.dp.toPx()

        if (progressWidth > 0f) {
            var y = 0f
            while (y < glowHeight) {
                val distanceFromTop = (y / glowHeight).coerceIn(0f, 1f)
                val alpha = 0.18f * distanceFromTop * distanceFromTop
                val diffusion = maxDiffusion * (1f - distanceFromTop)
                val fadeStart = (progressWidth - diffusion * 0.15f)
                    .coerceAtLeast(0f)
                val fadeEnd = (progressWidth + diffusion)
                    .coerceAtMost(size.width)
                val height = stripHeight.coerceAtMost(glowHeight - y)

                if (alpha > 0.001f && fadeEnd > fadeStart) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                progressColor.copy(alpha = alpha),
                                progressColor.copy(alpha = 0f),
                            ),
                            startX = fadeStart,
                            endX = fadeEnd,
                        ),
                        topLeft = Offset(0f, y),
                        size = Size(fadeEnd, height),
                    )
                }
                y += stripHeight
            }
        }
        drawRect(
            color = trackColor,
            topLeft = Offset(0f, size.height - barHeight),
            size = Size(size.width, barHeight),
        )
        drawRect(
            color = progressColor,
            topLeft = Offset(0f, size.height - barHeight),
            size = Size(progressWidth, barHeight),
        )
    }
}

@Composable
private fun StatisticCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    unit: String,
    onClick: () -> Unit,
) {
    NormalCard(
        modifier = modifier,
        onClick = onClick,
        cornerRadius = 20.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = 10.dp,
                    end = 16.dp,
                    bottom = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column {
                AppText(
                    text = title,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    AppText(
                        text = value,
                        style = LegadoTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        modifier = Modifier
                            .alignBy(FirstBaseline)
                            .basicMarquee()
                    )
                    AppText(
                        text = unit,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .alignBy(FirstBaseline)
                            .padding(start = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingGoalCard(
    todayReadTimeMillis: Long,
    goalMinutes: Int,
    onClick: () -> Unit,
) {
    val todayMinutes = (todayReadTimeMillis / 60_000L).toInt()
    val progress = (todayReadTimeMillis / 60_000f / goalMinutes)
        .coerceIn(0f, 1f)

    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        cornerRadius = 20.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppIcon(
                    imageVector = Icons.Default.TrackChanges,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.primary,
                )
                AppText(
                    text = stringResource(R.string.home_today_reading_goal),
                    style = LegadoTheme.typography.titleMediumEmphasized,
                )
                Spacer(modifier = Modifier.weight(1f))
                MediumTonalButton(
                    onClick = onClick,
                    icon = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.home_set_goal),
                )
            }

            SemiCircleProgress(
                progress = progress,
                modifier = Modifier
                    .width(240.dp)
                    .padding(vertical = 8.dp)
                    .aspectRatio(2f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppText(
                        text = stringResource(
                            R.string.home_today_goal_value,
                            todayMinutes,
                            goalMinutes,
                        ),
                        style = LegadoTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    AppText(
                        text = stringResource(R.string.home_minutes),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SemiCircleProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "DailyReadingProgress",
    )
    val trackColor = LegadoTheme.colorScheme.surfaceContainerHighest
    val progressColor = LegadoTheme.colorScheme.primary

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val arcSize = size.width - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val boundingSize = Size(arcSize, arcSize)
            drawArc(
                color = trackColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = boundingSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = 180f,
                sweepAngle = 180f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = boundingSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
private fun WebDavBackupCard(
    latestBackup: HomeBackupUi?,
    isLoading: Boolean,
    isActionRunning: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val lastBackupText = when {
        isLoading -> stringResource(R.string.home_loading_webdav_backup)
        latestBackup != null -> {
            val date = remember(latestBackup.lastModify) {
                DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                ).format(Date(latestBackup.lastModify))
            }
            stringResource(R.string.home_latest_backup_value, date)
        }

        else -> stringResource(R.string.home_no_webdav_backup)
    }

    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppIcon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.primary,
                    modifier = Modifier.offset(y = 1.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = stringResource(R.string.home_webdav_backup),
                        style = LegadoTheme.typography.titleMediumEmphasized,
                    )
                    AppText(
                        text = lastBackupText,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isLoading || isActionRunning) {
                    AppContainedLoadingIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onBackup,
                    enabled = !isActionRunning,
                    icon = Icons.Default.CloudUpload,
                    text = stringResource(R.string.backup),
                )
                SmallTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRestore,
                    enabled = !isActionRunning,
                    icon = Icons.Default.CloudDownload,
                    text = stringResource(R.string.restore),
                )
                SmallTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSettings,
                    enabled = !isActionRunning,
                    icon = Icons.Default.Settings,
                    text = stringResource(R.string.setting),
                )
            }
        }
    }
}

@Composable
private fun HomeDialogs(
    dialog: HomeDialog?,
    onIntent: (HomeIntent) -> Unit,
) {
    val goalDialog = dialog as? HomeDialog.SetReadingGoal
    var goalInput by remember(goalDialog?.currentMinutes) {
        mutableStateOf(goalDialog?.currentMinutes?.toString().orEmpty())
    }
    val goalMinutes = goalInput.toIntOrNull()
    val isGoalValid = goalMinutes != null &&
            goalMinutes in 1..MAX_DAILY_READING_GOAL_MINUTES

    AppAlertDialog(
        show = goalDialog != null,
        onDismissRequest = { onIntent(HomeIntent.DismissDialog) },
        title = stringResource(R.string.home_set_reading_goal),
        content = {
            OutlinedTextField(
                value = goalInput,
                onValueChange = { value ->
                    goalInput = value.filter(Char::isDigit)
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    AppText(stringResource(R.string.home_goal_minutes_label))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = if (isGoalValid) {
            {
                onIntent(HomeIntent.UpdateReadingGoal(goalMinutes))
            }
        } else {
            null
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(HomeIntent.DismissDialog) },
    )

    val restoreDialog = dialog as? HomeDialog.ConfirmRestore
    AppAlertDialog(
        show = restoreDialog != null,
        onDismissRequest = { onIntent(HomeIntent.DismissDialog) },
        title = stringResource(R.string.restore_confirmation),
        text = restoreDialog?.let {
            stringResource(R.string.home_webdav_restore_confirmation, it.backupName)
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(HomeIntent.ConfirmRestore) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(HomeIntent.DismissDialog) },
    )
}

@Composable
private fun HomeSheets(
    sheet: HomeSheet?,
    onIntent: (HomeIntent) -> Unit,
) {
    BackupOptionSheet(
        show = sheet is HomeSheet.BackupOptions,
        onDismissRequest = { onIntent(HomeIntent.DismissSheet) },
        onBackupToLocal = {
            onIntent(
                HomeIntent.BackupDestinationSelected(HomeBackupDestination.Local)
            )
        },
        onBackupToNetwork = {
            onIntent(
                HomeIntent.BackupDestinationSelected(HomeBackupDestination.WebDav)
            )
        },
        onBackupToLocalAndNetwork = {
            onIntent(
                HomeIntent.BackupDestinationSelected(HomeBackupDestination.LocalAndWebDav)
            )
        },
    )
    RestoreOptionSheet(
        show = sheet is HomeSheet.RestoreOptions,
        onDismissRequest = { onIntent(HomeIntent.DismissSheet) },
        onRestoreFromLocal = { onIntent(HomeIntent.RestoreFromLocal) },
        onRestoreFromNetwork = { onIntent(HomeIntent.RestoreFromNetwork) },
    )
}
