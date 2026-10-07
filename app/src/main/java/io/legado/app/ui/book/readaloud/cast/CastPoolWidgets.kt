package io.legado.app.ui.book.readaloud.cast

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.readaloud.cast.CastGroupRow
import io.legado.app.help.readaloud.cast.CastPoolRow
import io.legado.app.help.readaloud.cast.VoicePoolStore
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.CastFieldSpec
import io.legado.app.ui.widget.components.CastFieldStack
import io.legado.app.ui.widget.components.CastOption
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.reorderAccessibility
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState

/**
 * 「分组树 + 池」这套界面的公共部件。
 *
 * 角色声音池和背景音乐池要长得一模一样，行模型也已经
 * 共用 [CastPoolRow]/[CastGroupRow]，所以列表、拖动、展开成员、增删改与移动对话框都只写
 * 这一遍。这里刻意不认识任何一个页面的 Intent：两个页面各自把自己的 UiState 当作
 * [CastPoolView] 交进来、把意图翻译成 [CastPoolActions] 的回调，以后再加第三种池不用抄代码。
 */

/** 一套池页面的文案差异（只有名词不同，交互完全一致）。 */
@Immutable
data class CastPoolWording(
    @StringRes val searchPoolHint: Int,
    @StringRes val searchMemberHint: Int,
    @StringRes val addMember: Int,
    @StringRes val removeMember: Int,
    @StringRes val noMembers: Int,
    @StringRes val poolField: Int,
    @StringRes val createPool: Int,
    @StringRes val editPool: Int,
    @StringRes val deletePool: Int,
    @StringRes val deletePoolConfirm: Int,
    @StringRes val pickerTitle: Int,
    /**
     * 这一行的卡片有没有「成员」那一层。
     *
     * 角色声音池 / 背景音乐池 = true（点开看成员、加成员、计数 (4/5)）。正则角色 = false：
     * 一条规则本身就是要表达的东西，没有下一级，所以不画展开三角、不显示计数，整行点开是编辑。
     */
    val hasMembers: Boolean = true,
)

/** 角色声音池（成员 = 音色）。 */
val VoicePoolWording = CastPoolWording(
    searchPoolHint = R.string.cast_search_pool_hint,
    searchMemberHint = R.string.cast_search_voice_hint,
    addMember = R.string.cast_add_voice_to_pool,
    removeMember = R.string.cast_remove_from_pool,
    noMembers = R.string.cast_pool_no_members,
    poolField = R.string.cast_voice_pool,
    createPool = R.string.cast_create_pool,
    editPool = R.string.cast_edit_pool,
    deletePool = R.string.cast_delete_pool,
    deletePoolConfirm = R.string.cast_delete_pool_confirm,
    pickerTitle = R.string.cast_add_voice_to_pool,
)

/** 背景音乐池（成员 = 配乐文件）。 */
val BgmPoolWording = CastPoolWording(
    searchPoolHint = R.string.cast_search_bgm_pool_hint,
    searchMemberHint = R.string.cast_search_track_hint,
    addMember = R.string.cast_add_track_to_pool,
    removeMember = R.string.cast_remove_from_bgm_pool,
    noMembers = R.string.cast_bgm_pool_no_members,
    poolField = R.string.cast_bgm_pool,
    createPool = R.string.cast_create_bgm_pool,
    editPool = R.string.cast_edit_bgm_pool,
    deletePool = R.string.cast_delete_bgm_pool,
    deletePoolConfirm = R.string.cast_delete_bgm_pool_confirm,
    pickerTitle = R.string.cast_add_track_to_pool,
)

/**
 * 页面交给公共部件的状态。两个池页的 UiState 都实现它（它们的字段同名同义），
 * 所以既不用把状态拷一遍，也不会出现「改了页面忘记改另一份」的错位。
 */
interface CastPoolView {
    val pools: List<CastPoolRow>
    val groups: List<CastGroupRow>

