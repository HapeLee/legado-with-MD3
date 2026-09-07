package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoggerContractTest {

    @Test
    fun providerUninstalledThrows() {
        LoggerProvider.uninstall()
        assertFailsWith<IllegalStateException> {
            LoggerProvider.current
        }
    }

    @Test
    fun providerInstalledReturnsDelegate() {
        LoggerProvider.install(NoOpLogger())
        try {
            assertEquals(true, LoggerProvider.isInstalled)
        } finally {
            LoggerProvider.uninstall()
        }
    }
}
