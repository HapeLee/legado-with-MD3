package io.legado.app.ui.book.readaloud.cast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.help.readaloud.cast.VoiceAudition
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.CastFieldSpec
import io.legado.app.ui.widget.components.CastFieldStack
import io.legado.app.ui.widget.components.CastOption
import io.legado.app.ui.widget.components.VoiceAuditionButton
import io.legado.app.ui.widget.components.castCardMaxHeight
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionsRow
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 正则角色管理（朗读规则 → 正则角色管理）。
 *
 * 一条规则 = 「正文里出现这段文字时怎么处理」，两种去向：
 * - 角色声音池 + 某个音色：命中的那几个字**由那个音色念**（旁白/角色音都不管它）。
 * - 背景音乐池 + 某段音频：命中的那几个字**不念**，改成播那段音频，走第三条音轨，
 *   与朗读、背景音乐并行，互不打断。
 *
 * 「角色正则」既是正则也是文本：填「爆炸」就是字面命中，填 `爆炸|雷声` 就是正则命中。
 * 正则编不过的（比如少一个右括号的普通文本）整串按字面量处理，不会因为一条写坏规则而整章读不出声。
 *
 * 列表与角色声音池 / 背景音乐池是同一个部件 [PoolTreeList]：分组可嵌套、可折叠、可整组停用，
 * 长按任意一行拖动排序，规则拖到哪个分组头下面即归入该分组（松手才落库）。
 * 规则没有成员那一层，所以 wording 里 `hasMembers = false`：不画展开三角、不显示计数。
 */
@Composable
fun RegexCastRuleRouteScreen(
    onBackClick: () -> Unit,
    viewModel: RegexCastRuleViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RegexCastRuleEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }
    RegexCastRuleScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

/** 一套正则角色列表的文案：只有名词不同，交互与两个池页完全一致。 */
private val RegexCastWording = CastPoolWording(
    searchPoolHint = R.string.regex_cast_search_hint,
    searchMemberHint = R.string.regex_cast_search_hint,
    addMember = R.string.regex_cast_create,
    removeMember = R.string.delete,
    noMembers = R.string.regex_cast_empty,
    poolField = R.string.regex_cast_name,
    createPool = R.string.regex_cast_create,
    editPool = R.string.regex_cast_edit,
    deletePool = R.string.regex_cast_delete_title,
    deletePoolConfirm = R.string.regex_cast_delete_title,
    pickerTitle = R.string.regex_cast_item,
    hasMembers = false,
)

