package io.legado.app.ui.config

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityAiImageProviderEditBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.main.ai.AiImageProviderConfig
import io.legado.app.ui.widget.compose.showComposeActionListDialog
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AiImageProviderEditActivity : BaseActivity<ActivityAiImageProviderEditBinding>() {

    override val binding by viewBinding(ActivityAiImageProviderEditBinding::inflate)
    private var providerId: String? = null

    // Compose state
    private var nameText by mutableStateOf("")
    private var baseUrlText by mutableStateOf("")
    private var apiKeyText by mutableStateOf("")
    private var modelText by mutableStateOf("")
    private var headersText by mutableStateOf("")
    private var timeoutText by mutableStateOf("300000")
    private var enabledState by mutableStateOf(true)
    private var providerType by mutableStateOf(AiImageProviderConfig.TYPE_OPENAI)
    private var paramsText by mutableStateOf("")
    private var stylePromptText by mutableStateOf("")
    private var scriptText by mutableStateOf("")
    private var jsLibText by mutableStateOf("")
    private var editingField: Field = Field.PARAMS

    private val codeEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val text = result.data?.getStringExtra("text") ?: return@registerForActivityResult
        when (editingField) {
            Field.PARAMS -> paramsText = text
            Field.STYLE_PROMPT -> stylePromptText = text
            Field.SCRIPT -> scriptText = text
            Field.JS_LIB -> jsLibText = text
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        providerId = intent.getStringExtra(EXTRA_PROVIDER_ID)
        providerType = intent.getStringExtra(EXTRA_PROVIDER_TYPE) ?: AiImageProviderConfig.TYPE_OPENAI
        val provider = currentProvider()
        if (provider != null) providerType = provider.type
        bind(provider)
        initComposeContent()
    }

    private fun initComposeContent() {
        val container = binding.root as? ViewGroup ?: return
        val titleBar = binding.titleBar
        val index = container.indexOfChild(titleBar)
        // Remove all children after the title bar (the ScrollView and save button)
        while (container.childCount > index + 1) {
            container.removeViewAt(index + 1)
        }
        // Also remove the title bar itself since Compose handles the top bar
        container.removeView(titleBar)
        val cv = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                val isOpenAi = providerType == AiImageProviderConfig.TYPE_OPENAI
                AiImageProviderEditScreen(
                    name = nameText,
                    onNameChange = { nameText = it },
                    baseUrl = baseUrlText,
                    onBaseUrlChange = { baseUrlText = it },
                    apiKey = apiKeyText,
                    onApiKeyChange = { apiKeyText = it },
                    model = modelText,
                    onModelChange = { modelText = it },
                    headers = headersText,
                    onHeadersChange = { headersText = it },
                    timeout = timeoutText,
                    onTimeoutChange = { timeoutText = it },
                    enabled = enabledState,
                    onEnabledChange = { enabledState = it },
                    providerType = providerType,
                    isOpenAi = isOpenAi,
                    onTypeClick = { selectType() },
                    stylePromptSummary = "${getString(R.string.ai_image_style_prompt)}: ${summary(stylePromptText)}",
                    onStylePromptClick = {
                        openCodeEditor(
                            Field.STYLE_PROMPT,
                            getString(R.string.ai_image_style_prompt),
                            stylePromptText,
                            "text.html.markdown"
                        )
                    },
                    paramsSummary = "${getString(R.string.ai_image_params)}: ${summary(paramsText.ifBlank { defaultParams() })}",
                    onParamsClick = {
                        openCodeEditor(
                            Field.PARAMS,
                            getString(R.string.ai_image_params),
                            paramsText.ifBlank { defaultParams() }
                        )
                    },
                    scriptSummary = "${getString(R.string.ai_image_script)}: ${summary(scriptText)}",
                    onScriptClick = {
                        openCodeEditor(Field.SCRIPT, getString(R.string.ai_image_script), scriptText)
                    },
                    jsLibSummary = "jsLib: ${summary(jsLibText)}",
                    onJsLibClick = {
                        openCodeEditor(Field.JS_LIB, "jsLib", jsLibText)
                    },
                    onSave = { save() },
                    onBack = { finish() }
                )
            }
        }
        container.addView(cv)
    }

    private fun bind(provider: AiImageProviderConfig?) {
        nameText = provider?.name.orEmpty()
        baseUrlText = provider?.baseUrl.orEmpty()
        apiKeyText = provider?.apiKey.orEmpty()
        modelText = provider?.model.orEmpty()
        headersText = provider?.headers.orEmpty()
        timeoutText = (provider?.validTimeout() ?: 300_000L).toString()
        enabledState = provider?.enabled ?: true
        paramsText = provider?.defaultParamsJson.orEmpty()
        stylePromptText = provider?.stylePrompt.orEmpty()
        scriptText = provider?.script.orEmpty()
        jsLibText = provider?.jsLib.orEmpty()
    }

    private fun selectType() {
        showComposeActionListDialog(
            title = getString(R.string.ai_image_provider_type),
            labels = listOf(
                getString(R.string.ai_image_provider_openai),
                getString(R.string.ai_image_provider_js)
            )
        ) { index ->
            providerType = if (index == 0) {
                AiImageProviderConfig.TYPE_OPENAI
            } else {
                AiImageProviderConfig.TYPE_JS
            }
        }
    }

    private fun openCodeEditor(
        field: Field,
        title: String,
        text: String,
        languageName: String = "source.js"
    ) {
        editingField = field
        codeEditLauncher.launch(Intent(this, CodeEditActivity::class.java).apply {
            putExtra("title", title)
            putExtra("text", text)
            putExtra("languageName", languageName)
        })
    }

    private fun save() {
        if (nameText.isBlank()) {
            toastOnUi(R.string.ai_provider_name_required)
            return
        }
        if (providerType == AiImageProviderConfig.TYPE_OPENAI && baseUrlText.isBlank()) {
            toastOnUi(R.string.ai_provider_url_required)
            return
        }
        val old = currentProvider()
        val needDefaultProvider = old == null && AppConfig.aiCurrentImageProvider == null
        val updated = (old ?: AiImageProviderConfig(name = nameText, type = providerType)).copy(
            name = nameText,
            type = providerType,
            baseUrl = baseUrlText,
            apiKey = apiKeyText,
            headers = headersText,
            model = modelText,
            defaultParamsJson = paramsText.ifBlank { defaultParams() },
            stylePrompt = stylePromptText.trim(),
            jsLib = jsLibText,
            script = scriptText,
            timeoutMillisecond = timeoutText.toLongOrNull() ?: 300_000L,
            enabled = if (needDefaultProvider) true else enabledState
        )
        AppConfig.aiImageProviderList = AppConfig.aiImageProviderList
            .filterNot { it.id == updated.id } + updated
        if (needDefaultProvider) {
            AppConfig.ensureCurrentImageProvider(updated.id)
        }
        postEvent(EventBus.AI_CONFIG_CHANGED, true)
        finish()
    }

    private fun currentProvider(): AiImageProviderConfig? {
        val id = providerId ?: return null
        return AppConfig.aiImageProviderList.firstOrNull { it.id == id }
    }

    private fun defaultParams(): String {
        return if (providerType == AiImageProviderConfig.TYPE_OPENAI) {
            "{\n  \"size\": \"1024x1024\"\n}"
        } else {
            ""
        }
    }

    private fun summary(value: String): String {
        return value.trim().lineSequence().firstOrNull()?.take(36)?.ifBlank { null } ?: "未设置"
    }

    private enum class Field {
        PARAMS,
        STYLE_PROMPT,
        SCRIPT,
        JS_LIB
    }

    companion object {
        private const val EXTRA_PROVIDER_ID = "providerId"
        private const val EXTRA_PROVIDER_TYPE = "providerType"

        fun newIntent(context: Context, providerId: String?, type: String): Intent {
            return Intent(context, AiImageProviderEditActivity::class.java).apply {
                putExtra(EXTRA_PROVIDER_TYPE, type)
                providerId?.let { putExtra(EXTRA_PROVIDER_ID, it) }
            }
        }
    }
}
