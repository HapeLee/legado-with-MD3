package io.legado.app.help.webView

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WebViewDataCleanerTest {

    private lateinit var testDirectory: File

    @Before
    fun setUp() {
        testDirectory = Files.createTempDirectory("webview-data-cleaner").toFile()
    }

    @After
    fun tearDown() {
        testDirectory.deleteRecursively()
    }

    @Test
    fun clearDataDirectories_clearsStandardAndHuaweiWebViewData() {
        val webViewDirectory = File(testDirectory, "app_webview").apply {
            File(this, "Default/Local Storage/data").createFileWithParents()
        }
        val huaweiWebViewDirectory = File(testDirectory, "app_hws_webview").apply {
            File(this, "Default/Cookies").createFileWithParents()
        }

        clearDataDirectories(webViewDirectory, huaweiWebViewDirectory)
        assertTrue(webViewDirectory.isDirectory)
        assertTrue(webViewDirectory.listFiles().isNullOrEmpty())
        assertFalse(huaweiWebViewDirectory.exists())
    }

    @Test
    fun clearDataDirectories_acceptsAlreadyEmptyDirectories() {
        val webViewDirectory = File(testDirectory, "app_webview").apply { mkdirs() }
        val huaweiWebViewDirectory = File(testDirectory, "app_hws_webview").apply { mkdirs() }

        clearDataDirectories(webViewDirectory, huaweiWebViewDirectory)
        assertTrue(webViewDirectory.isDirectory)
        assertFalse(huaweiWebViewDirectory.exists())
    }

    @Test
    fun clearDataDirectories_reportsFailureWhenHuaweiDirectoryCannotBeDeleted() {
        val webViewDirectory = File(testDirectory, "app_webview").apply { mkdirs() }
        val huaweiWebViewDirectory = object : File(testDirectory, "app_hws_webview") {
            override fun exists() = true
            override fun isDirectory() = true
            override fun listFiles(): Array<File> = emptyArray()
            override fun delete() = false
        }

        assertFalse(clearDataDirectories(webViewDirectory, huaweiWebViewDirectory))
    }

    private fun File.createFileWithParents() {
        parentFile?.mkdirs()
        createNewFile()
    }
}
