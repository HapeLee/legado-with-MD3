package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SymmetricCryptoContractTest {

    @Test
    fun providerUninstalledThrows() {
        SymmetricCryptoProvider.uninstall()
        assertFailsWith<IllegalStateException> {
            SymmetricCryptoProvider.current
        }
    }

    @Test
    fun providerInstalledReturnsDelegate() {
        val crypto = object : SymmetricCrypto {
            override fun encryptBase64(algorithm: String, key: ByteArray, data: String): String = data
            override fun decryptStr(algorithm: String, key: ByteArray, data: String): String = data
        }
        SymmetricCryptoProvider.install(crypto)
        try {
            assertEquals(crypto, SymmetricCryptoProvider.current)
            assertEquals(true, SymmetricCryptoProvider.isInstalled)
        } finally {
            SymmetricCryptoProvider.uninstall()
        }
    }
}
