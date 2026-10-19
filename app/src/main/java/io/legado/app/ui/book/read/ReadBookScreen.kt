package io.legado.app.ui.book.read

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.domain.usecase.BookmarkTargetVerdict
import io.legado.app.ui.book.read.sheet.AiRewritePresetConfigSheet
import io.legado.app.ui.book.read.sheet.AiTextCleanSheet
import io.legado.app.ui.book.read.sheet.AiTextRewriteSheet
import io.legado.app.ui.book.read.sheet.BgTextConfigSheet
import io.legado.app.ui.book.read.sheet.ChangeChapterSourceSheet
import io.legado.app.ui.book.read.sheet.ChapterSummarySheet
import io.legado.app.ui.book.read.sheet.CharsetConfigSheet
import io.legado.app.ui.book.read.sheet.ClickActionConfigSheet
import io.legado.app.ui.book.read.sheet.ContentEditSheet
import io.legado.app.ui.book.read.sheet.DownloadSheet
import io.legado.app.ui.book.read.sheet.EyeProtectionConfigSheet
import io.legado.app.ui.book.read.sheet.FloatingBarIconConfigSheet
import io.legado.app.ui.book.read.sheet.HighlightRuleConfigSheet
import io.legado.app.ui.book.read.sheet.MarkingSheet
import io.legado.app.ui.book.read.sheet.MoreConfigSheet
import io.legado.app.ui.book.read.sheet.PageAnimConfigSheet
import io.legado.app.ui.book.read.sheet.PageKeyConfigSheet
import io.legado.app.ui.book.read.sheet.PhotoSheet
import io.legado.app.ui.book.read.sheet.ReadAloudConfigContent
import io.legado.app.ui.book.read.sheet.ReadAloudNumberConfigSheet
import io.legado.app.ui.book.read.sheet.ReaderMoreActionsSheet
import io.legado.app.ui.book.read.sheet.ShadowSetSheet
import io.legado.app.ui.book.read.sheet.SimulatedReadingSheet
import io.legado.app.ui.book.read.sheet.TextProcessingSheet
import io.legado.app.ui.book.read.sheet.TitleBarActionConfigSheet
import io.legado.app.ui.book.read.sheet.ToolButtonConfigSheet
import io.legado.app.ui.book.read.sheet.UnderlineConfigSheet
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerViewModel
import io.legado.app.ui.dict.DictSheet
import io.legado.app.ui.widget.components.FontFolderState
import io.legado.app.ui.widget.components.FontSelectSheet
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.bookmark.BookmarkEditSheet
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.changeSource.ChangeSourceSheet
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.log.AppLogSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest

/**
 * Stateless reader overlays: back handling, dialogs and sheets.
 * [ReadBookRouteScreen] owns the Compose Canvas reading surface.
 */
@Composable
fun ReadBookOverlayRoute(
    viewModel: ReadBookViewModel,
    state: ReadBookUiState,
    preferences: ReadPreferences,
    onOpenTextSelectMenuConfig: () -> Unit,
    onPickBookmarkBadgeImage: () -> Unit,
    onResetBookmarkBadge: () -> Unit,
    sheetsVisible: Boolean = true,
    additionalSheetContent: @Composable (ReadBookSheet, Boolean, () -> Unit) -> Unit = { _, _, _ -> },
) {
    val aiActive = rememberFeatureActivated(
        state.findSheet<ReadBookSheet.ChapterSummary>() != null ||
                state.findSheet<ReadBookSheet.AiTextClean>() != null ||
                state.findSheet<ReadBookSheet.AiTextRewrite>() != null ||
                state.findSheet<ReadBookSheet.AiRewritePresetConfig>() != null
    )
    val highlightActive = rememberFeatureActivated(
        state.findSheet<ReadBookSheet.HighlightRuleConfig>() != null
    )
    val markingActive = rememberFeatureActivated(state.findSheet<ReadBookSheet.Marking>() != null)
    val contentEditActive =
        rememberFeatureActivated(state.findSheet<ReadBookSheet.ContentEdit>() != null)
    val contentProcessActive = rememberFeatureActivated(
        state.findSheet<ReadBookSheet.TextProcessing>() != null
    )
    val aiState = if (aiActive) {
        viewModel.aiState.collectAsStateWithLifecycle().value
    } else ReadAiUiState()
    val highlightRuleState = if (highlightActive) {
        viewModel.highlightRuleState.collectAsStateWithLifecycle().value
    } else HighlightRuleConfigUiState()
    val markingState = if (markingActive) {
        viewModel.markingState.collectAsStateWithLifecycle().value
    } else MarkingUiState()
    val contentEditState = if (contentEditActive) {
        viewModel.contentEditState.collectAsStateWithLifecycle().value
    } else ContentEditUiState()
    val contentProcessState = if (contentProcessActive) {
        viewModel.contentProcessState.collectAsStateWithLifecycle().value
    } else ContentProcessConfigUiState()
    ReadBookScreen(
        state = state,
        sheetsVisible = sheetsVisible,
        additionalSheetContent = additionalSheetContent,
        aiState = aiState,
        highlightRuleState = highlightRuleState,
        markingState = markingState,
        contentEditState = contentEditState,
        contentProcessState = contentProcessState,
        preferences = preferences,
        onIntent = viewModel::onIntent,
        onOpenTextSelectMenuConfig = onOpenTextSelectMenuConfig,
        onPickBookmarkBadgeImage = onPickBookmarkBadgeImage,
        onResetBookmarkBadge = onResetBookmarkBadge,
    )
}

