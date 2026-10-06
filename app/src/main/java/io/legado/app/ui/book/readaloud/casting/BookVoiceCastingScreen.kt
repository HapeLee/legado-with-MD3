package io.legado.app.ui.book.readaloud.casting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import io.legado.app.R
import io.legado.app.help.readaloud.cast.BookCastStore
import io.legado.app.ui.book.knowledge.CharacterAvatarCropDialog
import io.legado.app.ui.book.knowledge.CharacterAvatarSourceSheet
import io.legado.app.ui.book.knowledge.saveCharacterAvatar
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.CastImeScope
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.reorderAccessibility
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookVoiceCastingScreen(
    state: BookVoiceCastingUiState,
    onIntent: (BookVoiceCastingIntent) -> Unit,
    effects: Flow<BookVoiceCastingEffect>,
    onBack: () -> Unit,
    onManageCloudTts: () -> Unit,
    onOpenCharacterDetail: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is BookVoiceCastingEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    // 人物详情里改了名字、删了角色，回到这一页要看到最新的那一行
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onIntent(BookVoiceCastingIntent.Refresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.book_voice_casting),
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = stringResource(R.string.read_aloud_engines_and_voices),
                        onClick = onManageCloudTts,
                    )
                    TopBarActionButton(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = { onIntent(BookVoiceCastingIntent.Refresh) },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (state.isLoading && state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            VoiceCastingList(
                state = state,
                onIntent = onIntent,
                onOpenCharacterDetail = onOpenCharacterDetail,
                contentPadding = paddingValues,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun VoiceCastingList(
    state: BookVoiceCastingUiState,
    onIntent: (BookVoiceCastingIntent) -> Unit,
    onOpenCharacterDetail: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val specialItems = state.items.filter { it.kind != CastingSubjectKind.Character }
    val characters = state.items.filter { it.kind == CastingSubjectKind.Character }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<VoiceCastingItemUi?>(null) }
    // 卡片菜单里换头像：记下给谁换，选图/裁剪回来时还要靠这个 id 落库
    var avatarTargetId by remember { mutableStateOf<String?>(null) }
    var showAvatarSource by remember { mutableStateOf(false) }
    // 卡片菜单里设气泡：记下给谁设，保存要落回这一行
    var bubbleTarget by remember { mutableStateOf<VoiceCastingItemUi?>(null) }
    var pendingAvatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    val avatarTarget = state.items.firstOrNull { it.subjectId == avatarTargetId }
    val avatarScope = rememberCoroutineScope()
    val bubbleScope = rememberCoroutineScope()
    val avatarContext = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        pendingAvatarUri = uri?.toString()
    }
    fun commitAvatar(avatarUri: String) {
        val targetId = avatarTargetId ?: return
        avatarTargetId = null
        // 勾了确定就是把话说到这儿了，窗口得跟着收起来（本地那条走裁剪框，本来就是关的）
        showAvatarSource = false
        onIntent(BookVoiceCastingIntent.SetAvatar(targetId, avatarUri))
    }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    /**
     * 拖动期间页面自己记的那份顺序（角色 id）。松手才落库；落库回来的顺序和它一致了
     * 就交还给 state，新加/删掉的角色也就立刻反映出来。
     */
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    val ordered = remember(characters, dragOrder) {
        val ids = dragOrder
        if (ids == null) characters
        else {
            val byId = characters.associateBy { it.subjectId }
            val head = ids.mapNotNull { byId[it] }
            head + characters.filter { it.subjectId !in ids }
        }
    }
    // 「说明 + 旁白 + 人物小节标题」排在人物卡前面，拖动回调给的是整个 LazyColumn 的
    // 绝对下标，所以要减掉这段前缀才知道是第几张角色卡。
    val charOffset = 2 + specialItems.size
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        moveCharacter(ordered, from.index - charOffset, to.index - charOffset)?.let { next ->
            dragOrder = next.map(VoiceCastingItemUi::subjectId)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    var dragged by remember { mutableStateOf(false) }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (reorderableState.isAnyItemDragging) {
            dragged = true
        } else if (dragged) {
            dragged = false
            dragOrder?.let { onIntent(BookVoiceCastingIntent.SaveCharacterOrder(it)) }
        }
    }
    LaunchedEffect(ordered, dragOrder, characters) {
        if (dragOrder != null && dragOrder == characters.map(VoiceCastingItemUi::subjectId)) {
            dragOrder = null
        }
    }

    CastImeScope {
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = adaptiveContentPadding(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "intro", contentType = "intro") {
                AppText(
                    text = stringResource(R.string.book_voice_casting_summary),
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(
                items = specialItems,
                key = { "special:${it.subjectType}:${it.subjectId}" },
                contentType = { "casting" },
            ) { item ->
                // 旁白不是角色：点下去直接展开编辑，没有删除、也没有人物详情可去
                VoiceCastingCard(
                    bookUrl = state.bookUrl,
                    item = item,
                    voices = state.voices,
                    onIntent = onIntent,
                    onOpenCharacterDetail = onOpenCharacterDetail,
                    editing = editingId == item.subjectId,
                    onToggleEdit = {
                        editingId = if (editingId == item.subjectId) null else item.subjectId
                    },
                    onChanged = {
                        editingId = null
                        onIntent(BookVoiceCastingIntent.Refresh)
                    },
                )
            }
            item(key = "section", contentType = "section") {
                AppText(
                    text = stringResource(R.string.book_characters),
                    style = LegadoTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            if (characters.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    AppText(
                        text = stringResource(R.string.character_empty_hint),
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(
                    items = ordered,
                    key = { "char:${it.subjectId}" },
                    contentType = { "casting" },
                ) { item ->
                    val index = ordered.indexOf(item)
                    ReorderableItem(reorderableState, key = "char:${item.subjectId}") { isDragging ->
                        VoiceCastingCard(
                            bookUrl = state.bookUrl,
                            item = item,
                            voices = state.voices,
                            onIntent = onIntent,
                            onOpenCharacterDetail = onOpenCharacterDetail,
                            editing = editingId == item.subjectId,
                            onToggleEdit = {
                                editingId = if (editingId == item.subjectId) null else item.subjectId
                            },
                            onDelete = { deleteTarget = item },
                            onSetAvatar = {
                                avatarTargetId = item.subjectId
                                showAvatarSource = true
                            },
                            onSetBubble = { bubbleTarget = item },
                            onClearBubble = {
                                bubbleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        BookCastStore.updateBubble(state.bookUrl, item.subjectId, "")
                                    }
                                    onIntent(BookVoiceCastingIntent.Refresh)
                                }
                            },
                            onChanged = {
                                editingId = null
                                onIntent(BookVoiceCastingIntent.Refresh)
                            },
                            dragModifier = Modifier
                                .reorderAccessibility(
                                    index = index,
                                    itemCount = ordered.size,
                                    description = stringResource(
                                        R.string.a11y_reorder_named,
                                        item.name,
                                    ),
                                ) { from, to ->
                                    // 读屏的「上移/下移」没有松手时刻，移动完当场就落库
                                    moveCharacter(ordered, from, to)?.let { next ->
                                        dragOrder = next.map(VoiceCastingItemUi::subjectId)
                                        onIntent(
                                            BookVoiceCastingIntent.SaveCharacterOrder(
                                                next.map(VoiceCastingItemUi::subjectId)
                                            )
                                        )
                                    }
                                }
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.GestureThresholdActivate
                                        )
                                    },
                                    onDragStopped = {
                                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    },
                                ),
                            dragging = isDragging,
                        )
                    }
                }
            }
        }
        CastRoleDeleteDialog(
            bookUrl = state.bookUrl,
            target = deleteTarget,
            onDismiss = { deleteTarget = null },
            onDeleted = {
                editingId = null
                onIntent(BookVoiceCastingIntent.Refresh)
            },
        )
        CastBubbleSheet(
            show = bubbleTarget != null,
            characterName = bubbleTarget?.name.orEmpty(),
            initialJson = bubbleTarget?.bubbleRuleJson.orEmpty(),
            configNames = state.configNames,
            onDismissRequest = { bubbleTarget = null },
            onSave = { json ->
                val target = bubbleTarget
                bubbleTarget = null
                if (target != null) {
                    bubbleScope.launch {
                        withContext(Dispatchers.IO) {
                            BookCastStore.updateBubble(state.bookUrl, target.subjectId, json)
                        }
                        onIntent(BookVoiceCastingIntent.Refresh)
                    }
                }
            },
        )
        CharacterAvatarSourceSheet(
            show = showAvatarSource,
            onDismissRequest = { showAvatarSource = false },
            hasAvatar = !avatarTarget?.avatarUri.isNullOrBlank(),
            onEditAvatar = {
                showAvatarSource = false
                pendingAvatarUri = avatarTarget?.avatarUri
            },
            onPickLocal = {
                showAvatarSource = false
                imagePicker.launch(arrayOf("image/*"))
            },
            onUrl = { url -> commitAvatar(url) },
        )
        val saveFailedMsg = stringResource(R.string.save_failed)
        CharacterAvatarCropDialog(
            sourceUri = pendingAvatarUri?.let(Uri::parse),
            onDismissRequest = { pendingAvatarUri = null },
            onConfirm = { crop ->
                val sourceUri =
                    pendingAvatarUri?.let(Uri::parse) ?: return@CharacterAvatarCropDialog
                pendingAvatarUri = null
                avatarScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            saveCharacterAvatar(avatarContext, sourceUri, crop)
                        }
                    }.onSuccess { commitAvatar(it) }.onFailure {
                        avatarContext.toastOnUi(
                            it.localizedMessage ?: saveFailedMsg
                        )
                    }
                }
            },
        )
    }
}

