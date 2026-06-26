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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReplaceEditViewModel(
    private val app: Application,
    private val replaceRuleDao: ReplaceRuleDao,
    private val route: ReplaceEditRoute
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReplaceEditUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ReplaceEditEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        initData()
        observeGroups()
    }

    fun onIntent(intent: ReplaceEditIntent) {
        when (intent) {
            is ReplaceEditIntent.OnNameChange -> onNameChange(intent.value)
            is ReplaceEditIntent.OnPatternChange -> onPatternChange(intent.value)
            is ReplaceEditIntent.OnReplacementChange -> onReplacementChange(intent.value)
            is ReplaceEditIntent.OnScopeChange -> onScopeChange(intent.value)
            is ReplaceEditIntent.OnExcludeScopeChange -> onExcludeScopeChange(intent.value)
            is ReplaceEditIntent.OnGroupChange -> onGroupChange(intent.value)
            is ReplaceEditIntent.OnRegexChange -> onRegexChange(intent.value)
            is ReplaceEditIntent.OnScopeTitleChange -> onScopeTitleChange(intent.value)
            is ReplaceEditIntent.OnScopeContentChange -> onScopeContentChange(intent.value)
            is ReplaceEditIntent.OnTimeoutChange -> onTimeoutChange(intent.value)
            is ReplaceEditIntent.SetActiveField -> setActiveField(intent.field)
            is ReplaceEditIntent.InsertTextAtCursor -> insertTextAtCursor(intent.text)
            is ReplaceEditIntent.ToggleGroupDialog -> toggleGroupDialog(intent.show)
            is ReplaceEditIntent.DeleteGroups -> deleteGroups(intent.groups)
            ReplaceEditIntent.CopyRule -> copyRule()
            ReplaceEditIntent.PasteRule -> pasteRule()
            ReplaceEditIntent.Save -> save()
        }
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

    private fun copyRule() {
        viewModelScope.launch(Dispatchers.Main) {
            val ruleToCopy = getReplaceRuleFromState()
            val json = GSON.toJson(ruleToCopy)
            app.sendToClip(json)
            app.toastOnUi("规则已复制到剪贴板")
        }
    }

    private fun pasteRule() {
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
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    app.toastOnUi(e.localizedMessage ?: "格式不对")
                }
            }
        }
    }

    private fun onNameChange(v: String) {
        _uiState.update { it.copy(name = v) }
        setActiveField(ActiveField.Name)
    }

    private fun onScopeChange(v: String) {
        _uiState.update { it.copy(scope = v) }
        setActiveField(ActiveField.Scope)
    }

    private fun onPatternChange(v: String) {
        _uiState.update { it.copy(pattern = v) }
        setActiveField(ActiveField.Pattern)
    }

    private fun onReplacementChange(v: String) {
        _uiState.update { it.copy(replacement = v) }
        setActiveField(ActiveField.Replacement)
    }

    private fun onExcludeScopeChange(v: String) {
        _uiState.update { it.copy(excludeScope = v) }
        setActiveField(ActiveField.Exclude)
    }

    private fun onGroupChange(v: String) = _uiState.update { it.copy(group = v) }
    private fun onRegexChange(v: Boolean) = _uiState.update { it.copy(isRegex = v) }
    private fun onScopeTitleChange(v: Boolean) = _uiState.update { it.copy(scopeTitle = v) }
    private fun onScopeContentChange(v: Boolean) = _uiState.update { it.copy(scopeContent = v) }
    private fun onTimeoutChange(v: String) = _uiState.update { it.copy(timeout = v) }
    private fun toggleGroupDialog(show: Boolean) = _uiState.update { it.copy(showGroupDialog = show) }

    private fun setActiveField(field: ActiveField) {
        _uiState.update { it.copy(activeField = field) }
    }

    private fun insertTextAtCursor(text: String) {
        when (_uiState.value.activeField) {
            ActiveField.Name -> _uiState.update { it.copy(name = it.name + text) }
            ActiveField.Pattern -> _uiState.update { it.copy(pattern = it.pattern + text) }
            ActiveField.Replacement -> _uiState.update { it.copy(replacement = it.replacement + text) }
            ActiveField.Scope -> _uiState.update { it.copy(scope = it.scope + text) }
            ActiveField.Exclude -> _uiState.update { it.copy(excludeScope = it.excludeScope + text) }
            else -> {}
        }
    }

    private fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value

            val existingRule = if (state.id > 0) replaceRuleDao.findById(state.id) else null
            val rule = (existingRule ?: ReplaceRule()).apply {
                id = existingRule?.id ?: if (state.id <= 0) System.currentTimeMillis() else state.id
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

            if (existingRule == null && rule.order == Int.MIN_VALUE) {
                rule.order = replaceRuleDao.maxOrder + 1
            }

            replaceRuleDao.insert(rule)

            _effects.tryEmit(ReplaceEditEffect.NavigateBack)
        }
    }


    private fun deleteGroups(groups: List<String>) {
        viewModelScope.launch {
            replaceRuleDao.clearGroups(groups)
            toggleGroupDialog(false)
        }
    }
}