@Composable
fun RegexCastRuleScreen(
    state: RegexCastRuleUiState,
    onIntent: (RegexCastRuleIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onIntent(RegexCastRuleIntent.MoveItem(from.index, to.index))
    }
    // 松手才落库。刚进页面时 isAnyItemDragging 也是 false，所以要先真的拖过一把。
    var dragged by remember { mutableStateOf(false) }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (reorderableState.isAnyItemDragging) {
            dragged = true
        } else if (dragged) {
            dragged = false
            onIntent(RegexCastRuleIntent.SaveSortOrder)
        }
    }

    // 文件选择是宿主动作：Screen 起 SAF 选择器，选到的 uri 交回 ViewModel 读写内容。
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onIntent(RegexCastRuleIntent.ImportFrom(it)) }
    }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { onIntent(RegexCastRuleIntent.ExportTo(it)) }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Miuix 引擎分支不套 contentColor，隐式取色在深色下会发黑
        contentColor = LegadoTheme.colorScheme.onSurface,
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.regex_cast_rule),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionsRow {
                        IconButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = stringResource(R.string.regex_cast_import),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { exporter.launch("regex_cast_rules.json") }) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = stringResource(R.string.regex_cast_export),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onIntent(RegexCastRuleIntent.CreateGroup("")) }) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = stringResource(R.string.regex_cast_new_group),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onIntent(RegexCastRuleIntent.ShowCreate) }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.regex_cast_create),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Text(
                text = stringResource(R.string.regex_cast_rule_summary),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PoolTreeList(
                view = state,
                wording = RegexCastWording,
                actions = CastPoolActions(
                    onMove = { from, to -> onIntent(RegexCastRuleIntent.MoveItem(from, to)) },
                    onQuery = { onIntent(RegexCastRuleIntent.Query(it)) },
                    onToggleGroup = { onIntent(RegexCastRuleIntent.ToggleGroup(it)) },
                    onGroupEnabled = { id, on -> onIntent(RegexCastRuleIntent.GroupEnabled(id, on)) },
                    onRenameGroup = { onIntent(RegexCastRuleIntent.RenameGroup(it)) },
                    onCreateGroup = { onIntent(RegexCastRuleIntent.CreateGroup(it)) },
                    onMoveGroup = { onIntent(RegexCastRuleIntent.MoveGroup(it)) },
                    onDeleteGroup = { onIntent(RegexCastRuleIntent.DeleteGroup(it)) },
                    onPoolEnabled = { id, on -> onIntent(RegexCastRuleIntent.RuleEnabled(id, on)) },
                    onEditPool = { row ->
                        row.id.toLongOrNull()?.let { onIntent(RegexCastRuleIntent.ShowEdit(it)) }
                    },
                    onDeletePool = { row ->
                        row.id.toLongOrNull()?.let { onIntent(RegexCastRuleIntent.ShowDelete(it)) }
                    },
                    // 规则没有成员：下面这一整组回调都不会被界面调到（hasMembers = false）
                    onExpandPool = {},
                    onAddMembers = {},
                    onMemberToggle = { _, _, _ -> },
                    onMemberRemove = { _, _ -> },
                    onMemberQuery = { _, _ -> },
                ),
                listState = listState,
                reorderableState = reorderableState,
                modifier = Modifier.weight(1f),
            )
        }
    }

    state.editTarget?.let { holder ->
        RegexCastEditDialog(
            holder = holder,
            state = state,
            onSave = { rule -> onIntent(RegexCastRuleIntent.Save(rule)) },
            onPickPool = { kind, poolId, keep ->
                onIntent(RegexCastRuleIntent.PickPool(kind, poolId, keep))
            },
            onDismiss = { onIntent(RegexCastRuleIntent.DismissEdit) },
        )
    }

    state.deleteTarget?.let { holder ->
        PoolConfirmDialog(
            title = stringResource(R.string.regex_cast_delete_title),
            text = stringResource(
                R.string.regex_cast_delete_text,
                holder.rule.name.ifBlank { holder.rule.pattern },
            ),
            onConfirm = { onIntent(RegexCastRuleIntent.Delete(holder.rule.id)) },
            onDismiss = { onIntent(RegexCastRuleIntent.DismissDelete) },
        )
    }

    state.groupDialog?.let { dialog ->
        GroupEditDialog(
            dialog = dialog,
            onConfirm = { editingId, parentId, name ->
                onIntent(RegexCastRuleIntent.ConfirmGroup(editingId, parentId, name))
            },
            onDismiss = { onIntent(RegexCastRuleIntent.DismissGroupDialog) },
        )
    }

    state.moveGroupTarget?.let { groupId ->
        MoveGroupDialog(
            target = state.groups.firstOrNull { it.id == groupId },
            groups = state.groups,
            onConfirm = { parent -> onIntent(RegexCastRuleIntent.ConfirmMoveGroup(groupId, parent)) },
            onDismiss = { onIntent(RegexCastRuleIntent.DismissMoveGroup) },
        )
    }

    state.deleteGroupTarget?.let { groupId ->
        PoolConfirmDialog(
            title = stringResource(R.string.regex_cast_delete_group_title),
            text = stringResource(
                R.string.regex_cast_delete_group_text,
                state.groups.firstOrNull { it.id == groupId }?.name.orEmpty(),
            ),
            onConfirm = { onIntent(RegexCastRuleIntent.ConfirmDeleteGroup(groupId)) },
            onDismiss = { onIntent(RegexCastRuleIntent.DismissDeleteGroup) },
        )
    }
}

