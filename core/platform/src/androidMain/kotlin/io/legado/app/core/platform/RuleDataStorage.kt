package io.legado.app.core.platform

import java.io.File
import java.security.MessageDigest

/**
 * [RuleDataStorage] 的 JVM 实现：委托 `java.io.File` 与 `MessageDigest`。
 *
 * androidMain 与 desktopMain 都是 JVM，故实现相同（同 [JvmFileSystem]/[JcaDigest] 的先例）。
 * 根目录由 host 设置，本实现只负责「根目录 + IO + MD5」。
 */
actual object RuleDataStorage {

    private var configuredRootDir: String? = null

    actual var rootDir: String
        get() = configuredRootDir ?: error(
            "RuleDataStorage.rootDir 未设置：请在 host 的 composition root 设置大变量存储根目录" +
                "（Android 为 Context.externalFiles/ruleData）。"
        )
        set(value) {
            configuredRootDir = value
        }

    actual fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        // 小写十六进制复用 CryptoCodecs，与 :app 的 CryptoUtils.toHexString 一致
        // （文件名必须逐字节兼容，既有数据才读得到）。
        return digest.encodeHexLower()
    }

    actual fun readText(relativePath: String): String? {
        val file = resolve(relativePath)
        return if (file.exists()) file.readText() else null
    }

    actual fun writeText(relativePath: String, text: String) {
        val file = resolve(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    actual fun exists(relativePath: String): Boolean = resolve(relativePath).exists()

    actual fun delete(relativePath: String, recursive: Boolean) {
        val file = resolve(relativePath)
        if (recursive) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    actual fun list(relativeDir: String): List<RuleDataEntry> {
        val children = resolve(relativeDir).listFiles() ?: return emptyList()
        return children.map { RuleDataEntry(it.name, it.isDirectory) }
    }

    private fun resolve(relativePath: String): File = File(rootDir, relativePath)
}