/** 把 [from] 挪到 [to]；越界或原地不动返回 null（调用方据此忽略这次移动）。 */
private fun moveCharacter(
    items: List<VoiceCastingItemUi>,
    from: Int,
    to: Int,
): List<VoiceCastingItemUi>? {
    if (from !in items.indices || to !in items.indices || from == to) return null
    return items.toMutableList().apply { add(to, removeAt(from)) }
}

@Composable
private fun VoiceCastingCard(
    bookUrl: String,
    item: VoiceCastingItemUi,
    voices: List<VoiceOptionUi>,
    onIntent: (BookVoiceCastingIntent) -> Unit,
    onOpenCharacterDetail: (String) -> Unit,
    editing: Boolean = false,
    onToggleEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSetAvatar: () -> Unit = {},
    onSetBubble: () -> Unit = {},
    onClearBubble: () -> Unit = {},
    onChanged: () -> Unit = onToggleEdit,
    dragModifier: Modifier = Modifier,
    dragging: Boolean = false,
) {
    val title = subjectTitle(item.kind, item.name)
    val voiceName = shortenVoiceName(item.voiceName)
    val voiceText = when {
        !item.hasBinding -> stringResource(R.string.voice_not_assigned)
        item.voiceAvailable -> voiceName
        voiceName.isNotBlank() -> stringResource(R.string.voice_unavailable_named, voiceName)
        else -> stringResource(R.string.voice_unavailable)
    }
    val isCharacter = item.kind == CastingSubjectKind.Character
    // 人物档案上那份头像（人物详情里设的）在这里也要露脸：一屏角色靠脸认比靠名字快，
    // 取不到图（链接失效、文件被清）就退回默认图标，不留空圈。
    val avatarUri = item.avatarUri?.takeIf { it.isNotBlank() }
    val avatarLoadFailed = remember(avatarUri) { mutableStateOf(false) }
    // 男女主 / 男女配 / 其余三档整卡外观不同：底色、描边、投影都是卡片这一层的，
    // 不是文字小标签的差别，一屏角色扫下来就能先看到主角。
    val roleVisuals = castRoleCardVisuals(castRoleTierOf(item.role))
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier),
        onClick = {
            // 人物行点下去是官方的人物详情页（改名、头像、简介、事记都在那边），
            // 正在编辑时再点一下收起；旁白点下去直接展开它的音色编辑。
            if (isCharacter) {
                if (editing) onToggleEdit() else onOpenCharacterDetail(item.subjectId)
            } else {
                onToggleEdit()
            }
        },
        containerColor = roleVisuals.containerColor,
        border = roleVisuals.border,
        elevation = if (dragging) 8.dp else roleVisuals.elevation,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(LegadoTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarUri != null && !avatarLoadFailed.value) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onError = { avatarLoadFailed.value = true },
                        )
                    } else {
                        AppIcon(
                            imageVector = when (item.kind) {
                                CastingSubjectKind.Narrator -> Icons.AutoMirrored.Filled.MenuBook
                                CastingSubjectKind.Character -> Icons.Default.Person
                                else -> Icons.Default.RecordVoiceOver
                            },
                            contentDescription = null,
                        )
                    }
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (item.description.isNotBlank() || item.poolLabel.isNotBlank() ||
                        item.voiceEffect.isNotBlank()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (item.description.isNotBlank()) {
                                AnimatedTextLine(
                                    text = item.description,
                                    style = LegadoTheme.typography.bodySmall,
                                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (item.poolLabel.isNotBlank()) {
                                TextCard(text = item.poolLabel)
                            }
                            // 变声器是角色级的全局状态，与人物编辑里那一栏同一份
                            if (item.voiceEffect.isNotBlank()) {
                                TextCard(text = item.voiceEffect)
                            }
                        }
                    }
                    AnimatedTextLine(
                        text = if (item.lineCount > 0) {
                            voiceText + " · " + stringResource(
                                R.string.cast_table_row_count,
                                item.chapterCount,
                                item.lineCount,
                            )
                        } else {
                            voiceText
                        },
                        style = LegadoTheme.typography.labelMedium,
                        color = if (item.hasBinding && !item.voiceAvailable) {
                            LegadoTheme.colorScheme.error
                        } else {
                            LegadoTheme.colorScheme.primary
                        },
                    )
                }
            },
            trailingContent = when {
                !isCharacter && item.hasBinding && !item.voiceAvailable -> {
                    {
                        AppIcon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = stringResource(R.string.voice_unavailable),
                            tint = LegadoTheme.colorScheme.error,
                        )
                    }
                }

                isCharacter -> {
                    {
                        CastRoleRowActions(
                            editing = editing,
                            onToggleEdit = onToggleEdit,
                            onDelete = onDelete,
                            onSetAvatar = onSetAvatar,
                            onSetBubble = onSetBubble,
                            hasBubble = item.bubbleRuleJson.isNotBlank(),
                            onClearBubble = onClearBubble,
                        )
                    }
                }

                else -> null
            },
        ) {
            AnimatedTextLine(text = title)
        }
        // 编辑面板就地展开。这里不能再套 expandVertically：整张卡是 ReorderableItem 的一行，
        // 它自带 Modifier.animateItem()，行高变化本来就由列表按同一份时长统一补间——
        // 再叠一条每帧改高度的动画，两条口径对不上，下面的角色行会压在上面的行上抖。
        if (editing) {
            if (isCharacter) {
                CastRoleEditorPanel(
                    bookUrl = bookUrl,
                    item = item,
                    onSaved = onChanged,
                    onCancel = onToggleEdit,
                )
            } else {
                CastNarratorEditorPanel(
                    item = item,
                    voices = voices,
                    onSave = { voiceId ->
                        onIntent(BookVoiceCastingIntent.SetNarratorVoice(voiceId))
                        onChanged()
                    },
                    onCancel = onToggleEdit,
                )
            }
        }
    }
}

private const val VoiceNameMaxChars = 8

/**
 * 音色名在角色卡上最多八个字。
 *
 * 括号里那串是引擎的模型 ID（`zh-CN-Yunfan:DragonHDLatestNeural` 这种），
 * 全名一行都放不下，加上句数就顶成三行，把整张卡撑高。
 */
private fun shortenVoiceName(name: String): String {
    val head = name.substringBefore('(').substringBefore('（').trimEnd()
    val label = head.ifBlank { name.trim() }
    return if (label.length <= VoiceNameMaxChars) label else label.take(VoiceNameMaxChars) + "…"
}

@Composable
private fun subjectTitle(kind: CastingSubjectKind, name: String): String = when (kind) {
    CastingSubjectKind.Narrator -> stringResource(R.string.voice_role_narrator)
    CastingSubjectKind.UnknownMale -> stringResource(R.string.voice_role_unknown_male)
    CastingSubjectKind.UnknownFemale -> stringResource(R.string.voice_role_unknown_female)
    CastingSubjectKind.Unknown -> stringResource(R.string.voice_role_unknown)
    CastingSubjectKind.Character -> name
}
