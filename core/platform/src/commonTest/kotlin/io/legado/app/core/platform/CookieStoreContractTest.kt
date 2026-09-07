package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CookieStoreContractTest {

    @Test
    fun inMemoryReplaceThenRead() {
        val store = InMemoryCookieStore()
        store.replaceCookie("url1", "a=b")
        assertEquals("a=b", store.getCookie("url1"))
    }

    @Test
    fun inMemoryReplaceOverwrites() {
        val store = InMemoryCookieStore()
        store.replaceCookie("url1", "a=b")
        store.replaceCookie("url1", "c=d")
        assertEquals("c=d", store.getCookie("url1"))
    }

    @Test
    fun inMemoryRemoveClears() {
        val store = InMemoryCookieStore()
        store.replaceCookie("url1", "a=b")
        store.removeCookie("url1")
        assertNull(store.getCookie("url1"))
    }

    @Test
    fun providerUninstalledThrows() {
        CookieStoreProvider.uninstall()
        assertFailsWith<IllegalStateException> {
            CookieStoreProvider.current
        }
    }

    @Test
    fun providerInstalledReturnsDelegate() {
        val store = InMemoryCookieStore()
        CookieStoreProvider.install(store)
        try {
            assertEquals(store, CookieStoreProvider.current)
        } finally {
            CookieStoreProvider.uninstall()
        }
    }
}
