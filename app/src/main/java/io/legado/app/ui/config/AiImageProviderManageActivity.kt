package io.legado.app.ui.config

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityAiProviderManageBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.main.ai.AiImageProviderConfig
import io.legado.app.ui.widget.compose.AppManagementMenuAction
import io.legado.app.ui.widget.compose.showComposeActionListDialog
import io.legado.app.ui.widget.compose.showComposeConfirmDialog
import io.legado.app.ui.widget.compose.showComposeTextInputDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.postEvent
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
    private val providersState = mutableStateOf<List<AiImageProviderConfig>>(emptyList())
    private val currentProviderIdState = mutableStateOf("")
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
                showComposeConfirmDialog(
                    title = getString(R.string.upload_url),
                    message = value,
                    positiveText = getString(R.string.copy_text),
                    negativeText = getString(R.string.cancel),
                    onPositive = {
                        sendToClip(value)
                        toastOnUi(R.string.copy_complete)
                    }
                )
            } else {
                toastOnUi(R.string.export_success)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeRoot.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeRoot.setContent {
            AiImageProviderManageScreen(
                providers = providersState.value,
                currentProviderId = currentProviderIdState.value,
                onBack = { finish() },
                onImportRules = { showImportActions() },
                onExportRules = { exportAllRules() },
                onAdd = { showAddSelector() },
                onOpenProvider = { provider ->
                    openEdit(AiImageProviderEditActivity.newIntent(this, provider.id, provider.type))
                },
                providerActions = ::providerActions
            )
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        AppConfig.ensureCurrentImageProvider()
        val providers = AppConfig.aiImageProviderList.sortedBy { it.order }
        providersState.value = providers
        currentProviderIdState.value = AppConfig.aiCurrentImageProvider?.id.orEmpty()
    }

    private fun showAddSelector() {
        val labels = listOf(
            getString(R.string.ai_image_provider_openai),
            getString(R.string.ai_image_provider_js),
            "导入 JS 生图规则"
        )
        showComposeActionListDialog(
            title = getString(R.string.add),
            labels = labels
        ) { index ->
            if (index == 2) {
                showImportActions()
                return@showComposeActionListDialog
            }
            val type = if (index == 0) AiImageProviderConfig.TYPE_OPENAI else AiImageProviderConfig.TYPE_JS
            openEdit(AiImageProviderEditActivity.newIntent(this, null, type))
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_IMPORT_RULE, 0, "导入规则").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_EXPORT_RULES, 1, "导出规则").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_IMPORT_RULE -> {
                showImportActions()
                true
            }
            MENU_EXPORT_RULES -> {
                exportAllRules()
                true
            }
            else -> super.onCompatOptionsItemSelected(item)
        }
    }

    private fun openEdit(intent: Intent) {
        startActivity(intent)
    }

    private fun providerActions(provider: AiImageProviderConfig): List<AppManagementMenuAction> {
        val isJsRule = provider.type == AiImageProviderConfig.TYPE_JS
        val isCurrent = provider.id == AppConfig.aiCurrentImageProviderId
        return buildList {
            if (!isCurrent) {
                add(
                    AppManagementMenuAction("设为当前生图模型") {
                        if (!provider.enabled) {
                            toastOnUi("请先启用该生图模型")
                        } else {
                            AppConfig.aiCurrentImageProviderId = provider.id
                            notifyAiConfigChanged()
                            reload()
                            toastOnUi("已设为当前生图模型")
                        }
                    }
                )
            }
            add(
                AppManagementMenuAction(getString(if (provider.enabled) R.string.disable else R.string.enable)) {
                    AppConfig.aiImageProviderList = AppConfig.aiImageProviderList.map {
                        if (it.id == provider.id) it.copy(enabled = !it.enabled) else it
                    }
                    notifyAiConfigChanged()
                    reload()
                }
            )
            add(
                AppManagementMenuAction(getString(R.string.edit)) {
                    openEdit(
                        AiImageProviderEditActivity.newIntent(
                            this@AiImageProviderManageActivity,
                            provider.id,
                            provider.type
                        )
                    )
                }
            )
            if (isJsRule) {
                add(AppManagementMenuAction("导出规则") { exportRule(provider) })
                add(
                    AppManagementMenuAction("复制规则") {
                        sendToClip(serializeRule(provider))
                        toastOnUi(R.string.copy_complete)
                    }
                )
            }
            add(
                AppManagementMenuAction(
                    text = getString(R.string.delete),
                    danger = true,
                    onClick = { confirmDelete(provider) }
                )
            )
        }
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
        showComposeActionListDialog(
            title = provider.displayName(),
            labels = actions,
            dangerIndices = setOf(actions.lastIndex)
        ) { index ->
            when (actions.getOrNull(index)) {
                "设为当前生图模型" -> {
                    if (!provider.enabled) {
                        toastOnUi("请先启用该生图模型")
                    } else {
                        AppConfig.aiCurrentImageProviderId = provider.id
                        notifyAiConfigChanged()
                        reload()
                        toastOnUi("已设为当前生图模型")
                    }
                }
                getString(if (provider.enabled) R.string.disable else R.string.enable) -> {
                    AppConfig.aiImageProviderList = AppConfig.aiImageProviderList.map {
                        if (it.id == provider.id) it.copy(enabled = !it.enabled) else it
                    }
                    notifyAiConfigChanged()
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
        showComposeActionListDialog(
            title = getString(R.string.import_str),
            labels = listOf(getString(R.string.import_str), getString(R.string.import_on_line))
        ) { index ->
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
        showComposeTextInputDialog(
            title = getString(R.string.import_on_line),
            hint = "https://...",
            onPositive = { value ->
                val url = value.trim()
                if (url.isNotEmpty()) importRulesFromUrl(url)
            }
        )
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
            when {
                json.optString("script").isNotBlank() -> AiImageProviderConfig.TYPE_JS
                json.optString("showRule").isNotBlank() || json.optString("urlRule").isNotBlank() -> AiImageProviderConfig.TYPE_JS
                else -> ""
            }
        }
        if (type != AiImageProviderConfig.TYPE_JS) return null
        val script = json.optString("script")
            .ifBlank { json.optString("rule") }
            .ifBlank { legacyAiImageRuleScript(json) }
            .trim()
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

    private fun legacyAiImageRuleScript(json: JSONObject): String {
        val showRule = json.optString("showRule").trim()
        val urlRule = json.optString("urlRule").trim()
        if (showRule.isBlank() && urlRule.isBlank()) return ""
        return buildString {
            appendLine("/* Converted from legacy AI image showRule/urlRule. */")
            appendLine("var __legacyUrlRule = ${JSONObject.quote(urlRule)};")
            appendLine("var __legacyShowRule = ${JSONObject.quote(showRule)};")
            appendLine(
                """
                function __legacyEvalRule(rule) {
                    if (!rule) return result;
                    var code = String(rule);
                    if (code.indexOf('@js:') === 0) code = code.substring(4);
                    return eval(code);
                }
                function __legacyFirstImage(value) {
                    if (value == null) return '';
                    var text = String(value).trim();
                    if (/^(https?:|data:image\/)/i.test(text)) return text;
                    try {
                        if (text.charAt(0) === '{') {
                            var objectValue = JSON.parse(text);
                            return objectValue.url || objectValue.src || objectValue.image || objectValue.data || '';
                        }
                        if (text.charAt(0) === '[') {
                            var arrayValue = JSON.parse(text);
                            if (arrayValue.length > 0) return __legacyFirstImage(arrayValue[0]);
                        }
                    } catch (e) {}
                    var match = text.match(/<img[^>]+src\s*=\s*["']([^"']+)["']/i);
                    if (match && match[1]) return match[1];
                    match = text.match(/https?:\/\/[^\s"'<>]+/i);
                    if (match && match[0]) return match[0];
                    match = text.match(/data:image\/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=]+/i);
                    if (match && match[0]) return match[0];
                    return text;
                }
                var __legacyPrompt = prompt;
                var key = __legacyPrompt;
                var result = __legacyPrompt;
                if (__legacyUrlRule) {
                    var __legacyUrlResult = __legacyEvalRule(__legacyUrlRule);
                    if (typeof __legacyUrlResult !== 'undefined') result = __legacyUrlResult;
                }
                if (__legacyShowRule) {
                    var __legacyShowResult = __legacyEvalRule(__legacyShowRule);
                    if (typeof __legacyShowResult !== 'undefined') result = __legacyShowResult;
                }
                result = __legacyFirstImage(result);
                """.trimIndent()
            )
        }
    }

    private fun importRules(rules: List<AiImageProviderConfig>) {
        val validRules = rules.filter { it.type == AiImageProviderConfig.TYPE_JS && it.script.isNotBlank() }
        if (validRules.isEmpty()) {
            toastOnUi(R.string.wrong_format)
            return
        }
        val needDefaultProvider = AppConfig.aiCurrentImageProvider == null
        val startOrder = (AppConfig.aiImageProviderList.maxOfOrNull { it.order } ?: -1) + 1
        val importedRules = validRules.mapIndexed { index, rule ->
            rule.copy(
                id = UUID.randomUUID().toString(),
                name = rule.name.trim().ifBlank { "生图规则" },
                type = AiImageProviderConfig.TYPE_JS,
                baseUrl = "",
                apiKey = "",
                headers = "",
                defaultParamsJson = "",
                order = startOrder + index,
                enabled = if (needDefaultProvider && index == 0) true else rule.enabled
            )
        }
        AppConfig.aiImageProviderList = AppConfig.aiImageProviderList + importedRules
        if (needDefaultProvider) {
            AppConfig.ensureCurrentImageProvider(importedRules.firstOrNull()?.id)
        }
        notifyAiConfigChanged()
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

    private fun exportAllRules() {
        val rules = AppConfig.aiImageProviderList
            .filter { it.type == AiImageProviderConfig.TYPE_JS && it.script.isNotBlank() }
            .sortedBy { it.order }
        if (rules.isEmpty()) {
            toastOnUi(R.string.wrong_format)
            return
        }
        exportRules(rules)
    }

    private fun exportRules(providers: List<AiImageProviderConfig>) {
        exportRuleResult.launch {
            mode = HandleFileContract.EXPORT
            fileData = HandleFileContract.FileData(
                "ai-image-rules.json",
                serializeRules(providers).toByteArray(),
                "application/json"
            )
        }
    }

    private fun serializeRule(provider: AiImageProviderConfig): String {
        return GSON.toJson(
            mapOf(
                "type" to "ai_image_rule",
                "version" to 1,
                "rules" to listOf(sanitizedRule(provider))
            )
        )
    }

    private fun serializeRules(providers: List<AiImageProviderConfig>): String {
        return GSON.toJson(
            mapOf(
                "type" to "ai_image_rule",
                "version" to 1,
                "rules" to providers.map(::sanitizedRule)
            )
        )
    }

    private fun sanitizedRule(provider: AiImageProviderConfig): AiImageProviderConfig {
        return provider.copy(
            type = AiImageProviderConfig.TYPE_JS,
            baseUrl = "",
            apiKey = "",
            headers = "",
            defaultParamsJson = ""
        )
    }

    private fun safeFileName(value: String): String {
        return value.trim().ifBlank { "ai-image-rule" }
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    private fun confirmDelete(provider: AiImageProviderConfig) {
        showComposeConfirmDialog(
            title = provider.displayName(),
            message = getString(R.string.delete),
            dangerPositive = true,
            onPositive = {
                AppConfig.aiImageProviderList = AppConfig.aiImageProviderList.filterNot { it.id == provider.id }
                notifyAiConfigChanged()
                reload()
                toastOnUi(R.string.delete)
            }
        )
    }

    private fun notifyAiConfigChanged() {
        postEvent(EventBus.AI_CONFIG_CHANGED, true)
    }

    companion object {
        private const val MENU_IMPORT_RULE = 1
        private const val MENU_EXPORT_RULES = 2
    }
}
