package io.legado.app.help

import android.net.Uri
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.exception.NoStackTraceException
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.BackupRestoreLock
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
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJson
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * WebDAV 配置就绪状态（本地配置 + 本次进程的网络初始化结果）。
 *
 * UI 只消费这个状态来开关「拉取/覆盖云端进度」这类入口，
 * 不再直接读一次性的 [AppWebDav.authorization]。
 */
sealed interface WebDavConfigState {
    /** 尚未取得过状态（App 刚启动，[AppWebDav.upConfig] 还没跑）。 */
    data object Idle : WebDavConfigState

    /** 本地没有账号或密码。 */
    data object Unconfigured : WebDavConfigState

    /** 有配置，正在做网络初始化（校验授权 + 建目录）。 */
    data object Preparing : WebDavConfigState

    /** 本次进程内初始化成功，可真正读写云端。 */
    data object Ready : WebDavConfigState

    /** 网络初始化失败（账号密码错、服务不支持 MKCOL、无网络等）。 */
    data class Failed(val message: String?) : WebDavConfigState
}

/**
 * webDav初始化会访问网络,不要放到主线程
 */
object AppWebDav {
    private val backupGateway by lazy { GlobalContext.get().get<BackupSettingsGateway>() }

    private const val defaultWebDavUrl = "https://dav.jianguoyun.com/dav/"
    private val bookProgressUrl get() = "${rootWebDavUrl}bookProgress/"
    private val exportsWebDavUrl get() = "${rootWebDavUrl}books/"
    private val bgWebDavUrl get() = "${rootWebDavUrl}background/"

    private val configMutex = Mutex()
    private var appliedConfig: AppliedWebDavConfig? = null

    /**
     * 云端配置（本地账号密码）的就绪状态，供 UI 观测。
     *
     * [upConfig] 会访问网络，成功与否不能靠「读一次 [isOk]」来判断：UI 的取值是快照，
     * 初始化晚到或失败时菜单会永远消失。故把状态建模成 flow，由 UI 订阅。
     */
    private val _configState = MutableStateFlow<WebDavConfigState>(WebDavConfigState.Idle)
    val configState: StateFlow<WebDavConfigState> = _configState.asStateFlow()

    @Volatile
    var authorization: Authorization? = null
        private set

    @Volatile
    var defaultBookWebDav: RemoteBookWebDav? = null

    /**
     * 是否已配置 WebDAV 账号（纯本地判断，不触发网络）。
     *
     * 与上游一致：菜单可见性只看「配置了没」，网络校验属于 [upConfig] 的职责。
     * 此前这里返回 `authorization != null`，等于要求本次进程内已经完成过一次
     * 成功的网络初始化 + 目录创建，初始化失败就会让整个进度同步入口永久消失。
     */
    val isConfigured: Boolean
        get() = with(backupGateway.currentSettings) {
            webDavAccount.trim().isNotEmpty() && webDavPassword.isNotEmpty()
        }

    /** 本次进程内初始化是否已成功（能真正读写云端）。 */
    val isOk get() = authorization != null

    val isJianGuoYun get() = rootWebDavUrl.startsWith(defaultWebDavUrl, true)

    private val rootWebDavUrl: String
        get() {
            val configUrl = backupGateway.currentSettings.webDavUrl.trim()
            var url = if (configUrl.isEmpty()) defaultWebDavUrl else configUrl
            if (!url.endsWith("/")) url = "${url}/"
            backupGateway.currentSettings.webDavDir.trim().let {
                if (it.isNotEmpty()) {
                    url = "${url}${it}/"
                }
            }
            return url
        }

    suspend fun upConfig() {
        configMutex.withLock {
            val config = AppliedWebDavConfig(
                url = backupGateway.currentSettings.webDavUrl,
                account = backupGateway.currentSettings.webDavAccount,
                password = backupGateway.currentSettings.webDavPassword,
                dir = backupGateway.currentSettings.webDavDir,
            )
            // 配置没变时状态只会是 Ready 或 Unconfigured，两者都无需重新网络初始化。
            if (appliedConfig == config) return

            if (config.account.isEmpty() || config.password.isEmpty()) {
                appliedConfig = config
                authorization = null
                defaultBookWebDav = null
                _configState.value = WebDavConfigState.Unconfigured
                return
            }

            _configState.value = WebDavConfigState.Preparing
            kotlin.runCatching {
                val mAuthorization = Authorization(config.account, config.password)
                checkAuthorization(mAuthorization)
                WebDav(rootWebDavUrl, mAuthorization).makeAsDir()
                WebDav(bookProgressUrl, mAuthorization).makeAsDir()
                WebDav(exportsWebDavUrl, mAuthorization).makeAsDir()
                WebDav(bgWebDavUrl, mAuthorization).makeAsDir()
                val rootBooksUrl = "${rootWebDavUrl}books/"
                defaultBookWebDav = RemoteBookWebDav(rootBooksUrl, mAuthorization)
                authorization = mAuthorization
                _configState.value = WebDavConfigState.Ready
            }.onFailure { e ->
                // 不再静默：此前异常被吞掉，UI 上表现为进度同步入口无声消失。
                authorization = null
                defaultBookWebDav = null
                _configState.value = WebDavConfigState.Failed(e.localizedMessage)
                AppLog.put("webDav初始化失败\n${e.localizedMessage}", e)
            }
            // appliedConfig 只在成功时推进，失败留白以便下次设置变化或显式调用时重试。
            if (_configState.value == WebDavConfigState.Ready) appliedConfig = config
        }
    }

