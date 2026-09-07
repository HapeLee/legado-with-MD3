package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.LabSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabSettingsMappingTest {

    @Test
    fun `实验室设置各字段写读映射往返恒等`() {
        val samples = listOf(
            LabSettings(enabled = true),
            LabSettings(eInkDisplay = true),
            LabSettings(eyeProtection = true),
        )

        samples.forEach { expected ->
            assertEquals(expected, expected.toPrefMap().toTestSnapshot().toLabSettings())
        }
    }

    @Test
    fun `原子 update 只入队发生变化的键`() {
        val current = LabSettings(enabled = true)

        val diff = captureAtomicUpdateValues(
            current = current,
            read = { it.toLabSettings() },
            toPrefMap = LabSettings::toPrefMap,
            transform = { it.copy(eyeProtection = true) },
        )

        assertEquals(mapOf(PreferKey.labEyeProtection to PreferenceValue.BooleanValue(true)), diff)
        assertTrue(
            captureAtomicUpdateValues(
                current = current,
                read = { it.toLabSettings() },
                toPrefMap = LabSettings::toPrefMap,
                transform = { it.copy() },
            ).isEmpty()
        )
    }
}