    /** 拉平后实际渲染的行。 */
    val rows: List<PoolTreeRow>
    val collapsedGroups: Set<String>
    val searchActive: Boolean
    val searchQuery: String
    val dragTargetGroupId: String?
    val dragSourceGroupId: String?

    /** 展开中的池（行内显示成员列表），每个池自带成员与筛选词，所以能同时展开多个。 */
    val expandedPools: List<ExpandedPoolUi>
}

/**
 * 一个展开中的池。成员列表是筛选前的全量，[memberQuery] 只影响显示——
 * 若把筛完的结果写回状态，删空关键词后列表就回不到全量了。
 */
data class ExpandedPoolUi(
    val poolId: String,
    val members: List<CastMemberUi> = emptyList(),
    val memberQuery: String = "",
)

/**
 * 改掉 [poolId] 那一份展开状态；该池没展开就原样返回（后台刷新回来时池可能已经被收起了）。
 * 两个池页共用，所以展开状态只有这一处口径。
 */
fun List<ExpandedPoolUi>.withExpandedPool(
    poolId: String,
    transform: (ExpandedPoolUi) -> ExpandedPoolUi,
): List<ExpandedPoolUi> =
    if (none { it.poolId == poolId }) {
        this
    } else {
        map { if (it.poolId == poolId) transform(it) else it }
    }

/** 公共部件发回页面的动作，全部是回调：本文件不 import 任何页面的 Intent。 */
class CastPoolActions(
    val onMove: (Int, Int) -> Unit,
    val onQuery: (String) -> Unit,
    val onToggleGroup: (String) -> Unit,
    val onGroupEnabled: (String, Boolean) -> Unit,
    val onRenameGroup: (String) -> Unit,
    val onCreateGroup: (String) -> Unit,
    val onMoveGroup: (String) -> Unit,
    val onDeleteGroup: (String) -> Unit,
    val onPoolEnabled: (String, Boolean) -> Unit,
    val onEditPool: (CastPoolRow) -> Unit,
    val onDeletePool: (CastPoolRow) -> Unit,
    val onExpandPool: (String) -> Unit,
    /** 可以同时展开多个池，所以「加成员」这种池内动作一律带上池 id。 */
    val onAddMembers: (String) -> Unit,
    /** (池 id, 成员 id, 勾选) */
    val onMemberToggle: (String, String, Boolean) -> Unit,
    val onMemberRemove: (String, String) -> Unit,
    /** (池 id, 筛选词) */
    val onMemberQuery: (String, String) -> Unit,
)

