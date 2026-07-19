package io.legado.app.help.storage

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.entities.HighlightTagRule
import io.legado.app.data.entities.HomepageCustomSet
import io.legado.app.data.entities.HomepageModule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.upType
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.SettingsWriter
import io.legado.app.help.config.ThemeConfigStore
import io.legado.app.model.BookCover
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.utils.ACache
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isUri
import io.legado.app.utils.openInputStream
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream

/**
 * 恢复
 */
object Restore : KoinComponent {

    private const val TAG = "Restore"

    suspend fun restore(context: Context, uri: Uri) {
        BackupRestoreLock.withLock {
            LogUtils.d(TAG, "开始恢复备份 uri:$uri")
            val unzipResult = kotlin.runCatching {
                FileUtils.delete(Backup.backupPath)
                if (uri.isContentScheme()) {
                    DocumentFile.fromSingleUri(context, uri)!!.openInputStream()!!.use {
                        ZipUtils.unZipToPath(it, Backup.backupPath)
                    }
                } else {
                    ZipUtils.unZipToPath(File(uri.path!!), Backup.backupPath)
                }
            }.onFailure {
                AppLog.put("复制解压文件出错\n${it.localizedMessage}", it)
            }
            if (unzipResult.isSuccess) {
                kotlin.runCatching {
                    restoreUnzipped(Backup.backupPath)
                    LocalConfig.lastBackup = System.currentTimeMillis()
                }.onFailure {
                    appCtx.toastOnUi("恢复备份出错\n${it.localizedMessage}")
                    AppLog.put("恢复备份出错\n${it.localizedMessage}", it)
                }
            }
        }
    }

    suspend fun restoreLocked(path: String) {
        BackupRestoreLock.withLock {
            restoreUnzipped(path)
        }
    }

    internal suspend fun restoreUnzipped(path: String) {
        restore(path)
    }

