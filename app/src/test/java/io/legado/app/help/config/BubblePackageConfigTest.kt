package io.legado.app.help.config

import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BubblePackageConfigTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun legacyManifestWithoutNewFieldsFallsBackToDefaults() {
        val config = GSON.fromJsonObject<BubblePackageManager.Config>(
            """{"name":"旧气泡","svgTemplate":"<svg><text>${'$'}{num}</text></svg>"}"""
        ).getOrThrow()
        assertEquals("旧气泡", config.name)
        assertEquals(1f, config.sizeScale, 0.001f)
        assertEquals(1, config.formatVersion)
        assertTrue(config.resources.isEmpty())
    }

    @Test
    fun roundTripsResourcesThroughGson() {
        val original = BubblePackageManager.Config(
            name = "包",
            dirName = "pkg",
            svgTemplate = "<svg/>",
            sizeScale = 1.2f,
            resources = listOf(PackageResource("font", "assets/a.ttf", "font")),
            formatVersion = 2,
        )
        val restored = GSON.fromJsonObject<BubblePackageManager.Config>(
            GSON.toJson(original)
        ).getOrThrow()
        assertEquals(original, restored)
    }

    @Test
    fun normalizesResourceList() {
        val normalized = PackageResourcePolicy.normalize(
            listOf(
                PackageResource(" font ", "assets/a.ttf", " FONT "),
                PackageResource("img", "images/a.png", "image"),
            )
        )
        assertEquals("font", normalized[0].alias)
        assertEquals("font", normalized[0].type)
        assertEquals("assets/a.ttf", normalized[0].path)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateAlias() {
        PackageResourcePolicy.normalize(
            listOf(
                PackageResource("a", "assets/a.png", "image"),
                PackageResource("A", "assets/b.png", "image"),
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEscapingPath() {
        PackageResourcePolicy.normalize(
            listOf(PackageResource("a", "../a.png", "image"))
        )
    }

    @Test
    fun validatesPngHeader() {
        val root = temporaryFolder.newFolder("pkg")
        val image = File(root, "assets/a.png").apply {
            parentFile?.mkdirs()
            writeBytes(
                byteArrayOf(
                    0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                ) + ByteArray(8)
            )
        }
        PackageResourcePolicy.validateResolvedFile(
            image,
            PackageResourcePolicy.TYPE_IMAGE,
            "assets/a.png",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsImageWithWrongHeader() {
        val root = temporaryFolder.newFolder("bad")
        val image = File(root, "assets/a.png").apply {
            parentFile?.mkdirs()
            writeText("not a png")
        }
        PackageResourcePolicy.validateResolvedFile(
            image,
            PackageResourcePolicy.TYPE_IMAGE,
            "assets/a.png",
        )
    }
}