@Composable
fun PoolTreeList(
    view: CastPoolView,
    wording: CastPoolWording,
    actions: CastPoolActions,
    listState: LazyListState,
    reorderableState: ReorderableLazyListState,
    modifier: Modifier = Modifier,
    /** 成员行尾部的业务按钮（背景音乐那边放播放/停止）。 */
    memberTrailing: (@Composable (CastMemberUi) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val rows = view.rows
    // 搜索态下列表是拉平的命中结果，拖动会打乱归属，禁掉
    val canReorder = view.searchQuery.isBlank()
    // 分组头计数 = 直属子项（池 + 子组）里开着的 / 总数，和文件夹一样：
    // 子组里的声音池算子组自己的，不冒泡到外层组。「未分组」不是父级，根层分组不算它头上。
    fun childCount(groupId: String, withSubgroups: Boolean = true): Pair<Int, Int> {
        val pools = view.pools.filter { it.groupId == groupId }
        val groups = if (withSubgroups) view.groups.filter { it.parentId == groupId } else emptyList()
        return (pools.count { it.enabled } + groups.count { it.enabled }) to
            (pools.size + groups.size)
    }
    // 搜索框和提示必须放在 LazyColumn 外面：reorderable 回调给的是 LazyColumn 的
    // 绝对下标，列表里只要多一个前置 item，下标就会整体偏移，拖动时条目会来回乱跳。
    Column(modifier = modifier) {
        if (view.searchActive) {
            OutlinedTextField(
                value = view.searchQuery,
                onValueChange = actions.onQuery,
                placeholder = {
                    Text(
                        stringResource(wording.searchPoolHint),
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (canReorder) {
            Text(
                text = stringResource(R.string.cast_pool_sort_hint),
                style = MaterialTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        ) {
            items(rows, key = { it.key }) { row ->
                ReorderableItem(reorderableState, key = row.key) { isDragging ->
                    val index = rows.indexOf(row)
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 8.dp else 0.dp,
                        label = "DragElevation",
                    )
                    val draggable = canReorder && !row.isUngroupedHeader
                    val label = row.title.ifBlank { row.pool?.name.orEmpty() }
                    val dragModifier = Modifier
                        .reorderAccessibility(
                            index = index,
                            itemCount = rows.size,
                            enabled = draggable,
                            description = stringResource(R.string.a11y_reorder_named, label),
                        ) { from, to ->
                            actions.onMove(from, to)
                        }
                        .then(
                            if (draggable) {
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    },
                                )
                            } else {
                                Modifier
                            },
                        )
                    when {
                        row.isUngroupedHeader -> {
                            val (on, all) = childCount(UNGROUPED, withSubgroups = false)
                            GroupHeader(
                                title = stringResource(R.string.cast_group_none),
                                enabledCount = on,
                                total = all,
                                dropTarget = view.dragTargetGroupId == UNGROUPED,
                                collapsed = view.collapsedGroups.contains(UNGROUPED),
                                dragModifier = dragModifier,
                                onToggle = { actions.onToggleGroup(UNGROUPED) },
                                onRename = null,
                                onNewSubgroup = null,
                                onMove = null,
                                onDelete = null,
                            )
                        }

                        row.isGroup -> {
                            val (groupOn, groupAll) = childCount(row.groupId)
                            GroupHeader(
                                title = row.title,
                                depth = row.depth,
                                enabledCount = groupOn,
                                total = groupAll,
                                dropTarget = view.dragTargetGroupId == row.groupId,
                                collapsed = view.collapsedGroups.contains(row.groupId),
                                dragModifier = dragModifier,
                                elevation = elevation,
                                enabled = row.group?.enabled ?: true,
                                onEnabledChange = { on -> actions.onGroupEnabled(row.groupId, on) },
                                onToggle = { actions.onToggleGroup(row.groupId) },
                                onRename = { actions.onRenameGroup(row.groupId) },
                                onNewSubgroup = { actions.onCreateGroup(row.groupId) },
                                onMove = { actions.onMoveGroup(row.groupId) },
                                onDelete = { actions.onDeleteGroup(row.groupId) },
                            )
                        }

                        else -> row.pool?.let { pool ->
                            val opened = view.expandedPools.firstOrNull { it.poolId == pool.id }
                            CastPoolCard(
                                pool = pool,
                                depth = row.depth,
                                showGroup = !canReorder,
                                expanded = opened != null,
                                members = opened?.members.orEmpty(),
                                memberQuery = opened?.memberQuery.orEmpty(),
                                wording = wording,
                                dragModifier = dragModifier,
                                elevation = elevation,
                                onToggle = { actions.onPoolEnabled(pool.id, it) },
                                onEdit = { actions.onEditPool(pool) },
                                onDelete = { actions.onDeletePool(pool) },
                                onExpand = { actions.onExpandPool(pool.id) },
                                onAddMembers = { actions.onAddMembers(pool.id) },
                                onMemberToggle = { id, on -> actions.onMemberToggle(pool.id, id, on) },
                                onMemberRemove = { id -> actions.onMemberRemove(pool.id, id) },
                                onQuery = { q -> actions.onMemberQuery(pool.id, q) },
                                memberTrailing = memberTrailing,
                            )
                        }
                    }
                }
            }
        }
        // 拖动中把「松手会怎样」写在列表下方。落点分组头的高亮很可能已经被滚出屏幕，
        // 只有这一条永远看得见，用户不用猜自己这一把是放进去还是拖出来。
        DragOutcomeBar(view)
    }
}

/** 拖动进行中的落点说明（列表底部，一直在屏幕上）。 */
@Composable
private fun DragOutcomeBar(view: CastPoolView) {
    val source = view.dragSourceGroupId ?: return
    if (view.searchQuery.isNotBlank()) return
    val target = view.dragTargetGroupId
    val ungrouped = stringResource(R.string.cast_group_none)
    fun nameOf(id: String) = view.groups.firstOrNull { it.id == id }?.name ?: ungrouped
    val outcome = when {
        target == null -> stringResource(R.string.cast_pool_drag_invalid)
        target == source && target.isEmpty() ->
            stringResource(R.string.cast_pool_drag_stay_root)

        target == source ->
            stringResource(R.string.cast_pool_drag_stay, nameOf(target))

        target.isEmpty() ->
            stringResource(R.string.cast_pool_drag_leave, nameOf(source))

        source.isEmpty() ->
            stringResource(R.string.cast_pool_drag_join, nameOf(target))

        else -> stringResource(
            R.string.cast_pool_drag_move,
            nameOf(source),
            nameOf(target),
        )
    }
    NormalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        cornerRadius = 14.dp,
        elevation = 3.dp,
    ) {
        Text(
            text = outcome,
            style = MaterialTheme.typography.labelLarge,
            color = LegadoTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun GroupHeader(
    title: String,
    collapsed: Boolean,
    enabledCount: Int,
    total: Int,
    dragModifier: Modifier,
    onToggle: () -> Unit,
    onRename: (() -> Unit)?,
    onNewSubgroup: (() -> Unit)?,
    onMove: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    enabled: Boolean = true,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    /** 正拖着别的东西、且松手就会归入这个组：整条高亮，给出「往哪儿放」的反馈。 */
    dropTarget: Boolean = false,
    depth: Int = 0,
    elevation: Dp = 0.dp,
) {
    var showMenu by remember { mutableStateOf(false) }
    NormalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp, top = 6.dp, bottom = 6.dp)
            .then(dragModifier),
        cornerRadius = 14.dp,
        elevation = elevation,
        containerColor = if (dropTarget) {
            LegadoTheme.colorScheme.secondaryContainer
        } else {
            null
        },
        border = if (dropTarget) {
            BorderStroke(2.dp, LegadoTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = LegadoTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (dropTarget) {
                Text(
                    text = stringResource(R.string.cast_group_drop_here),
                    style = MaterialTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            onEnabledChange?.let { change ->
                Switch(checked = enabled, onCheckedChange = change)
            }
            Text(
                text = "($enabledCount/$total)",
                style = MaterialTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            if (onRename != null || onDelete != null) {
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.width(40.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more),
                            tint = LegadoTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                    RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) { dismiss ->
                        onRename?.let { action ->
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.cast_group_rename),
                                onClick = {
                                    dismiss()
                                    action()
                                },
                            )
                        }
                        onNewSubgroup?.let { action ->
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.cast_subgroup_create),
                                onClick = {
                                    dismiss()
                                    action()
                                },
                            )
                        }
                        onMove?.let { action ->
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.cast_group_move),
                                onClick = {
                                    dismiss()
                                    action()
                                },
                            )
                        }
                        onDelete?.let { action ->
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.cast_group_delete),
                                onClick = {
                                    dismiss()
                                    action()
                                },
                            )
                        }
                    }
                }
            }
            Icon(
                imageVector = if (collapsed) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropUp,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CastPoolCard(
    pool: CastPoolRow,
    depth: Int,
    showGroup: Boolean,
    expanded: Boolean,
    members: List<CastMemberUi>,
    memberQuery: String,
    wording: CastPoolWording,
    dragModifier: Modifier,
    elevation: Dp,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExpand: () -> Unit,
    onAddMembers: () -> Unit,
    onMemberToggle: (String, Boolean) -> Unit,
    onMemberRemove: (String) -> Unit,
    onQuery: (String) -> Unit,
    memberTrailing: (@Composable (CastMemberUi) -> Unit)?,
) {
    // 筛选词只管显示：状态里留的是池的全量成员，删空关键词就能看回来。
    // 把筛完的结果写回状态会让列表在筛过一次、删掉词之后永久变窄。
    val memberFilter = memberQuery.trim()
    val visibleMembers = if (memberFilter.isEmpty()) {
        members
    } else {
        members.filter { it.displayName.contains(memberFilter, ignoreCase = true) }
    }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .then(dragModifier),
        cornerRadius = 16.dp,
        elevation = elevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = if (wording.hasMembers) onExpand else onEdit)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = if (wording.hasMembers) {
                        "${pool.name} (${pool.enabledCount}/${pool.total})"
                    } else {
                        pool.name
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = LegadoTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!wording.hasMembers && pool.subtitle.isNotBlank()) {
                    Text(
                        text = pool.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 搜索态是拉平列表，得标出池所在分组
                if (showGroup && pool.groupName.isNotBlank()) {
                    Text(
                        text = pool.groupName,
                        style = MaterialTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(checked = pool.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit),
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = LegadoTheme.colorScheme.error,
                )
            }
            if (wording.hasMembers) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.ArrowDropUp
                    } else {
                        Icons.Default.ArrowDropDown
                    },
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 成员列表就地展开/收起，不套 expandVertically：整张池卡是 ReorderableItem 里的一行，
        // 它自带 Modifier.animateItem()。两条高度动画口径不一致——animateItem 按「上一帧量到的
        // 行高」摆放后面的行，expandVertically 每帧都在改这一行的真实高度，于是展开时
        // 下面的行直接压在上面的行上。
        if (expanded) {
            // 展开区：添加成员 + 成员复选框列表（复选框 = 池内启用）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = memberQuery,
                    onValueChange = onQuery,
                    placeholder = {
                        Text(
                            stringResource(wording.searchMemberHint),
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                )
                IconButton(onClick = onAddMembers) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(wording.addMember),
                        tint = LegadoTheme.colorScheme.onSurface,
                    )
                }
            }
            if (visibleMembers.isEmpty()) {
                Text(
                    text = stringResource(wording.noMembers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            visibleMembers.forEach { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMemberToggle(member.voiceId, !member.checked) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = member.checked, onCheckedChange = { onMemberToggle(member.voiceId, it) })
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = member.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LegadoTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 池里能混着不同引擎的音色，得看清这一条是谁家的（同「朗读引擎与音色」页）
                        if (member.engineName.isNotBlank()) {
                            Text(
                                text = member.engineName,
                                style = MaterialTheme.typography.bodySmall,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    memberTrailing?.invoke(member)
                    IconButton(onClick = { onMemberRemove(member.voiceId) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(wording.removeMember),
                            tint = LegadoTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(1.dp).padding(bottom = 6.dp))
        }
    }
}

/** 分组候选：分组树拉平 + 缩进，首项固定「未分组」。 */
@Composable
fun castGroupOptions(groups: List<CastGroupRow>): List<CastOption> = buildList {
    add(CastOption(UNGROUPED, stringResource(R.string.cast_group_none)))
    groups.forEach { add(CastOption(it.id, INDENT.repeat(it.depth) + it.name)) }
}

/**
 * 新建/编辑池。
 *
 * 分组改成从列表里选（三角展开分组树），不再手打组名——所以整屏只剩一个真输入框，
 * 切行不会让键盘先收再弹，见 [CastFieldStack]。
 */
@Composable
fun PoolEditDialog(
    dialog: PoolEditDialogState,
    groups: List<CastGroupRow>,
    wording: CastPoolWording,
    onSave: (editingId: String?, name: String, groupId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = castGroupOptions(groups)
    var name by remember(dialog) { mutableStateOf(dialog.name) }
    var groupId by remember(dialog) { mutableStateOf(dialog.groupId) }
    // 选中项的显示名。打字是按它筛选，所以不能拿 groupId 反推。
    var groupQuery by remember(dialog) {
        mutableStateOf(options.firstOrNull { it.key == dialog.groupId }?.label.orEmpty())
    }
    var groupExpanded by remember(dialog) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (dialog.editingId == null) wording.createPool else wording.editPool),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CastFieldStack(
                    specs = listOf(
                        CastFieldSpec(
                            id = POOL_NAME_FIELD,
                            label = stringResource(wording.poolField),
                            value = name,
                            onValueChange = { name = it },
                        ),
                        CastFieldSpec(
                            id = GROUP_FIELD,
                            label = stringResource(R.string.cast_group),
                            value = groupQuery,
                            options = options,
                            expanded = groupExpanded,
                            onValueChange = { groupQuery = it },
                            onSelected = {
                                groupId = it.key
                                groupQuery = it.label
                            },
                            onExpand = { groupExpanded = it },
                        ),
                    ),
                )
                dialog.errorRes?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(dialog.editingId, name, groupId) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun GroupEditDialog(
    dialog: GroupEditDialogState,
    onConfirm: (editingId: String?, parentId: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(dialog) { mutableStateOf(dialog.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (dialog.editingId == null) R.string.cast_group_create else R.string.cast_group_rename,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.cast_group)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                dialog.errorRes?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(dialog.editingId, dialog.parentId, name) },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** 分组换父级：拖动改不了组的层级，所以给一个「移动到」列表。 */
@Composable
fun MoveGroupDialog(
    target: CastGroupRow?,
    groups: List<CastGroupRow>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 不能挂到自己或自己的子孙下面：整棵子树从候选里剔掉
    val options = remember(target, groups) {
        val self = target?.path ?: return@remember groups
        groups.filterNot {
            it.id == target.id || it.path.startsWith("$self${VoicePoolStore.GROUP_SEPARATOR}")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cast_group_move_title, target?.name.orEmpty())) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                MoveRow(
                    label = stringResource(R.string.cast_group_root),
                    selected = target?.parentId == UNGROUPED,
                ) { onConfirm(UNGROUPED) }
                options.forEach { group ->
                    MoveRow(label = INDENT.repeat(group.depth) + group.name, selected = false) {
                        onConfirm(group.id)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MoveRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) {
            LegadoTheme.colorScheme.primary
        } else {
            LegadoTheme.colorScheme.onSurface
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

@Composable
fun PoolConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** 「添加成员」对话框：勾选候选，保存才并入池。 */
@Composable
fun MemberPickerDialog(
    pickerQuery: String,
    candidates: List<CastMemberUi>,
    wording: CastPoolWording,
    onQuery: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(wording.pickerTitle)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pickerQuery,
                    onValueChange = onQuery,
                    placeholder = { Text(stringResource(wording.searchMemberHint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (candidates.isEmpty()) {
                            Text(
                                text = stringResource(R.string.cast_no_match),
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                        candidates.forEach { cand ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(cand.voiceId, !cand.checked) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = cand.checked,
                                    onCheckedChange = { onToggle(cand.voiceId, it) },
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cand.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (cand.engineName.isNotBlank()) {
                                        Text(
                                            text = cand.engineName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** 「未分组」段的 id：不对应分组行，只作拖动落点与收起状态用。 */
const val UNGROUPED = ""
private const val POOL_NAME_FIELD = "pool_name"
private const val GROUP_FIELD = "pool_group"

/** 树形缩进用的全角空格：半角空格在 Text 里会被压缩，缩进就看不出来了。 */
private const val INDENT = "　"
