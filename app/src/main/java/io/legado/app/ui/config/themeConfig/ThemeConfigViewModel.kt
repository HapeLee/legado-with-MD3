package io.legado.app.ui.config.themeConfig

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.PreferKey
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.inputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream

class ThemeConfigViewModel(
    private val localPreferencesRepository: LocalPreferencesRepository
) : ViewModel() {

    val showThemeRefactorTip = localPreferencesRepository
        .getPreference(LocalPreferencesKeys.SHOW_THEME_REFACTOR_TIP, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setShowThemeRefactorTip(show: Boolean) {
        viewModelScope.launch {
            localPreferencesRepository.updatePreference(
                LocalPreferencesKeys.SHOW_THEME_REFACTOR_TIP,
                show
            )
        }
    }

    /**
     * 设置背景图片
     */
    suspend fun setBackgroundFromUri(
        uri: Uri,
        isDarkTheme: Boolean
    ) = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            val fileDoc = FileDoc.fromUri(uri, false)
            val suffix = fileDoc.name.substringAfterLast(".", "jpg")

            // 计算MD5作为文件名
            val md5 = uri.inputStream(appCtx).getOrThrow().use {
                MD5Utils.md5Encode(it)
            }
            val fileName = "$md5.$suffix"

            val preferenceKey = if (isDarkTheme) PreferKey.bgImageN else PreferKey.bgImage
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

            updateBackgroundPath(isDarkTheme, file.absolutePath)
        }.onFailure {
            it.printStackTrace()
        }
    }

    fun removeBackground(isDarkTheme: Boolean) {
        updateBackgroundPath(isDarkTheme, null)
    }

    /**
     * 更新背景路径并清理旧文件
     */
    private fun updateBackgroundPath(isDarkTheme: Boolean, newPath: String?) {
        val oldPath = if (isDarkTheme) {
            ThemeConfig.bgImageDark
        } else {
            ThemeConfig.bgImageLight
        }

        if (oldPath != newPath && oldPath != null) {
            val oldFile = File(oldPath)
            if (oldFile.absolutePath.contains(appCtx.externalFiles.absolutePath)) {
                oldFile.delete()
            }
        }

        if (isDarkTheme) {
            ThemeConfig.bgImageDark = newPath
        } else {
            ThemeConfig.bgImageLight = newPath
        }
    }
}
