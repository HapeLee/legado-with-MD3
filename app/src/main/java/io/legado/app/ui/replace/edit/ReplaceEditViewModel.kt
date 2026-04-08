package io.legado.app.ui.replace.edit

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.dao.ReplaceRuleDao
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.exception.NoStackTraceException
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class ReplaceEditUiState(
    val id: Long = 0,
    val name: String = "",
    val group: String = "默认",
    val pattern: String = "",
    val replacement: String = "",
    val isRegex: Boolean = false,
    val scope: String = "",
    val scopeTitle: Boolean = false,
    val scopeContent: Boolean = false,
    val excludeScope: String = "",
    val timeout: String = "3000",
    val allGroups: List<String> = emptyList(),
    val showGroupDialog: Boolean = false
)

class ReplaceEditViewModel(
    private val app: Application,
    private val replaceRuleDao: ReplaceRuleDao,
    private val route: ReplaceEditRoute
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReplaceEditUiState())
    val uiState = _uiState.asStateFlow()

    var activeField: ActiveField = ActiveField.None
    enum class ActiveField { Name, None, Pattern, Replacement, Scope, Exclude }

    init {
        initData()
        observeGroups()
    }

    private fun initData() {
        viewModelScope.launch {
            val id = route.id

            if (id > 0) {
                val rule = replaceRuleDao.findById(id)
                rule?.let { updateStateFromRule(it) }
            } else {
                _uiState.update {
                    it.copy(
                        id = id,
                        name = route.pattern ?: "",
                        pattern = route.pattern ?: "",
                        isRegex = route.isRegex,
                        scope = route.scope ?: "",
                        scopeTitle = route.isScopeTitle,
                        scopeContent = route.isScopeContent,
                        excludeScope = "",
                    )
                }
            }
        }
    }

    private fun observeGroups() {
        viewModelScope.launch {
            replaceRuleDao.flowGroups().collectLatest { groups ->
                _uiState.update { it.copy(allGroups = listOf("默认") + groups) }
            }
        }
    }

    private fun updateStateFromRule(rule: ReplaceRule) {
        _uiState.update {
            it.copy(
                id = rule.id,
                name = rule.name,
                group = rule.group ?: "默认",
                pattern = rule.pattern,
                replacement = rule.replacement,
                isRegex = rule.isRegex,
                scopeTitle = rule.scopeTitle,
                scopeContent = rule.scopeContent,
                scope = rule.scope ?: "",
                excludeScope = rule.excludeScope ?: "",
                timeout = rule.timeoutMillisecond.toString()
            )
        }
    }

    private fun getReplaceRuleFromState(): ReplaceRule {
        val state = _uiState.value
        val rule = ReplaceRule().apply {
            id = state.id
            name = state.name
            group = if (state.group == "默认" || state.group.isBlank()) null else state.group
            pattern = state.pattern
            replacement = state.replacement
            isRegex = state.isRegex
            scopeTitle = state.scopeTitle
            scopeContent = state.scopeContent
            scope = state.scope
            excludeScope = state.excludeScope
            timeoutMillisecond = state.timeout.toLongOrNull() ?: 3000L
        }
        return rule
    }

    fun copyRule() {
        viewModelScope.launch(Dispatchers.Main) {
            val ruleToCopy = getReplaceRuleFromState()
            val json = GSON.toJson(ruleToCopy)
            app.sendToClip(json)
            app.toastOnUi("规则已复制到剪贴板")
        }
    }

    fun pasteRule(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = app.getClipText()
                if (text.isNullOrBlank()) {
                    throw NoStackTraceException("剪贴板为空")
                }

                val pastedRule = GSON.fromJsonObject<ReplaceRule>(text).getOrNull()
                    ?: throw NoStackTraceException("格式不对")

                launch(Dispatchers.Main) {
                    updateStateFromRule(pastedRule)
                    onSuccess()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    app.toastOnUi(e.localizedMessage ?: "格式不对")
                }
            }
        }
    }

    fun onNameChange(v: String) {
        _uiState.update { it.copy(name = v) }
        activeField = ActiveField.Name
    }

    fun onScopeChange(v: String) {
        _uiState.update { it.copy(scope = v) }
        activeField = ActiveField.Scope
    }

    fun onPatternChange(v: String) {
        _uiState.update { it.copy(pattern = v) }
        activeField = ActiveField.Pattern
    }

    fun onReplacementChange(v: String) {
        _uiState.update { it.copy(replacement = v) }
        activeField = ActiveField.Replacement
    }

    fun onExcludeScopeChange(v: String) {
        _uiState.update { it.copy(excludeScope = v) }
        activeField = ActiveField.Exclude
    }
    fun onGroupChange(v: String) = _uiState.update { it.copy(group = v) }
    fun onRegexChange(v: Boolean) = _uiState.update { it.copy(isRegex = v) }
    fun onScopeTitleChange(v: Boolean) = _uiState.update { it.copy(scopeTitle = v) }
    fun onScopeContentChange(v: Boolean) = _uiState.update { it.copy(scopeContent = v) }
    fun onTimeoutChange(v: String) = _uiState.update { it.copy(timeout = v) }
    fun toggleGroupDialog(show: Boolean) = _uiState.update { it.copy(showGroupDialog = show) }
    fun insertTextAtCursor(text: String) {
        val state = _uiState.value
        when (activeField) {
            ActiveField.Name -> _uiState.update { it.copy(name = it.name + text) }
            ActiveField.Pattern -> _uiState.update { it.copy(pattern = it.pattern + text) }
            ActiveField.Replacement -> _uiState.update { it.copy(replacement = it.replacement + text) }
            ActiveField.Scope -> _uiState.update { it.copy(scope = it.scope + text) }
            ActiveField.Exclude -> _uiState.update { it.copy(excludeScope = it.excludeScope + text) }
            else -> {}
        }
    }

    fun save(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value

            val rule = ReplaceRule().apply {
                id = if (state.id <= 0) System.currentTimeMillis() else state.id
                name = state.name
                group = if (state.group == "默认" || state.group.isBlank()) null else state.group
                pattern = state.pattern
                replacement = state.replacement
                isRegex = state.isRegex
                scopeTitle = state.scopeTitle
                scopeContent = state.scopeContent
                scope = state.scope
                excludeScope = state.excludeScope
                timeoutMillisecond = state.timeout.toLongOrNull() ?: 3000L
            }

            if (state.id <= 0) {
                rule.order = replaceRuleDao.maxOrder + 1
            }

            if (rule.order == Int.MIN_VALUE) {
                rule.order = replaceRuleDao.maxOrder + 1
            }

            replaceRuleDao.insert(rule)

            launch(Dispatchers.Main) {
                onSuccess()
            }
        }
    }


    fun deleteGroups(groups: List<String>) {
        viewModelScope.launch {
            replaceRuleDao.clearGroups(groups)
            toggleGroupDialog(false)
        }
    }
}