@Composable
private fun rememberFeatureActivated(active: Boolean): Boolean {
    var activated by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active) activated = true
    }
    return active || activated
}

@Composable
fun ReadBookScreen(
    state: ReadBookUiState,
    sheetsVisible: Boolean = true,
    additionalSheetContent: @Composable (ReadBookSheet, Boolean, () -> Unit) -> Unit = { _, _, _ -> },
    aiState: ReadAiUiState,
    highlightRuleState: HighlightRuleConfigUiState,
    markingState: MarkingUiState,
    contentEditState: ContentEditUiState,
    contentProcessState: ContentProcessConfigUiState,
    preferences: ReadPreferences,
    onIntent: (ReadBookIntent) -> Unit,
    onOpenTextSelectMenuConfig: () -> Unit,
    onPickBookmarkBadgeImage: () -> Unit,
    onResetBookmarkBadge: () -> Unit,
) {
    // Dialogs driven by activeDialog state
    val restoreDialog = state.activeDialog as? ReadBookDialog.ConfirmRestoreProgress
    val syncDialog = state.activeDialog as? ReadBookDialog.SureSyncProgress
    val restoreLastProgressDialog = state.activeDialog as? ReadBookDialog.RestoreLastBookProgress
    val skipDialog = state.activeDialog as? ReadBookDialog.ConfirmSkipToChapter
    val payDialog = state.activeDialog as? ReadBookDialog.ConfirmChapterPay
    val addToBookshelfDialog = state.activeDialog as? ReadBookDialog.ConfirmAddToBookshelf
    val readRecordAliasDialog = state.activeDialog as? ReadBookDialog.ReadRecordAliasConflict
    var rememberAliasChoice by remember(readRecordAliasDialog) { mutableStateOf(false) }

    AppAlertDialog(
        show = readRecordAliasDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.ResolveReadRecordAlias(false, rememberAliasChoice)) },
        title = stringResource(R.string.read_record_alias_title),
        text = readRecordAliasDialog?.let {
            stringResource(R.string.read_record_alias_message, it.bookName, it.readTime / 60000, it.author)
        }.orEmpty(),
        confirmText = stringResource(R.string.read_record_alias_merge),
        onConfirm = { onIntent(ReadBookIntent.ResolveReadRecordAlias(true, rememberAliasChoice)) },
        dismissText = stringResource(R.string.read_record_alias_keep_separate),
        onDismiss = { onIntent(ReadBookIntent.ResolveReadRecordAlias(false, rememberAliasChoice)) },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                CheckboxItem(
                    title = stringResource(R.string.read_record_alias_remember),
                    checked = rememberAliasChoice,
                    onCheckedChange = { rememberAliasChoice = it },
                )
                MediumPlainButton(
                    onClick = {
                        onIntent(ReadBookIntent.ClearReadRecordAliasDecisions)
                    },
                    modifier = Modifier.align(Alignment.End),
                    text = stringResource(R.string.read_record_alias_revoke),
                )
            }
        },
    )

    AppAlertDialog(
        show = restoreDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
        title = stringResource(R.string.restore_progress),
        text = stringResource(R.string.found_cloud_progress),
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            restoreDialog?.let { onIntent(ReadBookIntent.SureNewProgress(it.progress)) }
            onIntent(ReadBookIntent.DismissDialog)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDialog) },
    )
    AppAlertDialog(
        show = syncDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
        title = stringResource(R.string.sync_progress),
        text = stringResource(R.string.progress_exceeds_cloud),
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            syncDialog?.let { onIntent(ReadBookIntent.SureSyncProgress(it.progress)) }
            onIntent(ReadBookIntent.DismissDialog)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDialog) },
    )
    AppAlertDialog(
        show = restoreLastProgressDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.KeepCurrentBookProgress) },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.restore_last_book_process),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(ReadBookIntent.RestoreLastBookProgress) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.KeepCurrentBookProgress) },
    )
    AppAlertDialog(
        show = skipDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
        title = stringResource(R.string.chapter_list),
        text = stringResource(R.string.confirm_skip_to_chapter),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(ReadBookIntent.DismissDialog) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDialog) },
    )
    AppAlertDialog(
        show = payDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
        title = stringResource(R.string.chapter_pay),
        text = payDialog?.chapterTitle ?: "",
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            onIntent(ReadBookIntent.DismissDialog)
            onIntent(ReadBookIntent.ConfirmPayAction)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDialog) },
    )
    AppAlertDialog(
        show = addToBookshelfDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.ExitWithoutAddingCurrentBookToBookshelf) },
        title = stringResource(R.string.add_to_bookshelf),
        text = stringResource(
            R.string.check_add_bookshelf,
            addToBookshelfDialog?.bookName.orEmpty()
        ),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(ReadBookIntent.ConfirmAddCurrentBookToBookshelf) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.ExitWithoutAddingCurrentBookToBookshelf) },
    )

    // 书签/笔记跳转前校验未通过的确认框
    val pendingTarget = state.pendingBookmarkTarget
    AppAlertDialog(
        show = pendingTarget != null,
        onDismissRequest = { onIntent(ReadBookIntent.CancelBookmarkTargetJump) },
        title = stringResource(R.string.bookmark_target_may_shift),
        text = stringResource(
            when (pendingTarget?.verdict) {
                is BookmarkTargetVerdict.SourceChanged ->
                    R.string.bookmark_target_source_changed

                BookmarkTargetVerdict.TitleMismatch ->
                    R.string.bookmark_target_title_mismatch

                null -> R.string.bookmark_target_title_mismatch
                BookmarkTargetVerdict.Match -> R.string.bookmark_target_title_mismatch
            }
        ),
        confirmText = stringResource(R.string.bookmark_target_jump_anyway),
        onConfirm = { onIntent(ReadBookIntent.ConfirmBookmarkTargetJump) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.CancelBookmarkTargetJump) },
    )

    // Keep each layer keyed and mounted until its own sheet exit animation has finished.
    // Native modal windows must be created in stack order, including after recreation.
    val layers = remember { androidx.compose.runtime.mutableStateListOf<ReaderSheetLayer>() }
    var nextLayerId by rememberSaveable { mutableStateOf(0L) }
    var activeLayerIds by rememberSaveable { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(state.sheetStack) {
        val previous = layers.filter { it.present }
        val nextIds = mutableMapOf<String, Long>()
        layers.indices.forEach { index -> layers[index] = layers[index].copy(present = false) }
        state.sheetStack.forEach { sheet ->
            val kind = checkNotNull(sheet::class.qualifiedName)
            val retained = previous.find { it.sheet::class == sheet::class }
            if (retained == null) {
                val id = activeLayerIds[kind] ?: nextLayerId++
                layers.add(ReaderSheetLayer(id, sheet, true))
                nextIds[kind] = id
            } else {
                val index = layers.indexOfFirst { it.id == retained.id }
                layers[index] = retained.copy(sheet = sheet, present = true)
                nextIds[kind] = retained.id
            }
        }
        activeLayerIds = nextIds.toMap()
    }
    layers.forEach { layer ->
        androidx.compose.runtime.key(layer.id) {
            val alive = io.legado.app.ui.book.read.sheet.rememberSheetAlive(
                layer.present,
                exitMillis = 600L
            )
            if (alive) {
                val visible = layer.present && sheetsVisible
                val layerIntent: (ReadBookIntent) -> Unit = { intent ->
                    if (layer.present) {
                        onIntent(
                            if (intent == ReadBookIntent.DismissSheet ||
                                intent == ReadBookIntent.SetActiveSheet(null)
                            ) ReadBookIntent.DismissSheetIfTop(layer.sheet) else intent
                        )
                    }
                }
                ReadBookSheetContent(
                    state = state.copy(
                        sheetStack = kotlinx.collections.immutable.persistentListOf(
                            layer.sheet
                        )
                    ),
                    sheetsVisible = visible,
                    aiState = aiState,
                    highlightRuleState = highlightRuleState,
                    markingState = markingState,
                    contentEditState = contentEditState,
                    contentProcessState = contentProcessState,
                    preferences = preferences,
                    onIntent = layerIntent,
                    onOpenTextSelectMenuConfig = onOpenTextSelectMenuConfig,
                    onPickBookmarkBadgeImage = onPickBookmarkBadgeImage,
                    onResetBookmarkBadge = onResetBookmarkBadge,
                )
                additionalSheetContent(layer.sheet, visible) {
                    layerIntent(ReadBookIntent.DismissSheet)
                }
            } else {
                androidx.compose.runtime.SideEffect { layers.removeAll { it.id == layer.id } }
            }
        }
    }
}

