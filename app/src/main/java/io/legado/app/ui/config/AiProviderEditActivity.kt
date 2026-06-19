package io.legado.app.ui.config

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ActivityAiProviderEditBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.ItemS3ContainerBinding
import io.legado.app.help.ai.AiChatService
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.applyUiLabelStyle
import io.legado.app.lib.theme.applyUiSectionTitleStyle
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.ui.main.ai.AI_API_MODE_CHAT_COMPLETIONS
import io.legado.app.ui.main.ai.AI_API_MODE_RESPONSES
import io.legado.app.ui.main.ai.AiModelConfig
import io.legado.app.ui.main.ai.AiProviderConfig
import io.legado.app.ui.widget.SourceSelectDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiProviderEditActivity : BaseActivity<ActivityAiProviderEditBinding>() {

    override val binding by viewBinding(ActivityAiProviderEditBinding::inflate)
    private val modelAdapter by lazy { ModelAdapter() }
    private val waitDialog by lazy { WaitDialog(this) }
    private var providerId: String? = null
    private var apiMode: String = AI_API_MODE_CHAT_COMPLETIONS
    private var currentTab: String = TAB_CONFIG

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        providerId = intent.getStringExtra(EXTRA_PROVIDER_ID)
        val provider = currentProvider()
        apiMode = normalizeApiMode(provider?.apiMode)
        bindProvider(provider)
        initViews()
        switchTab(TAB_CONFIG)
    }

    override fun onDestroy() {
        super.onDestroy()
        waitDialog.dismiss()
    }

    private fun initViews() {
        binding.run {
        modelRecyclerView.layoutManager = LinearLayoutManager(this@AiProviderEditActivity)
        modelRecyclerView.adapter = modelAdapter
        (modelRecyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        tvModelSummary.applyUiLabelStyle(this@AiProviderEditActivity)
        tvModelSummary.setTextColor(secondaryTextColor)
        listOf(tvApiMode, btnSave, btnAddModel, btnFetchModels).forEach { it.background = actionBackground() }
        tvApiMode.setOnClickListener { showApiModeSelector() }
        btnProviderConfig.setOnClickListener { switchTab(TAB_CONFIG) }
        btnModelManage.setOnClickListener { switchTab(TAB_MODEL) }
        btnSave.setOnClickListener { saveProvider(showToast = true) }
        btnAddModel.setOnClickListener { showEditModelDialog() }
        btnFetchModels.setOnClickListener { fetchModels() }
        renderApiMode()
            reloadModels()
        }
    }

    private fun bindProvider(provider: AiProviderConfig?) {
        binding.run {
            editProviderName.setText(provider?.name.orEmpty())
            editProviderBaseUrl.setText(provider?.baseUrl.orEmpty())
            editProviderApiKey.setText(provider?.apiKey.orEmpty())
            editProviderHeaders.setText(provider?.headers.orEmpty())
            cbPromptCache.isChecked = provider?.promptCache ?: false
        }
    }

    private fun switchTab(tab: String) {
        binding.run {
            currentTab = tab
            val isModel = tab == TAB_MODEL
            providerConfigContainer.isVisible = !isModel
            modelManageContainer.isVisible = isModel
            btnSave.isVisible = !isModel
            modelActionBar.isVisible = isModel
            btnProviderConfig.isSelected = !isModel
            btnModelManage.isSelected = isModel
            btnProviderConfig.setTextColor(if (!isModel) accentColor else primaryTextColor)
            btnModelManage.setTextColor(if (isModel) accentColor else primaryTextColor)
            if (isModel) {
                if (currentProviderOrSave() == null) {
                    switchTab(TAB_CONFIG)
                } else {
                    reloadModels()
                }
            }
        }
    }

    private fun showApiModeSelector() {
        val modes = listOf(AI_API_MODE_CHAT_COMPLETIONS, AI_API_MODE_RESPONSES)
        val labels = listOf(getString(R.string.ai_provider_mode_chat), getString(R.string.ai_provider_mode_responses))
        selector(getString(R.string.ai_api_mode), labels) { _, index ->
            apiMode = modes[index]
            renderApiMode()
        }
    }

    private fun renderApiMode() {
        binding.tvApiMode.text = "${getString(R.string.ai_api_mode)}: " + getString(
            if (apiMode == AI_API_MODE_RESPONSES) R.string.ai_provider_mode_responses
            else R.string.ai_provider_mode_chat
        )
    }

    private fun saveProvider(showToast: Boolean): AiProviderConfig? {
        val name = binding.editProviderName.text?.toString()?.trim().orEmpty()
        val baseUrl = binding.editProviderBaseUrl.text?.toString()?.trim().orEmpty()
        val apiKey = binding.editProviderApiKey.text?.toString()?.trim().orEmpty()
        val headers = binding.editProviderHeaders.text?.toString()?.trim().orEmpty()
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
            promptCache = binding.cbPromptCache.isChecked
        ) ?: AiProviderConfig(
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            headers = headers,
            apiMode = apiMode,
            promptCache = binding.cbPromptCache.isChecked
        )
        val index = providers.indexOfFirst { it.id == updated.id }
        if (index >= 0) providers[index] = updated else providers.add(updated)
        AppConfig.aiProviderList = providers
        providerId = updated.id
        if (showToast) toastOnUi(R.string.ai_provider_saved)
        reloadModels()
        return updated
    }

    private fun currentProviderOrSave(): AiProviderConfig? = currentProvider() ?: saveProvider(showToast = false)

    private fun currentProvider(): AiProviderConfig? {
        val id = providerId ?: return null
        return AppConfig.aiProviderList.firstOrNull { it.id == id }
    }

    private fun reloadModels() {
        val provider = currentProvider()
        val models = provider?.let { p -> AppConfig.aiModelConfigList.filter { it.providerId == p.id } }.orEmpty()
        binding.tvModelSummary.text = if (provider == null) {
            getString(R.string.ai_current_provider_summary_empty)
        } else {
            "${provider.name} · ${getString(R.string.ai_manage_models_summary, models.size)}"
        }
        modelAdapter.setItems(models)
    }

    private fun showEditModelDialog(model: AiModelConfig? = null) {
        val provider = currentProviderOrSave() ?: return
        val dialogBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.ai_model_input_hint)
            editView.inputType = InputType.TYPE_CLASS_TEXT
            editView.setText(model?.modelId.orEmpty())
            editView.setSelection(editView.text?.length ?: 0)
        }
        alert(title = getString(if (model == null) R.string.ai_add_model else R.string.ai_edit_model)) {
            customView { dialogBinding.root }
            okButton {
                saveModel(provider.id, model, dialogBinding.editView.text?.toString().orEmpty())
            }
            cancelButton()
        }
    }

    private fun saveModel(providerId: String, oldModel: AiModelConfig?, modelId: String) {
        val trimmedModelId = modelId.trim()
        if (trimmedModelId.isEmpty()) return
        val models = AppConfig.aiModelConfigList.toMutableList()
        val exists = models.any { it.providerId == providerId && it.modelId == trimmedModelId && it.id != oldModel?.id }
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
        reloadModels()
        toastOnUi(if (oldModel == null) R.string.ai_model_added else R.string.ai_model_saved)
    }

    private fun showModelActions(model: AiModelConfig) {
        selector(model.modelId, listOf(getString(R.string.ai_set_current), getString(R.string.edit), getString(R.string.delete))) { _, index ->
            when (index) {
                0 -> {
                    AppConfig.aiCurrentModelId = model.id
                    reloadModels()
                }
                1 -> showEditModelDialog(model)
                2 -> confirmRemoveModel(model)
            }
        }
    }

    private fun confirmRemoveModel(model: AiModelConfig) {
        alert(title = model.modelId, message = getString(R.string.ai_remove_model_confirm)) {
            okButton {
                AppConfig.aiModelConfigList = AppConfig.aiModelConfigList.filterNot { it.id == model.id }
                reloadModels()
                toastOnUi(R.string.ai_model_removed)
            }
            cancelButton()
        }
    }

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
                toastOnUi(getString(R.string.ai_fetch_models_failed, it.localizedMessage ?: "Error"))
            }
        }
    }

    private fun showFetchedModelSelector(providerId: String, modelIds: List<String>) {
        val allKey = getString(R.string.ai_add_all_models)
        val items = listOf(allKey) + modelIds.distinct()
        SourceSelectDialog.show(
            context = this,
            title = getString(R.string.ai_add_model_from_list),
            items = items,
            selectedKey = null,
            displayName = { it },
            searchTexts = { listOf(it) },
            itemKey = { it }
        ) { selected ->
            if (selected == allKey) appendFetchedModels(providerId, modelIds)
            else appendFetchedModels(providerId, listOf(selected))
        }
    }

    private fun appendFetchedModels(providerId: String, modelIds: List<String>) {
        val oldModels = AppConfig.aiModelConfigList
        val existingIds = oldModels.filter { it.providerId == providerId }.map { it.modelId }.toSet()
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
        reloadModels()
        toastOnUi(getString(R.string.ai_fetch_models_success, newModels.size))
    }

    private fun normalizeApiMode(value: String?): String {
        return if (value?.trim() == AI_API_MODE_RESPONSES) AI_API_MODE_RESPONSES else AI_API_MODE_CHAT_COMPLETIONS
    }

    private fun actionBackground() = UiCorner.actionSelector(
        ContextCompat.getColor(this, R.color.background_card),
        ContextCompat.getColor(this, R.color.background_menu),
        UiCorner.actionRadius(this)
    )

    private inner class ModelAdapter :
        RecyclerAdapter<AiModelConfig, ItemS3ContainerBinding>(this@AiProviderEditActivity) {

        override fun getViewBinding(parent: ViewGroup): ItemS3ContainerBinding {
            return ItemS3ContainerBinding.inflate(inflater, parent, false).apply {
                root.background = UiCorner.panelRounded(
                    root.context,
                    ContextCompat.getColor(root.context, R.color.background_card),
                    UiCorner.panelRadius(root.context)
                )
                btnMore.background = actionBackground()
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemS3ContainerBinding,
            item: AiModelConfig,
            payloads: MutableList<Any>
        ) = binding.run {
            val provider = currentProvider()
            val isCurrent = item.id == AppConfig.aiCurrentModelId
            tvName.text = item.modelId
            tvPath.text = provider?.name.orEmpty()
            tvCapacity.text = provider?.baseUrl.orEmpty()
            tvState.text = getString(if (isCurrent) R.string.ai_current_model else R.string.ai_model)
            tvSelected.visibility = if (isCurrent) View.VISIBLE else View.GONE
            tvSelected.setTextColor(ContextCompat.getColor(this@AiProviderEditActivity, R.color.accent))
            tvName.applyUiSectionTitleStyle(this@AiProviderEditActivity)
            tvPath.applyUiLabelStyle(this@AiProviderEditActivity)
            tvCapacity.applyUiLabelStyle(this@AiProviderEditActivity)
            tvState.applyUiLabelStyle(this@AiProviderEditActivity)
            listOf(tvPath, tvCapacity, tvState).forEach { it.setTextColor(secondaryTextColor) }
            btnMore.setOnClickListener { showModelActions(item) }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemS3ContainerBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.bindingAdapterPosition - getHeaderCount())?.let { showModelActions(it) }
            }
        }
    }

    companion object {
        const val EXTRA_PROVIDER_ID = "providerId"
        private const val TAB_CONFIG = "config"
        private const val TAB_MODEL = "model"
    }
}
