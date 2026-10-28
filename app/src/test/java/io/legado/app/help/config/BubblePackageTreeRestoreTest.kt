package io.legado.app.help.config

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * 备份恢复的整树替换路径（[BubblePackageManager.replacePackageTree]）。
 *
 * 只覆盖「备份内容本身决定成败」的场景：整树替换、坏包整体拒绝、空备份拒绝。
 * 事务失败注入由 [BubbleDirectoryTransactionTest] 负责；「当前生效包回落到内置」需要设置网关，
 * 不在本类覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BubblePackageTreeRestoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var root: File

    @Before
    fun setUp() {
        root = folder.newFolder("bubblePackages")
    }

    @Test
    fun replacesWholeTreeWithBackupPackages() {
        writePackage(File(root, "old"), "Old")
        val staging = newStaging().also { writePackage(File(it, "demo"), "Demo") }

        val installed = runBlocking { BubblePackageManager.replacePackageTree(staging, root) }

        assertEquals(1, installed)
        assertTrue(File(root, "demo/bubble.json").isFile)
        assertFalse("整树替换应移除本地多出来的包", File(root, "old").exists())
        assertFalse("staging 必须被清理", staging.exists())
        assertTrue("不应留下备份/失败目录", root.listFiles().orEmpty().none { it.name.startsWith('.') })
    }

    @Test
    fun rejectsBackupWithInvalidPackageAndKeepsOldTree() {
        writePackage(File(root, "old"), "Old")
        val staging = newStaging().also {
            writePackage(File(it, "demo"), "Demo")
            File(it, "broken").mkdirs()
        }

        val error = runCatching {
            runBlocking { BubblePackageManager.replacePackageTree(staging, root) }
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue("坏包必须让整次恢复被拒绝", File(root, "old/bubble.json").isFile)
        assertFalse(File(root, "demo").exists())
        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith('.') })
    }

    @Test
    fun rejectsEmptyBackupTree() {
        writePackage(File(root, "old"), "Old")
        val staging = newStaging()

        val error = runCatching {
            runBlocking { BubblePackageManager.replacePackageTree(staging, root) }
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(File(root, "old/bubble.json").isFile)
    }

    private fun newStaging(): File =
        File(root.parentFile, ".bubble-test-staging-${UUID.randomUUID()}").apply { mkdirs() }

    private fun writePackage(dir: File, name: String) {
        dir.mkdirs()
        File(dir, "bubble.json").writeText(
            """{"name":"$name","dirName":"${dir.name}",""" +
                """"svgTemplate":"<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">""" +
                """<path d=\"M0 0h24v24H0z\"/></svg>","formatVersion":1}"""
        )
    }
}
