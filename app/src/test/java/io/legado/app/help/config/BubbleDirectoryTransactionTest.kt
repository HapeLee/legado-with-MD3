package io.legado.app.help.config

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BubbleDirectoryTransactionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun installsNewPackageDirectory() {
        val target = File(temporaryFolder.root, "target")
        val staging = File(temporaryFolder.root, ".staging").apply {
            mkdirs()
            File(this, "bubble.json").writeText("{}")
        }
        val result = BubbleDirectoryTransaction().install(
            target,
            staging,
            File(temporaryFolder.root, "backup"),
        ) { it.name }
        assertEquals("target", result)
        assertTrue(File(target, "bubble.json").isFile)
        assertFalse(staging.exists())
    }

    @Test
    fun commitFailureRestoresPreviousPackage() {
        val target = File(temporaryFolder.root, "target").apply {
            mkdirs()
            File(this, "bubble.json").writeText("old")
        }
        val staging = File(temporaryFolder.root, ".staging").apply {
            mkdirs()
            File(this, "bubble.json").writeText("new")
        }
        runCatching {
            BubbleDirectoryTransaction().install(
                target,
                staging,
                File(temporaryFolder.root, "backup"),
            ) { error("commit failed") }
        }
        assertEquals("old", File(target, "bubble.json").readText())
    }
}
