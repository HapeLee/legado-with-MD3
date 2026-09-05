package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * FileSystem 契约测试基类：每个 target（androidHostTest / desktopTest）提供
 * [createFileSystem] 与 [createTempDir] 的具体实现，证明 JvmFileSystem 在该 target
 * 满足共享契约。场景移植自 `app/.../FileUtilsAtomicWriteTest`。
 */
abstract class FileSystemContractTest {

    abstract fun createFileSystem(): FileSystem

    /** 返回一个该 target 可写的临时目录绝对路径，测试独占。 */
    abstract fun createTempDir(): String

    private fun path(name: String): String =
        "${createTempDir()}/$name"

    @Test
    fun `writeBytes then readBytes round-trips`() {
        val fs = createFileSystem()
        val p = path("data.bin")
        val data = byteArrayOf(1, 2, 3, 4, 5)
        assertTrue(fs.writeBytes(p, data))
        assertEquals(data.toList(), fs.readBytes(p)?.toList())
    }

    @Test
    fun `readBytes on missing path returns null`() {
        val fs = createFileSystem()
        assertNull(fs.readBytes(path("does-not-exist.bin")))
    }

    @Test
    fun `exists reflects write and delete`() {
        val fs = createFileSystem()
        val p = path("exists.txt")
        assertFalse(fs.exists(p))
        fs.writeBytes(p, "x".toByteArray())
        assertTrue(fs.exists(p))
        assertTrue(fs.delete(p))
        assertFalse(fs.exists(p))
    }

    @Test
    fun `makeDirs creates nested directories`() {
        val fs = createFileSystem()
        val dir = path("a/b/c")
        fs.makeDirs(dir)
        assertTrue(fs.exists(dir))
    }

    @Test
    fun `writeTextAtomic writes a new file`() {
        val fs = createFileSystem()
        val p = path("readConfig.json")
        fs.writeTextAtomic(p, "[1,2,3]")
        assertEquals("[1,2,3]", String(fs.readBytes(p)!!))
    }

    @Test
    fun `writeTextAtomic overwrites existing content`() {
        val fs = createFileSystem()
        val p = path("readConfig.json")
        fs.writeBytes(p, "old".toByteArray())
        fs.writeTextAtomic(p, "new")
        assertEquals("new", String(fs.readBytes(p)!!))
    }

    @Test
    fun `writeTextAtomic leaves no temp file`() {
        val fs = createFileSystem()
        val p = path("readConfig.json")
        fs.writeTextAtomic(p, "content")
        assertFalse(fs.exists("$p.tmp"))
    }

    @Test
    fun `writeTextAtomic failure keeps old content`() {
        val fs = createFileSystem()
        val p = path("readConfig.json")
        fs.writeBytes(p, "old".toByteArray())
        // 把临时文件路径预先占成目录，逼 writeText 抛异常，模拟写到一半失败
        fs.makeDirs("$p.tmp")
        runCatching { fs.writeTextAtomic(p, "new") }
        assertEquals("old", String(fs.readBytes(p)!!))
    }

    @Test
    fun `copyFileAtomic overwrites existing target`() {
        val fs = createFileSystem()
        val src = path("backup.json")
        fs.writeBytes(src, "backup".toByteArray())
        val tgt = path("themeConfig.json")
        fs.writeBytes(tgt, "old".toByteArray())
        fs.copyFileAtomic(src, tgt)
        assertEquals("backup", String(fs.readBytes(tgt)!!))
        assertFalse(fs.exists("$tgt.tmp"))
    }

    @Test
    fun `copyFileAtomic failure keeps old target`() {
        val fs = createFileSystem()
        val tgt = path("themeConfig.json")
        fs.writeBytes(tgt, "old".toByteArray())
        runCatching { fs.copyFileAtomic(path("missing-backup.json"), tgt) }
        assertEquals("old", String(fs.readBytes(tgt)!!))
    }
}
