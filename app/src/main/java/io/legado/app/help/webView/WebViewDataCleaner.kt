package io.legado.app.help.webView

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import io.legado.app.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

object WebViewDataCleaner {

    suspend fun clear(context: Context) {
        withContext(Dispatchers.Main.immediate) {
            clearPlatformData(context.applicationContext)
        }

        val directoriesCleared = withContext(Dispatchers.IO) {
            clearDataDirectories(
                webViewDirectory = context.getDir("webview", Context.MODE_PRIVATE),
                huaweiWebViewDirectory = context.getDir("hws_webview", Context.MODE_PRIVATE),
            )
        }
        if (!directoriesCleared) {
            throw IOException("Unable to delete WebView data directories")
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
): Boolean {
    FileUtils.delete(webViewDirectory)
    FileUtils.delete(huaweiWebViewDirectory, deleteRootDir = true)
    return webViewDirectory.isMissingOrEmpty() && huaweiWebViewDirectory.isMissingOrEmpty()
}

private fun File.isMissingOrEmpty(): Boolean {
    return !exists() || isDirectory && listFiles()?.isEmpty() == true
}
