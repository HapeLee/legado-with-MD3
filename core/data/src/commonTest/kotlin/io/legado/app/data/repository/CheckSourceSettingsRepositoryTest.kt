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

private class FakePreferenceStore : PreferenceStore {
    private val state = MutableStateFlow<Map<String, PreferenceValue>>(emptyMap())

    val currentValues: Map<String, PreferenceValue>
        get() = state.value
    var durableWriteCount = 0
        private set

    override fun currentValues(defaults: Map<String, PreferenceValue>): Map<String, PreferenceValue> =
        defaults.mapValues { (key, defaultValue) -> state.value[key] ?: defaultValue }

    override fun observeValues(
        defaults: Map<String, PreferenceValue>,
    ): Flow<Map<String, PreferenceValue>> = state.map { values ->
        defaults.mapValues { (key, defaultValue) -> values[key] ?: defaultValue }
    }

    override fun currentLong(key: String, defaultValue: Long): Long =
        (state.value[key] as? PreferenceValue.LongValue)?.value ?: defaultValue

    override fun currentBoolean(key: String, defaultValue: Boolean): Boolean =
        (state.value[key] as? PreferenceValue.BooleanValue)?.value ?: defaultValue

    override fun observeLong(key: String, defaultValue: Long): Flow<Long> = state.map { values ->
        (values[key] as? PreferenceValue.LongValue)?.value ?: defaultValue
    }

    override fun observeBoolean(key: String, defaultValue: Boolean): Flow<Boolean> = state.map { values ->
        (values[key] as? PreferenceValue.BooleanValue)?.value ?: defaultValue
    }

    override fun observeInt(key: String, defaultValue: Int): Flow<Int> = state.map { values ->
        (values[key] as? PreferenceValue.IntValue)?.value ?: defaultValue
    }

    override suspend fun setInt(key: String, value: Int) {
        setAllAndAwait(mapOf(key to PreferenceValue.IntValue(value)))
    }

    override fun observeString(key: String, defaultValue: String): Flow<String> = state.map { values ->
        (values[key] as? PreferenceValue.StringValue)?.value ?: defaultValue
    }

    override suspend fun setString(key: String, value: String) {
        setAllAndAwait(mapOf(key to PreferenceValue.StringValue(value)))
    }

    override suspend fun setAllAndAwait(values: Map<String, PreferenceValue>) {
        durableWriteCount += 1
        state.value = state.value + values
    }
}
