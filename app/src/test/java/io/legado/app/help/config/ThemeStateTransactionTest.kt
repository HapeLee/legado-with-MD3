package io.legado.app.help.config

import io.legado.app.domain.model.settings.ThemeExportData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStateTransactionTest {

    @Test
    fun `后续步骤失败时恢复主题设置与封面选择`() = runBlocking {
        val oldSettings = ThemeExportData(appTheme = "old")
        var settings = oldSettings
        var albumId: String? = "old-album"
        val transaction = ThemeStateTransaction(
            exportSettings = { settings },
            currentAlbumId = { albumId },
            applySettings = { settings = it },
            selectAlbum = { albumId = it },
        )

        val result = runCatching {
            transaction.run {
                settings = ThemeExportData(appTheme = "new")
                albumId = "new-album"
                error("save imported theme failed")
            }
        }

        assertTrue(result.isFailure)
        assertEquals(oldSettings, settings)
        assertEquals("old-album", albumId)
    }
}