    /**
     * 进度同步等入口在打开菜单时调用：配置齐备但本次进程尚未初始化成功时补一次。
     *
     * 等价于上游 `onPrepareOptionsMenu` 每次开菜单都重算 `AppWebDav.isOk` 的效果，
     * 但把网络动作留在 IO，UI 只读 [configState]。
     */
    suspend fun ensureConfigured() {
        if (isOk) return
        if (!isConfigured) {
            _configState.value = WebDavConfigState.Unconfigured
            return
        }
        upConfig()
    }

    private data class AppliedWebDavConfig(
        val url: String,
        val account: String,
        val password: String,
        val dir: String,
    )

    @Throws(WebDavException::class)
    private suspend fun checkAuthorization(authorization: Authorization) {
        if (!WebDav(rootWebDavUrl, authorization).check()) {
            //appCtx.removePref(PreferKey.webDavPassword)
            appCtx.toastOnUi(R.string.webdav_application_authorization_error)
            throw WebDavException(appCtx.getString(R.string.webdav_application_authorization_error))
        }
    }

    @Throws(Exception::class)
    suspend fun getBackupNames(): ArrayList<String> {
        val names = arrayListOf<String>()
        authorization?.let {
            var files = WebDav(rootWebDavUrl, it).listFiles()
            files = files.sortedWith { o1, o2 ->
                AlphanumComparator.compare(o1.displayName, o2.displayName)
            }.reversed()
            files.forEach { webDav ->
                val name = webDav.displayName
                if (name.startsWith("backup")) {
                    names.add(name)
                }
            }
        } ?: throw NoStackTraceException("webDav没有配置")
        return names
    }

    @Throws(WebDavException::class)
    suspend fun restoreWebDav(name: String) {
        authorization?.let {
            val webDav = WebDav(rootWebDavUrl + name, it)
            BackupRestoreLock.withLock {
                webDav.downloadTo(Backup.zipFilePath, true)
                FileUtils.delete(Backup.backupPath)
                ZipUtils.unZipToPath(File(Backup.zipFilePath), Backup.backupPath)
                Restore.restoreUnzipped(Backup.backupPath)
                LocalConfig.lastBackup = System.currentTimeMillis()
            }
        }
    }

    suspend fun hasBackUp(backUpName: String): Boolean {
        authorization?.let {
            val url = "$rootWebDavUrl${backUpName}"
            return WebDav(url, it).exists()
        }
        return false
    }

    suspend fun lastBackUp(): Result<WebDavFile?> {
        return kotlin.runCatching {
            authorization?.let {
                var lastBackupFile: WebDavFile? = null
                WebDav(rootWebDavUrl, it).listFiles().reversed().forEach { webDavFile ->
                    if (webDavFile.displayName.startsWith("backup")) {
                        if (lastBackupFile == null
                            || webDavFile.lastModify > lastBackupFile.lastModify
                        ) {
                            lastBackupFile = webDavFile
                        }
                    }
                }
                lastBackupFile
            }
        }
    }

    suspend fun testWebDav(): Boolean {
        return kotlin.runCatching {
            val account = backupGateway.currentSettings.webDavAccount
            val password = backupGateway.currentSettings.webDavPassword
            if (account.isNullOrEmpty() || password.isNullOrEmpty()) {
                appCtx.toastOnUi("账号或密码为空")
                return false
            }

            val auth = Authorization(account, password)
            checkAuthorization(auth)

            // 上游 check() 会真正建立 configuredWebDav；这里同样落一次配置，
            // 否则「测试同步」通过后 authorization 仍是空，阅读界面的进度同步入口不会出现。
            upConfig()

            appCtx.toastOnUi("WebDAV 服务可用")
            true
        }.getOrElse {
            it.printStackTrace()
            if (it !is WebDavException) {
                appCtx.toastOnUi(it.message ?: "未知错误")
            }
            false
        }
    }



    /**
     * webDav备份
     * @param fileName 备份文件名
     */
    @Throws(Exception::class)
    suspend fun backUpWebDav(fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        authorization?.let {
            val putUrl = "$rootWebDavUrl$fileName"
            WebDav(putUrl, it).upload(Backup.zipFilePath)
        }
    }

