package io.legado.app.core.platform

import java.io.File

/**
 * JVM 文件系统实现：精确移植 `app/.../utils/FileUtils.kt` 的 readBytes/writeBytes/
 * writeTextAtomic/copyFileAtomic 逻辑（行为经 `FileUtilsAtomicWriteTest` 验证）。
 * androidMain 与 desktopMain 都是 JVM，故实现相同。
 */
object JvmFileSystem : FileSystem {

    override fun readBytes(path: String): ByteArray? =
        runCatching { File(path).takeIf { it.exists() }?.readBytes() }.getOrNull()

    override fun writeBytes(path: String, data: ByteArray): Boolean = runCatching {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
        true
    }.getOrDefault(false)

    override fun exists(path: String): Boolean = File(path).exists()

    override fun delete(path: String): Boolean = File(path).delete()

    override fun makeDirs(path: String): Boolean = File(path).mkdirs()

    override fun writeTextAtomic(path: String, text: String) {
        replaceAtomic(path) { it.writeText(text) }
    }

    override fun copyFileAtomic(sourcePath: String, targetPath: String) {
        val source = File(sourcePath)
        replaceAtomic(targetPath) { source.copyTo(it, overwrite = true) }
    }

    /**
     * 原子替换：先写 `targetPath.tmp`，再 rename 覆盖目标；rename 不覆盖已存在目标时
     * 退回原地写目标并删临时文件。与 FileUtils.replaceAtomic 行为一致。
     */
    private inline fun replaceAtomic(targetPath: String, produce: (File) -> Unit) {
        val target = File(targetPath)
        val temp = File("$targetPath.tmp")
        target.parent?.let { File(it).mkdirs() }
        produce(temp)
        if (!temp.renameTo(target)) {
            produce(target)
            temp.delete()
        }
    }
}
