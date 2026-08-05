package io.legado.app.ui.book.readaloud.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.AppLog
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TtsCacheViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TtsCacheUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TtsCacheEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val ttsCacheDir: File?
        get() {
            val baseDir = appCtx.externalCacheDir ?: appCtx.cacheDir
            return File(baseDir, "httpTTS").takeIf { it.exists() }
        }

    private val textIndexFile: File?
        get() = ttsCacheDir?.let { File(it, "tts_cache_index.json") }

    init {
        loadCache()
        loadLogs()
    }

    fun onIntent(intent: TtsCacheIntent) {
        when (intent) {
            TtsCacheIntent.LoadCache -> loadCache()
            TtsCacheIntent.LoadLogs -> loadLogs()
            is TtsCacheIntent.SelectTab -> _uiState.update { it.copy(selectedTab = intent.tab) }
            is TtsCacheIntent.DeleteFile -> deleteFile(intent.name)
            TtsCacheIntent.ClearAll -> clearAll()
            TtsCacheIntent.ShowClearAllDialog ->
                _uiState.update { it.copy(activeDialog = TtsCacheDialog.ClearAll) }

            TtsCacheIntent.DismissDialog ->
                _uiState.update { it.copy(activeDialog = null) }

            is TtsCacheIntent.ShowFileDetail -> _uiState.update {
                it.copy(
                    detailTitle = intent.name,
                    detailContent = buildString {
                        append("分片文字:\n${intent.text}\n\n")
                        append("文件大小: ${formatSize(intent.sizeBytes)}\n")
                        append("创建时间: ${detailDateFormat.format(Date(intent.lastModified))}\n")
                        append("文件名: ${intent.name}.mp3")
                    },
                    showDetail = true,
                )
            }

            is TtsCacheIntent.ShowLogDetail -> _uiState.update {
                it.copy(
                    detailTitle = detailTimeFormat.format(Date(intent.timestamp)),
                    detailContent = intent.fullContent,
                    showDetail = true,
                )
            }

            TtsCacheIntent.DismissDetail -> _uiState.update { it.copy(showDetail = false) }
        }
    }

    private fun loadCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = ttsCacheDir
            if (dir == null) {
                _uiState.update {
                    it.copy(loading = false, files = persistentListOf(), totalSizeBytes = 0)
                }
                return@launch
            }
            val index = loadTextIndex()
            val files = dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".mp3") }
                ?.sortedByDescending { it.lastModified() }
                ?.map { file ->
                    val baseName = file.nameWithoutExtension
                    val text = index[baseName].orEmpty()
                    TtsCacheFileUi(
                        name = baseName,
                        text = text,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                    )
                } ?: emptyList()
            val totalSize = files.sumOf { it.sizeBytes }
            _uiState.update {
                it.copy(
                    loading = false,
                    files = persistentListOf<TtsCacheFileUi>().let { list ->
                        list.builder().apply { addAll(files) }.build()
                    },
                    totalSizeBytes = totalSize,
                )
            }
        }
    }

    private fun loadLogs() {
        viewModelScope.launch(Dispatchers.Default) {
            val ttsKeywords =
                listOf("TTS", "预合成", "预下载", "朗读", "听书", "httpTTS", "朗读下载")
            val logs = AppLog.logs
                .filter { (_, message, _) ->
                    ttsKeywords.any { keyword -> message.contains(keyword, ignoreCase = true) }
                }
                .map { (timestamp, message, throwable) ->
                    val fullContent = if (throwable != null) {
                        "$message\n${throwable.stackTraceToString()}"
                    } else {
                        message
                    }
                    TtsLogEntryUi(
                        timestamp = timestamp,
                        message = message,
                        hasError = throwable != null,
                        fullContent = fullContent,
                    )
                }
            _uiState.update {
                it.copy(logs = persistentListOf<TtsLogEntryUi>().let { list ->
                    list.builder().apply { addAll(logs) }.build()
                })
            }
        }
    }

    private fun loadTextIndex(): Map<String, String> {
        return try {
            val file = textIndexFile ?: return emptyMap()
            if (!file.exists()) return emptyMap()
            val json = file.readText()
            val map = mutableMapOf<String, String>()
            // Simple JSON parsing: {"filename":"text",...}
            val regex = Regex("\"([^\"]+)\":\"([^\"]*)\"")
            regex.findAll(json).forEach { match ->
                map[match.groupValues[1]] = match.groupValues[2]
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun deleteFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = ttsCacheDir ?: return@launch
            val file = File(dir, "$name.mp3")
            if (file.exists() && file.delete()) {
                removeFromTextIndex(name)
                _effects.tryEmit(TtsCacheEffect.ShowToast("已删除"))
                loadCache()
            }
        }
    }

    private fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(activeDialog = null) }
            val dir = ttsCacheDir
            if (dir != null && dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
            textIndexFile?.delete()
            _effects.tryEmit(TtsCacheEffect.ShowToast("缓存已清除"))
            loadCache()
        }
    }

    private fun removeFromTextIndex(filename: String) {
        try {
            val file = textIndexFile ?: return
            if (!file.exists()) return
            val index = loadTextIndex().toMutableMap()
            index.remove(filename)
            writeTextIndex(index)
        } catch (_: Exception) {
        }
    }

    private fun writeTextIndex(index: Map<String, String>) {
        try {
            val file = textIndexFile ?: return
            val json = buildString {
                append("{")
                index.entries.forEachIndexed { i, (key, value) ->
                    if (i > 0) append(",")
                    val escaped = value
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                    append("\"$key\":\"$escaped\"")
                }
                append("}")
            }
            file.writeText(json)
        } catch (_: Exception) {
        }
    }

    companion object {
        private val detailDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private val detailTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            }
        }
    }
}
