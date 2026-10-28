package io.legado.app.help.config

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 文件关联分派靠「压缩包里有没有 bubble.json」把气泡包从一堆 zip 里认出来，
 * 这里钉住这个判定——认错会把用户的小说/备份包吞成气泡包。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BubblePackageArchiveTest {

    @JvmField
    @Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Application>()
    }

    @Test
    fun recognizesZipContainingManifest() {
        val zip = zipOf("bubblePackages/demo" to "{}", "bubble.json" to "{}")

        assertTrue(BubblePackageManager.isBubblePackageZip(FileInputStream(zip)))
    }

    @Test
    fun recognizesManifestNestedInPackageDirectory() {
        val zip = zipOf("demo/bubble.json" to "{}", "demo/assets/star.svg" to "<svg/>")

        assertTrue(BubblePackageManager.isBubblePackageZip(FileInputStream(zip)))
    }

    @Test
    fun leavesOrdinaryZipAlone() {
        val zip = zipOf("chapter.txt" to "正文", "toc.json" to "[]")

        assertFalse(BubblePackageManager.isBubblePackageZip(FileInputStream(zip)))
    }

    @Test
    fun survivesGarbageInput() {
        val garbage = tempFolder.newFile("garbage.zip")
        garbage.writeBytes(byteArrayOf(0x1, 0x2, 0x3))

        assertFalse(BubblePackageManager.isBubblePackageZip(FileInputStream(garbage)))
    }

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val zip = tempFolder.newFile("package-${System.nanoTime()}.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            entries.forEach { (name, content) ->
                val normalized = if (name.endsWith("/")) name else name
                out.putNextEntry(ZipEntry(normalized))
                if (!normalized.endsWith("/")) {
                    out.write(content.toByteArray())
                }
                out.closeEntry()
            }
        }
        return zip
    }
}
