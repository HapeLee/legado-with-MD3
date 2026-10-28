package io.legado.app.ui.config.bubble

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.help.config.BubbleSettings
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.ImageProvider
import io.legado.app.ui.book.read.ConfigUpdateAction
import io.legado.app.ui.book.read.ReadConfigUpdateBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class BubbleConfigViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BubbleConfigUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BubbleConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        refresh()
    }

    fun onIntent(intent: BubbleConfigIntent) {
        when (intent) {
            BubbleConfigIntent.Refresh -> refresh()
            is BubbleConfigIntent.Apply -> applyEntry(intent.entry)
            is BubbleConfigIntent.Delete -> deleteEntry(intent.entry)
            is BubbleConfigIntent.Export -> exportEntry(intent.entry, intent.targetUri)
            is BubbleConfigIntent.Import -> importEntry(intent.uri)
            is BubbleConfigIntent.ForceSoftwareChanged -> setForceSoftware(intent.enabled)
            BubbleConfigIntent.StartCreate -> startCreate()
            is BubbleConfigIntent.StartEdit -> startEdit(intent.entry)
            is BubbleConfigIntent.UpdateDraft -> updateDraft(intent.config)
            BubbleConfigIntent.SaveDraft -> saveDraft()
            BubbleConfigIntent.DismissEditor -> dismissEditor()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val entries = runCatching { BubblePackageManager.loadEntries() }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    entries = entries,
                    activeDirName = BubblePackageManager.activeDirName(),
                    forceSoftwareParagraphBubble = BubbleSettings.forceSoftwareParagraphBubble,
                    loading = false,
                )
            }
        }
    }

    private fun applyEntry(entry: BubblePackageManager.Entry) {
        viewModelScope.launch {
            runCatching {
                BubblePackageManager.apply(entry)
            }.onSuccess {
                _uiState.update { it.copy(activeDirName = entry.dirName) }
                notifyReader()
                _effects.tryEmit(BubbleConfigEffect.Message("已应用「${entry.config.name}」"))
            }.onFailure { emitError(it) }
        }
    }

    private fun deleteEntry(entry: BubblePackageManager.Entry) {
        viewModelScope.launch {
            runCatching {
                BubblePackageManager.deleteLocal(entry)
            }.onSuccess {
                notifyReader()
                refresh()
            }.onFailure { emitError(it) }
        }
    }

    private fun exportEntry(entry: BubblePackageManager.Entry, targetUri: String) {
        viewModelScope.launch {
            runCatching {
                val zip = BubblePackageManager.exportZip(entry)
                withContext(Dispatchers.IO) {
                    val output = appCtx.contentResolver.openOutputStream(Uri.parse(targetUri))
                        ?: error("无法写入所选文件")
                    output.use { stream ->
                        zip.inputStream().use { input -> input.copyTo(stream) }
                    }
                }
                zip.name
            }.onSuccess { name ->
                _effects.tryEmit(BubbleConfigEffect.Exported("已导出 $name"))
            }.onFailure { emitError(it) }
        }
    }

    private fun importEntry(uri: String) {
        viewModelScope.launch {
            runCatching {
                val value = uri.trim()
                if (value.startsWith("http", ignoreCase = true)) {
                    val bytes = okHttpClient.newCallResponseBody { url(value) }.bytes()
                    BubblePackageManager.importZipBytes(bytes)
                } else {
                    BubblePackageManager.importZipUri(Uri.parse(value))
                }
            }.onSuccess { entry ->
                refresh()
                _effects.tryEmit(BubbleConfigEffect.Message("已导入「${entry.config.name}」"))
            }.onFailure { emitError(it) }
        }
    }

    private fun setForceSoftware(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                BubbleSettings.setForceSoftwareParagraphBubble(enabled)
            }.onSuccess {
                _uiState.update { it.copy(forceSoftwareParagraphBubble = enabled) }
                notifyReader()
            }.onFailure { emitError(it) }
        }
    }

    private fun startCreate() {
        val config = BubblePackageManager.builtinConfig().copy(
            name = "自定义段评气泡",
            dirName = "",
            updatedAt = System.currentTimeMillis(),
        )
        _uiState.update { it.copy(editor = BubbleEditorState(original = null, config = config)) }
    }

    private fun startEdit(entry: BubblePackageManager.Entry) {
        if (entry.source == BubblePackageManager.Source.BUILTIN) return
        _uiState.update {
            it.copy(editor = BubbleEditorState(original = entry, config = entry.config))
        }
    }

    private fun updateDraft(config: BubblePackageManager.Config) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = editor.copy(config = config))
        }
    }

    private fun saveDraft() {
        val editor = _uiState.value.editor ?: return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(editor = state.editor?.copy(saving = true))
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    BubblePackageManager.addOrUpdate(editor.config, editor.original)
                }
            }.onSuccess {
                _uiState.update { it.copy(editor = null) }
                notifyReader()
                refresh()
                _effects.tryEmit(BubbleConfigEffect.Message("已保存"))
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(editor = state.editor?.copy(saving = false))
                }
                emitError(error)
            }
        }
    }

    private fun dismissEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    /** 气泡配色/模板变了，正文里的旧位图必须失效并重排。 */
    private fun notifyReader() {
        viewModelScope.launch {
            ImageProvider.clear()
            ReadConfigUpdateBus.post(
                setOf(
                    ConfigUpdateAction.RefreshInlineImages,
                    ConfigUpdateAction.ReloadContent,
                )
            )
        }
    }

    private fun emitError(error: Throwable) {
        _effects.tryEmit(
            BubbleConfigEffect.Message(error.localizedMessage ?: error.javaClass.simpleName)
        )
    }
}