/**
 * 新建/编辑一条正则角色。
 *
 * 草稿整个留在弹窗本地（[remember] 只按 holder 重建）：换「声音池选择」时 ViewModel 只刷候选，
 * 不会回头覆盖还没提交的编辑。
 *
 * 下拉沿用 [CastFieldStack]：整屏共用一个真输入框，切行不让键盘先收再弹；
 * 打字 = 按输入筛选，点三角 = 浏览全部。声音池不按启用状态过滤，音色也不要求先选池
 * （池只是筛选条件）。分组在列表页那套分组树里建，这里只挑已有分组。
 *
 * 「变声器」那一行只对「换音色念」（[RegexCastRule.POOL_ROLE]）那种向下存在：配乐那一种命中处
 * 不念、没有声音可变，切过去时连草稿值一起清掉，不留一个看不见的死值。候选与预设名由
 * [RegexCastRuleViewModel] 从 VoiceEffectStore 取（[RegexCastRuleUiState.effectOptions]），
 * 界面不直连 Store；写回落在 `regex_cast_rules.voiceEffect`，朗读侧消费方见
 * [io.legado.app.help.readaloud.cast.RegexCastRuleStore.effectsFor]。
 * 试听按钮不占朗读服务，用的就是当前选的音色 + 当前填的预设名。
 */
