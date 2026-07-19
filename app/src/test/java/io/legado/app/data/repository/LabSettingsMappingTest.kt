package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.LabSettings
import io.legado.app.help.config.PendingOverlayCore
import io.legado.app.help.config.setPrefValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class LabSettingsMappingTest {

    @Test
    fun `实验室设置各字段写读映射往返恒等`() {
        val samples = listOf(
            LabSettings(enabled = true),
            LabSettings(eInkDisplay = true),
            LabSettings(eyeProtection = true),
        )

        samples.forEach { expected ->
            assertEquals(expected, expected.toPrefMap().toPreferences().toLabSettings())
        }
    }

    @Test
    fun `update transform 只返回发生变化的键`() {
        val current = LabSettings(enabled = true)

        val diff = current.diffPrefMap(
            transform = { it.copy(eyeProtection = true) },
            toPrefMap = LabSettings::toPrefMap,
        )

        assertEquals(mapOf(PreferKey.labEyeProtection to true), diff)
        assertTrue(
            current.diffPrefMap(
                transform = { it.copy() },
                toPrefMap = LabSettings::toPrefMap,
            ).isEmpty()
        )
    }

    @Test
    fun `基于同一快照并发更新不同字段不丢更新`() {
        val current = LabSettings()
        val enabledDiff = current.diffPrefMap(
            transform = { it.copy(enabled = true) },
            toPrefMap = LabSettings::toPrefMap,
        )
        val eyeProtectionDiff = current.diffPrefMap(
            transform = { it.copy(eyeProtection = true) },
            toPrefMap = LabSettings::toPrefMap,
        )
        val core = PendingOverlayCore(
            initial = current.toPrefMap().toPreferences(),
            launchWrite = {},
            persist = { _, _ -> error("不会执行落盘") },
            persistAll = { error("不会执行落盘") },
        )
        val start = CountDownLatch(1)
        val writers = listOf(enabledDiff, eyeProtectionDiff).map { diff ->
            thread(start = true) {
                start.await()
                core.putAll(diff)
            }
        }

        start.countDown()
        writers.forEach(Thread::join)

        assertEquals(
            current.copy(enabled = true, eyeProtection = true),
            core.preferencesFlow.value.toLabSettings(),
        )
    }
}

private fun Map<String, Any?>.toPreferences(): Preferences {
    val preferences = mutablePreferencesOf()
    forEach { (key, value) -> preferences.setPrefValue(key, value) }
    return preferences
}
