package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.help.readaloud.cast.AiCastPresetStore
import io.legado.app.help.readaloud.cast.AiCastPresetUi
import io.legado.app.help.readaloud.cast.AiCastProgress
import io.legado.app.model.ReadBook
import io.legado.app.ui.ai.chat.AiThinkingCard
import io.legado.app.ui.ai.chat.AiThinkingStep
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.CastFieldSpec
import io.legado.app.ui.widget.components.CastFieldStack
import io.legado.app.ui.widget.components.CastImeTextField
import io.legado.app.ui.widget.components.CollapsibleHeader
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.AppIconButton
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.ToggleChip
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallOutlinedButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 分配角色弹层（朗读面板「AI 分配角色」按钮或底栏 ai_cast 按钮打开）。
 *
 * 提示词体系对齐官方「AI 改写」：预设（taskType=cast_assign，独立于改写预设）
 * +「管理」子页（新建/编辑/删除多条预设）+ 临时要求（仅本次）。本书角色记忆
 * （book_cast_memory，只属于本书）默认折叠，展开后可手动编辑——AI 逐章滚动更新
 * 它，用于把同一人物的不同称呼（特工化名/代号/昵称）归并为同一角色。
 *
 * 布局：走 AppModalBottomSheet。开始/取消运行/删除分配放在弹层头部的 start/endAction，
 * 内容区整体滚动，再长也不会把主操作顶到屏幕外。
 */