@Composable
private fun RegexCastEditDialog(
    holder: RegexCastRuleHolder,
    state: RegexCastRuleUiState,
    onSave: (RegexCastRule) -> Unit,
    onPickPool: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val target = holder.rule
    var draft by remember(holder) { mutableStateOf(target) }
    var kindExpanded by remember(holder) { mutableStateOf(false) }
    var poolExpanded by remember(holder) { mutableStateOf(false) }
    var itemExpanded by remember(holder) { mutableStateOf(false) }
    var groupExpanded by remember(holder) { mutableStateOf(false) }
    var effectExpanded by remember(holder) { mutableStateOf(false) }
    val roleLabel = stringResource(R.string.regex_cast_pool_role)
    val bgmLabel = stringResource(R.string.regex_cast_pool_bgm)
    /** 换音色念（可选变声器与试听）还是放配乐（两者都不适用）。 */
    val isRole = draft.poolKind != RegexCastRule.POOL_BGM
    // 下拉的显示值各自记一份：打字是筛选用的，不能直接写回业务字段
    var kindQuery by remember(holder) {
        mutableStateOf(if (target.poolKind == RegexCastRule.POOL_BGM) bgmLabel else roleLabel)
    }
    var poolQuery by remember(holder) {
        mutableStateOf(state.poolOptions.firstOrNull { it.key == target.poolId }?.label.orEmpty())
    }
    var itemQuery by remember(holder) {
        mutableStateOf(state.itemOptions.firstOrNull { it.key == target.itemId }?.label.orEmpty())
    }
    var groupQuery by remember(holder) {
        mutableStateOf(state.groupOptions.firstOrNull { it.key == target.groupId }?.label.orEmpty())
    }
    val kindOptions = listOf(
        CastOption(RegexCastRule.POOL_ROLE, roleLabel),
        CastOption(RegexCastRule.POOL_BGM, bgmLabel),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (holder.isNew) R.string.regex_cast_create else R.string.regex_cast_edit))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = castCardMaxHeight(0.72f))
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 两个开关排在输入栏之前：整屏字段变多后列表会滚，开关压在末尾就等于没了
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.regex_cast_use_regex),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = draft.useRegex,
                            onCheckedChange = { draft = draft.copy(useRegex = it) },
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.regex_cast_enabled),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = draft.enabled,
                            onCheckedChange = { draft = draft.copy(enabled = it) },
                        )
                    }
                }
                val nameSpec = CastFieldSpec(
                    id = "name",
                    label = stringResource(R.string.regex_cast_name),
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                )
                val patternSpec = CastFieldSpec(
                    id = "pattern",
                    // 开关决定这一栏是正则还是原样文字，标题跟着走，别写着「正则」实际按字面量匹配
                    label = stringResource(
                        if (draft.useRegex) {
                            R.string.regex_cast_pattern
                        } else {
                            R.string.regex_cast_match_text
                        }
                    ),
                    value = draft.pattern,
                    onValueChange = { draft = draft.copy(pattern = it) },
                )
                val kindSpec = CastFieldSpec(
                    id = "kind",
                    label = stringResource(R.string.regex_cast_pool_kind),
                    value = kindQuery,
                    options = kindOptions,
                    expanded = kindExpanded,
                    onValueChange = { kindQuery = it },
                    onSelected = { option ->
                        // 换成配乐就没有「念」这件事了：变声器草稿一起清掉，
                        // 免得留一个界面上看不见、却还会影响朗读的旧值
                        draft = draft.copy(poolKind = option.key, poolId = "", voiceEffect = "")
                        kindQuery = option.label
                        poolQuery = ""
                        itemQuery = ""
                        effectExpanded = false
                        onPickPool(option.key, "", "")
                        kindExpanded = false
                    },
                    onExpand = { kindExpanded = it },
                )
                val poolSpec = CastFieldSpec(
                    id = "pool",
                    label = stringResource(R.string.regex_cast_pool),
                    value = poolQuery,
                    options = state.poolOptions,
                    expanded = poolExpanded,
                    onValueChange = { poolQuery = it },
                    onSelected = { option ->
                        draft = draft.copy(poolId = option.key)
                        poolQuery = option.label
                        onPickPool(draft.poolKind, option.key, draft.itemId)
                        poolExpanded = false
                    },
                    onExpand = { poolExpanded = it },
                )
                val itemSpec = CastFieldSpec(
                    id = "item",
                    label = stringResource(R.string.regex_cast_item),
                    value = itemQuery,
                    options = state.itemOptions,
                    expanded = itemExpanded,
                    onValueChange = { itemQuery = it },
                    onSelected = { option ->
                        draft = draft.copy(itemId = option.key)
                        itemQuery = option.label
                        itemExpanded = false
                    },
                    onExpand = { itemExpanded = it },
                )
                // 变声器那一行排在「音色」正下方：它改的就是那个音色怎么念
                val effectSpec = if (isRole) {
                    CastFieldSpec(
                        id = "effect",
                        label = stringResource(R.string.cast_voice_effect),
                        // 存的就是预设名本身（key = label），空串 = 不变声
                        value = draft.voiceEffect,
                        options = state.effectOptions,
                        expanded = effectExpanded,
                        onValueChange = { draft = draft.copy(voiceEffect = it) },
                        onSelected = { option ->
                            draft = draft.copy(voiceEffect = option.key)
                            effectExpanded = false
                        },
                        onExpand = { effectExpanded = it },
                    )
                } else {
                    null
                }
                val groupSpec = CastFieldSpec(
                    id = "group",
                    label = stringResource(R.string.regex_cast_group),
                    value = groupQuery,
                    options = state.groupOptions,
                    expanded = groupExpanded,
                    onValueChange = { groupQuery = it },
                    onSelected = { option ->
                        draft = draft.copy(groupId = option.key)
                        groupQuery = option.label
                        groupExpanded = false
                    },
                    onExpand = { groupExpanded = it },
                )
                val scopeSpec = CastFieldSpec(
                    id = "scope",
                    label = stringResource(R.string.specific_scope),
                    value = draft.scope.orEmpty(),
                    onValueChange = { draft = draft.copy(scope = it.takeIf { v -> v.isNotBlank() }) },
                )
                val excludeSpec = CastFieldSpec(
                    id = "exclude",
                    label = stringResource(R.string.exclude_scope),
                    value = draft.excludeScope.orEmpty(),
                    onValueChange = {
                        draft = draft.copy(excludeScope = it.takeIf { v -> v.isNotBlank() })
                    },
                )
                CastFieldStack(
                    specs = listOf(nameSpec, patternSpec, kindSpec, poolSpec, itemSpec) +
                        listOfNotNull(effectSpec) +
                        listOf(groupSpec, scopeSpec, excludeSpec),
                )
                if (isRole) {
                    // 试听：自己合成一句直接播，不进朗读队列、不打断正在朗读的内容。
                    // 只选了池没定下具体音色（itemId 空 = 朗读时随机）时没有可试的声音，按钮点不动
                    //（VoiceAuditionButton 内部按 voiceId 空白禁用）。
                    VoiceAuditionButton(
                        voiceId = draft.itemId,
                        text = remember(context) { VoiceAudition.defaultPreviewText(context) },
                        effect = draft.voiceEffect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = stringResource(R.string.regex_cast_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.pattern.isNotBlank() && draft.itemId.isNotBlank(),
                onClick = { onSave(draft) },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    // 换池之后候选列表变了，把显示值对齐一次，别留着上一次筛选时打的半截字
    LaunchedEffect(state.itemOptions) {
        val label = state.itemOptions.firstOrNull { it.key == draft.itemId }?.label.orEmpty()
        if (label.isNotEmpty() && itemQuery != label) itemQuery = label
    }
}
