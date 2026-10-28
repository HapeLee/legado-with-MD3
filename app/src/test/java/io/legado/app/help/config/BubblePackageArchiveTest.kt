package io.legado.app.help.config

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BubblePackageArchiveTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun zip(name: String, vararg entries: Pair<String, String>): File {
        val file = temporaryFolder.newFile(name)
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (path, content) ->
                output.putNextEntry(ZipEntry(path))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
        return file
    }

    @Test
    fun extractsManifestAndResources() {
        val zip = zip(
            "ok.zip",
            "bubble.json" to """{"name":"p","svgTemplate":"<svg/>"}""",
            "assets/font.ttf" to "ttf",
        )
        val destination = File(temporaryFolder.root, "out")
        val result = BubblePackageArchive.extract(zip, destination)
        assertEquals("bubble.json", result.manifestFile.name)
        assertTrue(File(destination, "assets/font.ttf").isFile)
    }

    @Test
    fun detectsBubblePackageByManifestName() {
        val zip = zip(
            "detect.zip",
            "pack/bubble.json" to "{}",
        )
        assertTrue(BubblePackageArchive.isBubblePackage(zip))
        assertFalse(
            BubblePackageArchive.isBubblePackage(
                zip("other.zip", "bookshelf.json" to "[]")
            )
        )
    }

    @Test(expected = Exception::class)
    fun rejectsMissingManifest() {
        val zip = zip("manifestless.zip", "assets/a.png" to "x")
        BubblePackageArchive.extract(zip, File(temporaryFolder.root, "out2"))
    }

    @Test(expected = Exception::class)
    fun rejectsPathTraversal() {
        val zip = zip("evil.zip", "../evil.txt" to "x")
        BubblePackageArchive.extract(zip, File(temporaryFolder.root, "out3"))
    }

    @Test(expected = Exception::class)
    fun rejectsDuplicatePaths() {
        val zip = zip(
            "dup.zip",
            "bubble.json" to "{}",
            "assets/a.png" to "a",
            "assets/a.png" to "b",
        )
        BubblePackageArchive.extract(zip, File(temporaryFolder.root, "out4"))
    }
}
