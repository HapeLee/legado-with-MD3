package io.legado.app.feature.rss.source

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.RssSource
import io.legado.app.data.repository.RssRepository
import io.legado.app.data.repository.RssSourceImporter
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.splitNotBlank
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class ImportPhase { Idle, Loading, Ready, Saving, Complete, Error }

@Stable
internal data class RssSourceImportItem(
    val name: String,
    val url: String,
    val status: ImportStatus,
    val selected: Boolean,
)

@Stable
internal data class RssSourceImportEditor(
    val index: Int,
    val json: String,
    val invalid: Boolean = false,
)

@Stable
internal data class RssSourceImportUiState(
    val phase: ImportPhase = ImportPhase.Idle,
    val items: ImmutableList<RssSourceImportItem> = persistentListOf(),
    val groups: ImmutableList<String> = persistentListOf(),
    val keepName: Boolean = false,
    val keepGroup: Boolean = false,
    val keepEnable: Boolean = false,
    val customGroup: String = "",
    val appendGroup: Boolean = false,
    val editor: RssSourceImportEditor? = null,
    val error: String? = null,
)

internal sealed interface RssSourceImportIntent {
    data class Load(val source: String) : RssSourceImportIntent
    data object Dismiss : RssSourceImportIntent
    data class Toggle(val index: Int) : RssSourceImportIntent
    data class SelectAll(val selected: Boolean) : RssSourceImportIntent
    data class KeepName(val keep: Boolean) : RssSourceImportIntent
    data class KeepGroup(val keep: Boolean) : RssSourceImportIntent
    data class KeepEnable(val keep: Boolean) : RssSourceImportIntent
    data class Group(val name: String, val append: Boolean) : RssSourceImportIntent
    data class Edit(val index: Int) : RssSourceImportIntent
    data class EditText(val json: String) : RssSourceImportIntent
    data object SaveEdit : RssSourceImportIntent
    data object CancelEdit : RssSourceImportIntent
    data object AcknowledgeError : RssSourceImportIntent
    data object Confirm : RssSourceImportIntent
}

