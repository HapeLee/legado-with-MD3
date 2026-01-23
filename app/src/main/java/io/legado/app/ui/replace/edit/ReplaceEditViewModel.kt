package io.legado.app.ui.replace.edit

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
    val name: TextFieldValue = TextFieldValue(""),
    val group: String = "默认",
    val pattern: TextFieldValue = TextFieldValue(""),
    val replacement: TextFieldValue = TextFieldValue(""),
    val isRegex: Boolean = false,
    val scope: TextFieldValue = TextFieldValue(""),
    val scopeTitle: Boolean = false,
    val scopeContent: Boolean = false,
    val excludeScope: TextFieldValue = TextFieldValue(""),
    val timeout: String = "3000",
    val allGroups: List<String> = emptyList(),
    val showGroupDialog: Boolean = false
)

class ReplaceEditViewModel(
    private val app: Application,
    private val replaceRuleDao: ReplaceRuleDao,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ReplaceEditRoute>()

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
                        id = System.currentTimeMillis(),
                        name = TextFieldValue(""),
                        pattern = TextFieldValue(route.pattern ?: ""),
                        isRegex = route.isRegex,
                        scope = TextFieldValue(route.scope ?: ""),
                        scopeTitle = route.isScopeTitle,
                        scopeContent = route.isScopeContent,
                        excludeScope = TextFieldValue(""),
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
                name = TextFieldValue(rule.name),
                group = rule.group ?: "默认",
                pattern = TextFieldValue(rule.pattern),
                replacement = TextFieldValue(rule.replacement),
                isRegex = rule.isRegex,
                scopeTitle = rule.scopeTitle,
                scopeContent = rule.scopeContent,
                scope = TextFieldValue(rule.scope ?: ""),
                excludeScope = TextFieldValue(rule.excludeScope ?: ""),
                timeout = rule.timeoutMillisecond.toString()
            )
        }
    }

    private fun getReplaceRuleFromState(): ReplaceRule {
        val state = _uiState.value
        val rule = ReplaceRule().apply {
            id = state.id
            name = state.name.text
            group = if (state.group == "默认" || state.group.isBlank()) null else state.group
            pattern = state.pattern.text
            replacement = state.replacement.text
            isRegex = state.isRegex
            scopeTitle = state.scopeTitle
            scopeContent = state.scopeContent
            scope = state.scope.text
            excludeScope = state.excludeScope.text
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

    fun onNameChange(v: TextFieldValue) {
        _uiState.update { it.copy(name = v) }
        activeField = ActiveField.Name
    }
    fun onScopeChange(v: TextFieldValue) {
        _uiState.update { it.copy(scope = v) }
        activeField = ActiveField.Scope
    }
    fun onPatternChange(v: TextFieldValue) {
        _uiState.update { it.copy(pattern = v) }
        activeField = ActiveField.Pattern
    }
    fun onReplacementChange(v: TextFieldValue) {
        _uiState.update { it.copy(replacement = v) }
        activeField = ActiveField.Replacement
    }
    fun onExcludeScopeChange(v: TextFieldValue) {
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
            ActiveField.Name -> {
                val newTfv = insertIntoTextFieldValue(state.name, text)
                _uiState.update { it.copy(name = newTfv) }
            }
            ActiveField.Pattern -> {
                val newTfv = insertIntoTextFieldValue(state.pattern, text)
                _uiState.update { it.copy(pattern = newTfv) }
            }
            ActiveField.Replacement -> {
                val newTfv = insertIntoTextFieldValue(state.replacement, text)
                _uiState.update { it.copy(replacement = newTfv) }
            }
            ActiveField.Scope -> {
                val newTfv = insertIntoTextFieldValue(state.scope, text)
                _uiState.update { it.copy(scope = newTfv) }
            }
            ActiveField.Exclude -> {
                val newTfv = insertIntoTextFieldValue(state.excludeScope, text)
                _uiState.update { it.copy(excludeScope = newTfv) }
            }
            else -> {}
        }
    }

    private fun insertIntoTextFieldValue(current: TextFieldValue, insert: String): TextFieldValue {
        val start = current.selection.start
        val end = current.selection.end
        val newText = current.text.replaceRange(start, end, insert)
        val newCursor = start + insert.length
        return TextFieldValue(
            text = newText,
            selection = TextRange(newCursor)
        )
    }

    fun save(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value

            val rule = ReplaceRule().apply {
                id = state.id
                name = state.name.text
                group = if (state.group == "默认" || state.group.isBlank()) null else state.group
                pattern = state.pattern.text
                replacement = state.replacement.text
                isRegex = state.isRegex
                scopeTitle = state.scopeTitle
                scopeContent = state.scopeContent
                scope = state.scope.text
                excludeScope = state.excludeScope.text
                timeoutMillisecond = state.timeout.toLongOrNull() ?: 3000L
            }

            if (rule.id <= 0) {
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