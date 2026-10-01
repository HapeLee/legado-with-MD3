package io.legado.app.ui.book.readaloud.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.CastFieldSpec
import io.legado.app.ui.widget.components.CastFieldStack
import io.legado.app.ui.widget.components.CastOption
import io.legado.app.ui.widget.components.castCardMaxHeight
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionsRow
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.koin.androidx.compose.koinViewModel

/**
 * 正则角色管理（朗读规则 → 正则角色管理）。
 *
 * 一条规则 = 「正文里出现这段文字时怎么处理」，两种去向：
 * - 角色声音池 + 某个音色：命中的那几个字**由那个音色念**（旁白/角色音都不管它）。
 * - 背景音乐池 + 某段音频：命中的那几个字**不念**，改成播那段音频，走第三条音轨，
 *   与朗读、背景音乐并行，互不打断。
 *
 * 「角色正则」既是正则也是文本：填「爆炸」就是字面命中，填 `（爆炸|雷声）` 就是正则命中。
 * 正则编不过的（比如带裸括号的普通文本）整串按字面量处理，不会因为一条写坏规则而整章读不出声。
 */
@Composable
fun RegexCastRuleRouteScreen(
    onBackClick: () -> Unit,
    viewModel: RegexCastRuleViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RegexCastRuleScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@Composable
fun RegexCastRuleScreen(
    state: RegexCastRuleUiState,
    onIntent: (RegexCastRuleIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = adaptiveContentPadding(top = 0.dp, bottom = 120.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.regex_cast_rule_summary),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.regex_cast_empty),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.rows.size, key = { state.rows[it].rule.id }) { index ->
                val row = state.rows[index]
                Column {
                    row.section?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = LegadoTheme.colorScheme.primary,
                        )
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                text = row.rule.name.ifBlank { row.rule.pattern },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = row.summary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = row.rule.enabled,
                                    onCheckedChange = {
                                        onIntent(RegexCastRuleIntent.Toggle(row.rule, it))
                                    },
                                )
                                IconButton(onClick = { onIntent(RegexCastRuleIntent.ShowDelete(row.rule)) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                    )
                                }
                            }
                        },
                        // 整行点开编辑，不额外摆按钮
                        modifier = Modifier
                            .animateItem()
                            .clickable { onIntent(RegexCastRuleIntent.ShowEdit(row.rule)) },
                    )
                }
            }
        }
    }

    state.editTarget?.let { target ->
        RegexCastEditDialog(
            target = target,
            isNew = state.isNew,
            state = state,
            onSave = { rule -> onIntent(RegexCastRuleIntent.Save(rule)) },
            onPickPool = { kind, poolId -> onIntent(RegexCastRuleIntent.PickPool(kind, poolId)) },
            onDismiss = { onIntent(RegexCastRuleIntent.DismissEdit) },
        )
    }

    state.deleteTarget?.let { target ->
        PoolConfirmDialog(
            title = stringResource(R.string.regex_cast_delete_title),
            text = stringResource(R.string.regex_cast_delete_text, target.name.ifBlank { target.pattern }),
            onConfirm = { onIntent(RegexCastRuleIntent.Delete(target)) },
            onDismiss = { onIntent(RegexCastRuleIntent.DismissDelete) },
        )
    }
}

/**
 * 新建/编辑一条正则角色。
 *
 * 草稿整个留在弹窗本地（[remember] 只按 id 重建）：换「声音池选择」时 ViewModel 只刷候选，
 * 不会回头覆盖还没提交的编辑。
 */
