package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * R4.2 —— 排版配置的写盘必须原子。
 *
 * 原实现是「先 `FileUtils.delete`，再 `createFileIfNotExist().writeText()`」：两步之间目标
 * 文件根本不存在，进程在此时被杀，用户的整套排版方案就没了。两条保存通道
 * （`ReadStyleConfigStore.save()` 与 `ReadStyleSaveQueue`）还会并发落到同一个文件上。
 */
class FileUtilsAtomicWriteTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `写入新文件`() {
        val path = File(folder.root, "readConfig.json").absolutePath

        FileUtils.writeTextAtomic(path, "[1,2,3]")

        assertEquals("[1,2,3]", File(path).readText())
    }

    @Test
    fun `覆盖已有文件`() {
        val target = folder.newFile("readConfig.json")
        target.writeText("旧内容")

        FileUtils.writeTextAtomic(target.absolutePath, "新内容")

        assertEquals("新内容", target.readText())
    }

    @Test
    fun `不留下临时文件`() {
        val path = File(folder.root, "readConfig.json").absolutePath

        FileUtils.writeTextAtomic(path, "内容")

        assertFalse("临时文件应已被 rename 掉", File("$path.tmp").exists())
    }

    @Test
    fun `写入中途失败时目标文件保持旧内容`() {
        val target = folder.newFile("readConfig.json")
        target.writeText("旧内容")
        // 把临时文件路径预先占成目录，逼 writeText 抛异常，模拟写到一半没写成
        File("${target.absolutePath}.tmp").mkdirs()

        runCatching { FileUtils.writeTextAtomic(target.absolutePath, "新内容") }

        assertEquals(
            "写失败后目标必须还是完整的旧内容——先删后写会在这里丢掉整套排版配置",
            "旧内容",
            target.readText(),
        )
    }

    @Test
    fun `原子复制覆盖已有文件`() {
        val source = folder.newFile("backup.json")
        source.writeText("备份内容")
        val target = folder.newFile("themeConfig.json")
        target.writeText("旧内容")

        FileUtils.copyFileAtomic(source, target.absolutePath)

        assertEquals("备份内容", target.readText())
        assertFalse("临时文件应已被 rename 掉", File("${target.absolutePath}.tmp").exists())
    }

    @Test
    fun `复制失败时目标文件保持旧内容`() {
        // 源不存在——恢复备份途中备份文件消失
        val source = File(folder.root, "missing-backup.json")
        val target = folder.newFile("themeConfig.json")
        target.writeText("旧内容")

        runCatching { FileUtils.copyFileAtomic(source, target.absolutePath) }

        assertEquals(
            "恢复备份失败后目标必须还是旧配置——先删后复制会让用户两头空",
            "旧内容",
            target.readText(),
        )
    }
}
