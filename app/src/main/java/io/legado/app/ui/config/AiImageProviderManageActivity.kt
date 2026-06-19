package io.legado.app.ui.config

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ActivityAiProviderManageBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.ItemS3ContainerBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.applyUiLabelStyle
import io.legado.app.lib.theme.applyUiSectionTitleStyle
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.main.ai.AiImageProviderConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.readText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AiImageProviderManageActivity : BaseActivity<ActivityAiProviderManageBinding>() {

    override val binding by viewBinding(ActivityAiProviderManageBinding::inflate)
    private val adapter by lazy { Adapter() }
    private val importRule = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            lifecycleScope.launch {
                runCatching {
                    parseImportedRules(uri.readText(this@AiImageProviderManageActivity))
                }.onSuccess { rules ->
                    importRules(rules)
                }.onFailure {
                    toastOnUi(it.localizedMessage ?: getString(R.string.wrong_format))
                }
            }
        }
    }
    private val exportRuleResult = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val value = uri.toString()
            if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
                alert(R.string.upload_url) {
                    setMessage(value)
                    positiveButton(R.string.copy_text) {
                        sendToClip(value)
                        toastOnUi(R.string.copy_complete)
                    }
                    negativeButton(R.string.cancel)
                }
            } else {
                toastOnUi(R.string.export_success)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = getString(R.string.ai_image_provider_manage)
        binding.tvSummary.text = getString(R.string.ai_image_provider_manage_summary)
        binding.tvSummary.applyUiLabelStyle(this)
        binding.tvSummary.setTextColor(secondaryTextColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        (binding.recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.btnAdd.text = getString(R.string.add)
        binding.btnAdd.background = actionBackground()
        binding.btnAdd.setOnClickListener { showAddSelector() }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        adapter.setItems(AppConfig.aiImageProviderList.sortedBy { it.order })
    }

    private fun showAddSelector() {
        selector(
            getString(R.string.add),
            listOf(
                getString(R.string.ai_image_provider_openai),
                getString(R.string.ai_image_provider_js),
                "导入 JS 生图规则"
            )
        ) { _, index ->
            if (index == 2) {
                showImportActions()
                return@selector
            }
            val type = if (index == 0) AiImageProviderConfig.TYPE_OPENAI else AiImageProviderConfig.TYPE_JS
            openEdit(AiImageProviderEditActivity.newIntent(this, null, type))
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_IMPORT_RULE, 0, "导入规则").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_IMPORT_RULE -> {
                showImportActions()
                true
            }
            else -> super.onCompatOptionsItemSelected(item)
        }
    }

    private fun openEdit(intent: Intent) {
        startActivity(intent)
    }

    private fun showActions(provider: AiImageProviderConfig) {
        val isJsRule = provider.type == AiImageProviderConfig.TYPE_JS
        val isCurrent = provider.id == AppConfig.aiCurrentImageProviderId
        val actions = buildList {
            if (!isCurrent) add("设为当前生图模型")
            add(getString(if (provider.enabled) R.string.disable else R.string.enable))
            add(getString(R.string.edit))
            if (isJsRule) {
                add("导出规则")
                add("复制规则")
            }
            add(getString(R.string.delete))
        }
        selector(
            provider.displayName(),
            actions
        ) { _, index ->
            when (actions[index]) {
                "设为当前生图模型" -> {
                    if (!provider.enabled) {
                        toastOnUi("请先启用该生图模型")
                    } else {
                        AppConfig.aiCurrentImageProviderId = provider.id
                        reload()
                        toastOnUi("已设为当前生图模型")
                    }
                }
                getString(if (provider.enabled) R.string.disable else R.string.enable) -> {
                    AppConfig.aiImageProviderList = AppConfig.aiImageProviderList.map {
                        if (it.id == provider.id) it.copy(enabled = !it.enabled) else it
                    }
                    reload()
                }
                getString(R.string.edit) ->
                    openEdit(AiImageProviderEditActivity.newIntent(this, provider.id, provider.type))
                "导出规则" -> exportRule(provider)
                "复制规则" -> {
                    sendToClip(serializeRule(provider))
                    toastOnUi(R.string.copy_complete)
                }
                getString(R.string.delete) -> confirmDelete(provider)
            }
        }
    }

    private fun showImportActions() {
        selector(
            getString(R.string.import_str),
            listOf(getString(R.string.import_str), getString(R.string.import_on_line))
        ) { _, index ->
            when (index) {
                0 -> launchImportFile()
                1 -> showImportUrlDialog()
            }
        }
    }

    private fun launchImportFile() {
        importRule.launch {
            mode = HandleFileContract.FILE
            title = getString(R.string.import_str)
            allowExtensions = arrayOf("json")
        }
    }

    private fun showImportUrlDialog() {
        alert(R.string.import_on_line) {
            val dialogBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "https://..."
            }
            customView { dialogBinding.root }
            okButton {
                val url = dialogBinding.editView.text?.toString().orEmpty().trim()
                if (url.isNotEmpty()) importRulesFromUrl(url)
            }
            cancelButton()
        }
    }

    private fun importRulesFromUrl(url: String) {
        lifecycleScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    okHttpClient.newCallResponseBody { url(url) }.use { it.string() }
                }
                parseImportedRules(text)
            }.onSuccess { rules ->
                importRules(rules)
            }.onFailure {
                toastOnUi(it.localizedMessage ?: getString(R.string.wrong_format))
            }
        }
    }

    private fun parseImportedRules(raw: String): List<AiImageProviderConfig> {
        val text = raw.trim()
        if (text.isBlank()) return emptyList()
        val root = if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
        val array = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("rules") ?: JSONArray().put(root)
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                parseRule(json)?.let { add(it) }
            }
        }
    }

    private fun parseRule(json: JSONObject): AiImageProviderConfig? {
        val type = json.optString("type").ifBlank {
            if (json.optString("script").isNotBlank()) AiImageProviderConfig.TYPE_JS else ""
        }
        if (type != AiImageProviderConfig.TYPE_JS) return null
        val script = json.optString("script").ifBlank { json.optString("rule") }.trim()
        if (script.isBlank()) return null
        return AiImageProviderConfig(
            name = json.optString("name").ifBlank { getString(R.string.ai_image_provider_js) },
            type = AiImageProviderConfig.TYPE_JS,
            model = json.optString("model").ifBlank { "JS" },
            stylePrompt = json.optString("stylePrompt"),
            jsLib = json.optString("jsLib"),
            loginUrl = json.optString("loginUrl"),
            loginUi = json.optString("loginUi"),
            enabledCookieJar = json.optBoolean("enabledCookieJar", false),
            script = script,
            timeoutMillisecond = json.optLong("timeoutMillisecond", 300_000L).takeIf { it > 0L } ?: 300_000L,
            order = json.optInt("order", 0),
            enabled = json.optBoolean("enabled", true)
        )
    }

    private fun importRules(rules: List<AiImageProviderConfig>) {
        val validRules = rules.filter { it.type == AiImageProviderConfig.TYPE_JS && it.script.isNotBlank() }
        if (validRules.isEmpty()) {
            toastOnUi(R.string.wrong_format)
            return
        }
        val startOrder = (AppConfig.aiImageProviderList.maxOfOrNull { it.order } ?: -1) + 1
        AppConfig.aiImageProviderList = AppConfig.aiImageProviderList + validRules.mapIndexed { index, rule ->
            rule.copy(
                id = UUID.randomUUID().toString(),
                name = rule.name.trim().ifBlank { "生图规则" },
                type = AiImageProviderConfig.TYPE_JS,
                baseUrl = "",
                apiKey = "",
                headers = "",
                defaultParamsJson = "",
                order = startOrder + index
            )
        }
        reload()
        toastOnUi("已导入 ${validRules.size} 个生图规则")
    }

    private fun exportRule(provider: AiImageProviderConfig) {
        exportRuleResult.launch {
            mode = HandleFileContract.EXPORT
            fileData = HandleFileContract.FileData(
                "ai-image-rule-${safeFileName(provider.displayName())}.json",
                serializeRule(provider).toByteArray(),
                "application/json"
            )
        }
    }

    private fun serializeRule(provider: AiImageProviderConfig): String {
        val rule = provider.copy(
            type = AiImageProviderConfig.TYPE_JS,
            baseUrl = "",
            apiKey = "",
            headers = "",
            defaultParamsJson = ""
        )
        return GSON.toJson(
            mapOf(
                "type" to "ai_image_rule",
                "version" to 1,
                "rules" to listOf(rule)
            )
        )
    }

    private fun safeFileName(value: String): String {
        return value.trim().ifBlank { "ai-image-rule" }
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    private fun confirmDelete(provider: AiImageProviderConfig) {
        alert(provider.displayName()) {
            setMessage(R.string.delete)
            okButton {
                AppConfig.aiImageProviderList = AppConfig.aiImageProviderList.filterNot { it.id == provider.id }
                reload()
                toastOnUi(R.string.delete)
            }
            cancelButton()
        }
    }

    private fun actionBackground() = UiCorner.actionSelector(
        ContextCompat.getColor(this, R.color.background_card),
        ContextCompat.getColor(this, R.color.background_menu),
        UiCorner.actionRadius(this)
    )

    private inner class Adapter :
        RecyclerAdapter<AiImageProviderConfig, ItemS3ContainerBinding>(this@AiImageProviderManageActivity) {

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
            item: AiImageProviderConfig,
            payloads: MutableList<Any>
        ) = binding.run {
            val currentId = AppConfig.aiCurrentImageProvider?.id ?: AppConfig.aiCurrentImageProviderId
            val isCurrent = item.id == currentId
            tvName.text = item.displayName()
            tvPath.text = if (item.type == AiImageProviderConfig.TYPE_OPENAI) {
                item.baseUrl.ifBlank { "OpenAI" }
            } else {
                getString(R.string.ai_image_provider_js)
            }
            tvCapacity.text = item.model.ifBlank { if (item.type == AiImageProviderConfig.TYPE_OPENAI) "gpt-image-1" else "JS" }
            tvState.text = buildString {
                if (isCurrent) append("当前使用 · ")
                append(getString(if (item.enabled) R.string.enabled else R.string.disabled))
            }
            tvSelected.visibility = if (isCurrent) View.VISIBLE else View.GONE
            tvSelected.text = "✓"
            tvSelected.setTextColor(ContextCompat.getColor(this@AiImageProviderManageActivity, R.color.accent))
            tvName.applyUiSectionTitleStyle(this@AiImageProviderManageActivity)
            tvPath.applyUiLabelStyle(this@AiImageProviderManageActivity)
            tvCapacity.applyUiLabelStyle(this@AiImageProviderManageActivity)
            tvState.applyUiLabelStyle(this@AiImageProviderManageActivity)
            listOf(tvPath, tvCapacity, tvState).forEach { it.setTextColor(secondaryTextColor) }
            btnMore.setOnClickListener { showActions(item) }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemS3ContainerBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.bindingAdapterPosition - getHeaderCount())?.let {
                    openEdit(AiImageProviderEditActivity.newIntent(this@AiImageProviderManageActivity, it.id, it.type))
                }
            }
        }
    }

    companion object {
        private const val MENU_IMPORT_RULE = 1
    }
}
