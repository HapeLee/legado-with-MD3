package io.legado.app.smoke.roomprobe

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Desktop target subclass: builds the database via the `name`-only overload
 * and exercises the KSP-generated desktop actual implementation.
 */
class RoomKmpProbeDesktopTest : RoomKmpProbeTest() {
    override fun dbPath(): String =
        System.getProperty("java.io.tmpdir") + "room-kmp-probe-desktop-${System.nanoTime()}.db"

    override fun createDatabase(path: String): ProbeDatabase =
        Room.databaseBuilder<ProbeDatabase>(name = path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
}
