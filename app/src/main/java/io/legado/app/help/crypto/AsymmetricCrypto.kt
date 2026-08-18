package io.legado.app.help.crypto

import androidx.annotation.Keep
import java.io.InputStream
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

@Keep
@Suppress("unused")
class AsymmetricCrypto(private val algorithm: String) {

    private val keyAlgorithm = algorithm.substringBefore('/').let { name ->
        val separator = name.lowercase().lastIndexOf("with")
        if (separator >= 0) name.substring(separator + 4) else name
    }.let { if (it.equals("ECDSA", true)) "EC" else it }
    private var publicKey: PublicKey
    private var privateKey: PrivateKey

    init {
        val pair = KeyPairGenerator.getInstance(keyAlgorithm).generateKeyPair()
        publicKey = pair.public
        privateKey = pair.private
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun setPrivateKey(key: ByteArray): AsymmetricCrypto {
        privateKey = KeyFactory.getInstance(keyAlgorithm).generatePrivate(PKCS8EncodedKeySpec(key))
        return this
    }

    fun setPrivateKey(key: String): AsymmetricCrypto = setPrivateKey(key.encodeToByteArray())

    @Suppress("MemberVisibilityCanBePrivate")
    fun setPublicKey(key: ByteArray): AsymmetricCrypto {
        publicKey = KeyFactory.getInstance(keyAlgorithm).generatePublic(X509EncodedKeySpec(key))
        return this
    }

    fun setPublicKey(key: String): AsymmetricCrypto = setPublicKey(key.encodeToByteArray())

    private fun key(usePublicKey: Boolean?): Key = if (usePublicKey == true) publicKey else privateKey

    private fun crypt(data: ByteArray, usePublicKey: Boolean?, mode: Int): ByteArray {
        val key = key(usePublicKey)
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(mode, key)
        if (key !is RSAKey) return cipher.doFinal(data)
        val keySize = (key.modulus.bitLength() + 7) / 8
        val blockSize = if (mode == Cipher.DECRYPT_MODE) keySize else rsaEncryptBlockSize(keySize)
        if (data.size <= blockSize) return cipher.doFinal(data)
        val output = ArrayList<Byte>()
        var offset = 0
        while (offset < data.size) {
            cipher.doFinal(data, offset, minOf(blockSize, data.size - offset)).forEach(output::add)
            offset += blockSize
        }
        return output.toByteArray()
    }

    private fun rsaEncryptBlockSize(keySize: Int): Int = when {
        algorithm.contains("OAEPWithSHA-512", true) -> keySize - 2 * 64 - 2
        algorithm.contains("OAEPWithSHA-384", true) -> keySize - 2 * 48 - 2
        algorithm.contains("OAEPWithSHA-256", true) -> keySize - 2 * 32 - 2
        algorithm.contains("OAEPWithSHA-224", true) -> keySize - 2 * 28 - 2
        algorithm.contains("OAEPWithSHA-1", true) || algorithm.contains("OAEPPadding", true) -> keySize - 42
        algorithm.contains("NoPadding", true) -> keySize
        else -> keySize - 11
    }

    private fun decode(data: String): ByteArray =
        if (data.length % 2 == 0 && data.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            data.hexToByteArray()
        } else {
            data.base64ToByteArray()
        }

    @JvmOverloads
    fun decrypt(data: Any, usePublicKey: Boolean? = true): ByteArray {
        return when (data) {
            is ByteArray -> crypt(data, usePublicKey, Cipher.DECRYPT_MODE)
            is String -> crypt(decode(data), usePublicKey, Cipher.DECRYPT_MODE)
            is InputStream -> crypt(data.readBytes(), usePublicKey, Cipher.DECRYPT_MODE)
            else -> throw IllegalArgumentException("Unexpected input type")
        }
    }

    @JvmOverloads
    fun decryptStr(data: Any, usePublicKey: Boolean? = true): String {
        return when (data) {
            is ByteArray -> String(decrypt(data, usePublicKey), Charsets.UTF_8)
            is String -> String(decrypt(data, usePublicKey), Charsets.UTF_8)
            is InputStream -> String(decrypt(data, usePublicKey), Charsets.UTF_8)
            else -> throw IllegalArgumentException("Unexpected input type")
        }
    }

    @JvmOverloads
    fun encrypt(data: Any, usePublicKey: Boolean? = true): ByteArray {
        return when (data) {
            is ByteArray -> crypt(data, usePublicKey, Cipher.ENCRYPT_MODE)
            is String -> crypt(data.toByteArray(), usePublicKey, Cipher.ENCRYPT_MODE)
            is InputStream -> crypt(data.readBytes(), usePublicKey, Cipher.ENCRYPT_MODE)
            else -> throw IllegalArgumentException("Unexpected input type")
        }
    }

    @JvmOverloads
    fun encryptHex(data: Any, usePublicKey: Boolean? = true): String {
        return when (data) {
            is ByteArray, is String, is InputStream -> encrypt(data, usePublicKey).toHexString()
            else -> throw IllegalArgumentException("Unexpected input type")
        }
    }

    @JvmOverloads
    fun encryptBase64(data: Any, usePublicKey: Boolean? = true): String {
        return encrypt(data, usePublicKey).toBase64()
    }

    @JvmOverloads
    fun decryptHex(data: String, usePublicKey: Boolean? = true): ByteArray =
        crypt(data.hexToByteArray(), usePublicKey, Cipher.DECRYPT_MODE)

    @JvmOverloads
    fun decryptBase64(data: String, usePublicKey: Boolean? = true): ByteArray =
        crypt(data.base64ToByteArray(), usePublicKey, Cipher.DECRYPT_MODE)

}
