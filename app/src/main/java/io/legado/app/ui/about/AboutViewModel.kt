package io.legado.app.ui.about

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.help.CrashHandler
import io.legado.app.help.config.AppConfig
import io.legado.app.help.update.AppUpdate
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.delete
import io.legado.app.utils.externalCache
import io.legado.app.utils.find
import io.legado.app.utils.getFile
import io.legado.app.utils.list
import io.legado.app.utils.openInputStream
import io.legado.app.utils.openOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileFilter

class AboutViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AboutEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: AboutIntent) {
        when (intent) {
            is AboutIntent.DismissSheet -> _uiState.update { it.copy(sheet = AboutSheet.None) }
            is AboutIntent.DismissDialog -> _uiState.update { it.copy(dialog = null) }
            is AboutIntent.CheckUpdate -> checkUpdate()
            is AboutIntent.ShowMdFile -> showMdFile(intent.title, intent.fileName)
            is AboutIntent.ShowCrashLogs -> showCrashLogs()
            is AboutIntent.SaveLog -> saveLog()
            is AboutIntent.CreateHeapDump -> createHeapDump()
            is AboutIntent.OpenUrl -> _effects.tryEmit(AboutEffect.OpenUrl(intent.url))
            is AboutIntent.ClearCrashLogs -> clearCrashLogs()
            is AboutIntent.ReadCrashFile -> readCrashFile(intent.fileDoc)
            is AboutIntent.StartDownload -> startDownload()
        }
    }

    private fun checkUpdate() {
        _uiState.update { it.copy(dialog = AboutDialog.CheckingUpdate) }
        AppUpdate.gitHubUpdate?.run {
            check(viewModelScope)
                .onSuccess { updateInfo ->
                    _uiState.update {
                        it.copy(
                            dialog = null,
                            sheet = AboutSheet.Update(updateInfo)
                        )
                    }
                }.onError { e ->
                    _uiState.update { it.copy(dialog = null) }
                    _effects.tryEmit(
                        AboutEffect.ShowToast("${context.getString(R.string.check_update)}\n${e.localizedMessage}")
                    )
                }.onFinally {
                    _uiState.update { it.copy(dialog = null) }
                }
        }
    }

    private fun showMdFile(title: String, fileName: String) {
        execute {
            String(context.assets.open(fileName).readBytes())
        }.onSuccess { content ->
            _uiState.update { it.copy(sheet = AboutSheet.Markdown(title, content)) }
        }
    }

    private fun showCrashLogs() {
        execute {
            loadCrashLogFiles()
        }.onSuccess { files ->
            _uiState.update {
                it.copy(
                    crashLogFiles = files,
                    sheet = AboutSheet.CrashLogs
                )
            }
        }
    }

    private fun readCrashFile(fileDoc: FileDoc) {
        execute {
            String(fileDoc.readBytes())
        }.onSuccess { content ->
            _uiState.update { it.copy(sheet = AboutSheet.Markdown(fileDoc.name, content)) }
        }.onError {
            _effects.tryEmit(AboutEffect.ShowToast(it.localizedMessage ?: ""))
        }
    }

    private fun clearCrashLogs() {
        execute {
            context.externalCacheDir
                ?.getFile("crash")
                ?.let { FileUtils.delete(it, false) }
            val backupPath = AppConfig.backupPath
            if (!backupPath.isNullOrEmpty()) {
                val uri = Uri.parse(backupPath)
                FileDoc.fromUri(uri, true)
                    .find("crash")
                    ?.delete()
            }
        }.onError {
            _effects.tryEmit(AboutEffect.ShowToast(it.localizedMessage ?: ""))
        }.onFinally {
            execute {
                loadCrashLogFiles()
            }.onSuccess { files ->
                _uiState.update { it.copy(crashLogFiles = files) }
            }
        }
    }

    private fun loadCrashLogFiles(): List<FileDoc> {
        val list = arrayListOf<FileDoc>()
        context.externalCacheDir
            ?.getFile("crash")
            ?.listFiles(FileFilter { it.isFile })
            ?.forEach { list.add(FileDoc.fromFile(it)) }
        val backupPath = AppConfig.backupPath
        if (!backupPath.isNullOrEmpty()) {
            val uri = Uri.parse(backupPath)
            FileDoc.fromUri(uri, true)
                .find("crash")
                ?.list { !it.isDir }
                ?.let { list.addAll(it) }
        }
        return list.sortedByDescending { it.name }.distinctBy { it.name }
    }

    private fun saveLog() {
        execute {
            val backupPath = AppConfig.backupPath ?: run {
                _effects.tryEmit(AboutEffect.ShowToast(context.getString(R.string.about_backup_dir_not_set)))
                return@execute
            }
            if (!AppConfig.recordLog) {
                _effects.tryEmit(AboutEffect.ShowToast(context.getString(R.string.about_log_recording_disabled)))
                delay(3000)
            }
            val doc = FileDoc.fromUri(backupPath.toUri(), true)
            copyLogs(doc)
            copyHeapDump(doc)
            _effects.tryEmit(AboutEffect.ShowToast(context.getString(R.string.about_saved_to_backup_dir)))
        }.onError {
            AppLog.put("保存日志出错\n${it.localizedMessage}", it, true)
        }
    }

    private fun createHeapDump() {
        execute {
            val backupPath = AppConfig.backupPath ?: run {
                _effects.tryEmit(AboutEffect.ShowToast(context.getString(R.string.about_backup_dir_not_set)))
                return@execute
            }
            if (!AppConfig.recordHeapDump) {
                _effects.tryEmit(AboutEffect.ShowToast(context.getString(R.string.about_heap_dump_recording_disabled)))
                delay(3000)
            }
            _effects.tryEmit(AboutEffect.ShowToast(context.getString(R.string.about_heap_dump_creating)))
            System.gc()
            CrashHandler.doHeapDump(true)
            val doc = FileDoc.fromUri(backupPath.toUri(), true)
            if (!copyHeapDump(doc)) {
                _effects.tryEmit(AboutEffect.ShowToast(context.getString(R.string.about_heap_dump_not_found)))
            } else {
                _effects.tryEmit(AboutEffect.ShowToast(context.getString(R.string.about_saved_to_backup_dir)))
            }
        }.onError {
            AppLog.put("保存堆转储失败\n${it.localizedMessage}", it)
        }
    }

    private fun startDownload() {
        val sheet = _uiState.value.sheet
        if (sheet is AboutSheet.Update) {
            val info = sheet.updateInfo
            if (info.downloadUrl.isBlank() || info.fileName.isBlank()) {
                _effects.tryEmit(AboutEffect.ShowToast(context.getString(R.string.about_download_info_incomplete)))
            } else {
                _effects.tryEmit(AboutEffect.StartDownload(info.downloadUrl, info.fileName))
            }
        }
    }

    private fun copyLogs(doc: FileDoc) {
        val cacheDir = context.externalCache
        val logFiles = File(cacheDir, "logs")
        val crashFiles = File(cacheDir, "crash")
        val logcatFile = File(cacheDir, "logcat.txt")
        dumpLogcat(logcatFile)
        val zipFile = File(cacheDir, "logs.zip")
        ZipUtils.zipFiles(arrayListOf(logFiles, crashFiles, logcatFile), zipFile)
        doc.find("logs.zip")?.delete()
        zipFile.inputStream().use { input ->
            doc.createFileIfNotExist("logs.zip").openOutputStream().getOrNull()
                ?.use { input.copyTo(it) }
        }
        zipFile.delete()
    }

    private fun copyHeapDump(doc: FileDoc): Boolean {
        val heapFile = FileDoc.fromFile(File(context.externalCache, "heapDump")).list()
            ?.firstOrNull() ?: return false
        doc.find("heapDump")?.delete()
        val heapDumpDoc = doc.createFolderIfNotExist("heapDump")
        heapFile.openInputStream().getOrNull()?.use { input ->
            heapDumpDoc.createFileIfNotExist(heapFile.name).openOutputStream().getOrNull()
                ?.use { input.copyTo(it) }
        }
        return true
    }

    private fun dumpLogcat(file: File) {
        try {
            val process = Runtime.getRuntime().exec("logcat -d")
            file.outputStream().use { process.inputStream.copyTo(it) }
        } catch (e: Exception) {
            AppLog.put("保存Logcat失败\n$e", e)
        }
    }
}