@Composable
fun AiCastDialogSheet(
    show: Boolean,
    sceneOnly: Boolean = false,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val book = ReadBook.book
    val bookUrl = book?.bookUrl.orEmpty()
    val chapterTotal = book?.totalChapterNum ?: 0
    val progress by AiCastProgress.state.collectAsStateWithLifecycle()

    var chapterIndex by remember { mutableStateOf(ReadBook.durChapterIndex) }
    var chapterTitle by remember { mutableStateOf(book?.durChapterTitle.orEmpty()) }
    var assignedCount by remember(chapterIndex) { mutableStateOf(-1) }
    var dialogueCount by remember(chapterIndex) { mutableStateOf(-1) }
    var sceneCount by remember(chapterIndex) { mutableStateOf(-1) }
    var reassign by remember { mutableStateOf(false) }
    // 思考过程永远回显，只是默认收起（见 thinkingOpen）；这一行才是真的决定模型想多久
    var reasoningLevel by remember { mutableStateOf(AiReasoningLevel.AUTO) }
    var assignScene by remember { mutableStateOf(sceneOnly) }
    var extraChapters by remember { mutableIntStateOf(0) }
    // 范围模式：起止章直接选（章号从 1 开始填，与听书下载同一套口径），关掉就是「当前章 + 追加章节数」
    var byRange by remember { mutableStateOf(false) }
    var rangeStartText by remember { mutableStateOf((chapterIndex + 1).toString()) }
    var rangeEndText by remember { mutableStateOf((chapterIndex + 1).toString()) }
    var memoryOpen by remember { mutableStateOf(false) }
    var requestOpen by remember { mutableStateOf(false) }

    /**
     * 本书上一次分配跑到哪儿（停在第几章、哪些章失败、上次用的范围），存在 prefs。
     *
     * 每次跑完（[AiCastProgress.State.running] 由 true 变 false）都重读一次，所以取消后
     * 「从第 N 章继续」「重试失败 N 章」这两个入口关掉重开悬浮窗照样在。
     * 章号是 0 基（写入侧见 `AiCastAssignUseCase.persistRunState`），这里显示与填框一律 +1。
     */
    val storedRun by produceState(
        initialValue = AiCastPresetStore.AiCastRunState(),
        key1 = bookUrl,
        key2 = progress.running,
    ) {
        value = withContext(Dispatchers.IO) { AiCastPresetStore.loadCastRunState(bookUrl) }
    }
    // 跑完/取消后停在哪儿：优先看本次运行的进度状态，其次看书里存的那一份
    val resumeChapter = if (progress.running) AiCastPresetStore.NO_RESUME_CHAPTER else storedRun.resumeChapter
    val retryChapters = if (progress.failedChapters.isNotEmpty()) {
        progress.failedChapters
    } else {
        storedRun.failedChapters
    }

    /** 本次要分配的章区间（0 基、含两端）；null = 范围填得不合法。换算规则见 [castChapterRange]。 */
    val castRange: Pair<Int, Int>? = castChapterRange(
        byRange = byRange,
        startText = rangeStartText,
        endText = rangeEndText,
        chapterTotal = chapterTotal,
        currentChapter = chapterIndex,
        extraChapters = extraChapters,
    )

    // 子页：0=主面板 1=预设管理
    var page by remember { mutableStateOf(0) }
    var presetsVersion by remember { mutableIntStateOf(0) }
    val presets by produceState<ImmutableList<AiCastPresetUi>>(
        initialValue = persistentListOf(),
        presetsVersion,
    ) {
        value = withContext(Dispatchers.IO) {
            AiCastPresetStore.loadPresets().toImmutableList()
        }
    }
    // 固定附加的提示词（角色设定/判断规则/输出格式）：也是预设行，只是永远拼进 prompt、不作为「分配要求」被选中
    val fixedSlots by produceState<ImmutableList<AiCastPresetUi>>(
        initialValue = persistentListOf(),
        presetsVersion,
    ) {
        value = withContext(Dispatchers.IO) {
            AiCastPresetStore.loadFixedSlots().toImmutableList()
        }
    }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(presets) {
        val cur = selectedPresetId
        if (cur == null || presets.none { it.id == cur }) {
            selectedPresetId = presets.firstOrNull()?.id
        }
    }
    var temporary by remember { mutableStateOf("") }
    var memoryText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bookUrl) {
        if (book == null) return@LaunchedEffect
        if (memoryText == null) {
            withContext(Dispatchers.IO) {
                memoryText = AiCastPresetStore.memory(bookUrl)
                reasoningLevel = AiCastPresetStore.savedReasoningLevel()
            }
        }
    }

    val running = progress.running
    /**
     * 发一次分配意图。[onlyChapters] 非空 = 重试：只跑失败的那几章（范围三兄弟由
     * `castChapterPlan` 忽略）；空 = 按界面上选中的那种模式跑整段范围（见 [castRange]）。
     */
    val startCast: (List<Int>) -> Unit = { onlyChapters ->
        val range = castRange
        val plan = when {
            onlyChapters.isNotEmpty() -> onlyChapters.first() to onlyChapters.last()
            range == null -> null
            else -> range
        }
        if (plan == null) {
            // 范围填错不是「点了没反应」：必须当场说一句，否则用户以为按钮坏了
            context.toastOnUi(R.string.read_aloud_audio_download_invalid_range)
        } else {
            onIntent(
                ReadBookIntent.StartAiCast(
                    startChapter = plan.first,
                    count = plan.second - plan.first + 1,
                    endChapter = plan.second,
                    onlyChapters = onlyChapters,
                    reassign = reassign,
                    presetId = selectedPresetId.orEmpty(),
                    temporaryInstruction = temporary.trim(),
                    reasoningLevel = reasoningLevel,
                    assignScene = assignScene || sceneOnly,
                    rolesPass = !sceneOnly,
                ),
            )
        }
    }
    val allDone = dialogueCount >= 0 && assignedCount >= dialogueCount
    // 思考过程没有开关了：模型回了就永远显示，只是默认收起，收起时头部保留那颗秒表
    val thinkingPending = running && progress.reasoning.isBlank()
    val hasLog = progress.reasoning.isNotBlank() || progress.answer.isNotBlank() || thinkingPending
    val title = stringResource(
        when {
            page == 1 -> R.string.ai_cast_presets
            sceneOnly -> R.string.ai_cast_scene_only
            else -> R.string.ai_cast_roles
        },
    )
    // 这一章有没有东西可删（纯场景入口只数配乐段）
    val deletable = if (sceneOnly) sceneCount > 0 else assignedCount > 0
    // 预设管理是子页：左上角给返回；主面板的左上角给「删除分配」
    val startAction: @Composable () -> Unit = if (page == 1) {
        {
            MediumTonalButton(
                onClick = { page = 0 },
                icon = AppIcons.Back,
                contentDescription = stringResource(R.string.back),
            )
        }
    } else {
        {
            MediumTonalButton(
                onClick = {
                    onIntent(
                        ReadBookIntent.DeleteChapterCastAssignments(
                            chapterIndex,
                            // 这次分配里带了配乐，删除就必须连配乐一起删；
                            // 纯场景入口更是只有配乐可删
                            alsoScenes = sceneOnly || assignScene,
                        ),
                    )
                    assignedCount = 0
                    sceneCount = 0
                },
                icon = AppIcons.Delete,
                contentDescription = stringResource(R.string.ai_cast_delete),
                enabled = !running && deletable,
            )
        }
    }

    AppModalBottomSheet(
        show = show && book != null,
        onDismissRequest = onDismissRequest,
        title = title,
        startAction = startAction,
        endAction = {
            if (page == 0) {
                if (running) {
                    MediumTonalButton(
                        onClick = { onIntent(ReadBookIntent.CancelAiCast) },
                        icon = AppIcons.Close,
                        contentDescription = stringResource(R.string.cancel),
                    )
                } else {
                    MediumTonalButton(
                        onClick = { startCast(emptyList()) },
                        icon = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.ai_cast_start),
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (page == 1) {
                AiCastPresetManagePage(
                    presets = presets,
                    fixedSlots = fixedSlots,
                    onChanged = { presetsVersion++ },
                )
            } else {
                // 本章状态 + 总进度
                AiCastSection {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AppText(
                            text = chapterTitle.ifBlank { "—" },
                            style = LegadoTheme.typography.titleSmallEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (sceneOnly) {
                            AppText(
                                text = if (sceneCount < 0) {
                                    stringResource(R.string.ai_cast_status_loading)
                                } else {
                                    stringResource(R.string.ai_cast_scene_status, sceneCount)
                                },
                                style = LegadoTheme.typography.bodyMedium,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                            AppText(
                                text = stringResource(R.string.ai_cast_scene_only_hint),
                                style = LegadoTheme.typography.bodySmall,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            AppText(
                                text = if (assignedCount < 0) {
                                    stringResource(R.string.ai_cast_status_loading)
                                } else {
                                    stringResource(
                                        R.string.ai_cast_status,
                                        assignedCount,
                                        dialogueCount
                                    )
                                },
                                style = LegadoTheme.typography.bodyMedium,
                                color = if (allDone) {
                                    LegadoTheme.colorScheme.primary
                                } else {
                                    LegadoTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            if (allDone) {
                                AppText(
                                    text = stringResource(R.string.ai_cast_all_done),
                                    style = LegadoTheme.typography.bodySmall,
                                    color = LegadoTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (running || hasLog) {
                        AppLinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = if (progress.total <= 0) {
                                0f
                            } else {
                                progress.done.toFloat() / progress.total
                            },
                        )
                        AppText(
                            text = stringResource(
                                R.string.ai_cast_progress,
                                progress.done,
                                progress.total,
                            ) + progress.chapterTitle.let {
                                if (it.isBlank()) "" else " · $it"
                            },
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 跑完/取消后的那一句（「已取消，停在第 N 章」就在里面）
                    progress.finishedMessage?.let {
                        AppText(
                            text = it,
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.primary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 逐章失败明细：断网、连不上 AI、回复读不出来都得留一句话在这儿。
                    // 失败提示在跑完之后也要继续显示，不能只在 running 时出现。
                    val failureDetail = progress.failureText
                        .ifBlank { progress.lastError.orEmpty() }
                    if (failureDetail.isNotBlank()) {
                        AppText(
                            text = failureDetail,
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.error,
                            maxLines = 12,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 中断后的两个入口，都不用重填范围：续跑起点填好起止章，重试只跑失败章
                    if (!running && (resumeChapter >= 0 || retryChapters.isNotEmpty())) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (resumeChapter >= 0) {
                                SmallOutlinedButton(
                                    onClick = {
                                        byRange = true
                                        // 停在哪儿就从哪儿再来一遍（填的是显示用的 1 基章号）
                                        rangeStartText = (resumeChapter + 1).toString()
                                        rangeEndText =
                                            (storedRun.endChapter.coerceAtLeast(resumeChapter) + 1)
                                                .toString()
                                    },
                                    icon = Icons.Default.Restore,
                                    text = stringResource(
                                        R.string.ai_cast_resume_from,
                                        resumeChapter + 1
                                    ),
                                )
                            }
                            if (retryChapters.isNotEmpty()) {
                                SmallOutlinedButton(
                                    enabled = !running,
                                    onClick = { startCast(retryChapters) },
                                    text = stringResource(
                                        R.string.ai_cast_retry,
                                        retryChapters.size
                                    ),
                                )
                            }
                        }
                    }
                }

                // 思考/返回内容：紧跟状态，运行时不用往下翻
                if (hasLog) {
                    AiCastSection {
                        if (progress.reasoning.isNotBlank()) {
                            if (progress.reasoningFolded > 0) {
                                AppText(
                                    text = stringResource(
                                        R.string.ai_cast_transcript_folded,
                                        progress.reasoningFolded,
                                    ),
                                    style = LegadoTheme.typography.labelSmall,
                                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // autoExpandWhileStreaming = false：默认收起，和下面的本书角色记忆一样；
                            // 收起时头部那颗秒表照样在走，所以收起不等于什么都没发生
                            AiThinkingCard(
                                steps = listOf(
                                    AiThinkingStep.ReasoningStep(progress.reasoning),
                                ),
                                isStreaming = running,
                                durationSeconds = progress.reasoningSeconds,
                                autoExpandWhileStreaming = false,
                            )
                        } else if (thinkingPending) {
                            AppText(
                                text = stringResource(R.string.ai_cast_thinking_pending),
                                style = LegadoTheme.typography.bodySmall,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (progress.answer.isNotBlank()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                AppText(
                                    text = stringResource(R.string.ai_cast_answer),
                                    style = LegadoTheme.typography.titleSmall,
                                )
                                val answerScroll = rememberScrollState()
                                LaunchedEffect(progress.answer) {
                                    answerScroll.scrollTo(answerScroll.maxValue)
                                }
                                if (progress.answerFolded > 0) {
                                    AppText(
                                        text = stringResource(
                                            R.string.ai_cast_transcript_folded,
                                            progress.answerFolded,
                                        ),
                                        style = LegadoTheme.typography.labelSmall,
                                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(LegadoTheme.colorScheme.surfaceVariant)
                                        .verticalScroll(answerScroll)
                                        .padding(10.dp),
                                ) {
                                    AppText(
                                        text = progress.answer,
                                        style = LegadoTheme.typography.bodySmall,
                                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // 请求原文：跑过一次就有。分配不对时第一个要看的就是它——
                // 是正文没送全、档案里带着错名字，还是模型自己判错了，一眼能分开
                if (progress.request.isNotBlank()) {
                    CollapsibleHeader(
                        isCollapsed = !requestOpen,
                        onToggle = { requestOpen = !requestOpen },
                        title = stringResource(R.string.ai_cast_request),
                        subtitle = stringResource(R.string.ai_cast_request_hint),
                    )
                    if (requestOpen) {
                        AiCastSection {
                            val requestScroll = rememberScrollState()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LegadoTheme.colorScheme.surfaceVariant)
                                    .verticalScroll(requestScroll)
                                    .padding(10.dp),
                            ) {
                                AppText(
                                    text = progress.request,
                                    style = LegadoTheme.typography.bodySmall,
                                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // 提示词预设 + 临时要求：只作用于角色那趟，纯场景入口里它们是死控件
                if (!sceneOnly) {
                    AiCastSection(
                        title = stringResource(R.string.ai_cast_preset_instruction),
                        trailing = {
                            SmallOutlinedButton(
                                onClick = { page = 1 },
                                icon = AppIcons.Settings,
                                text = stringResource(R.string.ai_cast_manage),
                            )
                        },
                    ) {
                        if (presets.isEmpty()) {
                            AppText(
                                text = stringResource(R.string.ai_cast_no_preset),
                                style = LegadoTheme.typography.bodyMedium,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                presets.forEach { preset ->
                                    ToggleChip(
                                        label = preset.name,
                                        selected = preset.id == selectedPresetId,
                                        onToggle = { selectedPresetId = preset.id },
                                    )
                                }
                            }
                            presets.firstOrNull { it.id == selectedPresetId }?.let { preset ->
                                AppText(
                                    text = preset.instruction,
                                    style = LegadoTheme.typography.bodySmall,
                                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        CastImeTextField(
                            value = temporary,
                            onValueChange = { temporary = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.ai_cast_temporary),
                            minLines = 1,
                            maxLines = 4,
                        )
                    }
                }

                // 本书角色记忆：默认折叠，展开才占高度
                if (!sceneOnly) {
                    CollapsibleHeader(
                        isCollapsed = !memoryOpen,
                        onToggle = { memoryOpen = !memoryOpen },
                        title = stringResource(R.string.ai_cast_memory),
                        subtitle = memoryText.orEmpty()
                            .lineSequence()
                            .firstOrNull { it.isNotBlank() }
                            ?: stringResource(R.string.ai_cast_memory_hint),
                    )
                }
                if (!sceneOnly && memoryOpen) {
                    AiCastSection {
                        AppText(
                            text = stringResource(R.string.ai_cast_memory_hint),
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                        CastImeTextField(
                            value = memoryText.orEmpty(),
                            onValueChange = { memoryText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 8,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            SmallOutlinedButton(
                                enabled = !running,
                                onClick = {
                                    val text = memoryText.orEmpty()
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            AiCastPresetStore.setMemory(bookUrl, text)
                                        }
                                        context.toastOnUi(R.string.ai_cast_memory_saved)
                                    }
                                },
                                text = stringResource(R.string.save),
                            )
                        }
                    }
                }

                // 运行选项
                AiCastSection {
                    TinySwitchSettingItem(
                        title = stringResource(R.string.ai_cast_reassign),
                        description = stringResource(R.string.ai_cast_reassign_summary),
                        checked = reassign,
                        enabled = !running,
                        color = Color.Transparent,
                        onCheckedChange = { reassign = it },
                    )
                    // 模型思考多久：这一行真的影响等待时间，思考回不回显都照它跑
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AppText(
                            text = stringResource(R.string.ai_cast_reasoning_level),
                            style = LegadoTheme.typography.bodyLarge,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ReasoningLevelChoices.forEach { choice ->
                                ToggleChip(
                                    label = stringResource(choice.labelRes),
                                    selected = choice.level == reasoningLevel,
                                    enabled = !running,
                                    onToggle = {
                                        reasoningLevel = choice.level
                                        scope.launch(Dispatchers.IO) {
                                            AiCastPresetStore.setReasoningLevel(choice.level)
                                        }
                                    },
                                )
                            }
                        }
                        AppText(
                            text = stringResource(R.string.ai_cast_reasoning_level_hint),
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!sceneOnly) {
                        TinySwitchSettingItem(
                            title = stringResource(R.string.ai_cast_assign_scene),
                            checked = assignScene,
                            enabled = !running,
                            color = Color.Transparent,
                            onCheckedChange = { assignScene = it },
                        )
                    }
                    if (assignScene && !sceneOnly) {
                        AppText(
                            text = stringResource(R.string.ai_cast_assign_scene_hint),
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 分配范围两种模式：「当前章 + 追加 N 章」或「指定起止章」。
                    // 选中的那一种真的决定发出去的是哪些章（见 startCast / castChapterPlan）。
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToggleChip(
                            label = stringResource(R.string.ai_cast_extra_chapters),
                            selected = !byRange,
                            enabled = !running,
                            onToggle = { byRange = false },
                        )
                        ToggleChip(
                            label = stringResource(R.string.ai_cast_range_mode),
                            selected = byRange,
                            enabled = !running,
                            onToggle = { byRange = true },
                        )
                    }
                    if (byRange) {
                        // 章号从 1 开始填；控件与文案沿用听书下载那张卡片的范围那一节
                        CastFieldStack(
                            specs = listOf(
                                CastFieldSpec(
                                    id = "castStartChapter",
                                    label = stringResource(
                                        R.string.read_aloud_audio_download_start_chapter,
                                    ),
                                    value = rangeStartText,
                                    onValueChange = { rangeStartText = it },
                                ),
                                CastFieldSpec(
                                    id = "castEndChapter",
                                    label = stringResource(
                                        R.string.read_aloud_audio_download_end_chapter,
                                    ),
                                    value = rangeEndText,
                                    onValueChange = { rangeEndText = it },
                                ),
                            ),
                        )
                        AppText(
                            text = if (castRange == null) {
                                stringResource(R.string.read_aloud_audio_download_invalid_range)
                            } else {
                                stringResource(
                                    R.string.read_aloud_audio_download_range_count,
                                    castRange.second - castRange.first + 1,
                                )
                            },
                            style = LegadoTheme.typography.bodySmall,
                            color = if (castRange == null) {
                                LegadoTheme.colorScheme.error
                            } else {
                                LegadoTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    } else {
                        TinySliderSettingItem(
                            title = stringResource(R.string.ai_cast_extra_chapters),
                            description = stringResource(R.string.ai_cast_extra_summary),
                            value = extraChapters.toFloat(),
                            valueRange = 0f..100f,
                            steps = 99,
                            enabled = !running,
                            color = Color.Transparent,
                            onValueChange = { extraChapters = it.toInt() },
                        )
                    }
                }
            }
        }
    }

    // 本章分配状态：已分配行数 vs 一级对话锚点数（与渲染同一套计数）；纯场景入口数配乐段
    LaunchedEffect(chapterIndex) {
        val currentBook = ReadBook.book ?: return@LaunchedEffect
        if (sceneOnly) {
            sceneCount = withContext(Dispatchers.IO) {
                io.legado.app.help.readaloud.cast.BgmSceneStore
                    .marks(currentBook.bookUrl, chapterIndex).size
            }
        } else {
            io.legado.app.help.readaloud.cast.AiCastDialogStats
                .load(currentBook.bookUrl, chapterIndex)
                .let { assignedCount = it.first; dialogueCount = it.second }
        }
    }
}

/**
 * 本次要分配的章区间（**0 基**、含两端）；null = 范围填得不合法（非数字、0、起点在终点之后）。
 *
 * 界面上的章号从 1 开始（与听书下载那张卡片同一口径），这里负责换算并夹到目录末尾；
 * 消费侧是 `castChapterPlan`（AiCastAssignUseCase），它按同一份 0 基口径逐章取正文。
 */
private fun castChapterRange(
    byRange: Boolean,
    startText: String,
    endText: String,
    chapterTotal: Int,
    currentChapter: Int,
    extraChapters: Int,
): Pair<Int, Int>? {
    if (chapterTotal <= 0) return null
    val last = chapterTotal - 1
    if (!byRange) {
        val start = currentChapter.coerceIn(0, last)
        return start to (start + extraChapters).coerceAtMost(last)
    }
    val start = startText.trim().toIntOrNull()?.minus(1) ?: return null
    val end = endText.trim().toIntOrNull()?.minus(1) ?: return null
    if (start < 0 || end < start) return null
    return start.coerceAtMost(last) to end.coerceAtMost(last)
}

/** 分区卡片：标题（可带右侧操作）+ 内容。卡片内边距统一 16.dp，与弹层内容边距同一档。 */
@Composable
private fun AiCastSection(
    title: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = LegadoTheme.typography.titleSmall,
                        color = LegadoTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    trailing?.invoke(this)
                }
            }
            content()
        }
    }
}

/**
 * 推理强度可选项。
 *
 * AUTO = 软件一个参数都不发，完全听模型与服务商自己的设置；OFF 只在支持关闭思考的服务商上
 * 有效（GLM-5.3 这类强制思考的模型关不掉，只能少给它想的机会）。
 */
private class ReasoningLevelChoice(val level: AiReasoningLevel, val labelRes: Int)

private val ReasoningLevelChoices = listOf(
    ReasoningLevelChoice(AiReasoningLevel.AUTO, R.string.ai_cast_reasoning_follow),
    ReasoningLevelChoice(AiReasoningLevel.OFF, R.string.ai_thinking_off),
    ReasoningLevelChoice(AiReasoningLevel.LOW, R.string.ai_reasoning_level_low),
    ReasoningLevelChoice(AiReasoningLevel.MEDIUM, R.string.ai_reasoning_level_medium),
    ReasoningLevelChoice(AiReasoningLevel.HIGH, R.string.ai_reasoning_level_high),
)

/** 预设管理子页：分配要求列表 + 固定附加的提示词（角色设定/判断规则/输出格式）+ 新建编辑表单，全部走 [AiCastPresetStore]。 */
@Composable
private fun AiCastPresetManagePage(
    presets: ImmutableList<AiCastPresetUi>,
    fixedSlots: ImmutableList<AiCastPresetUi>,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<AiCastPresetUi?>(null) }
    var editName by remember { mutableStateOf("") }
    var editInstruction by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<AiCastPresetUi?>(null) }
    val emptyErrorText = stringResource(R.string.ai_cast_preset_empty)
    val current = editing
    // 输出格式那两行按固定 id 存，名字改了就找不到，所以表单里不给改名字、也不给删
    val editingIsSlot = current != null && AiCastPresetStore.isFixedSlot(current.id)
    val startEdit: (AiCastPresetUi) -> Unit = { preset ->
        editing = preset
        editName = preset.name
        editInstruction = preset.instruction
        editError = null
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                text = stringResource(R.string.ai_cast_presets_summary),
                modifier = Modifier.weight(1f),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            SmallOutlinedButton(
                onClick = {
                    editing = AiCastPresetUi("", "", "")
                    editName = ""
                    editInstruction = ""
                    editError = null
                },
                icon = Icons.Default.Add,
                text = stringResource(R.string.add),
            )
        }

        if (current != null) {
            NormalCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 两个框共用一个真输入框：切焦点不再让平台先收键盘再弹（见 CastFieldStack）
                    val specs = if (editingIsSlot) {
                        listOf(
                            CastFieldSpec(
                                id = "preset_instruction",
                                label = stringResource(R.string.ai_cast_preset_format_instruction),
                                value = editInstruction,
                                // singleLine 默认是 true，不显式关掉的话 minLines/maxLines 全无效：
                                // 分配要求永远只有一行，长文本既看不到也滚不动
                                singleLine = false,
                                minLines = 3,
                                maxLines = 8,
                                onValueChange = { editInstruction = it },
                            ),
                        )
                    } else {
                        listOf(
                            CastFieldSpec(
                                id = "preset_name",
                                label = stringResource(R.string.ai_cast_preset_name),
                                value = editName,
                                singleLine = true,
                                onValueChange = { editName = it },
                            ),
                            CastFieldSpec(
                                id = "preset_instruction",
                                label = stringResource(R.string.ai_cast_preset_instruction),
                                value = editInstruction,
                                singleLine = false,
                                minLines = 3,
                                maxLines = 8,
                                onValueChange = { editInstruction = it },
                            ),
                        )
                    }
                    CastFieldStack(specs = specs)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        SmallOutlinedButton(
                            onClick = {
                                // 只填进表单，不落库：用户不点保存就不会覆盖他自己改过的预设
                                editInstruction = AiCastPresetStore.slotDefault(current.id)
                                    ?: AiCastPresetStore.DEFAULT_REQUIREMENT
                                context.toastOnUi(R.string.ai_cast_preset_default_done)
                            },
                            icon = Icons.Default.Restore,
                            text = stringResource(R.string.ai_cast_preset_default),
                        )
                    }
                    editError?.let {
                        AppText(
                            text = it,
                            color = LegadoTheme.colorScheme.error,
                            style = LegadoTheme.typography.bodySmall,
                        )
                    }
                    ConfirmDismissButtonsRow(
                        onDismiss = { editing = null; editError = null },
                        onConfirm = {
                            val name = if (editingIsSlot) current.name else editName.trim()
                            val instruction = editInstruction.trim()
                            if (name.isEmpty() || instruction.isEmpty()) {
                                editError = emptyErrorText
                                return@ConfirmDismissButtonsRow
                            }
                            val sort = presets.indexOfFirst { it.id == current.id }
                                .takeIf { it >= 0 } ?: presets.size
                            scope.launch(Dispatchers.IO) {
                                AiCastPresetStore.savePreset(
                                    current.copy(name = name, instruction = instruction),
                                    sort,
                                )
                                withContext(Dispatchers.Main) {
                                    editing = null
                                    editError = null
                                    onChanged()
                                }
                            }
                        },
                        dismissText = stringResource(R.string.cancel),
                        confirmText = stringResource(R.string.save),
                    )
                }
            }
        }

        if (presets.isEmpty() && fixedSlots.isEmpty() && current == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmptyMessage(messageResId = R.string.ai_cast_no_preset)
            }
        }

        presets.forEach { preset ->
            AiCastPresetRow(preset = preset, onEdit = { startEdit(preset) }, onDelete = { pendingDelete = preset })
        }

        if (fixedSlots.isNotEmpty()) {
            AppText(
                text = stringResource(R.string.ai_cast_fixed_slots_title),
                style = LegadoTheme.typography.bodyMedium,
            )
            AppText(
                text = stringResource(R.string.ai_cast_fixed_slots_hint),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            fixedSlots.forEach { slot ->
                AiCastPresetRow(preset = slot, onEdit = { startEdit(slot) }, onDelete = null)
            }
        }
    }

    AppAlertDialog(
        show = pendingDelete != null,
        onDismissRequest = { pendingDelete = null },
        title = stringResource(R.string.delete),
        text = pendingDelete?.name?.let {
            stringResource(R.string.ai_cast_preset_delete_confirm, it)
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            val target = pendingDelete ?: return@AppAlertDialog
            pendingDelete = null
            scope.launch(Dispatchers.IO) {
                AiCastPresetStore.deletePreset(target.id)
                withContext(Dispatchers.Main) { onChanged() }
            }
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { pendingDelete = null },
    )
}

/**
 * 一行预设：名字 + 正文（可展开看全）+ 编辑按钮；[onDelete] 为 null 时不给删（固定附加那几行）。
 *
 * 正文摘要过长时给一个展开/收起：判断规则那一大段拼在预设里时不能只露两行省略号，
 * 全文要在不离编辑框的情况下读得到。
 */
@Composable
private fun AiCastPresetRow(
    preset: AiCastPresetUi,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var expanded by remember(preset.id, preset.instruction) { mutableStateOf(false) }
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = preset.name,
                    style = LegadoTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = preset.instruction,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 24 else 2,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
                if (preset.instruction.length > 60) {
                    AppText(
                        text = stringResource(
                            if (expanded) R.string.ai_cast_preset_collapse
                            else R.string.ai_cast_preset_expand,
                        ),
                        style = LegadoTheme.typography.labelMedium,
                        color = LegadoTheme.colorScheme.primary,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
            }
            AppIconButton(onClick = onEdit) {
                AppIcon(
                    imageVector = AppIcons.Edit,
                    contentDescription = stringResource(R.string.edit),
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                AppIconButton(onClick = onDelete) {
                    AppIcon(
                        imageVector = AppIcons.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
