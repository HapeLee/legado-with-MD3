package io.legado.app.web.utils

import android.content.res.AssetManager
import android.text.TextUtils
import splitties.init.appCtx
import java.io.File
import java.io.InputStream


class AssetsWeb(rootPath: String) {
    private val assetManager: AssetManager = appCtx.assets
    private var rootPath = "web"

    init {
        if (!TextUtils.isEmpty(rootPath)) {
            this.rootPath = rootPath
        }
    }

    fun getInputStream(path: String): InputStream? {
        val path1 = (rootPath + path).replace("/+".toRegex(), File.separator)
        return try {
            assetManager.open(path1)
        } catch (e: Exception) {
            null
        }
    }

    fun getMimeType(path: String): String {
        val lastDot = path.lastIndexOf(".")
        if (lastDot == -1) return "text/html"
        val suffix = path.substring(lastDot)
        return when {
            suffix.equals(".html", ignoreCase = true)
                    || suffix.equals(".htm", ignoreCase = true) -> "text/html"
            suffix.equals(".js", ignoreCase = true) -> "text/javascript"
            suffix.equals(".css", ignoreCase = true) -> "text/css"
            suffix.equals(".ico", ignoreCase = true) -> "image/x-icon"
            suffix.equals(".jpg", ignoreCase = true) -> "image/jpg"
            suffix.equals(".png", ignoreCase = true) -> "image/png"
            suffix.equals(".svg", ignoreCase = true) -> "image/svg+xml"
            else -> "text/html"
        }
    }
}
