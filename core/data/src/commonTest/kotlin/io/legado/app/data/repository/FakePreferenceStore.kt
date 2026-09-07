package io.legado.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 内存版 [PreferenceStore]，供 core:data 的 repository 单测使用。
 * 记录每次持久化写入次数（[durableWriteCount]），并可捕获最近一次原子更新的落盘值。
 */
class FakePreferenceStore(
    initial: Map<String, PreferenceValue> = emptyMap(),
) : PreferenceStore {
    private val state = MutableStateFlow(initial)

    val currentValues: Map<String, PreferenceValue>
        get() = state.value
    var durableWriteCount = 0
        private set

    /** 最近一次原子更新的落盘值（无写入时为 null）。 */
    var lastAtomicWrite: Map<String, PreferenceValue?>? = null
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

    override fun currentSnapshot(): Map<String, PreferenceValue> = state.value

    override fun observeSnapshot(): Flow<Map<String, PreferenceValue>> = state

    override suspend fun atomicUpdate(
        transform: (Map<String, PreferenceValue>) -> Map<String, PreferenceValue?>,
    ) {
        val next = transform(state.value)
        durableWriteCount += 1
        lastAtomicWrite = next.filterKeys { state.value[it] != next[it] }
        state.value = next.filterValues { it != null }.mapValues { (_, v) -> v!! }
    }
}