    /**
     * 获取云端所有背景名称
     */
    private suspend fun getAllBgWebDavFiles(): Result<List<WebDavFile>> {
        return kotlin.runCatching {
            if (!NetworkUtils.isAvailable())
                throw NoStackTraceException("网络未连接")
            authorization.let {
                it ?: throw NoStackTraceException("webDav未配置")
                WebDav(bgWebDavUrl, it).listFiles()
            }
        }
    }

    /**
     * 上传背景图片
     */
    suspend fun upBgs(files: Array<File>) {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bgWebDavFiles = getAllBgWebDavFiles().getOrThrow()
            .map { it.displayName }
            .toSet()
        files.forEach {
            if (!bgWebDavFiles.contains(it.name) && it.exists()) {
                WebDav("$bgWebDavUrl${it.name}", authorization)
                    .upload(it)
            }
        }
    }

    /**
     * 下载背景图片
     */
    suspend fun downBgs() {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bgWebDavFiles = getAllBgWebDavFiles().getOrThrow()
            .map { it.displayName }
            .toSet()
    }

    @Suppress("unused")
    suspend fun exportWebDav(byteArray: ByteArray, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        try {
            authorization?.let {
                // 如果导出的本地文件存在,开始上传
                val putUrl = exportsWebDavUrl + fileName
                WebDav(putUrl, it).upload(byteArray, "text/plain")
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav导出失败\n${e.localizedMessage}", e, true)
        }
    }

    suspend fun exportWebDav(uri: Uri, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        try {
            authorization?.let {
                // 如果导出的本地文件存在,开始上传
                val putUrl = exportsWebDavUrl + fileName
                WebDav(putUrl, it).upload(uri, "text/plain")
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav导出失败\n${e.localizedMessage}", e, true)
        }
    }

    suspend fun uploadBookProgress(
        book: Book,
        toast: Boolean = false,
        onSuccess: (() -> Unit)? = null
    ) {
        val authorization = authorization ?: return
        if (!backupGateway.currentSettings.syncBookProgress) return
        if (!NetworkUtils.isAvailable()) return
        try {
            val bookProgress = BookProgress(book)
            val json = GSON.toJson(bookProgress)
            val url = getProgressUrl(book.name, book.author)
            WebDav(url, authorization).upload(json.toByteArray(), "application/json")
            book.syncTime = System.currentTimeMillis()
            onSuccess?.invoke()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("上传进度失败\n${e.localizedMessage}", e, toast)
        }
    }

    suspend fun uploadBookProgress(
        bookProgress: BookProgress,
        onSuccess: (() -> Unit)? = null
    ): Boolean {
        try {
            val authorization = authorization ?: return false
            if (!backupGateway.currentSettings.syncBookProgress) return false
            if (!NetworkUtils.isAvailable()) return false
            val json = GSON.toJson(bookProgress)
            val url = getProgressUrl(bookProgress.name, bookProgress.author)
            WebDav(url, authorization).upload(json.toByteArray(), "application/json")
            onSuccess?.invoke()
            return true
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("上传进度失败\n${e.localizedMessage}", e)
            return false
        }
    }

    private fun getProgressUrl(name: String, author: String): String {
        return bookProgressUrl + getProgressFileName(name, author)
    }

    private fun getProgressFileName(name: String, author: String): String {
        return UrlUtil.replaceReservedChar("${name}_${author}".normalizeFileName()) + ".json"
    }

    /**
     * 获取书籍进度
     */
    suspend fun getBookProgress(book: Book): BookProgress? {
        return getBookProgress(book.name, book.author)
    }

    /**
     * 获取书籍进度
     */
    suspend fun getBookProgress(name: String, author: String): BookProgress? {
        val url = getProgressUrl(name, author)
        kotlin.runCatching {
            val authorization = authorization ?: return null
            WebDav(url, authorization).download().let { byteArray ->
                val json = String(byteArray)
                if (json.isJson()) {
                    return GSON.fromJsonObject<BookProgress>(json).getOrNull()

                }



            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("获取书籍进度失败\n${it.localizedMessage}", it)
        }
        return null
    }

    suspend fun downloadAllBookProgress() {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bookProgressFiles = WebDav(bookProgressUrl, authorization).listFiles()
        val map = hashMapOf<String, WebDavFile>()
        bookProgressFiles.forEach {
            map[it.displayName] = it
        }
        appDb.bookDao.all.forEach { book ->
            val progressFileName = getProgressFileName(book.name, book.author)
            val webDavFile = map[progressFileName]
            webDavFile ?: return@forEach
            if (webDavFile.lastModify <= book.syncTime) {
                //本地同步时间大于上传时间不用同步
                return@forEach
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

}
