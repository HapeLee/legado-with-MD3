package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.help.ai.AiChatService
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.ai.AI_API_MODE_CHAT_COMPLETIONS
import io.legado.app.ui.main.ai.AI_API_MODE_RESPONSES
import io.legado.app.ui.main.ai.AiModelConfig
import io.legado.app.ui.main.ai.AiProviderConfig
import io.legado.app.ui.widget.compose.ComposeActionListDialog
import io.legado.app.ui.widget.compose.ComposeConfirmDialog
import io.legado.app.ui.widget.compose.ComposeFetchedModelDialog
import io.legado.app.ui.widget.compose.LegadoMiuixActionButton
import io.legado.app.ui.widget.compose.rememberAppDialogStyle
import io.legado.app.ui.widget.compose.showComposeTextInputDialog
import io.legado.app.ui.widget.compose.toMiuixPalette
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.postEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiProviderEditActivity : BaseActivity<ViewBinding>() {

    // ── Compose state ────────────────────────────────────────────────────────
    private var currentTab by mutableStateOf(TAB_CONFIG)
    private var providerName by mutableStateOf("")
    private var providerBaseUrl by mutableStateOf("")
    private var providerApiKey by mutableStateOf("")
    private var providerHeaders by mutableStateOf("")
    private var promptCache by mutableStateOf(false)
    private var apiMode by mutableStateOf(AI_API_MODE_CHAT_COMPLETIONS)
    private var modelList by mutableStateOf<List<AiModelConfig>>(emptyList())
    private var modelSummary by mutableStateOf("")

    private val waitDialog by lazy { WaitDialog(this) }
    private var providerId: String? = null

    // Minimal ViewBinding shim: BaseActivity requires a ViewBinding.
    // We supply a plain LinearLayout that hosts the ComposeView.
    override val binding by lazy {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(
                ComposeView(this@AiProviderEditActivity).apply {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                    )
                    setContent { AiProviderEditScreen() }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }
        object : ViewBinding {
            override fun getRoot(): View = root
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        providerId = intent.getStringExtra(EXTRA_PROVIDER_ID)
        val provider = currentProvider()
        apiMode = normalizeApiMode(provider?.apiMode)
        bindProvider(provider)
        reloadModels()
    }

    override fun onDestroy() {
        super.onDestroy()
        waitDialog.dismiss()
    }

    // ── Root Composable ──────────────────────────────────────────────────────

    @Composable
    private fun AiProviderEditScreen() {
        val tabLabels = listOf(
            stringResource(R.string.ai_provider_config),
            stringResource(R.string.ai_model_manage)
        )
        val isModelTab = currentTab == TAB_MODEL

        Column(modifier = Modifier.fillMaxSize()) {
            // Title bar
            AppDialogTitleBar(
                title = stringResource(R.string.ai_edit_provider)
            )
            // Tab bar
            AiProviderTabBar(
                tabs = tabLabels,
                selectedIndex = if (isModelTab) 1 else 0,
                onTabSelected = { index ->
                    switchTab(if (index == 1) TAB_MODEL else TAB_CONFIG)
                }
            )
            // Content
            if (isModelTab) {
                val provider = currentProvider()
                AiModelManageTab(
                    models = modelList,
                    currentModelId = AppConfig.aiCurrentModelId,
                    providerName = modelSummary,
                    onModelClick = { showModelActions(it) },
                    onAddModel = { showEditModelDialog() },
                    onFetchModels = { fetchModels() }
                )
            } else {
                AiProviderConfigTab(
                    name = providerName,
                    onNameChange = { providerName = it },
                    baseUrl = providerBaseUrl,
                    onBaseUrlChange = { providerBaseUrl = it },
                    apiKey = providerApiKey,
                    onApiKeyChange = { providerApiKey = it },
                    headers = providerHeaders,
                    onHeadersChange = { providerHeaders = it },
                    apiModeLabel = "${stringResource(R.string.ai_api_mode)}: " + stringResource(
                        if (apiMode == AI_API_MODE_RESPONSES) R.string.ai_provider_mode_responses
                        else R.string.ai_provider_mode_chat
                    ),
                    onApiModeClick = { showApiModeSelector() },
                    promptCache = promptCache,
                    onPromptCacheChange = { promptCache = it },
                    modifier = Modifier.weight(1f)
                )
            }
            // Save button (config tab only)
            if (!isModelTab) {
                val style = rememberAppDialogStyle()
                val palette = style.toMiuixPalette()
                LegadoMiuixActionButton(
                    text = stringResource(R.string.action_save),
                    palette = palette,
                    onClick = { saveProvider(showToast = true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    primary = true,
                    cornerRadius = style.panelRadius
                )
            }
        }
    }

    // ── Tab switching ────────────────────────────────────────────────────────

    private fun switchTab(tab: String) {
        currentTab = tab
        if (tab == TAB_MODEL) {
            if (currentProviderOrSave() == null) {
                currentTab = TAB_CONFIG
            } else {
                reloadModels()
            }
        }
    }

    // ── Provider binding ─────────────────────────────────────────────────────

    private fun bindProvider(provider: AiProviderConfig?) {
        providerName = provider?.name.orEmpty()
        providerBaseUrl = provider?.baseUrl.orEmpty()
        providerApiKey = provider?.apiKey.orEmpty()
        providerHeaders = provider?.headers.orEmpty()
        promptCache = provider?.promptCache ?: false
    }

    // ── API mode ─────────────────────────────────────────────────────────────

    private fun showApiModeSelector() {
        val modes = listOf(AI_API_MODE_CHAT_COMPLETIONS, AI_API_MODE_RESPONSES)
        val labels = listOf(
            getString(R.string.ai_provider_mode_chat),
            getString(R.string.ai_provider_mode_responses)
        )
        showDialogFragment(
            ComposeActionListDialog.create(
                title = getString(R.string.ai_api_mode),
                labels = labels,
                negativeText = getString(R.string.cancel),
                onSelected = { index ->
                    modes.getOrNull(index)?.let { selectedMode ->
                        apiMode = selectedMode
                    }
                }
            )
        )
    }

    // ── Save / load ──────────────────────────────────────────────────────────

    private fun saveProvider(showToast: Boolean): AiProviderConfig? {
        val name = providerName.trim()
        val baseUrl = providerBaseUrl.trim()
        val apiKey = providerApiKey.trim()
        val headers = providerHeaders.trim()
        when {
            name.isEmpty() -> {
                toastOnUi(R.string.ai_provider_name_required)
                return null
            }
            baseUrl.isEmpty() -> {
                toastOnUi(R.string.ai_provider_url_required)
                return null
            }
        }
        val providers = AppConfig.aiProviderList.toMutableList()
        val oldProvider = currentProvider()
        val updated = oldProvider?.copy(
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            headers = headers,
            apiMode = apiMode,
            promptCache = promptCache
        ) ?: AiProviderConfig(
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            headers = headers,
            apiMode = apiMode,
            promptCache = promptCache
        )
        val index = providers.indexOfFirst { it.id == updated.id }
        if (index >= 0) providers[index] = updated else providers.add(updated)
        AppConfig.aiProviderList = providers
        providerId = updated.id
        notifyAiConfigChanged()
        if (showToast) toastOnUi(R.string.ai_provider_saved)
        reloadModels()
        return updated
    }

    private fun currentProviderOrSave(): AiProviderConfig? =
        currentProvider() ?: saveProvider(showToast = false)

    private fun currentProvider(): AiProviderConfig? {
        val id = providerId ?: return null
        return AppConfig.aiProviderList.firstOrNull { it.id == id }
    }

    private fun reloadModels() {
        val provider = currentProvider()
        val models = provider?.let { p ->
            AppConfig.aiModelConfigList.filter { it.providerId == p.id }
        }.orEmpty()
        modelSummary = if (provider == null) {
            getString(R.string.ai_current_provider_summary_empty)
        } else {
            "${provider.name} · ${getString(R.string.ai_manage_models_summary, models.size)}"
        }
        modelList = models
    }

    // ── Model CRUD ───────────────────────────────────────────────────────────

    private fun showEditModelDialog(model: AiModelConfig? = null) {
        val provider = currentProviderOrSave() ?: return
        showComposeTextInputDialog(
            title = getString(if (model == null) R.string.ai_add_model else R.string.ai_edit_model),
            hint = getString(R.string.ai_model_input_hint),
            initialValue = model?.modelId.orEmpty(),
            onPositive = { saveModel(provider.id, model, it) }
        )
    }

    private fun saveModel(providerId: String, oldModel: AiModelConfig?, modelId: String) {
        val trimmedModelId = modelId.trim()
        if (trimmedModelId.isEmpty()) return
        val models = AppConfig.aiModelConfigList.toMutableList()
        val exists = models.any {
            it.providerId == providerId && it.modelId == trimmedModelId && it.id != oldModel?.id
        }
        if (exists) {
            toastOnUi(R.string.ai_model_exists)
            return
        }
        val updated = oldModel?.copy(providerId = providerId, modelId = trimmedModelId)
            ?: AiModelConfig(providerId = providerId, modelId = trimmedModelId)
        val index = models.indexOfFirst { it.id == updated.id }
        if (index >= 0) models[index] = updated else models.add(updated)
        AppConfig.aiModelConfigList = models
        AppConfig.aiCurrentModelId = updated.id
        notifyAiConfigChanged()
        reloadModels()
        toastOnUi(if (oldModel == null) R.string.ai_model_added else R.string.ai_model_saved)
    }

    private fun showModelActions(model: AiModelConfig) {
        val actions = listOf(
            getString(R.string.ai_set_current),
            getString(R.string.edit),
            getString(R.string.delete)
        )
        showDialogFragment(
            ComposeActionListDialog.create(
                title = model.modelId,
                labels = actions,
                dangerIndices = setOf(2),
                negativeText = getString(R.string.cancel),
                onSelected = { index ->
                    when (index) {
                        0 -> {
                            AppConfig.aiCurrentModelId = model.id
                            notifyAiConfigChanged()
                            reloadModels()
                        }
                        1 -> showEditModelDialog(model)
                        2 -> confirmRemoveModel(model)
                    }
                }
            )
        )
    }

    private fun confirmRemoveModel(model: AiModelConfig) {
        showDialogFragment(
            ComposeConfirmDialog.create(
                title = model.modelId,
                message = getString(R.string.ai_remove_model_confirm),
                positiveText = getString(R.string.delete),
                negativeText = getString(R.string.cancel),
                dangerPositive = true,
                onPositive = {
                    AppConfig.aiModelConfigList =
                        AppConfig.aiModelConfigList.filterNot { it.id == model.id }
                    notifyAiConfigChanged()
                    reloadModels()
                    toastOnUi(R.string.ai_model_removed)
                }
            )
        )
    }

    // ── Fetch remote models ──────────────────────────────────────────────────

    private fun fetchModels() {
        val provider = currentProviderOrSave() ?: return
        waitDialog.setText(R.string.loading)
        waitDialog.show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AiChatService.fetchModels(provider) }
            }
            waitDialog.dismiss()
            result.onSuccess { modelIds ->
                if (modelIds.isEmpty()) toastOnUi(R.string.ai_fetch_models_empty)
                else showFetchedModelSelector(provider.id, modelIds)
            }.onFailure {
                toastOnUi(
                    getString(R.string.ai_fetch_models_failed, it.localizedMessage ?: "Error")
                )
            }
        }
    }

    private fun showFetchedModelSelector(providerId: String, modelIds: List<String>) {
        val fetchedModelIds = modelIds.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (fetchedModelIds.isEmpty()) {
            toastOnUi(R.string.ai_fetch_models_empty)
            return
        }
        val existingIds = AppConfig.aiModelConfigList
            .filter { it.providerId == providerId }
            .map { it.modelId }
            .toSet()
        showDialogFragment(
            ComposeFetchedModelDialog.create(
                modelIds = fetchedModelIds,
                existingIds = existingIds,
                onAddSingle = { modelId ->
                    appendFetchedModels(providerId, listOf(modelId))
                },
                onAddSelected = { selectedIds ->
                    appendFetchedModels(providerId, selectedIds)
                },
                onAddAll = { allIds ->
                    appendFetchedModels(providerId, allIds)
                }
            )
        )
    }

    private fun appendFetchedModels(providerId: String, modelIds: List<String>) {
        val oldModels = AppConfig.aiModelConfigList
        val existingIds = oldModels
            .filter { it.providerId == providerId }
            .map { it.modelId }
            .toSet()
        val newModels = modelIds.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { it in existingIds }
            .map { AiModelConfig(providerId = providerId, modelId = it) }
        if (newModels.isEmpty()) {
            toastOnUi(R.string.ai_fetch_models_no_new)
            return
        }
        AppConfig.aiModelConfigList = oldModels + newModels
        if (AppConfig.aiCurrentModelId.isNullOrBlank()) {
            AppConfig.aiCurrentModelId = newModels.first().id
        }
        notifyAiConfigChanged()
        reloadModels()
        toastOnUi(getString(R.string.ai_fetch_models_success, newModels.size))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun notifyAiConfigChanged() {
        postEvent(EventBus.AI_CONFIG_CHANGED, true)
    }

    private fun normalizeApiMode(value: String?): String {
        return if (value?.trim() == AI_API_MODE_RESPONSES) AI_API_MODE_RESPONSES
        else AI_API_MODE_CHAT_COMPLETIONS
    }

    companion object {
        const val EXTRA_PROVIDER_ID = "providerId"
        private const val TAB_CONFIG = "config"
        private const val TAB_MODEL = "model"
    }
}
