package io.legado.app.ui.config.themeConfig

import android.net.Uri
import androidx.lifecycle.ViewModel
import io.legado.app.constant.PreferKey
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.inputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream

class ThemeConfigViewModel : ViewModel() {

    /**
     * 设置背景图片
     */
    suspend fun setBackgroundFromUri(
        uri: Uri,
        preferenceKey: String
    ) = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            val fileDoc = FileDoc.fromUri(uri, false)
            val suffix = fileDoc.name.substringAfterLast(".", "jpg")

            // 计算MD5作为文件名
            val md5 = uri.inputStream(appCtx).getOrThrow().use {
                MD5Utils.md5Encode(it)
            }
            val fileName = "$md5.$suffix"

            val folder = File(appCtx.externalFiles, preferenceKey)
            val file = File(folder, fileName)

            if (!file.exists()) {
                FileUtils.createFileIfNotExist(file.absolutePath)
                uri.inputStream(appCtx).getOrThrow().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            updateBackgroundPath(preferenceKey, file.absolutePath)
        }.onFailure {
            it.printStackTrace()
        }
    }

    fun removeBackground(preferenceKey: String) {
        updateBackgroundPath(preferenceKey, null)
    }

    /**
     * 更新背景路径并清理旧文件
     */
    private fun updateBackgroundPath(preferenceKey: String, newPath: String?) {
        val oldPath = when (preferenceKey) {
            PreferKey.bgImage -> ThemeConfig.bgImageLight
            PreferKey.bgImageN -> ThemeConfig.bgImageDark
            else -> null
        }

        if (oldPath != newPath && oldPath != null) {
            val oldFile = File(oldPath)
            if (oldFile.absolutePath.contains(appCtx.externalFiles.absolutePath)) {
                oldFile.delete()
            }
        }

        when (preferenceKey) {
            PreferKey.bgImage -> ThemeConfig.bgImageLight = newPath
            PreferKey.bgImageN -> ThemeConfig.bgImageDark = newPath
        }
    }
}