package io.legado.app.help.crypto

import androidx.annotation.Keep
import java.io.InputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

@Keep
@Suppress("unused")
class Sign(private val algorithm: String) {

    private val keyAlgorithm = algorithm.substringBefore('/').let { name ->
        val separator = name.lowercase().lastIndexOf("with")
        val keyName = if (separator >= 0) name.substring(separator + 4) else name
        if (keyName.equals("ECDSA", true)) "EC" else keyName
    }
    private var privateKey: PrivateKey? = null
    private var publicKey: PublicKey? = null

    fun setPrivateKey(key: ByteArray): Sign {
        privateKey = KeyFactory.getInstance(keyAlgorithm).generatePrivate(PKCS8EncodedKeySpec(key))
        return this
    }

    fun setPrivateKey(key: String): Sign = setPrivateKey(key.encodeToByteArray())

    fun setPublicKey(key: ByteArray): Sign {
        publicKey = KeyFactory.getInstance(keyAlgorithm).generatePublic(X509EncodedKeySpec(key))
        return this
    }

    fun setPublicKey(key: String): Sign = setPublicKey(key.encodeToByteArray())

    fun sign(data: ByteArray): ByteArray = Signature.getInstance(algorithm).run {
        initSign(requireNotNull(privateKey) { "Private key is required for signing" })
        update(data)
        sign()
    }

    fun sign(data: String): ByteArray = sign(data.toByteArray())

    fun sign(data: InputStream): ByteArray {
        val signature = Signature.getInstance(algorithm)
        signature.initSign(requireNotNull(privateKey) { "Private key is required for signing" })
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = data.read(buffer)
            if (read < 0) break
            if (read > 0) signature.update(buffer, 0, read)
        }
        return signature.sign()
    }

    fun signHex(data: ByteArray): String = sign(data).toHexString()

    fun signHex(data: String): String = sign(data).toHexString()

    fun signHex(data: InputStream): String = sign(data).toHexString()

    fun verify(data: ByteArray, signature: ByteArray): Boolean = Signature.getInstance(algorithm).run {
        initVerify(requireNotNull(publicKey) { "Public key is required for verification" })
        update(data)
        verify(signature)
    }

}
