package io.legado.app.help.webView

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

object WebViewDataCleaner {

    suspend fun clear(context: Context) {
        withContext(Dispatchers.Main.immediate) {
            clearPlatformData(context.applicationContext)
        }

        withContext(Dispatchers.IO) {
            val dataDirectory = File(context.applicationInfo.dataDir)
            clearDataDirectories(
<<<<<<< Updated upstream
                webViewDirectory = File(context.applicationInfo.dataDir, "app_webview"),
                huaweiWebViewDirectory = File(context.applicationInfo.dataDir, "app_hws_webview"),
=======
                webViewDirectory = File(dataDirectory, "app_webview"),
                huaweiWebViewDirectory = File(dataDirectory, "app_hws_webview"),
>>>>>>> Stashed changes
            )
        }
    }

    private suspend fun clearPlatformData(context: Context) {
        val cookieManager = CookieManager.getInstance()
        suspendCancellableCoroutine { continuation ->
            cookieManager.removeAllCookies {
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
        cookieManager.flush()
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(context).apply {
            clearFormData()
            clearHttpAuthUsernamePassword()
        }
    }
}

internal fun clearDataDirectories(
    webViewDirectory: File,
    huaweiWebViewDirectory: File,
<<<<<<< Updated upstream
): Boolean {
    val webViewCleared = webViewDirectory.clearContents()
    val huaweiWebViewCleared = huaweiWebViewDirectory.deleteDirectory()
    return webViewCleared && huaweiWebViewCleared
}

private fun File.clearContents(): Boolean {
    if (!exists()) return true
    if (!isDirectory) return false

    val children = listFiles() ?: return false
    val childrenDeleted = children.fold(true) { success, child ->
        child.deleteRecursively() && success
    }
    return childrenDeleted && listFiles()?.isEmpty() == true
}

private fun File.deleteDirectory(): Boolean {
    if (!exists()) return true
    if (!isDirectory) return false
    return deleteRecursively() && !exists()
=======
) {
    FileUtils.delete(webViewDirectory)
    FileUtils.delete(huaweiWebViewDirectory, deleteRootDir = true)
>>>>>>> Stashed changes
}
