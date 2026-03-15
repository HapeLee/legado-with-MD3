package io.legado.app.ui.rss.source.manage

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.data.repository.UploadRepository
import io.legado.app.help.DefaultData
import io.legado.app.help.source.SourceHelp
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class RssSourceItemUi(
    override val id: String,
    val name: String,
    val group: String?,
    val isEnabled: Boolean,
    val source: RssSource
) : SelectableItem<String>

data class RssSourceUiState(
    override val items: List<RssSourceItemUi> = emptyList(),
    override val selectedIds: Set<String> = emptySet(),
    override val searchKey: String = "",
    val groupFilterName: String? = null,
    val interaction: InteractionState = InteractionState()
) : ListUiState<RssSourceItemUi> {
    override val isSearch: Boolean get() = interaction.isSearchMode
    override val isLoading: Boolean get() = interaction.isUploading
}

class RssSourceViewModel(
    application: Application,
    uploadRepository: UploadRepository
) : BaseRuleViewModel<RssSourceItemUi, RssSource, String, RssSourceUiState>(
    application,
    RssSourceUiState(interaction = InteractionState(isLoading = true)),
    uploadRepository
) {
    companion object {
        const val FILTER_ENABLED = "@enabled"
        const val FILTER_DISABLED = "@disabled"
        const val FILTER_LOGIN = "@login"
        const val FILTER_NO_GROUP = "@noGroup"
        const val PREFIX_GROUP = "group:"
    }

    private val dao = appDb.rssSourceDao

    override val rawDataFlow: Flow<List<RssSource>> = dao.flowAll()

    val groupsFlow: StateFlow<List<String>> = dao.flowGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _groupFilterName = MutableStateFlow<String?>(null)
    val groupFilterName = _groupFilterName.asStateFlow()

    override val uiState: StateFlow<RssSourceUiState> by lazy {
        combine(
            super.uiState,
            _groupFilterName
        ) { baseState, filterName ->
            baseState.copy(groupFilterName = filterName)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = initialState
        )
    }

    override fun setGroupFilter(filter: String?) {
        super.setGroupFilter(filter)
        _groupFilterName.value = when {
            filter == null -> null
            filter == FILTER_ENABLED -> context.getString(R.string.enabled)
            filter == FILTER_DISABLED -> context.getString(R.string.disabled)
            filter == FILTER_LOGIN -> context.getString(R.string.need_login)
            filter == FILTER_NO_GROUP -> context.getString(R.string.no_group)
            filter.startsWith(PREFIX_GROUP) -> filter.substringAfter(PREFIX_GROUP)
            else -> filter
        }
    }

    override fun filterData(
        data: List<RssSource>,
        searchKey: String,
        groupFilter: String
    ): List<RssSource> {
        var filtered = data

        if (groupFilter.isNotEmpty()) {
            filtered = when {
                groupFilter == FILTER_ENABLED -> filtered.filter { it.enabled }
                groupFilter == FILTER_DISABLED -> filtered.filter { !it.enabled }
                groupFilter == FILTER_LOGIN -> filtered.filter { !it.loginUrl.isNullOrEmpty() }
                groupFilter == FILTER_NO_GROUP -> filtered.filter {
                    it.sourceGroup.isNullOrEmpty() || it.sourceGroup?.contains(
                        "未分组"
                    ) == true
                }

                groupFilter.startsWith(PREFIX_GROUP) -> {
                    val groupName = groupFilter.substringAfter(PREFIX_GROUP)
                    filtered.filter { it.sourceGroup?.split(",")?.contains(groupName) == true }
                }

                else -> filtered
            }
        }

        if (searchKey.isNotEmpty()) {
            filtered = filtered.filter {
                it.sourceName.contains(searchKey, ignoreCase = true) ||
                        it.sourceUrl.contains(searchKey, ignoreCase = true) ||
                        it.sourceGroup?.contains(searchKey, ignoreCase = true) == true ||
                        it.sourceComment?.contains(searchKey, ignoreCase = true) == true
            }
        }

        return filtered.sortedBy { it.customOrder }
    }

    override fun composeUiState(
        items: List<RssSourceItemUi>,
        selectedIds: Set<String>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<RssSource>
    ): RssSourceUiState {
        return RssSourceUiState(
            items = items,
            selectedIds = selectedIds,
            searchKey = _searchKey.value,
            interaction = InteractionState(
                isSearchMode = isSearch,
                isUploading = isUploading || (importState is BaseImportUiState.Loading),
                isLoading = false
            )
        )
    }

    override fun RssSource.toUiItem() =
        RssSourceItemUi(sourceUrl, sourceName, sourceGroup, enabled, this)

    override fun ruleItemToEntity(item: RssSourceItemUi): RssSource = item.source

    override suspend fun generateJson(entities: List<RssSource>): String = GSON.toJson(entities)

    override fun parseImportRules(text: String): List<RssSource> {
        return when {
            text.isJsonArray() -> GSON.fromJsonArray<RssSource>(text).getOrThrow()
            text.isJsonObject() -> listOf(GSON.fromJsonObject<RssSource>(text).getOrThrow())
            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: RssSource, oldRule: RssSource): Boolean {
        return !newRule.equal(oldRule)
    }

    override suspend fun findOldRule(newRule: RssSource): RssSource? {
        return withContext(Dispatchers.IO) { dao.getByKey(newRule.sourceUrl) }
    }

    override fun saveImportedRules() {
        val state = _importState.value as? BaseImportUiState.Success<RssSource> ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val rulesToSave = state.items
                .filter { it.isSelected }
                .map { it.data }
            dao.insert(*rulesToSave.toTypedArray())
            withContext(Dispatchers.Main) {
                _importState.value = BaseImportUiState.Idle
            }
        }
    }

    fun saveSortOrder() {
        val currentLocal = _localItems.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val sources = currentLocal.mapIndexed { index, item ->
                item.source.copy(customOrder = index + 1)
            }
            dao.update(*sources.toTypedArray())
            withContext(Dispatchers.Main) {
                _localItems.value = null
            }
        }
    }

    fun topSource(vararg sources: RssSource) {
        viewModelScope.launch(Dispatchers.IO) {
            val minOrder = dao.minOrder - 1
            val updated = sources.sortedBy { it.customOrder }.mapIndexed { index, source ->
                source.copy(customOrder = minOrder - index)
            }
            dao.update(*updated.toTypedArray())
        }
    }

    fun bottomSource(vararg sources: RssSource) {
        viewModelScope.launch(Dispatchers.IO) {
            val maxOrder = dao.maxOrder + 1
            val updated = sources.sortedBy { it.customOrder }.mapIndexed { index, source ->
                source.copy(customOrder = maxOrder + index)
            }
            dao.update(*updated.toTypedArray())
        }
    }

    fun del(vararg rssSource: RssSource) {
        viewModelScope.launch(Dispatchers.IO) {
            SourceHelp.deleteRssSources(rssSource.toList())
        }
    }

    fun delSelectionByIds(ids: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val sources = dao.getRssSources(*ids.toTypedArray())
            SourceHelp.deleteRssSources(sources)
            _selectedIds.update { it - ids }
        }
    }

    fun update(vararg rssSource: RssSource) {
        viewModelScope.launch(Dispatchers.IO) { dao.update(*rssSource) }
    }

    fun upOrder() {
        viewModelScope.launch(Dispatchers.IO) {
            val sources = dao.all
            for ((index: Int, source: RssSource) in sources.withIndex()) {
                source.customOrder = index + 1
            }
            dao.update(*sources.toTypedArray())
        }
    }

    fun enableSelectionByIds(ids: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val sources = dao.getRssSources(*ids.toTypedArray())
            val updated = sources.map { it.copy(enabled = true) }
            dao.update(*updated.toTypedArray())
        }
    }

    fun disableSelectionByIds(ids: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val sources = dao.getRssSources(*ids.toTypedArray())
            val updated = sources.map { it.copy(enabled = false) }
            dao.update(*updated.toTypedArray())
        }
    }

    fun saveToFile(sources: List<RssSource>, success: (file: File) -> Unit) {
        execute {
            val path = "${context.filesDir}/shareRssSource.json"
            FileUtils.delete(path)
            val file = FileUtils.createFileWithReplace(path)
            file.writeText(GSON.toJson(sources))
            file
        }.onSuccess {
            success.invoke(it)
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }

    fun selectionAddToGroups(ids: Set<String>, groups: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sources = dao.getRssSources(*ids.toTypedArray())
            val updated = sources.map { it.copy().addGroup(groups) }
            dao.update(*updated.toTypedArray())
        }
    }

    fun selectionRemoveFromGroups(ids: Set<String>, groups: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sources = dao.getRssSources(*ids.toTypedArray())
            val updated = sources.map { it.copy().removeGroup(groups) }
            dao.update(*updated.toTypedArray())
        }
    }

    fun upGroup(oldGroup: String, newGroup: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sources = dao.getByGroup(oldGroup)
            sources.forEach { source ->
                source.sourceGroup?.split(",")?.toHashSet()?.let {
                    it.remove(oldGroup)
                    if (newGroup.isNotEmpty()) it.add(newGroup)
                    source.sourceGroup = it.joinToString(",")
                }
            }
            dao.update(*sources.toTypedArray())
        }
    }

    fun delGroup(group: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sources = dao.getByGroup(group)
            sources.forEach { source ->
                source.sourceGroup?.split(",")?.toHashSet()?.let {
                    it.remove(group)
                    source.sourceGroup = it.joinToString(",")
                }
            }
            dao.update(*sources.toTypedArray())
        }
    }

    fun importDefault() {
        viewModelScope.launch(Dispatchers.IO) {
            DefaultData.importDefaultRssSources()
        }
    }

    fun checkSelectedInterval(selectedIds: Set<String>, allItems: List<RssSourceItemUi>) {
        if (selectedIds.isEmpty()) return
        val indices = allItems.mapIndexedNotNull { index, item ->
            if (selectedIds.contains(item.id)) index else null
        }
        val min = indices.minOrNull() ?: return
        val max = indices.maxOrNull() ?: return
        val newSelection = allItems.subList(min, max + 1).map { it.id }.toSet()
        _selectedIds.value = newSelection
    }

}