package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceRuntimeContractTest {

    @Test
    fun providerUninstalledThrows() {
        SourceRuntimeProvider.uninstall()
        assertFailsWith<IllegalStateException> {
            SourceRuntimeProvider.current
        }
    }

    @Test
    fun providerInstalledReturnsDelegate() {
        val runtime = object : SourceRuntime {
            override fun getShareScope(jsLib: String?): JsScope? = null
            override fun removeJsLib(jsLib: String?) {}
            override fun clearExploreKindsCache(source: Any) {}
        }
        SourceRuntimeProvider.install(runtime)
        try {
            assertEquals(runtime, SourceRuntimeProvider.current)
            assertEquals(true, SourceRuntimeProvider.isInstalled)
        } finally {
            SourceRuntimeProvider.uninstall()
        }
    }
}
