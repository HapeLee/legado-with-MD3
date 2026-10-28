package io.legado.app.ui.association

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.AppPattern.bookFileRegex
import io.legado.app.data.entities.Book
import io.legado.app.help.config.BubblePackageArchive
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.*
import java.io.File

class FileAssociationViewModel(application: Application) : BaseAssociationViewModel(application) {
    val importBookLiveData = MutableLiveData<Uri>()
    val onLineImportLive = MutableLiveData<Uri>()
    val openBookLiveData = MutableLiveData<Book>()
    val notSupportedLiveData = MutableLiveData<Pair<Uri, String>>()

    fun dispatchIntent(uri: Uri) {
        execute {
            //如果是普通的url，需要根据返回的内容判断是什么
            if (uri.isContentScheme() || uri.isFileScheme()) {
                val fileDoc = FileDoc.fromUri(uri, false)
                val fileName = fileDoc.name
                if (fileName.matches(AppPattern.archiveFileRegex)) {
                    // 气泡包也是 zip：先看里面有没有 bubble.json，是就按气泡包导入，不再当书籍压缩包拆。
                    val zipFile = fileDoc.asFile() ?: runCatching {
                        val temp = FileUtils.createFileIfNotExist(
                            File(ArchiveUtils.TEMP_PATH),
                            "bubble_${System.currentTimeMillis()}.zip"
                        )
                        fileDoc.openInputStream().getOrThrow().use { input ->
                            temp.outputStream().use { output -> input.copyTo(output) }
                        }
                        temp
                    }.getOrNull()
                    if (zipFile != null && BubblePackageArchive.isBubblePackage(zipFile)) {
                        BubblePackageManager.importZip(zipFile)
                        successLive.postValue("bubblePackage" to zipFile.toUri().toString())
                        return@execute
                    }
                    ArchiveUtils.deCompress(fileDoc, ArchiveUtils.TEMP_PATH) {
                        it.matches(bookFileRegex)
                    }.forEach {
                        dispatch(FileDoc.fromFile(it))
                    }
                } else {
                    dispatch(fileDoc)
                }
            } else {
                onLineImportLive.postValue(uri)
            }
        }.onError {
            it.printOnDebug()
            val msg = "无法打开文件\n${it.localizedMessage}"
            errorLive.postValue(msg)
            AppLog.put(msg, it)
        }
    }

    private fun dispatch(fileDoc: FileDoc) {
        kotlin.runCatching {
            if (fileDoc.openInputStream().getOrNull().isJson()) {
                importJson(fileDoc.uri)
                return
            }
        }.onFailure {
            it.printOnDebug()
            AppLog.put("尝试导入为JSON文件失败\n${it.localizedMessage}", it)
        }
        if (fileDoc.name.matches(bookFileRegex)) {
            importBookLiveData.postValue(fileDoc.uri)
            return
        }
        notSupportedLiveData.postValue(Pair(fileDoc.uri, fileDoc.name))
    }

    fun importBook(uri: Uri) {
        val book = LocalBook.importFile(uri)
        openBookLiveData.postValue(book)
    }
}