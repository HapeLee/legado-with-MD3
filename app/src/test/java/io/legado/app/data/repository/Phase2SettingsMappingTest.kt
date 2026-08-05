package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.settings.BookExportSettings
import io.legado.app.domain.model.settings.ChangeSourceSettings
import io.legado.app.domain.model.settings.CoverSettings
import io.legado.app.domain.model.settings.DownloadCacheSettings
import io.legado.app.domain.model.settings.ImportBookSettings
import io.legado.app.domain.model.settings.TranslationSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class Phase2SettingsMappingTest {

    @Test
    fun `翻译设置写读映射往返恒等`() {
        assertRoundTrips(
            samples = listOf(
                TranslationSettings(
                    provider = TranslationConstants.PROVIDER_APP_AI,
                    targetLanguage = "ja",
                    maxCharsPerChunk = 12_345,
                    concurrentChunks = 3,
                    retryCount = 4,
                )
            ),
            toPrefMap = TranslationSettings::toPrefMap,
            fromPreferences = Preferences::toTranslationSettings,
        )
    }

    @Test
    fun `换源设置写读映射覆盖每个布尔字段`() {
        val base = ChangeSourceSettings(searchScope = "group-a")
        assertRoundTrips(
            samples = listOf(
                base,
                base.copy(checkAuthor = true),
                base.copy(loadInfo = true),
                base.copy(loadToc = true),
                base.copy(loadWordCount = true),
                base.copy(migrateChapters = false),
                base.copy(migrateReadingProgress = false),
                base.copy(migrateGroup = false),
                base.copy(migrateCover = false),
                base.copy(migrateCategory = false),
                base.copy(migrateRemark = false),
                base.copy(migrateReadConfig = false),
                base.copy(deleteDownloadedChapters = true),
            ),
            toPrefMap = ChangeSourceSettings::toPrefMap,
            fromPreferences = Preferences::toChangeSourceSettings,
        )
    }

    @Test
    fun `导入书籍设置写读映射往返恒等并支持删除 nullable 键`() {
        val settings = ImportBookSettings(
            importBookPath = "content://books",
            bookImportFileName = "name-rule",
            localBookImportSort = 2,
            remoteServerId = 42L,
        )
        assertRoundTrips(
            samples = listOf(settings),
            toPrefMap = ImportBookSettings::toPrefMap,
            fromPreferences = Preferences::toImportBookSettings,
        )

        assertEquals(
            mapOf(PreferKey.importBookPath to null),
            captureAtomicUpdateValues(
                current = settings,
                read = Preferences::toImportBookSettings,
                toPrefMap = ImportBookSettings::toPrefMap,
                transform = { it.copy(importBookPath = null) },
            ),
        )
    }

    @Test
    fun `下载缓存设置写读映射往返恒等`() {
        assertRoundTrips(
            samples = listOf(
                DownloadCacheSettings(
                    bitmapCacheSize = 61,
                    imageRetainNum = 7,
                    preDownloadNum = 13,
                    threadCount = 5,
                    cacheBookThreadCount = 6,
                    userAgent = "phase-2-agent",
                    cronetEnabled = true,
                )
            ),
            toPrefMap = DownloadCacheSettings::toPrefMap,
            fromPreferences = Preferences::toDownloadCacheSettings,
        )
    }

    @Test
    fun `书籍导出设置写读映射覆盖每个布尔字段`() {
        val base = BookExportSettings(
            bookExportFileName = "book-{name}",
            episodeExportFileName = "episode-{index}",
            exportCharset = "GB18030",
            exportType = 3,
        )
        assertRoundTrips(
            samples = listOf(
                base,
                base.copy(exportUseReplace = false),
                base.copy(exportToWebDav = true),
                base.copy(exportNoChapterName = true),
                base.copy(enableCustomExport = true),
                base.copy(exportPictureFile = true),
                base.copy(parallelExportBook = true),
            ),
            toPrefMap = BookExportSettings::toPrefMap,
            fromPreferences = Preferences::toBookExportSettings,
        )
    }

    @Test
    fun `封面设置写读映射覆盖每个布尔字段`() {
        val base = CoverSettings(
            textColor = 0xFF112233.toInt(),
            shadowColor = 0xFF223344.toInt(),
            textColorDark = 0xFF334455.toInt(),
            shadowColorDark = 0xFF445566.toInt(),
            infoOrientation = "1",
            exploreFilterState = 3,
            defaultCover = "content://cover/light",
            defaultCoverDark = "content://cover/dark",
        )
        assertRoundTrips(
            samples = listOf(
                base,
                base.copy(loadOnlyOnWifi = true),
                base.copy(useDefaultCover = true),
                base.copy(showShadow = true),
                base.copy(showStroke = false),
                base.copy(useDefaultColor = false),
                base.copy(showName = false),
                base.copy(showAuthor = false),
                base.copy(showNameDark = false),
                base.copy(showAuthorDark = false),
            ),
            toPrefMap = CoverSettings::toPrefMap,
            fromPreferences = Preferences::toCoverSettings,
        )
    }
}

private fun <T> assertRoundTrips(
    samples: List<T>,
    toPrefMap: (T) -> Map<String, Any?>,
    fromPreferences: (Preferences) -> T,
) {
    samples.forEach { expected ->
        val preferences = toPrefMap(expected).toTestPreferences()
        assertEquals(expected, fromPreferences(preferences))
    }
}
