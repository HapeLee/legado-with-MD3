package io.legado.app.ui.config

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogAiMcpServerEditBinding
import io.legado.app.databinding.DialogAiProviderEditBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.ai.AiChatService
import io.legado.app.help.ai.AiToolRegistry
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.prefs.SwitchPreference
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.main.ai.AiModelConfig
import io.legado.app.ui.main.ai.AiMcpServerConfig
import io.legado.app.ui.main.ai.AiImageProviderConfig
import io.legado.app.ui.main.ai.AiImageGalleryActivity
import io.legado.app.ui.main.ai.AiPersonaConfig
import io.legado.app.ui.main.ai.AiProviderConfig
import io.legado.app.ui.main.ai.AiSkillConfig
import io.legado.app.utils.postEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val defaultSkillUrls = listOf(
        "https://raw.githubusercontent.com/DandanLLab/legadoSkill/main/.trae/skills/legado-book-source-tamer/SKILL.md",
        "https://raw.githubusercontent.com/DandanLLab/legadoSkill/main/skills/SKILLV0.7.md",
        "https://raw.githubusercontent.com/DandanLLab/legadoSkill/main/SKILL.md"
    )

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_ai)
        refreshUi()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.ai_setting)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        listView.setEdgeEffectColor(primaryColor)
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "aiAddProvider", "aiManageProviders", "aiAddModel", "aiFetchModels", "aiManageModels" ->
                startActivity(Intent(requireContext(), AiProviderManageActivity::class.java))
            "aiAddMcpServer" -> showEditMcpServerDialog()
            "aiManageMcpServers" -> showManageMcpServersDialog()
            "aiManageNativeTools" -> showManageNativeToolsDialog()
            PreferKey.aiTavilyApiKey -> showTavilyApiKeyDialog()
            PreferKey.aiTavilyBaseUrl -> showTavilyBaseUrlDialog()
            PreferKey.aiTavilyTopic -> showTavilyTopicDialog()
            PreferKey.aiTavilySearchDepth -> showTavilySearchDepthDialog()
            PreferKey.aiTavilyMaxResults -> showTavilyMaxResultsDialog()
            PreferKey.aiSystemPrompt -> showSystemPromptDialog()
            "aiContextCompression" -> showContextCompressionDialog()
            "aiPersonaManage" -> showPersonaManageDialog()
            "aiImageGallery" ->
                startActivity(Intent(requireContext(), AiImageGalleryActivity::class.java))
            "aiImageProviderManage" ->
                startActivity(Intent(requireContext(), AiImageProviderManageActivity::class.java))
            "aiImportDefaultSkill" -> importDefaultSkill()
            PreferKey.aiSkillPrompt -> showManageSkillsDialog()
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PreferKey.aiAssistantEnabled) {
            refreshUi(notifyMain = true)
        }
    }

    private fun showEditProviderDialog(provider: AiProviderConfig? = null) {
        val binding = DialogAiProviderEditBinding.inflate(layoutInflater).apply {
            editProviderName.setText(provider?.name.orEmpty())
            editProviderBaseUrl.setText(provider?.baseUrl.orEmpty())
            editProviderApiKey.setText(provider?.apiKey.orEmpty())
            editProviderHeaders.setText(provider?.headers.orEmpty())
        }
        alert(
            title = getString(
                if (provider == null) R.string.ai_add_provider else R.string.ai_edit_provider
            )
        ) {
            customView { binding.root }
            okButton {
                val name = binding.editProviderName.text?.toString()?.trim().orEmpty()
                val baseUrl = binding.editProviderBaseUrl.text?.toString()?.trim().orEmpty()
                val apiKey = binding.editProviderApiKey.text?.toString()?.trim().orEmpty()
                val headers = binding.editProviderHeaders.text?.toString()?.trim().orEmpty()
                when {
                    name.isEmpty() -> {
                        toastOnUi(R.string.ai_provider_name_required)
                        return@okButton
                    }

                    baseUrl.isEmpty() -> {
                        toastOnUi(R.string.ai_provider_url_required)
                        return@okButton
                    }
                }
                val providers = AppConfig.aiProviderList.toMutableList()
                val updated = provider?.copy(
                    name = name,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    headers = headers
                ) ?: AiProviderConfig(
                    name = name,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    headers = headers
                )
                val targetIndex = providers.indexOfFirst { it.id == updated.id }
                if (targetIndex >= 0) {
                    providers[targetIndex] = updated
                } else {
                    providers.add(updated)
                }
                AppConfig.aiProviderList = providers
                AppConfig.aiCurrentProviderId = updated.id
                refreshUi()
                toastOnUi(R.string.ai_provider_saved)
            }
            cancelButton()
        }
    }

    private fun showManageProvidersDialog() {
        val providers = AppConfig.aiProviderList
        if (providers.isEmpty()) {
            toastOnUi(R.string.ai_no_providers)
            return
        }
        context?.selector(
            getString(R.string.ai_manage_providers),
            providers.map { it.name }
        ) { _, _, index ->
            val provider = providers[index]
            context?.selector(
                provider.name,
                arrayListOf(
                    getString(R.string.ai_set_current_provider),
                    getString(R.string.ai_edit_provider),
                    getString(R.string.ai_remove_provider)
                )
            ) { _, action ->
                when (action) {
                    0 -> {
                        AppConfig.aiCurrentProviderId = provider.id
                        refreshUi()
                    }

                    1 -> showEditProviderDialog(provider)
                    2 -> confirmRemoveProvider(provider)
                }
            }
        }
    }

    private fun confirmRemoveProvider(provider: AiProviderConfig) {
        val relatedModelCount = AppConfig.aiModelConfigList.count { it.providerId == provider.id }
        alert(
            title = provider.name,
            message = getString(
                if (relatedModelCount > 0) {
                    R.string.ai_remove_provider_confirm_with_models
                } else {
                    R.string.ai_remove_provider_confirm
                },
                relatedModelCount
            )
        ) {
            okButton {
                AppConfig.aiProviderList = AppConfig.aiProviderList.filterNot { it.id == provider.id }
                refreshUi()
                toastOnUi(R.string.ai_provider_removed)
            }
            cancelButton()
        }
    }

    private fun showEditModelDialog(model: AiModelConfig? = null) {
        val provider = AppConfig.aiCurrentProvider
        if (provider == null) {
            toastOnUi(R.string.ai_no_providers)
            return
        }
        val binding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.ai_model_input_hint)
            editView.inputType = InputType.TYPE_CLASS_TEXT
            editView.setText(model?.modelId.orEmpty())
            editView.setSelection(editView.text?.length ?: 0)
        }
        alert(
            title = getString(
                if (model == null) R.string.ai_add_model else R.string.ai_edit_model
            )
        ) {
            customView { binding.root }
            okButton {
                val modelId = binding.editView.text?.toString()?.trim().orEmpty()
                if (modelId.isEmpty()) {
                    return@okButton
                }
                val models = AppConfig.aiModelConfigList.toMutableList()
                val exists = models.any {
                    it.providerId == provider.id && it.modelId == modelId && it.id != model?.id
                }
                if (exists) {
                    toastOnUi(R.string.ai_model_exists)
                    return@okButton
                }
                val updated = model?.copy(
                    providerId = provider.id,
                    modelId = modelId
                ) ?: AiModelConfig(
                    providerId = provider.id,
                    modelId = modelId
                )
                val targetIndex = models.indexOfFirst { it.id == updated.id }
                if (targetIndex >= 0) {
                    models[targetIndex] = updated
                } else {
                    models.add(updated)
                }
                AppConfig.aiModelConfigList = models
                AppConfig.aiCurrentModelId = updated.id
                refreshUi()
                toastOnUi(
                    if (model == null) R.string.ai_model_added else R.string.ai_model_saved
                )
            }
            cancelButton()
        }
    }

    private fun showAddModelOptionsDialog() {
        if (AppConfig.aiCurrentProvider == null) {
            toastOnUi(R.string.ai_no_providers)
            return
        }
        context?.selector(
            getString(R.string.ai_add_model),
            listOf(
                getString(R.string.ai_add_model_from_list),
                getString(R.string.ai_add_model_manual)
            )
        ) { _, _, index ->
            when (index) {
                0 -> fetchModelsFromCurrentProvider(showSelector = true)
                1 -> showEditModelDialog()
            }
        }
    }

    private fun showManageModelsDialog() {
        if (AppConfig.aiCurrentProvider == null) {
            toastOnUi(R.string.ai_no_providers)
            return
        }
        val models = currentProviderModels()
        if (models.isEmpty()) {
            toastOnUi(R.string.ai_no_models)
            return
        }
        context?.selector(
            getString(R.string.ai_manage_models),
            models.map { it.modelId }
        ) { _, _, index ->
            val model = models[index]
            context?.selector(
                model.modelId,
                arrayListOf(
                    getString(R.string.ai_set_current),
                    getString(R.string.ai_edit_model),
                    getString(R.string.ai_remove_model)
                )
            ) { _, action ->
                when (action) {
                    0 -> {
                        AppConfig.aiCurrentModelId = model.id
                        refreshUi()
                    }

                    1 -> showEditModelDialog(model)
                    2 -> confirmRemoveModel(model)
                }
            }
        }
    }

    private fun fetchModelsFromCurrentProvider(showSelector: Boolean = false) {
        val provider = AppConfig.aiCurrentProvider
        if (provider == null) {
            toastOnUi(R.string.ai_no_providers)
            return
        }
        toastOnUi(R.string.ai_fetch_models_loading)
        lifecycleScope.launch {
            val result = withContext(IO) {
                runCatching { AiChatService.fetchModels(provider) }
            }
            result.onSuccess { modelIds ->
                if (modelIds.isEmpty()) {
                    toastOnUi(R.string.ai_fetch_models_empty)
                    return@onSuccess
                }
                if (showSelector) {
                    showFetchedModelSelector(provider.id, modelIds)
                } else {
                    appendFetchedModels(provider.id, modelIds)
                }
            }.onFailure {
                toastOnUi(getString(R.string.ai_fetch_models_failed, it.localizedMessage ?: "Error"))
            }
        }
    }

    private fun showFetchedModelSelector(providerId: String, modelIds: List<String>) {
        val items = buildList {
            add(getString(R.string.ai_add_all_models))
            addAll(modelIds)
        }
        context?.selector(
            getString(R.string.ai_add_model_from_list),
            items
        ) { _, _, index ->
            if (index == 0) {
                appendFetchedModels(providerId, modelIds)
            } else {
                val selectedModelId = items[index]
                val existing = AppConfig.aiModelConfigList.firstOrNull {
                    it.providerId == providerId && it.modelId == selectedModelId
                }
                if (existing != null) {
                    AppConfig.aiCurrentModelId = existing.id
                    refreshUi()
                    toastOnUi(R.string.ai_model_saved)
                } else {
                    appendFetchedModels(providerId, listOf(selectedModelId))
                }
            }
        }
    }

    private fun appendFetchedModels(providerId: String, modelIds: List<String>) {
        val oldModels = AppConfig.aiModelConfigList
        val existingIds = oldModels
            .filter { it.providerId == providerId }
            .map { it.modelId }
            .toSet()
        val newModels = modelIds
            .distinct()
            .filterNot { it in existingIds }
            .map { AiModelConfig(providerId = providerId, modelId = it) }
        if (newModels.isEmpty()) {
            toastOnUi(R.string.ai_fetch_models_no_new)
            return
        }
        AppConfig.aiModelConfigList = oldModels + newModels
        if (AppConfig.aiCurrentProviderId == providerId && AppConfig.aiCurrentModelId.isNullOrBlank()) {
            AppConfig.aiCurrentModelId = newModels.first().id
        }
        refreshUi()
        toastOnUi(getString(R.string.ai_fetch_models_success, newModels.size))
    }

    private fun confirmRemoveModel(model: AiModelConfig) {
        alert(
            title = model.modelId,
            message = getString(R.string.ai_remove_model_confirm)
        ) {
            okButton {
                AppConfig.aiModelConfigList =
                    AppConfig.aiModelConfigList.filterNot { it.id == model.id }
                refreshUi()
                toastOnUi(R.string.ai_model_removed)
            }
            cancelButton()
        }
    }

    private fun currentProviderModels(): List<AiModelConfig> {
        val providerId = AppConfig.aiCurrentProviderId ?: return emptyList()
        return AppConfig.aiModelConfigList.filter { it.providerId == providerId }
    }

    private fun showEditMcpServerDialog(server: AiMcpServerConfig? = null) {
        val binding = DialogAiMcpServerEditBinding.inflate(layoutInflater).apply {
            editMcpServerName.setText(server?.name.orEmpty())
            editMcpServerEndpoint.setText(server?.endpoint.orEmpty())
            editMcpServerApiKey.setText(server?.apiKey.orEmpty())
            checkMcpServerEnabled.isChecked = server?.enabled ?: true
        }
        alert(
            title = getString(
                if (server == null) R.string.ai_add_mcp_server else R.string.ai_edit_mcp_server
            )
        ) {
            customView { binding.root }
            okButton {
                val name = binding.editMcpServerName.text?.toString()?.trim().orEmpty()
                val endpoint = binding.editMcpServerEndpoint.text?.toString()?.trim().orEmpty()
                val apiKey = binding.editMcpServerApiKey.text?.toString()?.trim().orEmpty()
                when {
                    name.isEmpty() -> {
                        toastOnUi(R.string.ai_mcp_server_name_required)
                        return@okButton
                    }

                    endpoint.isEmpty() -> {
                        toastOnUi(R.string.ai_mcp_server_endpoint_required)
                        return@okButton
                    }
                }
                val servers = AppConfig.aiMcpServerList.toMutableList()
                val updated = server?.copy(
                    name = name,
                    endpoint = endpoint,
                    apiKey = apiKey,
                    enabled = binding.checkMcpServerEnabled.isChecked
                ) ?: AiMcpServerConfig(
                    name = name,
                    endpoint = endpoint,
                    apiKey = apiKey,
                    enabled = binding.checkMcpServerEnabled.isChecked
                )
                val targetIndex = servers.indexOfFirst { it.id == updated.id }
                if (targetIndex >= 0) {
                    servers[targetIndex] = updated
                } else {
                    servers.add(updated)
                }
                AppConfig.aiMcpServerList = servers
                refreshUi()
                toastOnUi(R.string.ai_mcp_server_saved)
            }
            cancelButton()
        }
    }

    private fun showManageMcpServersDialog() {
        val servers = AppConfig.aiMcpServerList
        if (servers.isEmpty()) {
            toastOnUi(R.string.ai_no_mcp_servers)
            return
        }
        context?.selector(
            getString(R.string.ai_manage_mcp_servers),
            servers.map { server ->
                buildString {
                    append(server.name)
                    if (!server.enabled) append(" (off)")
                }
            }
        ) { _, _, index ->
            val server = servers[index]
            context?.selector(
                server.name,
                arrayListOf(
                    getString(
                        if (server.enabled) {
                            R.string.ai_disable_mcp_server
                        } else {
                            R.string.ai_enable_mcp_server
                        }
                    ),
                    getString(R.string.ai_edit_mcp_server),
                    getString(R.string.ai_remove_mcp_server)
                )
            ) { _, action ->
                when (action) {
                    0 -> {
                        AppConfig.aiMcpServerList = AppConfig.aiMcpServerList.map {
                            if (it.id == server.id) it.copy(enabled = !it.enabled) else it
                        }
                        refreshUi()
                    }

                    1 -> showEditMcpServerDialog(server)
                    2 -> confirmRemoveMcpServer(server)
                }
            }
        }
    }

    private fun confirmRemoveMcpServer(server: AiMcpServerConfig) {
        alert(
            title = server.name,
            message = getString(R.string.ai_remove_mcp_server_confirm)
        ) {
            okButton {
                AppConfig.aiMcpServerList = AppConfig.aiMcpServerList.filterNot { it.id == server.id }
                refreshUi()
                toastOnUi(R.string.ai_mcp_server_removed)
            }
            cancelButton()
        }
    }

    private fun showSystemPromptDialog() {
        val binding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.ai_system_prompt_hint)
            editView.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            editView.minLines = 8
            editView.setText(AppConfig.aiSystemPrompt)
            editView.setSelection(editView.text?.length ?: 0)
        }
        alert(titleResource = R.string.ai_system_prompt) {
            customView { binding.root }
            okButton {
                AppConfig.aiSystemPrompt = binding.editView.text?.toString().orEmpty()
                refreshUi()
            }
            neutralButton(R.string.restore_default) {
                AppConfig.aiSystemPrompt = AppConfig.DEFAULT_AI_SYSTEM_PROMPT
                refreshUi()
            }
            cancelButton()
        }
    }

    private fun showContextCompressionDialog() {
        val enabledText = if (AppConfig.aiContextCompressionEnabled) "关闭上下文压缩" else "启用上下文压缩"
        context?.selector(
            getString(R.string.ai_context_compression),
            arrayListOf(
                enabledText,
                "上下文长度: ${AppConfig.aiContextWindowTokens}",
                "思考上下文: ${AppConfig.aiThinkingContextTokens}"
            )
        ) { _, index ->
            when (index) {
                0 -> {
                    AppConfig.aiContextCompressionEnabled = !AppConfig.aiContextCompressionEnabled
                    refreshUi()
                }
                1 -> showTokenSelector(true)
                2 -> showTokenSelector(false)
            }
        }
    }

    private fun showTokenSelector(contextWindow: Boolean) {
        val values = if (contextWindow) {
            listOf(32_000, 64_000, 128_000, 258_000, 512_000, 1_000_000)
        } else {
            listOf(0, 32_000, 64_000, 128_000, 258_000)
        }
        context?.selector(
            if (contextWindow) getString(R.string.ai_context_tokens) else getString(R.string.ai_thinking_context_tokens),
            values.map { it.toString() }
        ) { _, index ->
            if (contextWindow) AppConfig.aiContextWindowTokens = values[index]
            else AppConfig.aiThinkingContextTokens = values[index]
            refreshUi()
        }
    }

    private fun showPersonaManageDialog() {
        val personas = AppConfig.aiPersonaList
        val items = personas.map {
            if (it.id == AppConfig.aiCurrentPersonaId) "✓ ${it.name}" else it.name
        } + "新增人格提示词"
        context?.selector(getString(R.string.ai_persona_manage), items) { _, index ->
            if (index >= personas.size) {
                showEditPersonaDialog()
            } else {
                val persona = personas[index]
                context?.selector(persona.name, arrayListOf("设为当前", "编辑", "删除")) { _, action ->
                    when (action) {
                        0 -> {
                            AppConfig.aiCurrentPersonaId = persona.id
                            refreshUi()
                        }
                        1 -> showEditPersonaDialog(persona)
                        2 -> {
                            AppConfig.aiPersonaList = AppConfig.aiPersonaList.filterNot { it.id == persona.id }
                            if (AppConfig.aiCurrentPersonaId == persona.id) AppConfig.aiCurrentPersonaId = null
                            refreshUi()
                        }
                    }
                }
            }
        }
    }

    private fun showEditPersonaDialog(persona: AiPersonaConfig? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_persona_edit, null)
        val editName = dialogView.findViewById<android.widget.EditText>(R.id.edit_persona_name)
        val editPrompt = dialogView.findViewById<android.widget.EditText>(R.id.edit_persona_prompt)
        editName.setText(persona?.name.orEmpty())
        editPrompt.setText(persona?.prompt.orEmpty())
        alert(titleResource = R.string.ai_persona_manage) {
            customView { dialogView }
            okButton {
                val name = editName.text?.toString()?.trim().orEmpty()
                val prompt = editPrompt.text?.toString()?.trim().orEmpty()
                if (name.isBlank() || prompt.isBlank()) return@okButton
                val updated = persona?.copy(name = name, prompt = prompt)
                    ?: AiPersonaConfig(name = name, prompt = prompt)
                AppConfig.aiPersonaList = AppConfig.aiPersonaList
                    .filterNot { it.id == updated.id } + updated
                AppConfig.aiCurrentPersonaId = updated.id
                refreshUi()
            }
            cancelButton()
        }
    }

    private fun showImageProviderManageDialog() {
        val providers = AppConfig.aiImageProviderList
        val items = providers.map {
            "${if (it.enabled) "✓ " else ""}${it.displayName()} (${it.type})"
        } + "新增 OpenAI 生图供应商" + "新增 JS 生图规则"
        context?.selector(getString(R.string.ai_image_provider_manage), items) { _, index ->
            when {
                index < providers.size -> showImageProviderActions(providers[index])
                index == providers.size -> showEditImageProviderDialog(AiImageProviderConfig(name = "", type = AiImageProviderConfig.TYPE_OPENAI))
                else -> showEditImageProviderDialog(AiImageProviderConfig(name = "", type = AiImageProviderConfig.TYPE_JS))
            }
        }
    }

    private fun showImageProviderActions(provider: AiImageProviderConfig) {
        context?.selector(provider.displayName(), arrayListOf("启用/停用", "编辑", "删除")) { _, action ->
            when (action) {
                0 -> {
                    AppConfig.aiImageProviderList = AppConfig.aiImageProviderList.map {
                        if (it.id == provider.id) it.copy(enabled = !it.enabled) else it
                    }
                    refreshUi()
                }
                1 -> showEditImageProviderDialog(provider)
                2 -> {
                    AppConfig.aiImageProviderList = AppConfig.aiImageProviderList.filterNot { it.id == provider.id }
                    refreshUi()
                }
            }
        }
    }

    private fun showEditImageProviderDialog(provider: AiImageProviderConfig) {
        val binding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = if (provider.type == AiImageProviderConfig.TYPE_OPENAI) {
                "名称\nBaseUrl\nApiKey\n模型\nHeaders(JSON，可空)\n默认参数(JSON，可空)"
            } else {
                "名称\nJS脚本，需返回图片URL、base64或JSON"
            }
            editView.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            editView.minLines = 8
            editView.setText(
                if (provider.type == AiImageProviderConfig.TYPE_OPENAI) {
                    listOf(
                        provider.name,
                        provider.baseUrl,
                        provider.apiKey,
                        provider.model,
                        provider.headers,
                        provider.defaultParamsJson
                    ).joinToString("\n")
                } else {
                    provider.name + "\n" + provider.script
                }
            )
        }
        alert(titleResource = R.string.ai_image_provider_manage) {
            customView { binding.root }
            okButton {
                val lines = binding.editView.text?.toString().orEmpty().lines()
                val name = lines.firstOrNull()?.trim().orEmpty()
                if (name.isBlank()) return@okButton
                val updated = if (provider.type == AiImageProviderConfig.TYPE_OPENAI) {
                    provider.copy(
                        name = name,
                        baseUrl = lines.getOrNull(1)?.trim().orEmpty(),
                        apiKey = lines.getOrNull(2)?.trim().orEmpty(),
                        model = lines.getOrNull(3)?.trim().orEmpty(),
                        headers = lines.getOrNull(4)?.trim().orEmpty(),
                        defaultParamsJson = lines.drop(5).joinToString("\n").trim()
                    )
                } else {
                    provider.copy(name = name, script = lines.drop(1).joinToString("\n").trim())
                }
                AppConfig.aiImageProviderList = AppConfig.aiImageProviderList
                    .filterNot { it.id == updated.id } + updated
                refreshUi()
            }
            cancelButton()
        }
    }

    private fun showManageNativeToolsDialog() {
        lifecycleScope.launch {
            val tools = runCatching { AiToolRegistry.resolveAllToolNamesForManage() }
                .getOrDefault(emptyList())
            if (tools.isEmpty()) {
                toastOnUi(R.string.not_available)
                return@launch
            }
            val enabled = AppConfig.aiEnabledToolNames.toMutableSet()
            val groupedTools = tools.map { AiToolRegistry.metaOfTool(it) }
                .groupBy { it.group }
                .toSortedMap(compareBy { groupOrder(it) })
            val displayItems = mutableListOf<ToolDisplayItem>()
            groupedTools.forEach { (group, groupTools) ->
                displayItems.add(ToolDisplayItem.Header(group))
                groupTools.sortedBy { it.label }.forEach { tool ->
                    val isEnabled = if (enabled.isEmpty()) {
                        tool.name in AiToolRegistry.defaultEnabledTools
                    } else {
                        tool.name in enabled
                    }
                    displayItems.add(ToolDisplayItem.Tool(tool.name, tool.label, isEnabled))
                }
            }
            alert(getString(R.string.ai_manage_native_tools)) {
                customView { createToolListView(displayItems, enabled) }
                okButton {
                    AppConfig.aiEnabledToolNames = enabled
                    refreshUi()
                }
                negativeButton(R.string.select_all) {
                    AppConfig.aiEnabledToolNames = tools.toSet()
                    refreshUi()
                }
                neutralButton(R.string.restore_default) {
                    AppConfig.aiEnabledToolNames = emptySet()
                    refreshUi()
                }
                cancelButton()
            }
        }
    }

    private fun groupOrder(group: String): Int {
        return when (group) {
            "书架" -> 0
            "书源" -> 1
            "阅读" -> 2
            "阅读网络" -> 3
            "联网搜索" -> 4
            "AI 生图" -> 5
            "角色资料" -> 6
            "设置" -> 7
            "MCP 工具" -> 8
            else -> 8
        }
    }

    private fun showTavilyApiKeyDialog() {
        val binding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.ai_tavily_api_key_hint)
            editView.inputType = InputType.TYPE_CLASS_TEXT
            editView.setText(AppConfig.aiTavilyApiKey)
            editView.setSelection(editView.text?.length ?: 0)
        }
        alert(titleResource = R.string.ai_tavily_api_key) {
            customView { binding.root }
            okButton {
                AppConfig.aiTavilyApiKey = binding.editView.text?.toString().orEmpty()
                refreshUi()
            }
            cancelButton()
        }
    }

    private fun showTavilyBaseUrlDialog() {
        val binding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = "https://api.tavily.com/search"
            editView.inputType = InputType.TYPE_CLASS_TEXT
            editView.setText(AppConfig.aiTavilyBaseUrl)
            editView.setSelection(editView.text?.length ?: 0)
        }
        alert(titleResource = R.string.ai_tavily_base_url) {
            customView { binding.root }
            okButton {
                AppConfig.aiTavilyBaseUrl = binding.editView.text?.toString().orEmpty()
                refreshUi()
            }
            neutralButton(R.string.restore_default) {
                AppConfig.aiTavilyBaseUrl = "https://api.tavily.com/search"
                refreshUi()
            }
            cancelButton()
        }
    }

    private fun showTavilyTopicDialog() {
        val values = listOf("general", "news", "finance")
        val labels = listOf(
            getString(R.string.ai_tavily_topic_general),
            getString(R.string.ai_tavily_topic_news),
            getString(R.string.ai_tavily_topic_finance)
        )
        context?.selector(getString(R.string.ai_tavily_topic), labels) { _, _, index ->
            AppConfig.aiTavilyTopic = values[index]
            refreshUi()
        }
    }

    private fun showTavilySearchDepthDialog() {
        val values = listOf("basic", "advanced", "ultra-fast")
        val labels = listOf(
            getString(R.string.ai_tavily_search_depth_basic),
            getString(R.string.ai_tavily_search_depth_advanced),
            getString(R.string.ai_tavily_search_depth_ultra_fast)
        )
        context?.selector(getString(R.string.ai_tavily_search_depth), labels) { _, _, index ->
            AppConfig.aiTavilySearchDepth = values[index]
            refreshUi()
        }
    }

    private fun showTavilyMaxResultsDialog() {
        val binding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = "1-10"
            editView.inputType = InputType.TYPE_CLASS_NUMBER
            editView.setText(AppConfig.aiTavilyMaxResults.toString())
            editView.setSelection(editView.text?.length ?: 0)
        }
        alert(titleResource = R.string.ai_tavily_max_results) {
            customView { binding.root }
            okButton {
                val value = binding.editView.text?.toString()?.trim()?.toIntOrNull()
                if (value == null) {
                    toastOnUi(R.string.ai_tavily_max_results_invalid)
                    return@okButton
                }
                AppConfig.aiTavilyMaxResults = value
                refreshUi()
            }
            cancelButton()
        }
    }

    private fun importDefaultSkill() {
        toastOnUi(R.string.ai_skill_importing)
        lifecycleScope.launch {
            val result = withContext(IO) {
                runCatching {
                    var lastError = ""
                    defaultSkillUrls.forEach { skillUrl ->
                        okHttpClient.newCallResponse {
                            url(skillUrl)
                        }.use { response ->
                            if (response.isSuccessful) {
                                return@runCatching skillUrl to response.body?.string().orEmpty()
                            }
                            lastError = "${response.code} ${response.message}"
                        }
                    }
                    error(lastError.ifBlank { "No available SKILL.md" })
                }
            }
            result.onSuccess { (skillUrl, skill) ->
                if (skill.isBlank()) {
                    toastOnUi(R.string.ai_skill_import_empty)
                    return@onSuccess
                }
                val skillConfig = parseSkillConfig(skill, skillUrl)
                AppConfig.aiSkillList = AppConfig.aiSkillList
                    .filterNot { it.sourceUrl == skillConfig.sourceUrl || it.name == skillConfig.name }
                    .plus(skillConfig)
                refreshUi()
                toastOnUi(R.string.ai_skill_imported)
            }.onFailure {
                toastOnUi(getString(R.string.ai_skill_import_failed, it.localizedMessage ?: "Error"))
            }
        }
    }

    private fun showManageSkillsDialog() {
        val skills = AppConfig.aiSkillList
        val actions = mutableListOf(getString(R.string.ai_add_skill_manual))
        actions += skills.map { skill ->
            buildString {
                append(skill.name)
                append(" · ")
                append(
                    getString(
                        if (skill.enabled) R.string.enabled else R.string.disabled
                    )
                )
            }
        }
        context?.selector(getString(R.string.ai_manage_skills), actions) { _, _, index ->
            if (index == 0) {
                showSkillEditDialog()
            } else {
                showSkillActionDialog(skills[index - 1])
            }
        }
    }

    private fun showSkillActionDialog(skill: AiSkillConfig) {
        context?.selector(
            skill.name,
            arrayListOf(
                getString(if (skill.enabled) R.string.disable else R.string.enable),
                getString(R.string.edit),
                getString(R.string.delete)
            )
        ) { _, action ->
            when (action) {
                0 -> {
                    AppConfig.aiSkillList = AppConfig.aiSkillList.map {
                        if (it.id == skill.id) it.copy(enabled = !it.enabled) else it
                    }
                    refreshUi()
                }

                1 -> showSkillEditDialog(skill)
                2 -> confirmRemoveSkill(skill)
            }
        }
    }

    private fun showSkillEditDialog(skill: AiSkillConfig? = null) {
        val binding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.ai_skill_prompt_hint)
            editView.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            editView.minLines = 8
            editView.setText(skill?.content.orEmpty())
            editView.setSelection(editView.text?.length ?: 0)
        }
        alert(titleResource = R.string.ai_skill_prompt) {
            customView { binding.root }
            okButton {
                val content = binding.editView.text?.toString().orEmpty()
                if (content.isBlank()) {
                    toastOnUi(R.string.ai_skill_import_empty)
                    return@okButton
                }
                val updated = parseSkillConfig(content, skill?.sourceUrl.orEmpty(), skill)
                val skills = AppConfig.aiSkillList.toMutableList()
                val index = skills.indexOfFirst { it.id == updated.id }
                if (index >= 0) {
                    skills[index] = updated
                } else {
                    skills.add(updated)
                }
                AppConfig.aiSkillList = skills
                refreshUi()
            }
            cancelButton()
        }
    }

    private fun confirmRemoveSkill(skill: AiSkillConfig) {
        alert(
            title = skill.name,
            message = getString(R.string.ai_remove_skill_confirm)
        ) {
            okButton {
                AppConfig.aiSkillList = AppConfig.aiSkillList.filterNot { it.id == skill.id }
                refreshUi()
            }
            cancelButton()
        }
    }

    private fun parseSkillConfig(
        content: String,
        sourceUrl: String = "",
        oldSkill: AiSkillConfig? = null
    ): AiSkillConfig {
        val name = Regex("""(?m)^\s*name:\s*["']?([^"'\n]+)["']?\s*$""")
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
        val description = Regex("""(?m)^\s*description:\s*["']?([^"'\n]+)["']?\s*$""")
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
        return (oldSkill ?: AiSkillConfig(
            name = name.ifBlank { getString(R.string.ai_skill_default_name) },
            content = content
        )).copy(
            name = name.ifBlank { oldSkill?.name ?: getString(R.string.ai_skill_default_name) },
            description = description.ifBlank { oldSkill?.description.orEmpty() },
            content = content.trim(),
            sourceUrl = sourceUrl.ifBlank { oldSkill?.sourceUrl.orEmpty() },
            enabled = oldSkill?.enabled ?: true
        )
    }

    private fun refreshUi(notifyMain: Boolean = false) {
        val currentProvider = AppConfig.aiCurrentProvider
        val providerModels = currentProviderModels()
        val mcpServers = AppConfig.aiMcpServerList
        val enabledMcpCount = mcpServers.count { it.enabled }
        val canEnable = AppConfig.aiCurrentModelConfig != null
        val storedEnabled = preferenceManager.sharedPreferences
            ?.getBoolean(PreferKey.aiAssistantEnabled, false) == true
        if (!canEnable && storedEnabled) {
            AppConfig.aiAssistantEnabled = false
        }
        findPreference<SwitchPreference>(PreferKey.aiAssistantEnabled)?.apply {
            isEnabled = canEnable
            isChecked = AppConfig.aiAssistantEnabled
            summary = getString(
                if (canEnable) R.string.ai_enable_summary else R.string.ai_enable_summary_disabled
            )
        }
        findPreference<Preference>("aiManageProviders")?.summary =
            if (AppConfig.aiProviderList.isEmpty()) {
                getString(R.string.ai_no_providers)
            } else {
                buildString {
                    append(currentProvider?.name ?: getString(R.string.ai_current_provider_summary_empty))
                    append(" · ")
                    append(getString(R.string.ai_manage_providers_summary, AppConfig.aiProviderList.size))
                }
            }
        findPreference<Preference>("aiManageModels")?.summary =
            if (providerModels.isEmpty()) {
                getString(
                    if (currentProvider == null) {
                        R.string.ai_current_model_summary_empty
                    } else {
                        R.string.ai_current_model_summary_no_provider_models
                    }
                )
            } else {
                buildString {
                    append(AppConfig.aiCurrentModelConfig?.modelId ?: providerModels.first().modelId)
                    append(" · ")
                    append(getString(R.string.ai_manage_models_summary, providerModels.size))
                }
            }
        findPreference<Preference>("aiAddModel")?.summary =
            getString(R.string.ai_add_model_summary_modern)
        findPreference<Preference>("aiFetchModels")?.summary =
            getString(R.string.ai_fetch_models_summary_modern)
        findPreference<Preference>("aiManageMcpServers")?.summary =
            if (mcpServers.isEmpty()) {
                getString(R.string.ai_no_mcp_servers)
            } else {
                getString(
                    R.string.ai_manage_mcp_servers_summary,
                    enabledMcpCount,
                    mcpServers.size
                )
            }
        findPreference<SwitchPreference>(PreferKey.aiTavilyEnabled)?.summary =
            getString(
                if (AppConfig.aiTavilyApiKey.isBlank()) {
                    R.string.ai_tavily_enable_summary_missing
                } else {
                    R.string.ai_tavily_enable_summary
                }
            )
        findPreference<Preference>(PreferKey.aiTavilyApiKey)?.summary =
            if (AppConfig.aiTavilyApiKey.isBlank()) {
                getString(R.string.ai_tavily_api_key_summary)
            } else {
                getString(R.string.ai_tavily_api_key_summary_ready)
            }
        findPreference<Preference>(PreferKey.aiTavilyBaseUrl)?.summary = AppConfig.aiTavilyBaseUrl
        findPreference<Preference>(PreferKey.aiTavilyTopic)?.summary = getString(
            when (AppConfig.aiTavilyTopic) {
                "news" -> R.string.ai_tavily_topic_news
                "finance" -> R.string.ai_tavily_topic_finance
                else -> R.string.ai_tavily_topic_general
            }
        )
        findPreference<Preference>(PreferKey.aiTavilySearchDepth)?.summary = getString(
            when (AppConfig.aiTavilySearchDepth) {
                "advanced" -> R.string.ai_tavily_search_depth_advanced
                "ultra-fast" -> R.string.ai_tavily_search_depth_ultra_fast
                else -> R.string.ai_tavily_search_depth_basic
            }
        )
        findPreference<Preference>(PreferKey.aiTavilyMaxResults)?.summary =
            AppConfig.aiTavilyMaxResults.toString()
        findPreference<Preference>(PreferKey.aiSystemPrompt)?.summary =
            getString(R.string.ai_system_prompt_summary)
        findPreference<Preference>("aiContextCompression")?.summary =
            if (AppConfig.aiContextCompressionEnabled) {
                "${AppConfig.aiContextWindowTokens} / ${AppConfig.aiThinkingContextTokens}"
            } else {
                getString(R.string.ai_context_compression_summary_default)
            }
        findPreference<Preference>("aiPersonaManage")?.summary =
            AppConfig.aiCurrentPersona?.name
                ?: getString(R.string.ai_persona_manage_summary_empty)
        val imageProviders = AppConfig.aiImageProviderList
        findPreference<Preference>("aiImageProviderManage")?.summary =
            if (imageProviders.isEmpty()) {
                getString(R.string.ai_image_provider_summary_empty)
            } else {
                getString(
                    R.string.ai_image_provider_summary,
                    imageProviders.count { it.enabled },
                    imageProviders.size
                )
            }
        val skills = AppConfig.aiSkillList
        val enabledSkillCount = skills.count { it.enabled }
        findPreference<Preference>(PreferKey.aiSkillPrompt)?.summary =
            if (skills.isEmpty()) {
                getString(R.string.ai_skill_prompt_summary_empty)
            } else {
                getString(R.string.ai_skill_prompt_summary, enabledSkillCount, skills.size)
            }
        findPreference<Preference>("aiManageNativeTools")?.summary = run {
            val enabledTools = AppConfig.aiEnabledToolNames
            if (enabledTools.isEmpty()) {
                getString(R.string.ai_manage_native_tools_summary)
            } else {
                "${getString(R.string.ai_manage_native_tools_summary)} · ${enabledTools.size}"
            }
        }
        if (notifyMain || (!canEnable && storedEnabled)) {
            postEvent(EventBus.NOTIFY_MAIN, false)
        }
    }

    private sealed class ToolDisplayItem {
        data class Header(val title: String) : ToolDisplayItem()
        data class Tool(val name: String, val displayName: String, var isEnabled: Boolean) : ToolDisplayItem()
    }

    private fun createToolListView(
        items: List<ToolDisplayItem>,
        enabled: MutableSet<String>
    ): android.widget.ScrollView {
        val scrollView = android.widget.ScrollView(requireContext())
        val linearLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val dp8 = (8 * resources.displayMetrics.density).toInt()
            val dp16 = (16 * resources.displayMetrics.density).toInt()
            setPadding(dp16, dp8, dp16, dp8)
        }
        items.forEach { item ->
            when (item) {
                is ToolDisplayItem.Header -> {
                    val headerView = android.widget.TextView(requireContext()).apply {
                        text = item.title
                        textSize = 16f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        val dp16 = (16 * resources.displayMetrics.density).toInt()
                        val dp8 = (8 * resources.displayMetrics.density).toInt()
                        setPadding(0, dp16, 0, dp8)
                    }
                    linearLayout.addView(headerView)
                }
                is ToolDisplayItem.Tool -> {
                    val checkBox = android.widget.CheckBox(requireContext()).apply {
                        text = item.displayName
                        isChecked = item.isEnabled
                        setOnCheckedChangeListener { _, isChecked ->
                            item.isEnabled = isChecked
                            if (isChecked) enabled.add(item.name) else enabled.remove(item.name)
                        }
                    }
                    linearLayout.addView(checkBox)
                }
            }
        }
        scrollView.addView(linearLayout)
        return scrollView
    }
}
