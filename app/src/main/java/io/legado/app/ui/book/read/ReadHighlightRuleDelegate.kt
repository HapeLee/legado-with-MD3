package io.legado.app.ui.book.read

import android.content.Context
import android.net.Uri
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.repository.HighlightRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.ui.book.read.page.provider.TextChapterLayout
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.ImportItemWrapper
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.sendToClip
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 阅读页高亮规则域：增删改排序、导入（文本 / URL / 文件）、导出（文件 / 上传取链）。
 *
 * 自持 [HighlightRuleConfigUiState]，只通过 [Host] 反向触达 Toast 与「规则变了，重排正文」。
 *
 * `execute {}` 是 `BaseViewModel` 的成员，delegate 里改用它的实现体
 * `Coroutine.async(scope, Dispatchers.IO)`——默认参数（start / executeContext / semaphore）
 * 与 `execute {}` 完全一致，语义不变。
 */
class ReadHighlightRuleDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: Host,
    private val highlightRuleRepository: HighlightRuleRepository,
    private val uploadRepository: UploadRepository,
) {

    interface Host {
        fun showToast(message: String)

        /** 规则集变了：让正文重新走高亮排版。 */
        fun notifyRulesChanged()
    }

    private val _uiState = MutableStateFlow(HighlightRuleConfigUiState())
    val uiState = _uiState.asStateFlow()

    fun load() {
        val configName = ReadBookConfig.durConfig.name
        _uiState.update {
            it.copy(
                rules = highlightRuleRepository.load(configName).toImmutableList(),
                editingRule = null,
                showNewRule = false,
                deleteRule = null,
                importState = BaseImportUiState.Idle,
            )
        }
    }

    /** 关闭弹层时清掉编辑/删除/导入的中间态，规则列表保留。 */
    fun onSheetDismissed() {
        _uiState.update {
            it.copy(
                editingRule = null,
                showNewRule = false,
                deleteRule = null,
                importState = BaseImportUiState.Idle,
            )
        }
    }

    // --- 增删改排序 ---

    fun startAddRule() {
        _uiState.update { it.copy(showNewRule = true) }
    }

    fun startEditRule(rule: HighlightRule) {
        _uiState.update { it.copy(editingRule = rule) }
    }

    fun dismissRuleEdit() {
        _uiState.update { it.copy(editingRule = null, showNewRule = false) }
    }

    fun toggleRule(rule: HighlightRule, enabled: Boolean) {
        val rules = _uiState.value.rules.map {
            if (it.id == rule.id) it.copy(enabled = enabled) else it
        }
        saveRules(rules)
    }

    fun saveRule(rule: HighlightRule) {
        val currentRules = _uiState.value.rules
        val updatedRules = if (currentRules.any { it.id == rule.id }) {
            currentRules.map { if (it.id == rule.id) rule else it }
        } else {
            currentRules + rule
        }
        saveRules(updatedRules)
    }

    fun requestDeleteRule(rule: HighlightRule) {
        _uiState.update { it.copy(deleteRule = rule) }
    }

    fun dismissDeleteRule() {
        _uiState.update { it.copy(deleteRule = null) }
    }

    fun deletePendingRule() {
        val rule = _uiState.value.deleteRule ?: return
        val configName = ReadBookConfig.durConfig.name
        highlightRuleRepository.delete(rule)
        TextChapterLayout.invalidateRegexCache()
        _uiState.update {
            it.copy(
                rules = highlightRuleRepository.load(configName).toImmutableList(),
                deleteRule = null,
            )
        }
        host.notifyRulesChanged()
    }

    fun moveRule(from: Int, to: Int) {
        val rules = _uiState.value.rules
        if (from !in rules.indices || to !in rules.indices) return
        val reordered = rules.toMutableList().apply {
            add(to, removeAt(from))
        }
        _uiState.update { it.copy(rules = reordered.toImmutableList()) }
    }

    fun saveRuleOrder() {
        saveRules(_uiState.value.rules)
    }

    private fun saveRules(rules: List<HighlightRule>) {
        val configName = ReadBookConfig.durConfig.name
        val sanitizedRules = rules.map(highlightRuleRepository::sanitizeRule)
        highlightRuleRepository.save(configName, sanitizedRules)
        TextChapterLayout.invalidateRegexCache()
        _uiState.update {
            it.copy(
                rules = highlightRuleRepository.load(configName).toImmutableList(),
                editingRule = null,
                showNewRule = false,
                deleteRule = null,
            )
        }
        host.notifyRulesChanged()
    }

    // --- 导入 ---

    fun importSource(text: String) {
        _uiState.update { it.copy(importState = BaseImportUiState.Loading) }
        Coroutine.async(scope, Dispatchers.IO) {
            val importedRules = importSourceAwait(text.trim())
                .map(highlightRuleRepository::sanitizeRule)
            if (importedRules.isEmpty()) {
                throw NoStackTraceException(context.getString(R.string.wrong_format))
            }
            val oldRules = highlightRuleRepository.load(ReadBookConfig.durConfig.name)
                .associateBy { it.id }
            BaseImportUiState.Success(
                source = text,
                items = importedRules.map { rule ->
                    val oldRule = oldRules[rule.id]
                    val status = when {
                        oldRule == null -> ImportStatus.New
                        oldRule != rule -> ImportStatus.Update
                        else -> ImportStatus.Existing
                    }
                    ImportItemWrapper(
                        data = rule,
                        oldData = oldRule,
                        status = status,
                        isSelected = status != ImportStatus.Existing,
                    )
                }
            )
        }.onSuccess { importState ->
            _uiState.update { it.copy(importState = importState) }
        }.onError {
            AppLog.put("导入高亮规则失败\n${it.localizedMessage}", it, true)
            _uiState.update { state ->
                state.copy(
                    importState = BaseImportUiState.Error(
                        it.localizedMessage ?: context.getString(R.string.wrong_format)
                    )
                )
            }
        }
    }

    private suspend fun importSourceAwait(text: String): List<HighlightRule> {
        return when {
            text.isJsonArray() -> GSON.fromJsonArray<HighlightRule>(text).getOrThrow()
            text.isJsonObject() -> listOf(
                GSON.fromJsonObject<HighlightRule>(text).getOrThrow()
            )
            text.isAbsUrl() -> {
                val body = okHttpClient.newCallResponseBody {
                    if (text.endsWith("#requestWithoutUA")) {
                        url(text.substringBeforeLast("#requestWithoutUA"))
                        header(AppConst.UA_NAME, "null")
                    } else {
                        url(text)
                    }
                }.decompressed().text()
                importSourceAwait(body)
            }
            else -> throw NoStackTraceException(context.getString(R.string.wrong_format))
        }
    }

    fun importFile(uri: Uri) {
        Coroutine.async<String?>(scope, Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use {
                it.reader().readText()
            }
        }.onSuccess { text ->
            if (text.isNullOrBlank()) {
                _uiState.update { state ->
                    state.copy(
                        importState = BaseImportUiState.Error(
                            context.getString(R.string.wrong_format)
                        )
                    )
                }
            } else {
                importSource(text)
            }
        }.onError {
            _uiState.update { state ->
                state.copy(
                    importState = BaseImportUiState.Error(
                        it.localizedMessage ?: context.getString(R.string.wrong_format)
                    )
                )
            }
        }
    }

    fun cancelImport() {
        _uiState.update { it.copy(importState = BaseImportUiState.Idle) }
    }

    fun toggleImportSelection(index: Int) {
        val importState = _uiState.value.importState
            as? BaseImportUiState.Success<HighlightRule> ?: return
        if (index !in importState.items.indices) return
        val items = importState.items.toMutableList()
        val item = items[index]
        items[index] = item.copy(isSelected = !item.isSelected)
        _uiState.update { it.copy(importState = importState.copy(items = items)) }
    }

    fun toggleImportAll(isSelected: Boolean) {
        val importState = _uiState.value.importState
            as? BaseImportUiState.Success<HighlightRule> ?: return
        _uiState.update {
            it.copy(
                importState = importState.copy(
                    items = importState.items.map { item ->
                        item.copy(isSelected = isSelected)
                    }
                )
            )
        }
    }

    fun updateImportItem(index: Int, rule: HighlightRule) {
        val importState = _uiState.value.importState
            as? BaseImportUiState.Success<HighlightRule> ?: return
        if (index !in importState.items.indices) return
        val items = importState.items.toMutableList()
        items[index] = items[index].copy(data = rule)
        _uiState.update {
            it.copy(
                importState = importState.copy(
                    items = items,
                    version = importState.version + 1,
                )
            )
        }
    }

    fun saveImported() {
        val state = _uiState.value
        val importState = state.importState
            as? BaseImportUiState.Success<HighlightRule> ?: return
        val importedRules = importState.items
            .filter { it.isSelected }
            .map { highlightRuleRepository.sanitizeRule(it.data) }
        if (importedRules.isEmpty()) return
        val importedById = importedRules.associateBy { it.id }
        val mergedRules = state.rules.map { importedById[it.id] ?: it } +
                importedRules.filter { imported -> state.rules.none { it.id == imported.id } }
        saveRules(mergedRules)
        cancelImport()
    }

    // --- 导出 ---

    fun exportToFile(uri: Uri) {
        val rules = _uiState.value.rules
        Coroutine.async(scope, Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.bufferedWriter().use { writer ->
                    writer.write(GSON.toJson(rules))
                }
            } ?: throw NoStackTraceException(context.getString(R.string.error))
        }.onSuccess {
            host.showToast(context.getString(R.string.export_success))
        }.onError {
            host.showToast(it.localizedMessage ?: context.getString(R.string.error))
        }
    }

    fun exportAsUrl() {
        val rules = _uiState.value.rules
        Coroutine.async(scope, Dispatchers.IO) {
            uploadRepository.upload(
                fileName = HighlightRuleRepository.backupFileName,
                file = GSON.toJson(rules),
                contentType = "application/json",
            )
        }.onSuccess { url ->
            context.sendToClip(url)
            host.showToast(context.getString(R.string.copy_url))
        }
    }
}
