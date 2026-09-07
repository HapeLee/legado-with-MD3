package io.legado.app.data.repository

import io.legado.app.domain.gateway.CheckSourceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CheckSourceSettingsRepositoryTest {

    @Test
    fun updateWritesEverySettingInOneDurableOperation() = runTest {
        val preferences = FakePreferenceStore()
        val repository = CheckSourceSettingsRepository(preferences)
        val settings = CheckSourceSettings(
            timeoutMillis = 30_000L,
            checkSearch = false,
            checkDiscovery = true,
            checkInfo = false,
            checkCategory = false,
            checkContent = true,
        )

        repository.update(settings)

        assertEquals(1, preferences.durableWriteCount)
        assertEquals(settings, repository.currentSettings)
        assertEquals(
            mapOf(
                CheckSourcePreferenceKeys.Timeout to PreferenceValue.LongValue(30_000L),
                CheckSourcePreferenceKeys.Search to PreferenceValue.BooleanValue(false),
                CheckSourcePreferenceKeys.Discovery to PreferenceValue.BooleanValue(true),
                CheckSourcePreferenceKeys.Info to PreferenceValue.BooleanValue(false),
                CheckSourcePreferenceKeys.Category to PreferenceValue.BooleanValue(false),
                CheckSourcePreferenceKeys.Content to PreferenceValue.BooleanValue(true),
            ),
            preferences.currentValues,
        )
    }

    @Test
    fun updateRejectsInvalidValuesBeforeWriting() = runTest {
        val preferences = FakePreferenceStore()
        val repository = CheckSourceSettingsRepository(preferences)

        val timeoutFailure = try {
            repository.update(CheckSourceSettings(timeoutMillis = 0L))
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        val sourceTypeFailure = try {
            repository.update(CheckSourceSettings(checkSearch = false, checkDiscovery = false))
            null
        } catch (error: IllegalArgumentException) {
            error
        }

        assertNotNull(timeoutFailure)
        assertNotNull(sourceTypeFailure)
        assertEquals(0, preferences.durableWriteCount)
    }

    @Test
    fun settingsObservesOneSnapshotPerDurableWrite() = runTest {
        val preferences = FakePreferenceStore()
        val repository = CheckSourceSettingsRepository(preferences)
        val settings = CheckSourceSettings(timeoutMillis = 42_000L, checkContent = false)

        repository.update(settings)

        assertEquals(settings, repository.settings.first())
    }
}
