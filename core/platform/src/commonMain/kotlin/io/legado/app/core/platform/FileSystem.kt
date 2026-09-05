package io.legado.app.core.platform

/**
 * 文件系统访问契约：共享层只见 [String] 路径与 [ByteArray]，不暴露 `java.io.File` / `Uri` / `Context`。
 * 见 AGENTS.md「新的共享领域契约不得暴露 File/Uri/Context，用领域值、ByteArray/抽象 source-sink」。
 *
 * P1 第二个契约。首个真实消费方是 `:app` 的 `FileUtils`（原子写文本/复制，经
 * `FileUtilsAtomicWriteTest` 覆盖）。122 文件的全量 `java.io.File` 迁移归 P2 分批进行。
 *
 * 路径穿越检查由调用方在传入路径前完成；本契约不重新校验，避免与既有路径构造重复
 * （参照样本纪律 7：保留路径穿越检查在构造层）。
 */
interface FileSystem {
    /** 读取整个文件为 [ByteArray]；文件不存在或读取失败返回 null。 */
    fun readBytes(path: String): ByteArray?

    /** 写入字节；父目录不存在则创建。成功返回 true。 */
    fun writeBytes(path: String, data: ByteArray): Boolean

    /** 文件或目录是否存在。 */
    fun exists(path: String): Boolean

    /** 删除文件或空目录；成功返回 true。递归删除由调用方在更高层负责。 */
    fun delete(path: String): Boolean

    /** 创建多级目录（已存在则无操作）；成功或已存在返回 true。 */
    fun makeDirs(path: String): Boolean

    /**
     * 原子写文本：先写 `path.tmp` 临时文件，再 rename 覆盖 `path`；rename 不覆盖时退回原地写。
     * 失败时目标保持旧内容。不做 fsync——只保证进程被杀不丢，不保证掉电不丢。
     */
    fun writeTextAtomic(path: String, text: String)

    /** 原子复制覆盖：理由同 [writeTextAtomic]；源不存在等失败时目标保持旧内容。 */
    fun copyFileAtomic(sourcePath: String, targetPath: String)
}
