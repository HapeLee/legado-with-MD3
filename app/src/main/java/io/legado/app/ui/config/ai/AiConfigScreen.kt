package io.legado.app.ui.config.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.AiSkillProfile
import io.legado.app.domain.model.AiToolGroup
import io.legado.app.feature.settings.search.SearchableSetting
import io.legado.app.feature.settings.search.SearchableSettingsList
import io.legado.app.help.config.AppConfigStore
import io.legado.app.ui.book.read.AiQuickAccessViewModel
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiConfigRouteScreen(
    onBackClick: () -> Unit,
    onNavigateToProviderEdit: (providerId: String?) -> Unit,
    onNavigateToModelEdit: (providerId: String?, modelProfileId: String?) -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToAiSummary: () -> Unit,
    onNavigateToAiPrompt: () -> Unit,
    viewModel: AiConfigViewModel = koinViewModel()
) {
    AiConfigScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onNavigateToProviderEdit = onNavigateToProviderEdit,
        onNavigateToModelEdit = onNavigateToModelEdit,
        onNavigateToTranslation = onNavigateToTranslation,
        onNavigateToAiSummary = onNavigateToAiSummary,
        onNavigateToAiPrompt = onNavigateToAiPrompt
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigScreen(
    state: AiConfigUiState,
    effects: Flow<AiConfigEffect>,
    onIntent: (AiConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToProviderEdit: (providerId: String?) -> Unit,
    onNavigateToModelEdit: (providerId: String?, modelProfileId: String?) -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToAiSummary: () -> Unit,
    onNavigateToAiPrompt: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showModelSheet by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<AiSkillProfile?>(null) }
    var showQuickSkillPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiConfigEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is AiConfigEffect.ShowMessageRes ->
                    snackbarHostState.showSnackbar(resources.getString(effect.resId))
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.ai_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        SearchableSettingsList(
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_provider_database)) {
                    state.providers.forEach { provider ->
                        ClickableSettingItem(
                            title = provider.providerName,
                            description = provider.baseUrl,
                            option = "${provider.protocol} / ${provider.modelCount}",
                            onClick = { onNavigateToProviderEdit(provider.providerId) }
                        )
                    }
                    SearchableSetting("ai.ai_new_provider") {
                        ClickableSettingItem(
                            title = stringResource(R.string.ai_new_provider),
                            onClick = { onNavigateToProviderEdit(null) }
                        )
                    }
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_model_database)) {
                    SearchableSetting("ai.ai_current_model") {
                        ClickableSettingItem(
                            title = stringResource(R.string.ai_current_model),
                            description = state.currentModelName.ifBlank {
                                stringResource(R.string.ai_model_not_configured)
                            },
                            onClick = {
                                if (state.models.none { it.text && it.enabled }) {
                                    onNavigateToProviderEdit(null)
                                } else {
                                    showModelSheet = true
                                }
                            }
                        )
                    }
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_tasks)) {
                    SearchableSetting("ai.translation_config") {
                        ClickableSettingItem(
                            title = stringResource(R.string.translation_config),
                            onClick = onNavigateToTranslation
                        )
                    }
                    SearchableSetting("ai.ai_chapter_summary") {
                        ClickableSettingItem(
                            title = stringResource(R.string.ai_chapter_summary),
                            onClick = onNavigateToAiSummary
                        )
                    }
                    SearchableSetting("ai.ai_prompt_config") {
                        ClickableSettingItem(
                            title = stringResource(R.string.ai_prompt_config),
                            description = stringResource(R.string.ai_prompt_config_desc),
                            onClick = onNavigateToAiPrompt
                        )
                    }
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_skills)) {
                    state.skills.forEach { skill ->
                        ClickableSettingItem(
                            title = skill.name,
                            description = skill.description.ifBlank {
                                stringResource(R.string.ai_skill_no_description)
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = skill.enabled,
                                        onCheckedChange = {
                                            onIntent(AiConfigIntent.SetSkillEnabled(skill.id, it))
                                        }
                                    )
                                    IconButton(
                                        onClick = { onIntent(AiConfigIntent.DeleteSkill(skill.id)) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.delete)
                                        )
                                    }
                                }
                            },
                            onClick = { editingSkill = skill.toProfile() }
                        )
                    }
                    SearchableSetting("ai.ai_quick_skill_picker") {
                        ClickableSettingItem(
                            title = stringResource(R.string.ai_quick_skill_picker),
                            description = stringResource(R.string.ai_quick_skill_picker_desc),
                            onClick = { showQuickSkillPicker = true }
                        )
                    }
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_new_skill),
                        onClick = {
                            editingSkill = AiSkillProfile(id = "", name = "", content = "")
                        }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_tool_groups)) {
                    Text(
                        text = stringResource(R.string.ai_tool_groups_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    AiToolGroup.values().forEach { group ->
                        val enabled = group.key !in state.disabledToolGroups
                        ClickableSettingItem(
                            title = stringResource(group.titleRes()),
                            description = stringResource(group.descriptionRes()),
                            trailingContent = {
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { checked ->
                                        onIntent(
                                            AiConfigIntent.SetToolGroupEnabled(group.key, checked)
                                        )
                                    }
                                )
                            },
                            onClick = {
                                onIntent(AiConfigIntent.SetToolGroupEnabled(group.key, !enabled))
                            }
                        )
                    }
                }
            }
        }
    }

    AppModalBottomSheet(
        show = showModelSheet,
        onDismissRequest = { showModelSheet = false },
        title = stringResource(R.string.ai_select_model)
    ) {
        LazyColumn {
            state.providers
                .filter { it.models.any { model -> model.text && model.enabled } }
                .forEach { provider ->
                    item {
                        SplicedColumnGroup(title = provider.providerName) {
                            if (provider.models.isEmpty()) {
                                ClickableSettingItem(
                                    title = stringResource(R.string.ai_no_models_imported),
                                    description = stringResource(R.string.ai_fetch_and_save_models),
                                    onClick = {
                                        showModelSheet = false
                                        onNavigateToProviderEdit(provider.providerId)
                                    }
                                )
                            } else {
                                provider.models.filter { it.text && it.enabled }.forEach { model ->
                                    ClickableSettingItem(
                                        title = model.modelName,
                                        description = model.modelId,
                                        trailingContent = if (model.isCurrent) {
                                            { TextCard(text = stringResource(R.string.ai_current)) }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            onIntent(AiConfigIntent.SetDefaultModel(model.modelProfileId))
                                            showModelSheet = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    QuickSkillPickerSheet(
        show = showQuickSkillPicker,
        skills = state.skills,
        onDismiss = { showQuickSkillPicker = false },
        onSave = { ids ->
            // 用逗号拼接写入；空集写空串（raw != null → 菜单不显示任何技能）。
            AppConfigStore.putString(
                PreferKey.aiQuickSkillIds,
                ids.joinToString(",")
            )
            showQuickSkillPicker = false
        }
    )

    if (editingSkill != null) {
        val skill = editingSkill!!
        var name by remember(skill) { mutableStateOf(skill.name) }
        var content by remember(skill) { mutableStateOf(skill.content) }
        var desc by remember(skill) { mutableStateOf(skill.description) }
        var url by remember(skill) { mutableStateOf(skill.sourceUrl) }

        AppModalBottomSheet(
            show = true,
            onDismissRequest = { editingSkill = null },
            title = stringResource(
                if (skill.id.isBlank()) R.string.ai_new_skill else R.string.ai_edit_skill
            )
        ) {
            // 正文可能很长（SKILL.md 动辄上百行）。整体可滚动 + 正文框限高内部滚动，
            // 避免正文把「来源链接」和「保存」挤出可视区。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ai_skill_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(stringResource(R.string.ai_skill_description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.ai_skill_content)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.ai_skill_source_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onIntent(
                            AiConfigIntent.SaveSkill(
                                skill.copy(
                                    name = name.trim(),
                                    description = desc.trim(),
                                    content = content.trim(),
                                    sourceUrl = url.trim()
                                )
                            )
                        )
                        editingSkill = null
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

/**
 * 悬浮菜单技能多选弹窗。
 * 候选为已启用的技能（排除书源编写——它本就不进悬浮菜单）。
 * 初始勾选：未配置时默认全选（等价于当前"显示全部"的行为）；已配置时按偏好还原。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickSkillPickerSheet(
    show: Boolean,
    skills: List<AiSkillListItemUi>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    if (!show) return
    // 候选项与 AiQuickAccessViewModel 的过滤口径一致。
    val candidates = skills.filter { it.enabled && it.id != "builtin.book_source" }
    val raw = AppConfigStore.getString(PreferKey.aiQuickSkillIds)
    val initiallySelected = if (raw == null) {
        candidates.map { it.id }.toSet()
    } else {
        AiQuickAccessViewModel.parseSelected(raw)
    }

    var selected by remember(show) {
        mutableStateOf(initiallySelected)
    }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.ai_quick_skill_picker)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.ai_quick_skill_picker_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            candidates.forEach { skill ->
                val checked = skill.id in selected
                ClickableSettingItem(
                    title = skill.name,
                    description = skill.description.ifBlank {
                        stringResource(R.string.ai_skill_no_description)
                    },
                    trailingContent = {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                // 每次生成新集合，确保 Compose 检测到变化并重组。
                                selected = if (checked) selected - skill.id else selected + skill.id
                            }
                        )
                    },
                    onClick = {
                        selected = if (checked) selected - skill.id else selected + skill.id
                    }
                )
            }
            Button(
                onClick = { onSave(selected.toSet()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

private fun AiToolGroup.titleRes(): Int = when (this) {
    AiToolGroup.Reading -> R.string.ai_tool_group_reading
    AiToolGroup.Knowledge -> R.string.ai_tool_group_knowledge
    AiToolGroup.Writeback -> R.string.ai_tool_group_writeback
    AiToolGroup.Memory -> R.string.ai_tool_group_memory
    AiToolGroup.Skill -> R.string.ai_tool_group_skill
    AiToolGroup.BookSource -> R.string.ai_tool_group_source
}

private fun AiToolGroup.descriptionRes(): Int = when (this) {
    AiToolGroup.Reading -> R.string.ai_tool_group_reading_desc
    AiToolGroup.Knowledge -> R.string.ai_tool_group_knowledge_desc
    AiToolGroup.Writeback -> R.string.ai_tool_group_writeback_desc
    AiToolGroup.Memory -> R.string.ai_tool_group_memory_desc
    AiToolGroup.Skill -> R.string.ai_tool_group_skill_desc
    AiToolGroup.BookSource -> R.string.ai_tool_group_source_desc
}