internal class RssSourceImportViewModel(
    private val application: Application,
    private val repository: RssRepository,
    private val importer: RssSourceImporter,
    private val settings: OtherSettingsGateway,
) : ViewModel() {
    private val _state = MutableStateFlow(RssSourceImportUiState())
    val state = _state.asStateFlow()

    private var loadJob: Job? = null
    private var input: String? = null
    private var sources = mutableListOf<RssSource>()
    private var originals = emptyList<RssSource?>()

    init {
        viewModelScope.launch {
            settings.settings.collect { options ->
                _state.update { it.withSettings(options) }
            }
        }
    }

    fun onIntent(intent: RssSourceImportIntent) {
        when (intent) {
            is RssSourceImportIntent.Load -> load(intent.source)
            RssSourceImportIntent.Dismiss -> dismiss()
            is RssSourceImportIntent.Toggle -> _state.update { state ->
                state.copy(items = state.items.mapIndexed { index, item ->
                    if (index == intent.index) item.copy(selected = !item.selected) else item
                }.toImmutableList())
            }
            is RssSourceImportIntent.SelectAll -> _state.update { state ->
                state.copy(items = state.items.map { it.copy(selected = intent.selected) }.toImmutableList())
            }
            is RssSourceImportIntent.KeepName -> updateSettings { it.copy(importKeepName = intent.keep) }
            is RssSourceImportIntent.KeepGroup -> updateSettings { it.copy(importKeepGroup = intent.keep) }
            is RssSourceImportIntent.KeepEnable -> updateSettings { it.copy(importKeepEnable = intent.keep) }
            is RssSourceImportIntent.Group -> _state.update {
                it.copy(customGroup = intent.name.trim(), appendGroup = intent.append)
            }
            is RssSourceImportIntent.Edit -> sources.getOrNull(intent.index)?.let { source ->
                _state.update { it.copy(editor = RssSourceImportEditor(intent.index, GSON.toJson(source))) }
            }
            is RssSourceImportIntent.EditText -> _state.update { state ->
                state.copy(editor = state.editor?.copy(json = intent.json, invalid = false))
            }
            RssSourceImportIntent.SaveEdit -> saveEdit()
            RssSourceImportIntent.CancelEdit -> _state.update { it.copy(editor = null) }
            RssSourceImportIntent.AcknowledgeError -> _state.update { it.copy(error = null) }
            RssSourceImportIntent.Confirm -> confirm()
        }
    }

    private fun load(source: String) {
        if (source == input || _state.value.phase == ImportPhase.Saving) return
        dismiss()
        input = source
        _state.value = RssSourceImportUiState(phase = ImportPhase.Loading)
        loadJob = viewModelScope.launch {
            try {
                val incoming = importer.read(source)
                if (incoming.isEmpty()) {
                    _state.value = RssSourceImportUiState(
                        phase = ImportPhase.Error,
                        error = application.getString(R.string.wrong_format),
                    )
                    return@launch
                }
                val localSources = incoming.map { repository.getByKey(it.sourceUrl) }
                val groups = repository.flowGroups().first()
                sources = incoming.toMutableList()
                originals = localSources
                _state.value = RssSourceImportUiState(
                    phase = ImportPhase.Ready,
                    items = incoming.mapIndexed { index, item ->
                        val status = item.importStatus(localSources[index])
                        item.toUi(status, status != ImportStatus.Existing)
                    }.toImmutableList(),
                    groups = groups.toImmutableList(),
                ).withSettings(settings.currentSettings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = RssSourceImportUiState(phase = ImportPhase.Error, error = errorMessage(e))
            }
        }
    }

    private fun dismiss() {
        if (_state.value.phase == ImportPhase.Saving) return
        loadJob?.cancel()
        input = null
        sources.clear()
        originals = emptyList()
        _state.value = RssSourceImportUiState()
    }

    private fun saveEdit() {
        val state = _state.value
        val editor = state.editor ?: return
        val updated = GSON.fromJsonObject<RssSource>(editor.json).getOrNull()
        if (updated == null || updated.sourceUrl.isNullOrBlank()) {
            _state.value = state.copy(editor = editor.copy(invalid = true))
            return
        }
        sources[editor.index] = updated
        _state.value = state.copy(
            editor = null,
            items = state.items.mapIndexed { index, item ->
                if (index == editor.index) updated.toUi(updated.importStatus(originals[index]), item.selected)
                else item
            }.toImmutableList(),
        )
    }

    private fun confirm() {
        val state = _state.value.withSettings(settings.currentSettings)
        if (state.phase != ImportPhase.Ready || state.items.none { it.selected }) return
        val selected = sources.mapIndexedNotNull { index, source ->
            source.takeIf { state.items[index].selected }?.withImportOptions(originals[index], state)
        }
        _state.value = state.copy(phase = ImportPhase.Saving)
        viewModelScope.launch {
            try {
                importer.save(selected)
                _state.value = RssSourceImportUiState(phase = ImportPhase.Complete)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = ImportPhase.Ready, error = errorMessage(e)) }
            }
        }
    }

    private fun updateSettings(transform: (OtherSettings) -> OtherSettings) {
        viewModelScope.launch {
            try {
                settings.update(transform)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = errorMessage(e)
                _state.update { if (it.phase == ImportPhase.Ready) it.copy(error = message) else it }
            }
        }
    }

    private fun errorMessage(error: Exception): String {
        AppLog.put("ImportError:${error.localizedMessage}", error)
        return error.localizedMessage ?: application.getString(R.string.unknown_error)
    }
}

private fun RssSourceImportUiState.withSettings(settings: OtherSettings) = copy(
    keepName = settings.importKeepName,
    keepGroup = settings.importKeepGroup,
    keepEnable = settings.importKeepEnable,
)

private fun RssSource.importStatus(original: RssSource?): ImportStatus = when {
    original == null -> ImportStatus.New
    lastUpdateTime > original.lastUpdateTime -> ImportStatus.Update
    else -> ImportStatus.Existing
}

private fun RssSource.toUi(status: ImportStatus, selected: Boolean) =
    RssSourceImportItem(sourceName.orEmpty(), sourceUrl.orEmpty(), status, selected)

private fun RssSource.withImportOptions(
    original: RssSource?,
    state: RssSourceImportUiState,
): RssSource = copy().apply {
    if (original != null) {
        if (state.keepName) sourceName = original.sourceName
        if (state.keepGroup) sourceGroup = original.sourceGroup
        if (state.keepEnable) enabled = original.enabled
        customOrder = original.customOrder
    }
    if (state.customGroup.isNotEmpty()) {
        sourceGroup = if (state.appendGroup) {
            val groups = sourceGroup.orEmpty().splitNotBlank(AppPattern.splitGroupRegex)
            (groups + state.customGroup).distinct().joinToString(",")
        } else {
            state.customGroup
        }
    }
}
