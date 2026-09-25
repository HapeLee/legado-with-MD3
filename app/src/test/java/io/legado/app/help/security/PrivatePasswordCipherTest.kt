package io.legado.app.help.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivatePasswordCipherTest {

    @Test
    fun `verifier matches the password it was derived from`() {
        val salt = PrivatePasswordCipher.generateSalt()
        val verifier = requireNotNull(PrivatePasswordCipher.deriveVerifier("pwd-123", salt))
        assertTrue(PrivatePasswordCipher.verify("pwd-123", salt, verifier))
    }

    @Test
    fun `verifier rejects a wrong password`() {
        val salt = PrivatePasswordCipher.generateSalt()
        val verifier = requireNotNull(PrivatePasswordCipher.deriveVerifier("pwd-123", salt))
        assertFalse(PrivatePasswordCipher.verify("pwd-124", salt, verifier))
        assertFalse(PrivatePasswordCipher.verify("", salt, verifier))
    }

    @Test
    fun `same password with different salts yields different verifiers`() {
        val first = requireNotNull(
            PrivatePasswordCipher.deriveVerifier(
                "pwd",
                PrivatePasswordCipher.generateSalt()
            )
        )
        val second = requireNotNull(
            PrivatePasswordCipher.deriveVerifier(
                "pwd",
                PrivatePasswordCipher.generateSalt()
            )
        )
        assertNotEquals(first, second)
    }

    @Test
    fun `missing salt or verifier is rejected instead of silently passing`() {
        assertFalse(PrivatePasswordCipher.verify("pwd", "", "verifier"))
        assertFalse(PrivatePasswordCipher.verify("pwd", PrivatePasswordCipher.generateSalt(), ""))
        assertNull(PrivatePasswordCipher.deriveVerifier("pwd", "not-a-valid-base64-salt"))
    }
}
