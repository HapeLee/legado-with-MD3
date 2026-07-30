package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.OtherSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class OtherSettingsMappingTest {

    @Test
    fun `其他设置 25 键写读映射逐字段对应`() {
        otherMappingSamples().forEach { expected ->
            assertEquals(expected.expectedPrefMap(), expected.toPrefMap())
            assertEquals(expected, expected.expectedPrefMap().toTestPreferences().toOtherSettings())
        }
    }

    @Test
    fun `默认书籍目录 nullable 删除走真实原子路径`() {
        val values = captureAtomicUpdateValues(
            current = OtherSettings(defaultBookTreeUri = "content://old"),
            read = { it.toOtherSettings() },
            toPrefMap = OtherSettings::toPrefMap,
            transform = { it.copy(defaultBookTreeUri = null) },
        )

        assertEquals(mapOf(PreferKey.defaultBookTreeUri to null), values)
    }
}

private fun otherMappingSamples(): List<OtherSettings> {
    val base = OtherSettings(
        updateToVariant = "beta",
        defaultBookTreeUri = "content://books",
        sourceEditMaxLine = 123,
        webPort = 4321,
        fontSort = 7,
    )
    return listOf(
        base,
        base.copy(autoCheckUpdateOnStart = true),
        base.copy(webServiceAutoStart = true),
        base.copy(autoRefresh = true),
        base.copy(defaultToRead = true),
        base.copy(notificationsPost = false),
        base.copy(ignoreBatteryPermission = false),
        base.copy(firebaseEnable = false),
        base.copy(antiAlias = true),
        base.copy(replaceEnableDefault = false),
        base.copy(autoClearExpired = false),
        base.copy(showAddToShelfAlert = false),
        base.copy(showMangaUi = false),
        base.copy(webServiceWakeLock = true),
        base.copy(processText = false),
        base.copy(recordLog = true),
        base.copy(recordHeapDump = true),
        base.copy(audioPlayUseWakeLock = true),
        base.copy(importKeepName = true),
        base.copy(importKeepGroup = true),
        base.copy(importKeepEnable = true),
    )
}

private fun OtherSettings.expectedPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.updateToVariant to updateToVariant,
    PreferKey.autoCheckUpdateOnStart to autoCheckUpdateOnStart,
    PreferKey.webServiceAutoStart to webServiceAutoStart,
    PreferKey.autoRefresh to autoRefresh,
    PreferKey.defaultToRead to defaultToRead,
    PreferKey.notificationsPost to notificationsPost,
    PreferKey.ignoreBatteryPermission to ignoreBatteryPermission,
    PreferKey.firebaseEnable to firebaseEnable,
    PreferKey.defaultBookTreeUri to defaultBookTreeUri,
    PreferKey.antiAlias to antiAlias,
    PreferKey.replaceEnableDefault to replaceEnableDefault,
    PreferKey.autoClearExpired to autoClearExpired,
    PreferKey.showAddToShelfAlert to showAddToShelfAlert,
    PreferKey.showMangaUi to showMangaUi,
    PreferKey.webServiceWakeLock to webServiceWakeLock,
    PreferKey.sourceEditMaxLine to sourceEditMaxLine,
    PreferKey.webPort to webPort,
    PreferKey.processText to processText,
    PreferKey.recordLog to recordLog,
    PreferKey.recordHeapDump to recordHeapDump,
    PreferKey.audioPlayWakeLock to audioPlayUseWakeLock,
    PreferKey.importKeepName to importKeepName,
    PreferKey.importKeepGroup to importKeepGroup,
    PreferKey.importKeepEnable to importKeepEnable,
    PreferKey.fontSort to fontSort,
)
