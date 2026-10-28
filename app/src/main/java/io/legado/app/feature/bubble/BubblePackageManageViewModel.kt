package io.legado.app.feature.bubble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.help.config.BubblePackageManager
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BubblePackageManageViewModel(
    private val settingsGateway: ReadSettingsGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BubblePackageManageUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BubblePackageManageEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        refresh()
    }

    fun onIntent(intent: BubblePackageManageIntent) {
        when (intent) {
            BubblePackageManageIntent.Refresh -> refresh()
            is BubblePackageManageIntent.Apply -> applyPackage(intent.dirName)
            is BubblePackageManageIntent.Edit -> editPackage(intent.dirName)
            BubblePackageManageIntent.Create -> createDraft()
            is BubblePackageManageIntent.Duplicate -> duplicatePackage(intent.dirName)
            is BubblePackageManageIntent.RequestDelete -> requestDelete(intent.dirName)
            BubblePackageManageIntent.DismissDelete -> _uiState.update { it.copy(pendingDelete = null) }
            BubblePackageManageIntent.ConfirmDelete -> confirmDelete()
            is BubblePackageManageIntent.Export -> exportPackage(intent.dirName)
            BubblePackageManageIntent.RequestImport ->
                _effects.tryEmit(BubblePackageManageEffect.PickImportFile)
            is BubblePackageManageIntent.DraftNameChanged -> updateDraft { it.copy(name = intent.value) }
            is BubblePackageManageIntent.DraftTemplateChanged ->
                updateDraft { it.copy(svgTemplate = intent.value) }
            is BubblePackageManageIntent.DraftScaleChanged ->
                updateDraft { it.copy(sizeScale = intent.value) }
            is BubblePackageManageIntent.DraftColorChanged -> updateDraft { draft ->
                when (intent.slot) {
                    BubbleColorSlot.DAY_NORMAL -> draft.copy(dayNormalColor = intent.value)
                    BubbleColorSlot.DAY_EMPHASIS -> draft.copy(dayEmphasisColor = intent.value)
                    BubbleColorSlot.NIGHT_NORMAL -> draft.copy(nightNormalColor = intent.value)
                    BubbleColorSlot.NIGHT_EMPHASIS -> draft.copy(nightEmphasisColor = intent.value)
                }
            }
            BubblePackageManageIntent.SaveDraft -> saveDraft()
            BubblePackageManageIntent.DismissDraft -> _uiState.update { it.copy(draft = null) }
        }
    }

    fun importZip(file: File) {
        viewModelScope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) { BubblePackageManager.importZip(file) }
                R.string.bubble_package_imported
            }.getOrElse { R.string.bubble_package_import_failed }
            _effects.tryEmit(BubblePackageManageEffect.Message(message))
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) {
                runCatching { BubblePackageManager.loadEntries() }.getOrDefault(emptyList())
            }
            _uiState.update {
                it.copy(
                    packages = entries.map { entry ->
                        BubblePackageItem(
                            dirName = entry.dirName,
                            name = entry.config.name,
                            builtin = entry.source == BubblePackageManager.Source.BUILTIN,
                            sizeScale = entry.config.sizeScale,
                            resourceCount = entry.config.resources.size,
                        )
                    }.toPersistentList(),
                    activeDirName = BubblePackageManager.activeDirName(),
                    loading = false,
                )
            }
        }
    }

    private fun applyPackage(dirName: String) {
        viewModelScope.launch {
            runCatching {
                settingsGateway.update {
                    it.copy(reviewBubbleStyle = "package", reviewBubblePackage = dirName)
                }
                BubblePackageManager.invalidateCurrentEntry()
            }.onSuccess {
                _uiState.update { it.copy(activeDirName = dirName) }
                _effects.tryEmit(BubblePackageManageEffect.Message(R.string.bubble_package_applied))
            }.onFailure {
                _effects.tryEmit(BubblePackageManageEffect.Message(R.string.bubble_package_save_failed))
            }
        }
    }

    private fun editPackage(dirName: String) {
        viewModelScope.launch {
            val entry = withContext(Dispatchers.IO) { BubblePackageManager.entryOf(dirName) }
            if (entry == null) {
                _effects.tryEmit(BubblePackageManageEffect.Message(R.string.bubble_package_load_failed))
                return@launch
            }
            val editable = entry.source != BubblePackageManager.Source.BUILTIN
            _uiState.update {
                it.copy(
                    draft = BubblePackageDraft(
                        dirName = entry.dirName.takeIf { editable },
                        name = if (editable) entry.config.name else "${entry.config.name} 副本",
                        svgTemplate = entry.config.svgTemplate,
                        sizeScale = entry.config.sizeScale,
                        dayNormalColor = entry.config.dayNormalColor.orEmpty(),
                        dayEmphasisColor = entry.config.dayEmphasisColor.orEmpty(),
                        nightNormalColor = entry.config.nightNormalColor.orEmpty(),
                        nightEmphasisColor = entry.config.nightEmphasisColor.orEmpty(),
                        resources = entry.config.resources,
                        resourceRoot = entry.localDir,
                    )
                )
            }
        }
    }

    private fun createDraft() {
        val builtin = BubblePackageManager.builtinConfig()
        _uiState.update {
            it.copy(
                draft = BubblePackageDraft(
                    dirName = null,
                    name = "",
                    svgTemplate = builtin.svgTemplate,
                    sizeScale = builtin.sizeScale,
                    dayNormalColor = builtin.dayNormalColor.orEmpty(),
                    dayEmphasisColor = builtin.dayEmphasisColor.orEmpty(),
                    nightNormalColor = builtin.nightNormalColor.orEmpty(),
                    nightEmphasisColor = builtin.nightEmphasisColor.orEmpty(),
                )
            )
        }
    }

    private fun duplicatePackage(dirName: String) {
        editPackage(dirName)
    }

    private fun requestDelete(dirName: String) {
        _uiState.update { state ->
            state.copy(pendingDelete = state.packages.firstOrNull { it.dirName == dirName })
        }
    }

    private fun confirmDelete() {
        val item = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) {
                    val entry = BubblePackageManager.entryOf(item.dirName)
                        ?: error("bubble package not found")
                    BubblePackageManager.deleteLocal(entry)
                }
                if (BubblePackageManager.activeDirName() == item.dirName) {
                    settingsGateway.update {
                        it.copy(
                            reviewBubbleStyle = "source",
                            reviewBubblePackage = BubblePackageManager.BUILTIN_DIR_NAME,
                        )
                    }
                }
                R.string.bubble_package_deleted
            }.getOrElse { R.string.bubble_package_delete_failed }
            _uiState.update { it.copy(pendingDelete = null) }
            _effects.tryEmit(BubblePackageManageEffect.Message(message))
            refresh()
        }
    }

    private fun exportPackage(dirName: String) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val entry = BubblePackageManager.entryOf(dirName)
                        ?: error("bubble package not found")
                    BubblePackageManager.exportZip(entry)
                }
            }
            result.onSuccess { file ->
                _effects.tryEmit(BubblePackageManageEffect.ShareFile(file))
            }.onFailure {
                _effects.tryEmit(
                    BubblePackageManageEffect.Message(R.string.bubble_package_export_failed)
                )
            }
        }
    }

    private fun saveDraft() {
        val draft = _uiState.value.draft ?: return
        viewModelScope.launch {
            val message = runCatching {
                val oldEntry = draft.dirName?.let { dirName ->
                    withContext(Dispatchers.IO) { BubblePackageManager.entryOf(dirName) }
                }
                withContext(Dispatchers.IO) {
                    BubblePackageManager.addOrUpdate(
                        config = BubblePackageManager.Config(
                            name = draft.name,
                            dirName = draft.dirName.orEmpty(),
                            svgTemplate = draft.svgTemplate,
                            sizeScale = draft.sizeScale,
                            dayNormalColor = draft.dayNormalColor,
                            dayEmphasisColor = draft.dayEmphasisColor,
                            nightNormalColor = draft.nightNormalColor,
                            nightEmphasisColor = draft.nightEmphasisColor,
                            resources = draft.resources,
                            formatVersion = if (draft.resources.isEmpty()) 1 else 2,
                        ),
                        oldEntry = oldEntry,
                    )
                }
                R.string.bubble_package_saved
            }.getOrElse { R.string.bubble_package_save_failed }
            _uiState.update { it.copy(draft = null) }
            _effects.tryEmit(BubblePackageManageEffect.Message(message))
            refresh()
        }
    }

    private fun updateDraft(transform: (BubblePackageDraft) -> BubblePackageDraft) {
        _uiState.update { state ->
            state.draft?.let { state.copy(draft = transform(it)) } ?: state
        }
    }
}
