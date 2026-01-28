package io.legado.app.base

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.AppConst
import io.legado.app.data.repository.UploadRepository
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.ImportItemWrapper
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import io.legado.app.ui.widget.components.rules.RuleActionState
import io.legado.app.ui.widget.components.rules.SelectableItem
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isUri
import io.legado.app.utils.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 统一事件接口
sealed interface BaseRuleEvent {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val url: String? = null
    ) : BaseRuleEvent
}

abstract class BaseRuleViewModel<T : SelectableItem<ID>, Entity, ID, S : RuleActionState<T>>(
    application: Application,
    initialState: S,
    private val uploadRepository: UploadRepository? = null // 设为可空，提高灵活性
) : BaseViewModel(application) {

    protected val _searchKey = MutableStateFlow("")
    protected val _selectedIds = MutableStateFlow<Set<ID>>(emptySet())
    protected val _isSearchMode = MutableStateFlow(false)
    protected val _isUploading = MutableStateFlow(false)
    protected val _localItems = MutableStateFlow<List<T>?>(null)
    protected val _importState = MutableStateFlow<BaseImportUiState<Entity>>(BaseImportUiState.Idle)
    val importState = _importState.asStateFlow()
    protected val _eventChannel = Channel<BaseRuleEvent>()
    val events = _eventChannel.receiveAsFlow()

    abstract val rawDataFlow: Flow<List<Entity>>

    open fun filterData(data: List<Entity>, key: String): List<Entity> = data

    @OptIn(FlowPreview::class)
    private val itemsFlow: Flow<List<T>> by lazy {
        combine(
            rawDataFlow,
            _searchKey.debounce(300L),
            _localItems
        ) { data, key, local ->
            if (local != null && key.isEmpty()) {
                local
            } else {
                filterData(data, key).map { it.toUiItem() }
            }
        }
    }

    val uiState: StateFlow<S> by lazy {
        combine(
            itemsFlow,
            _selectedIds,
            _isSearchMode,
            _isUploading,
            _importState
        ) { items, selectedIds, isSearch, isUploading, importState ->
            composeUiState(items, selectedIds, isSearch, isUploading, importState)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = initialState
        )
    }

    abstract fun composeUiState(
        items: List<T>,
        selectedIds: Set<ID>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<Entity>
    ): S

    // 抽象方法：将实体转换为UI Item
    abstract fun Entity.toUiItem(): T

    // 抽象方法：生成用于导出/上传的JSON
    abstract suspend fun generateJson(entities: List<Entity>): String

    // 抽象方法：解析导入的文本为实体列表
    abstract fun parseImportRules(text: String): List<Entity>

    // 抽象方法：判断新旧实体是否有变化 (用于导入去重)
    abstract fun hasChanged(newRule: Entity, oldRule: Entity): Boolean

    // 抽象方法：根据ID查找旧实体 (用于导入对比)
    abstract suspend fun findOldRule(newRule: Entity): Entity?

    // 抽象方法：保存导入的规则
    abstract fun saveImportedRules()

    fun moveItemInList(from: Int, to: Int) {
        val currentList = uiState.value.items.toMutableList()
        if (from !in currentList.indices || to !in currentList.indices) return
        val item = currentList.removeAt(from)
        currentList.add(to, item)
        _localItems.value = currentList
    }

    fun setSearchKey(key: String?) {
        _localItems.value = null
        _searchKey.value = key ?: ""
    }

    fun setSearchMode(active: Boolean) {
        _isSearchMode.value = active
        if (!active) setSearchKey("")
    }

    fun toggleSelection(id: ID) {
        _selectedIds.update { if (it.contains(id)) it - id else it + id }
    }

    fun setSelection(ids: Set<ID>) {
        _selectedIds.value = ids
    }

    fun exportToUri(uri: Uri, rules: List<T>, selectedIds: Set<ID>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rulesToExport = rules
                    .filter { selectedIds.contains(it.id) }
                    .map { ruleItemToEntity(it) }

                if (rulesToExport.isEmpty()) {
                    _eventChannel.send(BaseRuleEvent.ShowSnackbar("没有选中的规则可导出"))
                    return@launch
                }

                val json = generateJson(rulesToExport)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(json)
                        writer.flush()
                    }
                }
                _eventChannel.send(BaseRuleEvent.ShowSnackbar("导出成功"))
            } catch (e: Exception) {
                e.printStackTrace()
                _eventChannel.send(BaseRuleEvent.ShowSnackbar("导出失败: ${e.localizedMessage}"))
            }
        }
    }

    abstract fun ruleItemToEntity(item: T): Entity

    fun uploadSelectedRules(selectedIds: Set<ID>, rules: List<T>) {
        val repo = uploadRepository ?: return
        viewModelScope.launch {
            if (selectedIds.isEmpty()) return@launch

            _isUploading.value = true
            try {
                val json = withContext(Dispatchers.Default) {
                    val rulesToExport = rules
                        .filter { selectedIds.contains(it.id) }
                        .map { ruleItemToEntity(it) }
                    generateJson(rulesToExport)
                }

                val url = repo.upload(
                    fileName = "export_rules.json",
                    file = json,
                    contentType = "application/json"
                )

                _eventChannel.send(
                    BaseRuleEvent.ShowSnackbar(
                        message = "上传成功: $url",
                        actionLabel = "复制链接",
                        url = url
                    )
                )
            } catch (e: Exception) {
                _eventChannel.send(
                    BaseRuleEvent.ShowSnackbar(
                        message = "上传失败: ${e.localizedMessage}"
                    )
                )
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun importSource(text: String) {
        _importState.value = BaseImportUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val jsonText = resolveSource(text.trim())
                val rules = parseImportRules(jsonText)
                val wrappers = rules.map { newRule ->
                    val oldRule = findOldRule(newRule)

                    val status = when {
                        oldRule == null -> ImportStatus.New
                        hasChanged(newRule, oldRule) -> ImportStatus.Update
                        else -> ImportStatus.Existing
                    }

                    ImportItemWrapper(
                        data = newRule,
                        oldData = oldRule,
                        status = status,
                        isSelected = status != ImportStatus.Existing
                    )
                }

                _importState.value = BaseImportUiState.Success(
                    source = text,
                    items = wrappers
                )
            }.onFailure {
                it.printStackTrace()
                _importState.value = BaseImportUiState.Error(it.localizedMessage ?: "Unknown Error")
            }
        }
    }

    protected suspend fun resolveSource(text: String): String {
        return when {
            text.isAbsUrl() -> {
                okHttpClient.newCallResponseBody {
                    if (text.endsWith("#requestWithoutUA")) {
                        url(text.substringBeforeLast("#requestWithoutUA"))
                        header(AppConst.UA_NAME, "null")
                    } else {
                        url(text)
                    }
                }.decompressed().text("utf-8")
            }

            text.isUri() -> text.toUri().readText(context)
            else -> text
        }
    }

    fun cancelImport() {
        _importState.value = BaseImportUiState.Idle
    }

    fun toggleImportSelection(index: Int) {
        val currentState = _importState.value as? BaseImportUiState.Success<Entity> ?: return
        val newItems = currentState.items.toMutableList()
        val item = newItems[index]
        newItems[index] = item.copy(isSelected = !item.isSelected)
        _importState.value = currentState.copy(items = newItems)
    }

    fun toggleImportAll(isSelected: Boolean) {
        val currentState = _importState.value as? BaseImportUiState.Success<Entity> ?: return
        val newItems = currentState.items.map { it.copy(isSelected = isSelected) }
        _importState.value = currentState.copy(items = newItems)
    }

}