@androidx.compose.runtime.Stable
private data class ReaderSheetLayer(
    val id: Long,
    val sheet: ReadBookSheet,
    val present: Boolean,
)

@Composable
private fun ReadBookSheetContent(
    state: ReadBookUiState,
    sheetsVisible: Boolean,
    aiState: ReadAiUiState,
    highlightRuleState: HighlightRuleConfigUiState,
    markingState: MarkingUiState,
    contentEditState: ContentEditUiState,
    contentProcessState: ContentProcessConfigUiState,
    preferences: ReadPreferences,
    onIntent: (ReadBookIntent) -> Unit,
    onOpenTextSelectMenuConfig: () -> Unit,
    onPickBookmarkBadgeImage: () -> Unit,
    onResetBookmarkBadge: () -> Unit,
) {
    val visibleState =
        if (sheetsVisible) state else state.copy(sheetStack = kotlinx.collections.immutable.persistentListOf())

    // Each mounted layer composes its own sheet; show controls its enter/exit animation.
    fun dismissSheet(sheet: ReadBookSheet?): () -> Unit = {
        sheet?.let { onIntent(ReadBookIntent.DismissSheetIfTop(it)) }
    }

    // 多角色分配弹层（点击正文角色胶囊打开）
    if (state.activeSheet is ReadBookSheet.RoleCast) {
        io.legado.app.ui.book.read.sheet.ReadAloudCastSheet(
            show = visibleState.findSheet<ReadBookSheet.RoleCast>() != null,
            ordinal = visibleState.findSheet<ReadBookSheet.RoleCast>()?.ordinal ?: -1,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.RoleCast>()),
            onIntent = onIntent,
            menuConfig = state.menuConfig,
        )
    }

    // 段首配乐弹层（点击正文 ♪ 胶囊打开）
    if (state.activeSheet is ReadBookSheet.BgmScene) {
        io.legado.app.ui.book.read.sheet.BgmSceneSheet(
            show = visibleState.findSheet<ReadBookSheet.BgmScene>() != null,
            paragraphIndex = visibleState.findSheet<ReadBookSheet.BgmScene>()?.paragraphIndex ?: -1,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.BgmScene>()),
            onIntent = onIntent,
            menuConfig = state.menuConfig,
        )
    }

    // 本章背景音乐总览（配乐弹层/朗读面板打开，一屏改完本章所有区间）
    if (state.activeSheet is ReadBookSheet.BgmSceneTable) {
        io.legado.app.ui.book.read.sheet.BgmSceneTableSheet(
            show = visibleState.findSheet<ReadBookSheet.BgmSceneTable>() != null,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.BgmSceneTable>()),
            onIntent = onIntent,
        )
    }

    // AI 分配角色弹层
    if (state.activeSheet is ReadBookSheet.AiCastDialog) {
        io.legado.app.ui.book.read.sheet.AiCastDialogSheet(
            show = visibleState.findSheet<ReadBookSheet.AiCastDialog>() != null,
            sceneOnly = visibleState.findSheet<ReadBookSheet.AiCastDialog>()?.sceneOnly == true,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.AiCastDialog>()),
            onIntent = onIntent,
        )
    }
    // 听书下载：项目通用 AppModalBottomSheet，和其它弹层一样 by show 驱动进出场
    if (state.activeSheet is ReadBookSheet.AudioDownload) {
        io.legado.app.ui.book.read.sheet.ReaderAudioDownloadSheet(
            show = visibleState.findSheet<ReadBookSheet.AudioDownload>() != null,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.AudioDownload>()),
        )
    }

    if (state.activeSheet is ReadBookSheet.ShadowSet) {
        ShadowSetSheet(
            show = visibleState.findSheet<ReadBookSheet.ShadowSet>() != null,
            config = state.sheetConfig,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.ShadowSet>()),
            onIntent = onIntent,
        )
    }
    if (state.activeSheet is ReadBookSheet.TextProcessing) {
        TextProcessingSheet(
            show = visibleState.findSheet<ReadBookSheet.TextProcessing>() != null,
            book = state.book,
            allRules = state.allReplaceRules,
            effectiveRules = state.effectiveReplaceRules,
            replaceEnabled = state.useReplaceRule,
            contentProcessState = contentProcessState,
            onIntent = onIntent,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.TextProcessing>()),
        )
    }
    if (state.activeSheet is ReadBookSheet.MoreActions) {
        ReaderMoreActionsSheet(
            show = visibleState.findSheet<ReadBookSheet.MoreActions>() != null,
            state = state,
            onIntent = onIntent,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.MoreActions>()),
        )
    }
    if (state.activeSheet is ReadBookSheet.UnderlineConfig) {
        UnderlineConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.UnderlineConfig>() != null,
            config = state.sheetConfig,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.UnderlineConfig>()),
            onIntent = onIntent,
        )
    }
    val fontSelectFolderState = remember(preferences.fontFolder) {
        FontFolderState.Loaded(preferences.fontFolder.takeIf { it.isNotEmpty() }?.toUri())
    }
    val fontSelectSystemTypefaces = stringArrayResource(R.array.system_typefaces)
    if (state.activeSheet is ReadBookSheet.FontSelect) {
        FontSelectSheet(
            show = visibleState.findSheet<ReadBookSheet.FontSelect>() != null,
            title = stringResource(R.string.select_font),
            folderState = fontSelectFolderState,
            selectedFontPath = state.styleConfig.textFont,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.FontSelect>()),
            onSelectFont = { onIntent(ReadBookIntent.SelectFont(it.uri.toString())) },
            onSelectSystemTypeface = { onIntent(ReadBookIntent.SelectSystemTypeface(it)) },
            onOpenFolderPicker = { onIntent(ReadBookIntent.OpenFontFolderPicker) },
            systemTypefaces = fontSelectSystemTypefaces,
        )
    }
    if (state.activeSheet is ReadBookSheet.TitleFontSelect) {
        FontSelectSheet(
            show = visibleState.findSheet<ReadBookSheet.TitleFontSelect>() != null,
            title = stringResource(R.string.read_config_title_settings),
            folderState = fontSelectFolderState,
            selectedFontPath = state.styleConfig.titleFont,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.TitleFontSelect>()),
            onSelectFont = { onIntent(ReadBookIntent.SelectTitleFont(it.uri.toString())) },
            onSelectSystemTypeface = { onIntent(ReadBookIntent.SelectTitleSystemTypeface(it)) },
            onOpenFolderPicker = { onIntent(ReadBookIntent.OpenFontFolderPicker) },
            systemTypefaces = fontSelectSystemTypefaces,
        )
    }
    if (state.activeSheet is ReadBookSheet.ToolButtonConfig) {
        ToolButtonConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.ToolButtonConfig>() != null,
            items = state.menuConfig.bottomBarButtons,
            customIcons = state.menuConfig.readMenuCustomIcons,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.ToolButtonConfig>()),
            onIntent = onIntent,
        )
    }
    if (state.activeSheet is ReadBookSheet.EyeProtection) {
        EyeProtectionConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.EyeProtection>() != null,
            enabled = state.eyeProtection.enabled,
            intensity = state.eyeProtection.intensity,
            autoNight = state.eyeProtection.autoNight,
            schedule = state.eyeProtection.schedule,
            startTime = state.eyeProtection.startTime,
            endTime = state.eyeProtection.endTime,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.EyeProtection>()),
            onEnabledChange = { onIntent(ReadBookIntent.EyeProtectionEnabledChanged(it)) },
            onIntensityChange = { onIntent(ReadBookIntent.EyeProtectionIntensityChanged(it)) },
            onAutoNightChange = { onIntent(ReadBookIntent.EyeProtectionAutoNightChanged(it)) },
            onScheduleChange = { onIntent(ReadBookIntent.EyeProtectionScheduleChanged(it)) },
            onStartTimeChange = { onIntent(ReadBookIntent.EyeProtectionStartTimeChanged(it)) },
            onEndTimeChange = { onIntent(ReadBookIntent.EyeProtectionEndTimeChanged(it)) },
        )
    }
    if (state.activeSheet is ReadBookSheet.FloatingBarIconConfig) {
        FloatingBarIconConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.FloatingBarIconConfig>() != null,
            items = state.menuConfig.titleBarButtons,
            customIcons = state.menuConfig.titleBarCustomIcons,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.FloatingBarIconConfig>()),
            onIntent = onIntent,
        )
    }
    if (state.activeSheet is ReadBookSheet.TitleBarActionConfig) {
        TitleBarActionConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.TitleBarActionConfig>() != null,
            items = state.menuConfig.titleBarActions,
            customIcons = state.menuConfig.titleBarCustomIcons,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.TitleBarActionConfig>()),
            onIntent = onIntent,
        )
    }
    if (state.activeSheet is ReadBookSheet.HighlightRuleConfig) {
        HighlightRuleConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.HighlightRuleConfig>() != null,
            state = highlightRuleState,
            allConfigNames = state.sheetConfig.configNames,
            config = state.sheetConfig,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.HighlightRuleConfig>()),
            onIntent = onIntent,
        )
    }
    if (state.activeSheet is ReadBookSheet.Marking) {
        MarkingSheet(
            show = visibleState.findSheet<ReadBookSheet.Marking>() != null,
            state = markingState,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.Marking>()),
            onSave = { style, note ->
                onIntent(ReadBookIntent.SaveMarking(style, note))
            },
            onDelete = { onIntent(ReadBookIntent.DeleteMarking) },
        )
    }
    if (state.activeSheet is ReadBookSheet.ContentEdit) {
        ContentEditSheet(
            show = visibleState.findSheet<ReadBookSheet.ContentEdit>() != null,
            state = contentEditState,
            onIntent = onIntent,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.ContentEdit>()),
        )
    }
    if (state.activeSheet is ReadBookSheet.ChapterSummary) {
        ChapterSummarySheet(
            show = visibleState.findSheet<ReadBookSheet.ChapterSummary>() != null,
            state = aiState.chapterSummary,
            onIntent = onIntent,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.ChapterSummary>()),
        )
    }
    if (state.activeSheet is ReadBookSheet.AiTextClean) {
        AiTextCleanSheet(
            show = visibleState.findSheet<ReadBookSheet.AiTextClean>() != null,
            state = aiState.aiTextClean,
            onIntent = onIntent,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.AiTextClean>()),
        )
    }
    if (state.activeSheet is ReadBookSheet.AiTextRewrite) {
        AiTextRewriteSheet(
            show = visibleState.findSheet<ReadBookSheet.AiTextRewrite>() != null,
            state = aiState.aiTextRewrite,
            onIntent = onIntent,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.AiTextRewrite>()),
        )
    }
    if (state.activeSheet is ReadBookSheet.AiRewritePresetConfig) {
        AiRewritePresetConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.AiRewritePresetConfig>() != null,
            state = aiState.aiRewritePresetConfig,
            onIntent = onIntent,
            onDismissRequest = { onIntent(ReadBookIntent.CloseAiRewritePresetConfig) },
        )
    }
    if (state.activeSheet is ReadBookSheet.MoreConfig) {
        MoreConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.MoreConfig>() != null,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.MoreConfig>()),
            onIntent = onIntent,
            onOpenClickRegionalConfig = {
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ClickActionConfig))
            },
            onOpenPageKeyConfig = {
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.PageKeyConfig))
            },
            onOpenTextSelectMenuConfig = onOpenTextSelectMenuConfig,
            onPickBookmarkBadgeImage = onPickBookmarkBadgeImage,
            onResetBookmarkBadge = onResetBookmarkBadge,
        )
    }
    if (state.activeSheet is ReadBookSheet.PreDownloadConfig) {
        ReadAloudNumberConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.PreDownloadConfig>() != null,
            title = stringResource(R.string.read_aloud_preload),
            description = stringResource(R.string.read_aloud_preload_summary, state.preDownloadNum),
            value = state.preDownloadNum,
            defaultValue = 10,
            valueRange = 0f..100f,
            onValueChange = { onIntent(ReadBookIntent.ApplyPreDownloadNum(it)) },
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.PreDownloadConfig>()),
        )
    }
    if (state.activeSheet is ReadBookSheet.PreSynthesisConcurrencyConfig) {
        ReadAloudNumberConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.PreSynthesisConcurrencyConfig>() != null,
            title = stringResource(R.string.tts_pre_synthesis_concurrency),
            description = stringResource(
                R.string.tts_pre_synthesis_concurrency_summary, state.preSynthesisConcurrency,
            ),
            value = state.preSynthesisConcurrency,
            defaultValue = 3,
            valueRange = 1f..8f,
            onValueChange = { onIntent(ReadBookIntent.ApplyPreSynthesisConcurrency(it)) },
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.PreSynthesisConcurrencyConfig>()),
        )
    }
    if (state.activeSheet is ReadBookSheet.AudioCacheCleanConfig) {
        ReadAloudNumberConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.AudioCacheCleanConfig>() != null,
            title = stringResource(R.string.audio_cache_clean_time),
            description = stringResource(
                R.string.audio_cache_clean_time_summary,
                state.audioCacheCleanTime
            ),
            value = state.audioCacheCleanTime,
            defaultValue = 10,
            valueRange = 0f..10080f,
            onValueChange = { onIntent(ReadBookIntent.ApplyAudioCacheCleanTime(it)) },
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.AudioCacheCleanConfig>()),
        )
    }
    if (state.activeSheet is ReadBookSheet.ParagraphIntervalConfig) {
        ReadAloudNumberConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.ParagraphIntervalConfig>() != null,
            title = stringResource(R.string.tts_paragraph_interval),
            description = stringResource(
                R.string.tts_paragraph_interval_summary,
                state.readAloudParagraphInterval
            ),
            value = state.readAloudParagraphInterval,
            defaultValue = 0,
            valueRange = 0f..5000f,
            onValueChange = { onIntent(ReadBookIntent.ApplyParagraphInterval(it)) },
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.ParagraphIntervalConfig>()),
        )
    }
    if (state.activeSheet is ReadBookSheet.AppLog) {
        AppLogSheet(
            show = visibleState.findSheet<ReadBookSheet.AppLog>() != null,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.AppLog>()),
        )
    }
    if (state.activeSheet is ReadBookSheet.BgTextConfig) {
        BgTextConfigSheet(
            show = visibleState.findSheet<ReadBookSheet.BgTextConfig>() != null,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.BgTextConfig>()),
            onIntent = onIntent,
            onSelectImage = { onIntent(ReadBookIntent.OpenReadStyleImagePicker) },
            onSelectImageForMode = { isNight ->
                onIntent(ReadBookIntent.OpenReadStyleImagePickerForMode(isNight))
            },
            onImportConfig = { onIntent(ReadBookIntent.OpenReadStyleImport) },
            onExportConfig = { onIntent(ReadBookIntent.OpenReadStyleExport) },
            styleConfig = state.styleConfig,
        )
    }

    val aloudPlayerViewModel: ReadAloudPlayerViewModel =
        org.koin.compose.koinInject()
    val aloudPlayerShellState by aloudPlayerViewModel.uiState.collectAsStateWithLifecycle()
    /*
     * 朗读设置是窗口级浮层（AppModalBottomSheet 在 miuix 引擎下走独立窗口），压上整屏页时它
     * 不会跟着下沉，于是从「引擎与音色」「角色与配乐」里那几个跳转项进新页面时，朗读设置
     * 还悬在新页面上面。导航栈顶不是阅读页就把它收起来；返回后栈顶回到阅读页，弹层自己摊回来，
     * 停在哪个 tab 由 readAloudConfigTab 记着（见 ReadBookContract 里的注释）。
     */
    val navRouteTracker: io.legado.app.ui.main.MainNavRouteTracker = org.koin.compose.koinInject()
    val readerIsTop = navRouteTracker.backStack.collectAsStateWithLifecycle()
        .value.lastOrNull() is io.legado.app.ui.main.MainRouteReadBook
    // 听书播放页是 Activity 级 morph 浮层（见 ReadAloudPlayerMorphHost），阅读器内
    // 只保留经典控制面板自己的朗读配置卡片；两者共用同一份配置内容。
    if (state.activeSheet is ReadBookSheet.ReadAloudConfig) {
        AppModalBottomSheet(
            show = visibleState.findSheet<ReadBookSheet.ReadAloudConfig>() != null && readerIsTop,
            onDismissRequest = dismissSheet(visibleState.findSheet<ReadBookSheet.ReadAloudConfig>()),
            title = stringResource(R.string.aloud_config),
        ) {
            ReadAloudConfigContent(
                state = state,
                playerState = aloudPlayerShellState,
                onIntent = onIntent,
                onPlayerIntent = aloudPlayerViewModel::onIntent,
                selectedTab = state.readAloudConfigTab,
                onTabSelected = { onIntent(ReadBookIntent.SetReadAloudConfigTab(it)) },
            )
        }
    }

    val dictSheet = state.findSheet<ReadBookSheet.Dict>()
    if (dictSheet != null) {
        DictSheet(
            show = sheetsVisible,
            word = dictSheet.word,
            onDismissRequest = dismissSheet(dictSheet),
        )
    }
    val photoSheet = state.findSheet<ReadBookSheet.Photo>()
    if (photoSheet != null) {
        PhotoSheet(
            show = sheetsVisible,
            src = photoSheet.src,
            sourceOrigin = photoSheet.sourceOrigin,
            onDismissRequest = dismissSheet(photoSheet),
        )
    }
    val bookmarkSheet = state.findSheet<ReadBookSheet.Bookmark>()
    bookmarkSheet?.let { sheet ->
        BookmarkEditSheet(
            show = sheetsVisible,
            bookmark = sheet.bookmark,
            onDismiss = { onIntent(ReadBookIntent.DismissSheetIfTop(sheet)) },
            onSave = { onIntent(ReadBookIntent.SaveBookmark(it)) },
            onDelete = { onIntent(ReadBookIntent.DeleteBookmark(it)) },
        )
    }

    val showCharsetSheet = visibleState.findSheet<ReadBookSheet.Charset>() != null
    val showSimulatedReadingSheet = visibleState.findSheet<ReadBookSheet.SimulatedReading>() != null

    // AlertDialog-based sheets and special cases — conditionally composed
    val sendSheetIntent = onIntent
    state.sheetStack.filter {
        sheetsVisible || it is ReadBookSheet.ChangeBookSource || it is ReadBookSheet.ChangeChapterSource
    }.forEach { sheet ->
        androidx.compose.runtime.key(sheet::class) {
            val onIntent: (ReadBookIntent) -> Unit = { intent ->
                sendSheetIntent(
                    if (intent == ReadBookIntent.DismissSheet ||
                        intent == ReadBookIntent.SetActiveSheet(null)
                    ) ReadBookIntent.DismissSheetIfTop(sheet) else intent
                )
            }
            val dismissSheet = { onIntent(ReadBookIntent.DismissSheet) }
            when (sheet) {
                is ReadBookSheet.ClickActionConfig -> {
                    ClickActionConfigSheet(
                        onDismissRequest = dismissSheet,
                    )
                }

                is ReadBookSheet.PageKeyConfig -> {
                    PageKeyConfigSheet(
                        onDismissRequest = dismissSheet,
                    )
                }


                is ReadBookSheet.PageAnim -> {
                    PageAnimConfigSheet(
                        onDismissRequest = dismissSheet,
                        onAnimChanged = { onIntent(ReadBookIntent.PageAnimChanged) },
                    )
                }

                is ReadBookSheet.Download -> {
                    DownloadSheet(
                        onDismissRequest = dismissSheet,
                        onDownload = { start, end ->
                            onIntent(ReadBookIntent.DismissSheet)
                            onIntent(ReadBookIntent.DownloadChapters(start, end))
                        },
                    )
                }

                is ReadBookSheet.AudioDownload -> Unit

                is ReadBookSheet.Charset -> {
                    CharsetConfigSheet(
                        show = showCharsetSheet,
                        onDismissRequest = dismissSheet,
                    )
                }

                is ReadBookSheet.SimulatedReading -> {
                    SimulatedReadingSheet(
                        show = showSimulatedReadingSheet,
                        onDismissRequest = dismissSheet,
                        onApply = { onIntent(ReadBookIntent.ApplySimulatedReading) },
                    )
                }

                is ReadBookSheet.Bookmark -> Unit

                is ReadBookSheet.BookNavigation -> Unit

                is ReadBookSheet.InfoConfig -> {
                    // Integrated into TypographyPage
                    LaunchedEffect(sheet) {
                        onIntent(ReadBookIntent.DismissSheet)
                    }
                }

                is ReadBookSheet.ChangeChapterSource -> {
                    val book = state.book
                    if (book != null) {
                        var showSheet by remember { mutableStateOf(true) }
                        LaunchedEffect(showSheet) {
                            if (!showSheet) {
                                kotlinx.coroutines.delay(300)
                                onIntent(ReadBookIntent.SetActiveSheet(null))
                            }
                        }
                        val viewModel = androidx.compose.runtime.key(
                            "chapter-source-${book.bookUrl}-${sheet.chapterIndex}"
                        ) {
                            org.koin.androidx.compose.koinViewModel<io.legado.app.ui.book.changesource.ChangeChapterSourceViewModel>()
                        }
                        androidx.compose.runtime.DisposableEffect(viewModel) {
                            onDispose { viewModel.dispose() }
                        }
                        LaunchedEffect(book.bookUrl, sheet.chapterIndex) {
                            viewModel.initData(
                                book,
                                sheet.chapterIndex,
                                sheet.chapterTitle
                            )
                        }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        ChangeChapterSourceSheet(
                            state = viewModel.uiState.collectAsStateWithLifecycle().value,
                            onIntent = viewModel::onIntent,
                            show = showSheet && sheetsVisible,
                            onDismissRequest = { showSheet = false },
                            onAnimationFinish = {
                                if (!showSheet) onIntent(ReadBookIntent.SetActiveSheet(null))
                            },
                            bookScoreFlow = viewModel::bookScoreFlow,
                            onBookScoreClick = viewModel::onBookScoreClick,
                            onEditSource = { sourceUrl ->
                                onIntent(ReadBookIntent.OpenSourceEditByUrl(sourceUrl))
                            },
                        )
                        // Handle ReplaceContent effect
                        LaunchedEffect(viewModel) {
                            viewModel.effects.collectLatest { effect ->
                                when (effect) {
                                    is io.legado.app.ui.book.changesource.ChangeChapterSourceEffect.ReplaceContent -> {
                                        showSheet = false
                                        onIntent(
                                            ReadBookIntent.SaveChapterContent(
                                                effect.content,
                                                sheet.chapterIndex
                                            )
                                        )
                                    }

                                    is io.legado.app.ui.book.changesource.ChangeChapterSourceEffect.ShowToast -> {
                                        context.toastOnUi(effect.message)
                                    }

                                    is io.legado.app.ui.book.changesource.ChangeChapterSourceEffect.Dismiss -> {
                                        // Handled by showSheet animation — no-op
                                    }
                                }
                            }
                        }
                    } else {
                        LaunchedEffect(sheet) {
                            onIntent(ReadBookIntent.DismissSheet)
                        }
                    }
                }

                is ReadBookSheet.ChangeBookSource -> {
                    val changeSourceSheet = sheet
                    val changeSourceBook = state.book
                    if (changeSourceBook == null) {
                        LaunchedEffect(changeSourceSheet) {
                            onIntent(ReadBookIntent.DismissSheet)
                        }
                    }
                    if (changeSourceBook != null) {
                        ChangeSourceSheet(
                            show = sheetsVisible,
                            oldBook = changeSourceBook,
                            fromReadBookActivity = true,
                            allowAddAsNew = false,
                            dismissOnReplaceStart = true,
                            onEditSource = { onIntent(ReadBookIntent.OpenSourceEditByUrl(it)) },
                            editedSourceUrl = state.pendingEditedSource,
                            onSourceEditHandled = { onIntent(ReadBookIntent.SourceEditHandled(it)) },
                            onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                            onReplace = { _, newBook, toc, _ ->
                                onIntent(ReadBookIntent.DismissSheet)
                                onIntent(ReadBookIntent.ChangeSource(newBook, toc))
                            },
                            onReplaceBook = { newBook ->
                                onIntent(ReadBookIntent.ChangeSourceBook(newBook))
                            },
                            onAddAsNew = { newBook, toc ->
                                onIntent(ReadBookIntent.DismissSheet)
                                onIntent(ReadBookIntent.AddSourceAsNewBook(newBook, toc))
                            },
                        )
                    }
                }

                // Sheets using AppModalBottomSheet are composed unconditionally above
                else -> {}
            }
        }
    }
}
