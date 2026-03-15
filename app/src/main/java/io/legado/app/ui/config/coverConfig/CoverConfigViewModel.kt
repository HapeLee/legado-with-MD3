package io.legado.app.ui.config.coverConfig

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.exception.NoStackTraceException
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.model.BookCover
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.externalFiles
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class CoverConfigViewModel : ViewModel() {

    fun addCoverFromUri(preferenceKey: String, uris: List<Uri>) {
        uris.forEach { uri ->
            readUri(appCtx, uri) { fileDoc, _ ->
                kotlin.runCatching {
                    var file = appCtx.externalFiles
                    val suffix = fileDoc.name.substringAfterLast(".")
                    val inputStreamForMd5 = appCtx.contentResolver.openInputStream(uri)
                        ?: throw NoStackTraceException("无法打开输入流")
                    val fileName = MD5Utils.md5Encode(inputStreamForMd5) + ".$suffix"
                    file = FileUtils.createFileIfNotExist(file, "covers", fileName)
                    FileOutputStream(file).use {
                        appCtx.contentResolver.openInputStream(uri)?.use { input ->
                            input.copyTo(it)
                        }
                    }
                    val currentCovers = if (preferenceKey == PreferKey.defaultCover) {
                        CoverConfig.defaultCover
                    } else {
                        CoverConfig.defaultCoverDark
                    }
                    val newList =
                        currentCovers.split(",").filter { it.isNotBlank() }.toMutableList()
                    if (!newList.contains(file.absolutePath)) {
                        newList.add(file.absolutePath)
                    }
                    val newCovers = newList.joinToString(",")
                    if (preferenceKey == PreferKey.defaultCover) {
                        CoverConfig.defaultCover = newCovers
                    } else {
                        CoverConfig.defaultCoverDark = newCovers
                    }
                    BookCover.upDefaultCover()
                }.onFailure {
                    appCtx.toastOnUi(it.localizedMessage)
                }
            }
        }
    }

    private fun readUri(
        context: Context,
        uri: Uri?,
        success: (fileDoc: FileDoc, inputStream: InputStream) -> Unit
    ) {
        uri ?: return
        try {
            if (uri.isContentScheme()) {
                val doc = DocumentFile.fromSingleUri(context, uri)
                doc ?: throw NoStackTraceException("未获取到文件")
                val fileDoc = FileDoc.fromDocumentFile(doc)
                context.contentResolver.openInputStream(uri)!!.use { inputStream ->
                    success.invoke(fileDoc, inputStream)
                }
            } else {
                PermissionsCompat.Builder()
                    .addPermissions(*Permissions.Group.STORAGE)
                    .onGranted {
                        RealPathUtil.getPath(context, uri)?.let { path ->
                            val file = File(path)
                            val fileDoc = FileDoc.fromFile(file)
                            FileInputStream(file).use { inputStream ->
                                success.invoke(fileDoc, inputStream)
                            }
                        }
                    }
                    .request()
            }
        } catch (e: Exception) {
            e.printOnDebug()
            AppLog.put("读取Uri出错\n$e", e, true)
        }
    }

    fun removeCover(preferenceKey: String, path: String) {
        val currentCovers = if (preferenceKey == PreferKey.defaultCover) {
            CoverConfig.defaultCover
        } else {
            CoverConfig.defaultCoverDark
        }
        val newList = currentCovers.split(",").filter { it.isNotBlank() && it != path }
        val newCovers = newList.joinToString(",")
        if (preferenceKey == PreferKey.defaultCover) {
            CoverConfig.defaultCover = newCovers
        } else {
            CoverConfig.defaultCoverDark = newCovers
        }
        BookCover.upDefaultCover()
    }

    fun updateShowName(show: Boolean, isNight: Boolean = false) {
        if (isNight) {
            CoverConfig.coverShowNameN = show
        } else {
            CoverConfig.coverShowName = show
        }
        BookCover.upDefaultCover()
    }

    fun updateShowAuthor(show: Boolean, isNight: Boolean = false) {
        if (isNight) {
            CoverConfig.coverShowAuthorN = show
        } else {
            CoverConfig.coverShowAuthor = show
        }
        BookCover.upDefaultCover()
    }

    fun updateCoverStyle() {
        BookCover.upDefaultCover()
    }
}
