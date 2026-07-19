package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.LabSettings
import io.legado.app.help.config.PendingOverlayCore
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
            assertEquals(expected, expected.toPrefMap().toTestPreferences().toLabSettings())
        }
    }

    @Test
    fun `原子 update 只入队发生变化的键`() {
        val current = LabSettings(enabled = true)

        val diff = captureAtomicUpdateValues(
            current = current,
            read = Preferences::toLabSettings,
            toPrefMap = LabSettings::toPrefMap,
            transform = { it.copy(eyeProtection = true) },
        )

        assertEquals(mapOf(PreferKey.labEyeProtection to true), diff)
        assertTrue(
            captureAtomicUpdateValues(
                current = current,
                read = Preferences::toLabSettings,
                toPrefMap = LabSettings::toPrefMap,
                transform = { it.copy() },
            ).isEmpty()
        )
    }

    @Test
    fun `基于同一快照并发更新不同字段不丢更新`() {
        val current = LabSettings()
        val transforms: List<(LabSettings) -> LabSettings> = listOf(
            { it.copy(enabled = true) },
            { it.copy(eyeProtection = true) },
        )
        val core = PendingOverlayCore(
            initial = current.toPrefMap().toTestPreferences(),
            launchWrite = {},
            persist = { _, _ -> error("不会执行落盘") },
            persistAll = { error("不会执行落盘") },
        )
        val start = CountDownLatch(1)
        val writers = transforms.map { transform ->
            thread(start = true) {
                start.await()
                core.atomicUpdate(
                    read = Preferences::toLabSettings,
                    toPrefMap = LabSettings::toPrefMap,
                    transform = transform,
                )
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
