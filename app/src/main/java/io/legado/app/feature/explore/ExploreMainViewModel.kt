package io.legado.app.feature.explore

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.data.repository.SettingsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
data class ExploreMainSourceItem(
    val url: String,
    val name: String,
)

@Stable
data class ExploreMainUiState(
    val ready: Boolean = false,
    val showContent: Boolean = false,
    val sources: ImmutableList<ExploreMainSourceItem> = persistentListOf(),
    val selectedSourceUrl: String? = null,
    val selectedKindKey: String? = null,
    val kinds: ImmutableList<ExploreKind> = persistentListOf(),
    val kindsLoading: Boolean = false,
    val kindsError: String? = null,
)

private data class ExploreMainInputs(
    val sources: ImmutableList<ExploreMainSourceItem>,
    val showContent: Boolean,
    val savedSourceUrl: String,
    val savedKinds: String,
)

sealed interface ExploreMainIntent {
    data class SelectSource(val sourceUrl: String) : ExploreMainIntent
    data class SelectKind(val kindKey: String) : ExploreMainIntent
    data class ShowContent(val enabled: Boolean) : ExploreMainIntent
    data object RetryKinds : ExploreMainIntent
}

class ExploreMainViewModel(
    private val repository: ExploreRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private val _uiState = MutableStateFlow(ExploreMainUiState())
    val uiState: StateFlow<ExploreMainUiState> = _uiState.asStateFlow()
    private var kindsJob: Job? = null
    private var loadedSourceUrl: String? = null
    private var categorySourceUrl: String? = null
    private var selectedSourceUrlOverride: String? = null
    private var kindSelections: Map<String, String> = emptyMap()
    private var kindsGeneration = 0L

    init {
        viewModelScope.launch {
            combine(
                repository.getExploreSources("", ""),
                settingsRepository.getBoolean(PreferKey.exploreMainContent),
                settingsRepository.getString(PreferKey.exploreMainSourceUrl),
                settingsRepository.getString(PreferKey.exploreMainKind),
            ) { sources, showContent, savedSourceUrl, savedKinds ->
                ExploreMainInputs(
                    sources = sources.map {
                        ExploreMainSourceItem(it.bookSourceUrl, it.bookSourceName)
                    }.toImmutableList(),
                    showContent = showContent,
                    savedSourceUrl = savedSourceUrl,
                    savedKinds = savedKinds,
                )
            }.collect { inputs ->
                // 用户选择优先于偏好回读：写入偏好是异步的，回读在写完成前仍会给出旧值，
                // 若跟随回读值就会把已选书源退回旧源并重复清空分类 ViewModelStore。
                val selectedSourceUrl = resolveExploreMainSource(
                    inputs.sources,
                    inputs.savedSourceUrl,
                    selectedSourceUrlOverride,
                )
                // 与书源选择同理：写偏好是异步的，回读可能仍是不含刚选分类的旧快照，
                // 因此本地已记录的选择优先，避免刚选的分类被旧回读抹掉。
                kindSelections = parseExploreKindSelections(inputs.savedKinds) + kindSelections
                updateCategorySource(selectedSourceUrl)
                _uiState.update { current ->
                    current.copy(
                        ready = true,
                        showContent = inputs.showContent,
                        sources = inputs.sources,
                        selectedSourceUrl = selectedSourceUrl,
                        selectedKindKey = selectedSourceUrl?.let { kindSelections[it] },
                    )
                }
                if (inputs.showContent) loadKinds(selectedSourceUrl)
            }
        }
    }

    fun onIntent(intent: ExploreMainIntent) {
        when (intent) {
            is ExploreMainIntent.SelectSource -> {
                if (_uiState.value.sources.none { it.url == intent.sourceUrl }) return
                selectedSourceUrlOverride = intent.sourceUrl
                updateCategorySource(intent.sourceUrl)
                _uiState.update { it.copy(selectedSourceUrl = intent.sourceUrl) }
                loadKinds(intent.sourceUrl)
                viewModelScope.launch {
                    settingsRepository.putString(PreferKey.exploreMainSourceUrl, intent.sourceUrl)
                }
            }

            is ExploreMainIntent.SelectKind -> {
                val sourceUrl = _uiState.value.selectedSourceUrl ?: return
                if (kindSelections[sourceUrl] == intent.kindKey) return
                kindSelections = kindSelections + (sourceUrl to intent.kindKey)
                _uiState.update { it.copy(selectedKindKey = intent.kindKey) }
                viewModelScope.launch {
                    settingsRepository.putString(
                        PreferKey.exploreMainKind,
                        encodeExploreKindSelections(kindSelections),
                    )
                }
            }

            is ExploreMainIntent.ShowContent -> {
                _uiState.update { it.copy(showContent = intent.enabled) }
                if (intent.enabled) loadKinds(_uiState.value.selectedSourceUrl)
                viewModelScope.launch {
                    settingsRepository.putBoolean(PreferKey.exploreMainContent, intent.enabled)
                }
            }

            ExploreMainIntent.RetryKinds -> loadKinds(
                _uiState.value.selectedSourceUrl,
                force = true
            )
        }
    }

    private fun updateCategorySource(sourceUrl: String?) {
        if (categorySourceUrl == sourceUrl) return
        categorySourceUrl = sourceUrl
        viewModelStore.clear()
    }

    override fun onCleared() {
        viewModelStore.clear()
        super.onCleared()
    }

    private fun loadKinds(sourceUrl: String?, force: Boolean = false) {
        if (!force && loadedSourceUrl == sourceUrl) return
        loadedSourceUrl = sourceUrl
        val generation = ++kindsGeneration
        kindsJob?.cancel()
        _uiState.update {
            it.copy(kinds = persistentListOf(), kindsLoading = sourceUrl != null, kindsError = null)
        }
        if (sourceUrl == null) return
        kindsJob = viewModelScope.launch {
            try {
                val kinds = repository.getSourceExploreKinds(sourceUrl)
                if (generation == kindsGeneration) {
                    _uiState.update {
                        it.copy(
                            kinds = kinds.toImmutableList(),
                            kindsLoading = false
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == kindsGeneration) {
                    _uiState.update {
                        it.copy(
                            kindsLoading = false,
                            kindsError = error.localizedMessage ?: error.toString()
                        )
                    }
                }
            }
        }
    }
}

internal fun resolveExploreMainSource(
    sources: List<ExploreMainSourceItem>,
    savedSourceUrl: String,
    overrideSourceUrl: String? = null,
): String? = overrideSourceUrl?.takeIf { url -> sources.any { it.url == url } }
    ?: sources.firstOrNull { it.url == savedSourceUrl }
        ?.url ?: sources.firstOrNull()?.url

internal fun loadableExploreMainKinds(kinds: List<ExploreKind>): List<ExploreKind> =
    kinds.filter { it.type == ExploreKind.Type.url && !it.url.isNullOrBlank() }
        .distinctBy { it.title to it.url }

/** 分类在偏好与 ViewModel 之间的稳定标识：标题可能重复，必须带上探索地址。 */
internal fun exploreKindKey(kind: ExploreKind): String =
    "${kind.title.length}:${kind.title}:${kind.url}"

private const val KIND_ENTRY_SEPARATOR = '\u0001'
private const val KIND_VALUE_SEPARATOR = '\u0000'

/**
 * 书源与「上次浏览的分类」的映射。使用控制字符分隔，书源地址与规则标题都不会包含它们，
 * 因此不需要引入 JSON 依赖即可稳定往返。
 */
internal fun encodeExploreKindSelections(selections: Map<String, String>): String =
    selections.entries.joinToString(KIND_ENTRY_SEPARATOR.toString()) { (sourceUrl, kindKey) ->
        "$sourceUrl$KIND_VALUE_SEPARATOR$kindKey"
    }

internal fun parseExploreKindSelections(raw: String): Map<String, String> =
    raw.split(KIND_ENTRY_SEPARATOR)
        .mapNotNull { entry ->
            val index = entry.indexOf(KIND_VALUE_SEPARATOR)
            if (index <= 0 || index == entry.lastIndex) {
                null
            } else {
                entry.substring(0, index) to entry.substring(index + 1)
            }
        }
        .toMap()
