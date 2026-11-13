package io.legado.app.ui.config.ai

import androidx.compose.foundation.lazy.LazyColumn
import io.legado.app.feature.settings.search.SearchableSetting
import io.legado.app.feature.settings.search.SearchableSettingsList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import io.legado.app.ui.main.ai.AiSkillConfig
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.remember

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
                SplicedColumnGroup(title = stringResource(R.string.ai_skills)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_new_skill),
                        onClick = { editingSkill = AiSkillConfig(name = "", content = "") }
                    )
                }
            }
        }
    }

    if (editingSkill != null) {
        AiSkillEditDialog(
            skill = editingSkill,
            onDismiss = { editingSkill = null },
            onConfirm = { name, content, desc, url ->
                val newSkill = editingSkill?.copy(
                    name = name, content = content, description = desc, sourceUrl = url
                ) ?: AiSkillConfig(name = name, content = content, description = desc, sourceUrl = url)
                onIntent(AiConfigIntent.SaveSkill(newSkill))
                editingSkill = null
            }
        )
    }
}

@Composable
fun AiSkillEditDialog(
    skill: AiSkillConfig?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, content: String, desc: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf(skill?.name ?: "") }
    var content by remember { mutableStateOf(skill?.content ?: "") }
    var desc by remember { mutableStateOf(skill?.description ?: "") }
    var url by remember { mutableStateOf(skill?.sourceUrl ?: "") }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        title = if (skill == null) "新建 Skill" else "编辑 Skill"
    ) {
        SplicedColumnGroup(title = "配置信息") {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("描述") },
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容 (Prompt)") },
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("来源链接") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onConfirm(name, content, desc, url) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}
