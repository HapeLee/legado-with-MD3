package io.legado.app.feature.settings.ai

import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.ai_chapter_summary
import io.legado.app.feature.settings.res.ai_config
import io.legado.app.feature.settings.res.ai_current
import io.legado.app.feature.settings.res.ai_current_model
import io.legado.app.feature.settings.res.ai_fetch_and_save_models
import io.legado.app.feature.settings.res.ai_model_database
import io.legado.app.feature.settings.res.ai_model_not_configured
import io.legado.app.feature.settings.res.ai_new_provider
import io.legado.app.feature.settings.res.ai_new_skill
import io.legado.app.feature.settings.res.ai_no_models_imported
import io.legado.app.feature.settings.res.ai_prompt_config
import io.legado.app.feature.settings.res.ai_prompt_config_desc
import io.legado.app.feature.settings.res.ai_provider_database
import io.legado.app.feature.settings.res.ai_select_model
import io.legado.app.feature.settings.res.ai_skills
import io.legado.app.feature.settings.res.ai_tasks
import io.legado.app.feature.settings.res.translation_config
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
import org.jetbrains.compose.resources.stringResource

// M5-5a：从 `:app` 的 `io.legado.app.ui.config.ai.AiConfigScreen` 迁来。
//
// 差异只有两类：
//   1. `R.string.*` → `Res.string.*`（14 条新增 + 3 条复用已搬过的：`translation_config`、
//      `ai_prompt_config`、`ai_prompt_config_desc`）。
//   2. `AiConfigRouteScreen` 不搬（只做 `koinViewModel()` 与转发 5 个导航回调）——
//      与 translation 同形：宿主那侧只剩「取 VM、收 state、接导航回调」。
//      提示文案仍由**本 Screen** 自己收 Effect 显示（Snackbar）。
//
// 本页是 ai 域的主入口，5 个导航回调都只是 `() -> Unit` / `(String?, String?) -> Unit`，
// 没有平台动作 ⇒ 不需要为宿主留任何 Effect 解释逻辑（对照 labConfig 的 `ACTION_SEND`
// 与 customTheme 的 `ThemeStore`）。

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

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiConfigEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(Res.string.ai_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup(title = stringResource(Res.string.ai_provider_database)) {
                    state.providers.forEach { provider ->
                        ClickableSettingItem(
                            title = provider.providerName,
                            description = provider.baseUrl,
                            option = "${provider.protocol} / ${provider.modelCount}",
                            onClick = { onNavigateToProviderEdit(provider.providerId) }
                        )
                    }
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_new_provider),
                        onClick = { onNavigateToProviderEdit(null) }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(Res.string.ai_model_database)) {
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_current_model),
                        description = state.currentModelName.ifBlank {
                            stringResource(Res.string.ai_model_not_configured)
                        },
                        onClick = {
                            if (state.models.isEmpty()) {
                                onNavigateToProviderEdit(null)
                            } else {
                                showModelSheet = true
                            }
                        }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(Res.string.ai_tasks)) {
                    ClickableSettingItem(
                        title = stringResource(Res.string.translation_config),
                        onClick = onNavigateToTranslation
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_chapter_summary),
                        onClick = onNavigateToAiSummary
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_prompt_config),
                        description = stringResource(Res.string.ai_prompt_config_desc),
                        onClick = onNavigateToAiPrompt
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(Res.string.ai_skills)) {
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_new_skill),
                        onClick = {}
                    )
                }
            }
        }
    }

    AppModalBottomSheet(
        show = showModelSheet,
        onDismissRequest = { showModelSheet = false },
        title = stringResource(Res.string.ai_select_model)
    ) {
        LazyColumn {
            state.providers.forEach { provider ->
                item {
                    SplicedColumnGroup(title = provider.providerName) {
                        if (provider.models.isEmpty()) {
                            ClickableSettingItem(
                                title = stringResource(Res.string.ai_no_models_imported),
                                description = stringResource(Res.string.ai_fetch_and_save_models),
                                onClick = {
                                    showModelSheet = false
                                    onNavigateToProviderEdit(provider.providerId)
                                }
                            )
                        } else {
                            provider.models.forEach { model ->
                                ClickableSettingItem(
                                    title = model.modelName,
                                    description = model.modelId,
                                    trailingContent = if (model.isCurrent) {
                                        { TextCard(text = stringResource(Res.string.ai_current)) }
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
}
