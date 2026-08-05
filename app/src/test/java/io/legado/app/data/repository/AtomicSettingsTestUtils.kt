package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.legado.app.help.config.PendingOverlayCore
import io.legado.app.help.config.setPrefValue
import kotlinx.coroutines.runBlocking

internal fun <T> captureAtomicUpdateValues(
    current: T,
    read: (Preferences) -> T,
    toPrefMap: (T) -> Map<String, Any?>,
    transform: (T) -> T,
): Map<String, Any?> {
    val initial = toPrefMap(current).toTestPreferences()
    val writeQueue = ArrayDeque<suspend () -> Unit>()
    var persistedValues: Map<String, Any?>? = null
    val core = PendingOverlayCore(
        initial = initial,
        launchWrite = { writeQueue += it },
        persist = { _, _ -> error("不会执行单键落盘") },
        persistAll = { values ->
            persistedValues = values
            initial
        },
    )

    core.atomicUpdate(read, toPrefMap, transform)
    if (writeQueue.isEmpty()) return emptyMap()
    check(writeQueue.size == 1)
    runBlocking { writeQueue.removeFirst().invoke() }
    return checkNotNull(persistedValues)
}

internal fun Map<String, Any?>.toTestPreferences(): Preferences {
    val preferences = mutablePreferencesOf()
    forEach { (key, value) -> preferences.setPrefValue(key, value) }
    return preferences
}
