package io.legado.app.ui.book.read

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.repository.ReadSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException

/**
 * 自定义书签角标的落盘与清除。
 *
 * 选择图片后把 content:// 拷贝进应用私有目录（免 SAF 授权、重启不失效），再写进
 * [ReadSettingsRepository]；因为写盘是异步入队，刷新视图前先用 `preferences.first{}`
 * 等新值落地，否则 `upStyle` 会读到旧路径回退成默认书签。
 */
class BookmarkBadgeDelegate(
    private val scope: CoroutineScope,
    private val context: Context,
    private val readSettingsRepository: ReadSettingsRepository,
    private val emitEffect: (ReadBookEffect) -> Unit,
) {

    fun applyBadgeImage(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val path = copyToAppStorage(uri)
                readSettingsRepository.update { it.copy(bookmarkBadgeImage = path) }
                path
            }.onSuccess { path ->
                readSettingsRepository.preferences.first { it.bookmarkBadgeImage == path }
                emitEffect(ReadBookEffect.UpdateReadViewConfig(setOf(ConfigUpdateAction.UpdateStyle)))
                emitEffect(ReadBookEffect.ShowToast(context.getString(R.string.success)))
            }.onFailure { throwable ->
                AppLog.put("选择书签角标失败", throwable)
                emitEffect(
                    ReadBookEffect.LongToast(
                        throwable.localizedMessage ?: context.getString(R.string.error)
                    )
                )
            }
        }
    }

    fun clearBadgeImage() {
        scope.launch(Dispatchers.IO) {
            val oldPath = runCatching {
                readSettingsRepository.preferences.first().bookmarkBadgeImage
            }.getOrDefault("")
            oldPath.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
            readSettingsRepository.update { it.copy(bookmarkBadgeImage = "") }
            readSettingsRepository.preferences.first { it.bookmarkBadgeImage.isEmpty() }
            emitEffect(ReadBookEffect.UpdateReadViewConfig(setOf(ConfigUpdateAction.UpdateStyle)))
        }
    }

    private fun copyToAppStorage(uri: Uri): String {
        val name = queryDisplayName(uri)
        val ext = name?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() && it.length <= 6 } ?: "img"
        val target = File(context.filesDir, "bookmark_badge.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw FileNotFoundException(uri.toString())
        return target.absolutePath
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }
}
