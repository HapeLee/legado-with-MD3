package io.legado.app.help

import android.app.Application
import io.legado.app.web.utils.AssetsWeb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DefaultAssetPathsTest {
    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun bundledDefaultsLoadWithoutHostFilesystemSeparators() {
        assertTrue(DefaultData.httpTTS.isNotEmpty())
        assertTrue(DefaultData.readConfigs.isNotEmpty())
        assertTrue(DefaultData.txtTocRules.isNotEmpty())
        assertTrue(DefaultData.themeConfigs.isNotEmpty())
        assertTrue(DefaultData.rssSources.isEmpty())
        assertNotNull(DefaultData.coverRule)
        assertTrue(DefaultData.dictRules.isNotEmpty())
        assertTrue(DefaultData.keyboardAssists.isNotEmpty())
        assertTrue(DirectLinkUpload.defaultRules.isNotEmpty())
    }

    @Test
    fun webAssetsAcceptBothRelativeAndRequestPaths() {
        val assets = AssetsWeb("defaultData")
        val expected = RuntimeEnvironment.getApplication().assets
            .open("defaultData/keyboardAssists.json").bufferedReader().use { it.readText() }
        for (path in listOf("keyboardAssists.json", "/keyboardAssists.json", "//keyboardAssists.json")) {
            val actual = assets.getInputStream(path)?.bufferedReader()?.use { it.readText() }
            assertEquals(path, expected, actual)
        }
        assertNull(assets.getInputStream("missing.json"))
    }
}
