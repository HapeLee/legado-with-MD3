package io.legado.app.support

import android.content.Context
import androidx.room.Room
import io.legado.app.data.AppDatabase

/** Installs a real Room database without the production asset-import callback. */
class InMemoryAppDatabaseFixture(context: Context) : AutoCloseable {
    val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    // Legacy model/service code uses the top-level lazy appDb. Override only its cached
    // value for this test, then restore it; no production test hook or callback is needed.
    private val delegate = Class.forName("io.legado.app.data.AppDatabaseKt")
        .getDeclaredField("appDb\$delegate")
        .apply { isAccessible = true }
        .get(null)
    private val valueField = delegate.javaClass.getDeclaredField("_value")
        .apply { isAccessible = true }
    private val previousValue = synchronized(delegate) {
        valueField.get(delegate).also { valueField.set(delegate, database) }
    }

    override fun close() {
        try {
            synchronized(delegate) { valueField.set(delegate, previousValue) }
        } finally {
            database.close()
        }
    }
}