    private suspend fun restore(path: String) {
        val aes = BackupAES()
        fileToListT<Book>(path, "bookshelf.json")?.let {
            it.forEach { book ->
                book.upType()
            }
            val restorePlan = planBookRestore(
                restoredBooks = it,
                existingBooks = appDb.bookDao.all,
                ignoreLocalBook = BackupConfig.ignoreLocalBook,
                locationStatus = ::localBookLocationStatus,
            )
            restorePlan.booksToUpsert
                .filter { book -> book.isLocal }
                .forEach { book -> book.coverUrl = LocalBook.getCoverPath(book) }
            appDb.runInTransaction {
                if (restorePlan.booksToDelete.isNotEmpty()) {
                    appDb.bookDao.delete(*restorePlan.booksToDelete.toTypedArray())
                }
                if (restorePlan.booksToUpdate.isNotEmpty()) {
                    appDb.bookDao.update(*restorePlan.booksToUpdate.toTypedArray())
                }
                if (restorePlan.booksToInsert.isNotEmpty()) {
                    appDb.bookDao.insert(*restorePlan.booksToInsert.toTypedArray())
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("bookmark")) {
            fileToListT<Bookmark>(path, "bookmark.json")?.let {
                try {
                    appDb.bookmarkDao.insert(*it.toTypedArray())
                } catch (_: SQLiteConstraintException) {
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("bookGroup")) {
            fileToListT<BookGroup>(path, "bookGroup.json")?.let {
                appDb.bookGroupDao.replaceAll(it)
            }
        }
        if (BackupConfig.dbIsNotIgnored("bookSource")) {
            fileToListT<BookSource>(path, "bookSource.json")?.let {
                try {
                    appDb.bookSourceDao.insert(*it.toTypedArray())
                } catch (_: SQLiteConstraintException) {
                }
            } ?: run {
                val bookSourceFile = File(path, "bookSource.json")
                if (bookSourceFile.exists()) {
                    val json = bookSourceFile.readText()
                    ImportOldData.importOldSource(json)
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("rssSource")) {
            fileToListT<RssSource>(path, "rssSources.json")?.let {
                try {
                    appDb.rssSourceDao.insert(*it.toTypedArray())
                } catch (_: SQLiteConstraintException) {
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("rssStar")) {
            fileToListT<RssStar>(path, "rssStar.json")?.let {
                try {
                    appDb.rssStarDao.insert(*it.toTypedArray())
                } catch (_: SQLiteConstraintException) {
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("replaceRule")) {
            fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
                try {
                    appDb.replaceRuleDao.insert(*it.toTypedArray())
                } catch (_: SQLiteConstraintException) {
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("searchHistory")) {
            fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
                try {
                    appDb.searchKeywordDao.insert(*it.toTypedArray())
                } catch (_: SQLiteConstraintException) {
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("sourceSub")) {
            fileToListT<RuleSub>(path, "sourceSub.json")?.let {
                try {
                    appDb.ruleSubDao.insert(*it.toTypedArray())
                } catch (_: SQLiteConstraintException) {
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("txtTocRule")) {
            fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
                try {
                    appDb.txtTocRuleDao.insert(*it.toTypedArray())
                } catch (_: SQLiteConstraintException) {
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("httpTTS")) {
            fileToListT<HttpTTS>(path, "httpTTS.json")?.let {
                try {
                    appDb.httpTTSDao.insert(*it.toTypedArray())
                } catch (_: SQLiteConstraintException) {
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("dictRule")) {
            fileToListT<DictRule>(path, "dictRule.json")?.let {
                try {
                    appDb.dictRuleDao.insert(*it.toTypedArray())
                } catch (_: SQLiteConstraintException) {
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("keyboardAssists")) {
            fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
                try {
                    appDb.keyboardAssistsDao.insert(*it.toTypedArray())
                } catch (_: SQLiteConstraintException) {
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("homepageModules")) {
            fileToListT<HomepageModule>(path, "homepageModules.json")?.let {
                appDb.homepageModuleDao.replaceAll(it)
            }
        }
        if (BackupConfig.dbIsNotIgnored("homepageCustomSets")) {
            fileToListT<HomepageCustomSet>(path, "homepageCustomSets.json")?.let {
                appDb.homepageCustomSetDao.replaceAll(it)
            }
        }
        if (BackupConfig.dbIsNotIgnored("highlightRule")) {
            fileToListT<HighlightRule>(path, "highlightRule.json")?.let {
                appDb.highlightRuleDao.replaceAll(it)
            }
        }
        if (BackupConfig.dbIsNotIgnored("highlightTagRule")) {
            fileToListT<HighlightTagRule>(path, "highlightTagRule.json")?.let {
                appDb.highlightTagRuleDao.replaceAll(it)
            }
        }
        if (BackupConfig.dbIsNotIgnored("tagGroupRule")) {
            fileToListT<TagGroupRule>(path, "tagGroupRule.json")?.let {
                appDb.tagGroupRuleDao.replaceAll(it)
            }
        }
        if (BackupConfig.dbIsNotIgnored("readRecord")) {
            fileToListT<ReadRecord>(path, "readRecord.json")?.let {
                it.forEach { readRecord ->
                    try {
                        restoreReadRecord(readRecord)
                    } catch (_: SQLiteConstraintException) {
                    }
                }
            }
            fileToListT<ReadRecordDetail>(path, "readRecordDetail.json")?.let {
                it.forEach { detail ->
                    try {
                        restoreReadRecordDetail(detail)
                    } catch (_: SQLiteConstraintException) {
                    }
                }
            }
            fileToListT<ReadRecordSession>(path, "readRecordSession.json")?.let {
                it.forEach { session ->
                    try {
                        restoreReadRecordSession(session)
                    } catch (_: SQLiteConstraintException) {
                    }
                }
            }
        }
        if (BackupConfig.dbIsNotIgnored("server")) {
            File(path, "servers.json").takeIf {
                it.exists()
            }?.runCatching {
                var json = readText()
                if (!json.isJsonArray()) {
                    json = aes.decryptStr(json)
                }
                GSON.fromJsonArray<Server>(json).getOrNull()?.let {
                    try {
                        appDb.serverDao.insert(*it.toTypedArray())
                    } catch (_: SQLiteConstraintException) {
                    }
                }
            }?.onFailure {
                AppLog.put("恢复服务器配置出错\n${it.localizedMessage}", it)
            }
        }
        File(path, DirectLinkUpload.ruleFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            ACache.get(cacheDir = false).put(DirectLinkUpload.ruleFileName, json)
        }?.onFailure {
            AppLog.put("恢复直链上传出错\n${it.localizedMessage}", it)
        }
        //恢复主题配置
        if (!BackupConfig.ignoreThemeConfig) {
            File(path, ThemeConfigStore.configFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ThemeConfigStore.configFilePath)
                copyTo(File(ThemeConfigStore.configFilePath))
                ThemeConfigStore.upConfig()
            }?.onFailure {
                AppLog.put("恢复主题出错\n${it.localizedMessage}", it)
            }
        }
        File(path, BookCover.configFileName).takeIf {
            it.exists() && !BackupConfig.ignoreCoverConfig
        }?.runCatching {
            val json = readText()
            BookCover.saveCoverRule(json)
        }?.onFailure {
            AppLog.put("恢复封面规则出错\n${it.localizedMessage}", it)
        }
        if (!BackupConfig.ignoreReadConfig) {
            //恢复阅读界面配置
            File(path, ReadBookConfig.configFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.configFilePath)
                copyTo(File(ReadBookConfig.configFilePath))
                ReadBookConfig.initConfigs()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
            File(path, ReadBookConfig.shareConfigFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.shareConfigFilePath)
                copyTo(File(ReadBookConfig.shareConfigFilePath))
                ReadBookConfig.initShareConfig()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
        }
        // 恢复配置文件 (手动解析 XML，替代反射逻辑)
        val configFile = File(path, "config.xml")
        if (configFile.exists()) {
            try {
                val map = readXmlToMap(configFile)
                if (map.isNotEmpty()) {
                    applyConfigMap(map, aes)
                }
            } catch (e: Exception) {
                AppLog.put("恢复配置 XML 出错\n${e.localizedMessage}", e)
            }
        }

        appCtx.toastOnUi(R.string.restore_success)
        withContext(Main) {
            delay(100)
            get<AppLocaleGateway>().setLanguage(OtherConfig.language)
            if (!BuildConfig.DEBUG) {
                LauncherIconHelp.changeIcon(appCtx.getPrefString(PreferKey.launcherIcon))
            }
            ThemeConfigStore.applyDayNight(appCtx)
        }
    }

    private fun localBookLocationStatus(bookUrl: String): LocalBookLocationStatus {
        val uri = bookUrl.takeIf { it.isUri() }?.toUri()
        if (uri?.isContentScheme() == true) {
            // Provider 离线、临时权限问题与文件确实删除无法可靠区分，失败时保守保留记录。
            return kotlin.runCatching {
                if (appCtx.contentResolver.openInputStream(uri)?.use { true } == true) {
                    LocalBookLocationStatus.Available
                } else {
                    LocalBookLocationStatus.Unknown
                }
            }.getOrDefault(LocalBookLocationStatus.Unknown)
        }

        val file = File(uri?.path ?: bookUrl)
        if (file.isFile) return LocalBookLocationStatus.Available
        return when (runCatching { Environment.getExternalStorageState(file) }.getOrNull()) {
            Environment.MEDIA_MOUNTED,
            Environment.MEDIA_MOUNTED_READ_ONLY -> LocalBookLocationStatus.Missing

            Environment.MEDIA_UNKNOWN -> LocalBookLocationStatus.Unknown
            null -> LocalBookLocationStatus.Unknown
            else -> LocalBookLocationStatus.Unknown
        }
    }

    private suspend fun restoreReadRecord(readRecord: ReadRecord) {
        val existing = appDb.readRecordDao.getReadRecord(
            readRecord.deviceId,
            readRecord.bookName,
            readRecord.bookAuthor
        )
        appDb.readRecordDao.insert(
            existing?.copy(
                readTime = maxOf(existing.readTime, readRecord.readTime),
                lastRead = maxOf(existing.lastRead, readRecord.lastRead)
            ) ?: readRecord
        )
    }

    private suspend fun restoreReadRecordDetail(detail: ReadRecordDetail) {
        val existing = appDb.readRecordDao.getDetail(
            detail.deviceId,
            detail.bookName,
            detail.bookAuthor,
            detail.date
        )
        appDb.readRecordDao.insertDetail(
            existing?.copy(
                readTime = maxOf(existing.readTime, detail.readTime),
                readWords = maxOf(existing.readWords, detail.readWords),
                firstReadTime = minPositive(existing.firstReadTime, detail.firstReadTime),
                lastReadTime = maxOf(existing.lastReadTime, detail.lastReadTime)
            ) ?: detail
        )
    }

    private suspend fun restoreReadRecordSession(session: ReadRecordSession) {
        val existing = appDb.readRecordDao.getSession(
            session.deviceId,
            session.bookName,
            session.bookAuthor,
            session.startTime,
            session.endTime
        )
        if (existing == null) {
            appDb.readRecordDao.insertSession(session)
        }
    }

    private fun minPositive(left: Long, right: Long): Long {
        return when {
            left <= 0L -> right
            right <= 0L -> left
            else -> minOf(left, right)
        }
    }

    private suspend fun applyConfigMap(map: Map<String, Any?>, aes: BackupAES) {
        val finalMap = normalizeConfigMap(
            map = map,
            keyIsNotIgnore = { BackupConfig.keyIsNotIgnore(it) },
            decryptWebDavPassword = { runCatching { aes.decryptStr(it) }.getOrNull() },
            hasLocalWebDavPassword = !appCtx.getPrefString(PreferKey.webDavPassword)
                .isNullOrBlank(),
        )
        // 经快照层批量恢复：立即对读侧生效（onRestoreFinish 的读取不再依赖回灌时机），单次原子 edit 落盘
        AppConfigStore.putAll(finalMap)
        // 恢复完成提示前等待落盘，dataStore.edit 返回即持久化完成
        SettingsWriter.awaitPendingWrites()
    }

    private fun readXmlToMap(file: File): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            FileInputStream(file).use { fis ->
                parser.setInput(fis, "UTF-8")
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val tagName = parser.name
                        val name = parser.getAttributeValue(null, "name")
                        if (name != null) {
                            when (tagName) {
                                "string" -> map[name] = parser.nextText()
                                "int" -> map[name] = parser.getAttributeValue(null, "value").toInt()
                                "long" -> map[name] = parser.getAttributeValue(null, "value").toLong()
                                "float" -> map[name] = parser.getAttributeValue(null, "value").toFloat()
                                "boolean" -> map[name] = parser.getAttributeValue(null, "value").toBoolean()
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private inline fun <reified T> fileToListT(path: String, fileName: String): List<T>? {
        try {
            val file = File(path, fileName)
            if (file.exists()) {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件大小 ${file.length()}")
                FileInputStream(file).use {
                    return GSON.fromJsonArray<T>(it).getOrThrow().also { list ->
                        LogUtils.d(TAG, "阅读恢复备份 $fileName 列表大小 ${list.size}")
                    }
                }
            } else {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件不存在")
            }
        } catch (e: Exception) {
            AppLog.put("$fileName\n读取解析出错\n${e.localizedMessage}", e)
            appCtx.toastOnUi("$fileName\n读取文件出错\n${e.localizedMessage}")
        }
        return null
    }

}