@Composable
private fun RegexCastEditDialog(
    target: RegexCastRule,
    isNew: Boolean,
    state: RegexCastRuleUiState,
    onSave: (RegexCastRule) -> Unit,
    onPickPool: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(target.id, isNew) { mutableStateOf(target) }
    var kindExpanded by remember(target.id) { mutableStateOf(false) }
    var poolExpanded by remember(target.id) { mutableStateOf(false) }
    var itemExpanded by remember(target.id) { mutableStateOf(false) }
    var groupExpanded by remember(target.id) { mutableStateOf(false) }
    val kindLabels = listOf(
        stringResource(R.string.regex_cast_pool_role) to RegexCastRule.POOL_ROLE,
        stringResource(R.string.regex_cast_pool_bgm) to RegexCastRule.POOL_BGM,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isNew) R.string.regex_cast_create else R.string.regex_cast_edit))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = castCardMaxHeight(0.72f))
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CastFieldStack(
                    specs = listOf(
                        CastFieldSpec(
                            id = "name",
                            label = stringResource(R.string.regex_cast_name),
                            value = draft.name,
                            onValueChange = { draft = draft.copy(name = it) },
                        ),
                        CastFieldSpec(
                            id = "pattern",
                            label = stringResource(R.string.regex_cast_pattern),
                            value = draft.pattern,
                            onValueChange = { draft = draft.copy(pattern = it) },
                        ),
                        CastFieldSpec(
                            id = "kind",
                            label = stringResource(R.string.regex_cast_pool_kind),
                            value = kindLabels.firstOrNull { it.second == draft.poolKind }?.first.orEmpty(),
                            options = kindLabels.map { (label, key) -> CastOption(key, label) },
                            expanded = kindExpanded,
                            onValueChange = { },
                            onSelected = { option ->
                                draft = draft.copy(poolKind = option.key, poolId = "", itemId = "")
                                onPickPool(option.key, "")
                                kindExpanded = false
                            },
                            onExpand = { kindExpanded = it },
                        ),
                        CastFieldSpec(
                            id = "pool",
                            label = stringResource(R.string.regex_cast_pool),
                            value = state.poolOptions.firstOrNull { it.key == draft.poolId }?.label.orEmpty(),
                            options = state.poolOptions,
                            expanded = poolExpanded,
                            onValueChange = { },
                            onSelected = { option ->
                                draft = draft.copy(poolId = option.key, itemId = "")
                                onPickPool(draft.poolKind, option.key)
                                poolExpanded = false
                            },
                            onExpand = { poolExpanded = it },
                        ),
                        CastFieldSpec(
                            id = "item",
                            label = stringResource(R.string.regex_cast_item),
                            value = state.itemOptions.firstOrNull { it.key == draft.itemId }?.label.orEmpty(),
                            options = state.itemOptions,
                            expanded = itemExpanded,
                            onValueChange = { },
                            onSelected = { option ->
                                draft = draft.copy(itemId = option.key)
                                itemExpanded = false
                            },
                            onExpand = { itemExpanded = it },
                        ),
                        CastFieldSpec(
                            id = "group",
                            label = stringResource(R.string.regex_cast_group),
                            value = draft.group,
                            options = state.groupOptions,
                            expanded = groupExpanded,
                            onValueChange = { draft = draft.copy(group = it) },
                            onSelected = { option ->
                                draft = draft.copy(group = option.key)
                                groupExpanded = false
                            },
                            onExpand = { groupExpanded = it },
                        ),
                        CastFieldSpec(
                            id = "scope",
                            label = stringResource(R.string.specific_scope),
                            value = draft.scope.orEmpty(),
                            onValueChange = { draft = draft.copy(scope = it.takeIf { v -> v.isNotBlank() }) },
                        ),
                        CastFieldSpec(
                            id = "exclude",
                            label = stringResource(R.string.exclude_scope),
                            value = draft.excludeScope.orEmpty(),
                            onValueChange = {
                                draft = draft.copy(excludeScope = it.takeIf { v -> v.isNotBlank() })
                            },
                        ),
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
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
                Text(
                    text = stringResource(R.string.regex_cast_scope_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.pattern.isNotBlank() && draft.poolId.isNotBlank(),
                onClick = { onSave(draft) },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
