package io.legado.app.smoke.roomprobe

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.ConstructedBy
import androidx.room.RoomDatabaseConstructor

/**
 * Minimal `@Database` in `commonMain` with the KMP `@ConstructedBy` pattern.
 *
 * KSP generates the actual implementations per target; the `expect object`
 * bridges the common declaration to those generated actuals.
 */
@Database(
    entities = [ProbeEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(ProbeDatabaseConstructor::class)
abstract class ProbeDatabase : RoomDatabase() {
    abstract fun probeDao(): ProbeDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ProbeDatabaseConstructor : RoomDatabaseConstructor<ProbeDatabase> {
    override fun initialize(): ProbeDatabase
}
