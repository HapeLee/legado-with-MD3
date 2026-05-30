package io.legado.app.help.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object BackupRestoreLock {

    private val mutex = Mutex()

    suspend fun <T> withLock(action: suspend () -> T): T {
        return mutex.withLock {
            action()
        }
    }
}
