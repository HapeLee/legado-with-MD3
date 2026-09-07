package io.legado.app.data.repository

import kotlinx.coroutines.runBlocking

/**
 * 把 `Map<String, Any?>` 转成快照（[PreferenceValue] 映射），供 mapping 测试把
 * `expectedPrefMap()` 喂给 `toXxxSettings()` 做读写回环断言。
 */
internal fun Map<String, Any?>.toTestSnapshot(): Map<String, PreferenceValue> =
    buildMap {
        for ((key, value) in this@toTestSnapshot) {
            preferenceValueOf(value)?.let { put(key, it) }
        }
    }

/**
 * 用 [FakePreferenceStore] 走真实原子更新路径，捕获最近一次落盘的差量值。
 * 用于断言「transform 只改了对应键」。
 */
internal fun <T> captureAtomicUpdateValues(
    current: T,
    read: (Map<String, PreferenceValue>) -> T,
    toPrefMap: (T) -> Map<String, Any?>,
    transform: (T) -> T,
): Map<String, PreferenceValue?> {
    val store = FakePreferenceStore(toPrefMap(current).toTestSnapshot())
    runBlocking {
        store.atomicUpdateSettings(
            read = read,
            toPrefMap = toPrefMap,
            transform = transform,
        )
    }
    return store.lastAtomicWrite ?: emptyMap()
}
