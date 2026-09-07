package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KeyValueStoreContractTest {

    @Test
    fun inMemoryPutGetDeleteRoundTrip() {
        val store = InMemoryKeyValueStore()
        store.put("k", "v")
        assertEquals("v", store.get("k"))
        store.delete("k")
        assertNull(store.get("k"))
    }

    @Test
    fun inMemoryPutOverwrites() {
        val store = InMemoryKeyValueStore()
        store.put("k", "a")
        store.put("k", "b")
        assertEquals("b", store.get("k"))
    }

    @Test
    fun inMemorySaveTimeExpiry() {
        val store = InMemoryKeyValueStore()
        store.put("k", "v", saveTime = -1) // 已过期（deadline 在过去）
        assertNull(store.get("k"))
    }

    @Test
    fun providerUninstalledThrows() {
        KeyValueStoreProvider.uninstall()
        assertFailsWith<IllegalStateException> {
            KeyValueStoreProvider.current
        }
    }

    @Test
    fun providerInstalledReturnsDelegate() {
        val store = InMemoryKeyValueStore()
        KeyValueStoreProvider.install(store)
        try {
            assertEquals(store, KeyValueStoreProvider.current)
            assertEquals(true, KeyValueStoreProvider.isInstalled)
        } finally {
            KeyValueStoreProvider.uninstall()
        }
    }
}
