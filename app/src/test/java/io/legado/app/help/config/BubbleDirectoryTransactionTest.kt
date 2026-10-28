package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * 气泡包目录事务的失败注入测试（对齐 Legado-Arc 同名测试的事务层场景）。
 *
 * 事务只有两次 move：`target → backup`、`staging → target`；回滚再各一次，因此
 * [FailingMovesExchange] 用「第几次 move 失败」来定位失败点。
 */
class BubbleDirectoryTransactionTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun replacesExistingDirectoryAndRemovesBackup() {
        val root = folder.newFolder("bubblePackages")
        val target = writePackage(File(root, "demo"), "old")
        val staging = writePackage(folder.newFolder("staging"), "new")
        val backup = File(root, ".demo.backup")

        val result = BubbleDirectoryTransaction().install(target, staging, backup) { installed ->
            assertEquals("new", readPackage(installed))
            7
        }

        assertEquals(7, result)
        assertEquals("new", readPackage(target))
        assertFalse("staging 必须被清理", staging.exists())
        assertFalse("成功安装后备份必须删除", backup.exists())
        assertEquals(listOf("demo"), root.list()!!.toList())
    }

    @Test
    fun createsTargetWithoutBackupWhenNoneExists() {
        val root = folder.newFolder("bubblePackages")
        val target = File(root, "demo")
        val staging = writePackage(folder.newFolder("staging"), "new")
        val backup = File(root, ".demo.backup")

        BubbleDirectoryTransaction().install(target, staging, backup) { 1 }

        assertEquals("new", readPackage(target))
        assertFalse("没有旧目录时不应产生备份", backup.exists())
        assertFalse(staging.exists())
    }

    @Test
    fun restoresExistingDirectoryWhenInstalledPackageFailsVerification() {
        val root = folder.newFolder("bubblePackages")
        val target = writePackage(File(root, "demo"), "old")
        val staging = writePackage(folder.newFolder("staging"), "new")
        val backup = File(root, ".demo.backup")

        val error = runCatching {
            BubbleDirectoryTransaction().install(target, staging, backup) {
                throw IllegalStateException("manifest mismatch")
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("旧目录必须原样恢复", "old", readPackage(target))
        assertFalse(backup.exists())
        assertEquals(listOf("demo"), root.list()!!.toList())
    }

    @Test
    fun removesNewTargetWhenVerificationFailsWithoutBackup() {
        val root = folder.newFolder("bubblePackages")
        val target = File(root, "demo")
        val staging = writePackage(folder.newFolder("staging"), "new")
        val backup = File(root, ".demo.backup")

        val error = runCatching {
            BubbleDirectoryTransaction().install(target, staging, backup) {
                throw IllegalStateException("manifest mismatch")
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertFalse("没有旧目录可回滚时必须删掉新装的目录", target.exists())
        assertFalse(staging.exists())
        assertEquals(emptyList<String>(), root.list()!!.toList())
    }

    @Test
    fun restoresExistingDirectoryWhenStagingMoveFails() {
        val root = folder.newFolder("bubblePackages")
        val target = writePackage(File(root, "demo"), "old")
        val staging = writePackage(folder.newFolder("staging"), "new")
        val backup = File(root, ".demo.backup")

        // move#1 = target → backup；move#2 = staging → target（注入失败）；move#3 = 回滚
        val error = runCatching {
            BubbleDirectoryTransaction(FailingMovesExchange(setOf(2)))
                .install(target, staging, backup) { 1 }
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertFalse(error is BubbleDirectoryRestoreException)
        assertEquals("旧目录必须回滚", "old", readPackage(target))
        assertFalse(backup.exists())
        assertFalse("失败的 staging 也必须被清理", staging.exists())
    }

    @Test
    fun failedTransactionRecoveryKeepsItsOnlyBackup() {
        val root = folder.newFolder("bubblePackages")
        val target = writePackage(File(root, "demo"), "old")
        val staging = writePackage(folder.newFolder("staging"), "new")
        val backup = File(root, ".demo.backup")

        // move#2（staging → target）与 move#3（backup → target）同时失败：唯一副本只能留在 backup。
        val error = runCatching {
            BubbleDirectoryTransaction(FailingMovesExchange(setOf(2, 3)))
                .install(target, staging, backup) { 1 }
        }.exceptionOrNull()

        assertTrue(error is BubbleDirectoryRestoreException)
        assertFalse(target.exists())
        assertTrue("备份目录必须保留", backup.exists())
        assertEquals("old", readPackage(backup))
        val exception = error as BubbleDirectoryRestoreException
        assertEquals(backup.absolutePath, exception.backupDir.absolutePath)
        assertTrue(exception.cause is IOException)
    }

    @Test
    fun keepsSiblingPackagesWhenInstalling() {
        val root = folder.newFolder("bubblePackages")
        val sibling = writePackage(File(root, "other"), "keep")
        val target = File(root, "demo")
        val staging = writePackage(folder.newFolder("staging"), "new")

        BubbleDirectoryTransaction().install(target, staging, File(root, ".demo.backup")) { 1 }

        assertEquals("new", readPackage(target))
        assertEquals("兄弟包不能被误删", "keep", readPackage(sibling))
        assertEquals(setOf("other", "demo"), root.list()!!.toSet())
    }

    private fun writePackage(dir: File, content: String): File {
        dir.mkdirs()
        File(dir, "bubble.json").writeText("""{"name":"demo"}""")
        File(dir, "content.txt").writeText(content)
        return dir
    }

    private fun readPackage(dir: File): String = File(dir, "content.txt").readText()

    /** 第 N 次 move 抛 IO；其余委托给真实实现。 */
    private class FailingMovesExchange(private val failures: Set<Int>) : FileExchange {
        private var moves = 0

        override fun move(source: File, target: File) {
            moves += 1
            if (moves in failures) throw IOException("injected move failure #$moves")
            NioFileExchange.move(source, target)
        }

        override fun delete(file: File) = NioFileExchange.delete(file)
    }
}
