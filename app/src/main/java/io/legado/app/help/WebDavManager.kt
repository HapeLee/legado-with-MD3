package io.legado.app.help

import android.content.SharedPreferences
import android.net.Uri
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.Restore
import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav
import io.legado.app.lib.webdav.WebDavException
import io.legado.app.lib.webdav.WebDavFile
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isJson
import io.legado.app.utils.normalizeFileName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

data class WebDavConfig(
    val url: String,
    val account: String,
    val password: String,
    val dir: String,
    val deviceName: String
)

sealed class WebDavState {
    data object Unconfigured : WebDavState()
    data object Checking : WebDavState()
    data class Ready(
        val rootUrl: String,
        val authorization: Authorization,
        val defaultBookWebDav: RemoteBookWebDav
    ) : WebDavState()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : WebDavState()
}

class WebDavManager(
    private val prefs: SharedPreferences = appCtx.defaultSharedPreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val configFlow = MutableStateFlow(readConfig())
    private val _state = MutableStateFlow<WebDavState>(WebDavState.Unconfigured)
    val state: StateFlow<WebDavState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
        refresh()
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        scope.cancel()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch(ioDispatcher) {
            syncConfig(configFlow.value)
        }
    }

    suspend fun testWebDav(): Boolean {
        val config = configFlow.value
        if (config.account.isBlank() || config.password.isBlank()) {
            _state.value = WebDavState.Unconfigured
            return false
        }
        return withContext(ioDispatcher) {
            try {
                val auth = Authorization(config.account, config.password)
                checkAuthorization(buildRootUrl(config), auth)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun defaultBookWebDavOrNull(): RemoteBookWebDav? {
        return (state.value as? WebDavState.Ready)?.defaultBookWebDav
    }

    @Throws(Exception::class)
    suspend fun getBackupNames(): ArrayList<String> {
        val auth = requireAuthorization()
        val rootUrl = buildRootUrl(configFlow.value)
        val names = arrayListOf<String>()
        var files = WebDav(rootUrl, auth).listFiles()
        files = files.sortedWith { o1, o2 ->
            AlphanumComparator.compare(o1.displayName, o2.displayName)
        }.reversed()
        files.forEach { webDav ->
            val name = webDav.displayName
            if (name.startsWith("backup")) {
                names.add(name)
            }
        }
        return names
    }

    @Throws(WebDavException::class)
    suspend fun restoreWebDav(name: String) {
        val auth = requireAuthorization()
        val rootUrl = buildRootUrl(configFlow.value)
        val webDav = WebDav(rootUrl + name, auth)
        webDav.downloadTo(Backup.zipFilePath, true)
        FileUtils.delete(Backup.backupPath)
        ZipUtils.unZipToPath(File(Backup.zipFilePath), Backup.backupPath)
        Restore.restoreLocked(Backup.backupPath)
    }

    suspend fun hasBackUp(backUpName: String): Boolean {
        val auth = requireAuthorization()
        val rootUrl = buildRootUrl(configFlow.value)
        val url = "$rootUrl$backUpName"
        return WebDav(url, auth).exists()
    }

    suspend fun lastBackUp(): Result<WebDavFile?> {
        return kotlin.runCatching {
            val auth = requireAuthorization()
            val rootUrl = buildRootUrl(configFlow.value)
            var lastBackupFile: WebDavFile? = null
            WebDav(rootUrl, auth).listFiles().reversed().forEach { webDavFile ->
                if (webDavFile.displayName.startsWith("backup")) {
                    if (lastBackupFile == null || webDavFile.lastModify > lastBackupFile.lastModify) {
                        lastBackupFile = webDavFile
                    }
                }
            }
            lastBackupFile
        }
    }

    @Throws(Exception::class)
    suspend fun backUpWebDav(fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        val auth = requireAuthorization()
        val rootUrl = buildRootUrl(configFlow.value)
        val putUrl = "$rootUrl$fileName"
        WebDav(putUrl, auth).upload(Backup.zipFilePath)
    }

    suspend fun exportWebDav(byteArray: ByteArray, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        val auth = requireAuthorization()
        val exportsUrl = buildRootUrl(configFlow.value) + "books/"
        val putUrl = exportsUrl + fileName
        WebDav(putUrl, auth).upload(byteArray, "text/plain")
    }

    suspend fun exportWebDav(uri: Uri, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        val auth = requireAuthorization()
        val exportsUrl = buildRootUrl(configFlow.value) + "books/"
        val putUrl = exportsUrl + fileName
        WebDav(putUrl, auth).upload(uri, "text/plain")
    }

    suspend fun uploadBookProgress(
        book: Book,
        toast: Boolean = false,
        onSuccess: (() -> Unit)? = null
    ) {
        val auth = requireAuthorization()
        if (!AppConfig.syncBookProgress) return
        if (!NetworkUtils.isAvailable()) return
        try {
            val bookProgress = BookProgress(book)
            val json = GSON.toJson(bookProgress)
            val url = getProgressUrl(book.name, book.author)
            WebDav(url, auth).upload(json.toByteArray(), "application/json")
            book.syncTime = System.currentTimeMillis()
            onSuccess?.invoke()
        } catch (e: Exception) {
            if (toast) {
                throw e
            }
        }
    }

    suspend fun uploadBookProgress(bookProgress: BookProgress, onSuccess: (() -> Unit)? = null) {
        val auth = requireAuthorization()
        if (!AppConfig.syncBookProgress) return
        if (!NetworkUtils.isAvailable()) return
        val json = GSON.toJson(bookProgress)
        val url = getProgressUrl(bookProgress.name, bookProgress.author)
        WebDav(url, auth).upload(json.toByteArray(), "application/json")
        onSuccess?.invoke()
    }

    suspend fun getBookProgress(book: Book): BookProgress? {
        val auth = requireAuthorization()
        val url = getProgressUrl(book.name, book.author)
        return kotlin.runCatching {
            WebDav(url, auth).download().let { byteArray ->
                val json = String(byteArray)
                if (json.isJson()) {
                    return GSON.fromJsonObject<BookProgress>(json).getOrNull()
                }
            }
            null
        }.getOrNull()
    }

    suspend fun downloadAllBookProgress() {
        val auth = requireAuthorization()
        if (!NetworkUtils.isAvailable()) return
        val progressUrl = buildRootUrl(configFlow.value) + "bookProgress/"
        val bookProgressFiles = WebDav(progressUrl, auth).listFiles()
        val map = hashMapOf<String, WebDavFile>()
        bookProgressFiles.forEach {
            map[it.displayName] = it
        }
        appDb.bookDao.all.forEach { book ->
            val progressFileName = getProgressFileName(book.name, book.author)
            val webDavFile = map[progressFileName] ?: return
            if (webDavFile.lastModify <= book.syncTime) {
                return
            }
            getBookProgress(book)?.let { bookProgress ->
                if (bookProgress.durChapterIndex > book.durChapterIndex
                    || (bookProgress.durChapterIndex == book.durChapterIndex
                            && bookProgress.durChapterPos > book.durChapterPos)
                ) {
                    book.durChapterIndex = bookProgress.durChapterIndex
                    book.durChapterPos = bookProgress.durChapterPos
                    book.durChapterTitle = bookProgress.durChapterTitle
                    book.durChapterTime = bookProgress.durChapterTime
                    book.syncTime = System.currentTimeMillis()
                    appDb.bookDao.update(book)
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PreferKey.webDavUrl
            || key == PreferKey.webDavAccount
            || key == PreferKey.webDavPassword
            || key == PreferKey.webDavDir
            || key == PreferKey.webDavDeviceName
        ) {
            configFlow.value = readConfig()
            refresh()
        }
    }

    private fun readConfig(): WebDavConfig {
        return WebDavConfig(
            url = appCtx.getPrefString(PreferKey.webDavUrl, "") ?: "",
            account = appCtx.getPrefString(PreferKey.webDavAccount, "") ?: "",
            password = appCtx.getPrefString(PreferKey.webDavPassword, "") ?: "",
            dir = appCtx.getPrefString(PreferKey.webDavDir, "legado") ?: "legado",
            deviceName = appCtx.getPrefString(PreferKey.webDavDeviceName, "") ?: ""
        )
    }

    private suspend fun syncConfig(config: WebDavConfig) {
        if (config.account.isBlank() || config.password.isBlank()) {
            _state.value = WebDavState.Unconfigured
            return
        }
        _state.value = WebDavState.Checking
        try {
            val rootUrl = buildRootUrl(config)
            val auth = Authorization(config.account, config.password)
            checkAuthorization(rootUrl, auth)
            WebDav(rootUrl, auth).makeAsDir()
            WebDav(rootUrl + "bookProgress/", auth).makeAsDir()
            WebDav(rootUrl + "books/", auth).makeAsDir()
            WebDav(rootUrl + "background/", auth).makeAsDir()
            val defaultBookWebDav = RemoteBookWebDav(rootUrl + "books/", auth)
            _state.value = WebDavState.Ready(rootUrl, auth, defaultBookWebDav)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = WebDavState.Error(e.localizedMessage ?: "WebDav init failed", e)
        }
    }

    @Throws(WebDavException::class)
    private suspend fun checkAuthorization(rootUrl: String, authorization: Authorization) {
        if (!WebDav(rootUrl, authorization).check()) {
            throw WebDavException("WebDav authorization failed")
        }
    }

    private fun buildRootUrl(config: WebDavConfig): String {
        val defaultUrl = "https://dav.jianguoyun.com/dav/"
        var url = if (config.url.isBlank()) defaultUrl else config.url.trim()
        if (!url.endsWith("/")) url = "$url/"
        val dir = config.dir.trim()
        if (dir.isNotEmpty()) {
            url = "$url$dir/"
        }
        return url
    }

    private fun requireAuthorization(): Authorization {
        return (state.value as? WebDavState.Ready)?.authorization
            ?: throw NoStackTraceException("webDav not configured")
    }

    private fun getProgressUrl(name: String, author: String): String {
        val progressUrl = buildRootUrl(configFlow.value) + "bookProgress/"
        return progressUrl + getProgressFileName(name, author)
    }

    private fun getProgressFileName(name: String, author: String): String {
        return UrlUtil.replaceReservedChar("${name}_${author}".normalizeFileName()) + ".json"
    }
}
