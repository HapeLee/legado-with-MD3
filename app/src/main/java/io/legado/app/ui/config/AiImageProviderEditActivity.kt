package io.legado.app.ui.config

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityAiImageProviderEditBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.UiCorner
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.main.ai.AiImageProviderConfig
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AiImageProviderEditActivity : BaseActivity<ActivityAiImageProviderEditBinding>() {

    override val binding by viewBinding(ActivityAiImageProviderEditBinding::inflate)
    private var providerId: String? = null
    private var providerType: String = AiImageProviderConfig.TYPE_OPENAI
    private var paramsText: String = ""
    private var stylePromptText: String = ""
    private var scriptText: String = ""
    private var jsLibText: String = ""
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
        refreshCodeButtons()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        providerId = intent.getStringExtra(EXTRA_PROVIDER_ID)
        providerType = intent.getStringExtra(EXTRA_PROVIDER_TYPE) ?: AiImageProviderConfig.TYPE_OPENAI
        val provider = currentProvider()
        if (provider != null) providerType = provider.type
        bind(provider)
        initView()
    }

    private fun initView() = binding.run {
        listOf(tvType, btnStylePrompt, btnParams, btnScript, btnJsLib, btnSave).forEach {
            it.background = actionBackground()
        }
        tvType.setOnClickListener { selectType() }
        btnStylePrompt.setOnClickListener {
            openCodeEditor(
                Field.STYLE_PROMPT,
                getString(R.string.ai_image_style_prompt),
                stylePromptText,
                "text.html.markdown"
            )
        }
        btnParams.setOnClickListener { openCodeEditor(Field.PARAMS, getString(R.string.ai_image_params), paramsText.ifBlank { defaultParams() }) }
        btnScript.setOnClickListener { openCodeEditor(Field.SCRIPT, getString(R.string.ai_image_script), scriptText) }
        btnJsLib.setOnClickListener { openCodeEditor(Field.JS_LIB, "jsLib", jsLibText) }
        btnSave.setOnClickListener { save() }
        refreshTypeUi()
        refreshCodeButtons()
    }

    private fun bind(provider: AiImageProviderConfig?) = binding.run {
        etName.setText(provider?.name.orEmpty())
        etBaseUrl.setText(provider?.baseUrl.orEmpty())
        etApiKey.setText(provider?.apiKey.orEmpty())
        etModel.setText(provider?.model.orEmpty())
        etHeaders.setText(provider?.headers.orEmpty())
        etTimeout.setText((provider?.validTimeout() ?: 300_000L).toString())
        cbEnabled.isChecked = provider?.enabled ?: true
        paramsText = provider?.defaultParamsJson.orEmpty()
        stylePromptText = provider?.stylePrompt.orEmpty()
        scriptText = provider?.script.orEmpty()
        jsLibText = provider?.jsLib.orEmpty()
    }

    private fun selectType() {
        selector(
            getString(R.string.ai_image_provider_type),
            listOf(getString(R.string.ai_image_provider_openai), getString(R.string.ai_image_provider_js))
        ) { _, index ->
            providerType = if (index == 0) AiImageProviderConfig.TYPE_OPENAI else AiImageProviderConfig.TYPE_JS
            refreshTypeUi()
        }
    }

    private fun refreshTypeUi() = binding.run {
        val isOpenAi = providerType == AiImageProviderConfig.TYPE_OPENAI
        tvType.text = "${getString(R.string.ai_image_provider_type)}: " +
            getString(if (isOpenAi) R.string.ai_image_provider_openai else R.string.ai_image_provider_js)
        openaiGroup.isVisible = isOpenAi
        btnParams.isVisible = isOpenAi
        btnScript.isVisible = !isOpenAi
        btnJsLib.isVisible = !isOpenAi
    }

    private fun refreshCodeButtons() = binding.run {
        btnStylePrompt.text = "${getString(R.string.ai_image_style_prompt)}: ${summary(stylePromptText)}"
        btnParams.text = "${getString(R.string.ai_image_params)}: ${summary(paramsText.ifBlank { defaultParams() })}"
        btnScript.text = "${getString(R.string.ai_image_script)}: ${summary(scriptText)}"
        btnJsLib.text = "jsLib: ${summary(jsLibText)}"
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
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            toastOnUi(R.string.ai_provider_name_required)
            return
        }
        if (providerType == AiImageProviderConfig.TYPE_OPENAI && binding.etBaseUrl.text?.toString()?.trim().isNullOrBlank()) {
            toastOnUi(R.string.ai_provider_url_required)
            return
        }
        val old = currentProvider()
        val updated = (old ?: AiImageProviderConfig(name = name, type = providerType)).copy(
            name = name,
            type = providerType,
            baseUrl = binding.etBaseUrl.text?.toString()?.trim().orEmpty(),
            apiKey = binding.etApiKey.text?.toString()?.trim().orEmpty(),
            headers = binding.etHeaders.text?.toString()?.trim().orEmpty(),
            model = binding.etModel.text?.toString()?.trim().orEmpty(),
            defaultParamsJson = paramsText.ifBlank { defaultParams() },
            stylePrompt = stylePromptText.trim(),
            jsLib = jsLibText,
            script = scriptText,
            timeoutMillisecond = binding.etTimeout.text?.toString()?.toLongOrNull() ?: 300_000L,
            enabled = binding.cbEnabled.isChecked
        )
        AppConfig.aiImageProviderList = AppConfig.aiImageProviderList
            .filterNot { it.id == updated.id } + updated
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

    private fun actionBackground() = UiCorner.actionSelector(
        ContextCompat.getColor(this, R.color.background_card),
        ContextCompat.getColor(this, R.color.background_menu),
        UiCorner.actionRadius(this)
    )

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
