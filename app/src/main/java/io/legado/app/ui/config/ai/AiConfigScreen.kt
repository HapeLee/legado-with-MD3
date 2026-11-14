package io.legado.app.ui.config.ai

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.feature.settings.search.SearchableSetting
import io.legado.app.feature.settings.search.SearchableSettingsList
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.main.ai.AiSkillConfig
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
    var showModelSheet by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<AiSkillConfig?>(null) }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiConfigEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is AiConfigEffect.ShowSkillSaved -> snackbarHostState.showSnackbar(effect.message)
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
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_new_skill),
                        onClick = { editingSkill = AiSkillConfig(name = "", content = "") }
                    )
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

    if (editingSkill != null) {
        val skill = editingSkill!!
        var name by remember(skill) { mutableStateOf(skill.name) }
        var content by remember(skill) { mutableStateOf(skill.content) }
        var desc by remember(skill) { mutableStateOf(skill.description) }
        var url by remember(skill) { mutableStateOf(skill.sourceUrl) }

        AppModalBottomSheet(
            show = true,
            onDismissRequest = { editingSkill = null },
            title = stringResource(R.string.ai_new_skill)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Content") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Source URL") },
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.ai_skill_save))
            }
        }
    }
}
