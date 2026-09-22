package io.legado.app.help.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * 私密内容解锁的密码学工具。
 *
 * 设计要点：
 * - 本地密码是唯一权威凭据，这里只额外保存「校验值」而非密码本身；
 * - 校验值走 PBKDF2WithHmacSHA256 + 随机 salt，杜绝明文比对；
 * - 生物快捷解锁不是"回调返回成功就解锁"，而是用 Android Keystore 中
 *   「每次使用都需生物验证」的密钥把密码封装成信封；只有 BiometricPrompt 拿着
 *   CryptoObject 验证通过，信封才解得出密码。回调被伪造也拿不到密钥。
 *
 * 官方约束（developer.android.com/identity/sign-in/biometric-auth）：
 * 时间有效期密钥不能与 CryptoObject 同时使用，允许锁屏凭据回退时也不能用 CryptoObject。
 * 因此这里只接受 BIOMETRIC_STRONG 的「每次使用验证」密钥，锁屏凭据回退由应用内密码承担。
 */
object PrivatePasswordCipher {

    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "legado_private_vault"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 210_000
    private const val PBKDF2_KEY_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    /**
     * 「每次使用都需验证」密钥要求 API 30+；低于 30 只能用手输密码，
     * 这里如实返回 false，由调用方把能力标成 UnsupportedSystem。
     */
    fun isBiometricCryptoSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return encode(salt)
    }

    /** 派生校验值；salt 非法或算法缺失时返回 null，由调用方按失败处理 */
    fun deriveVerifier(password: String, salt: String): String? {
        val saltBytes = decode(salt) ?: return null
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        return try {
            val derived =
                SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
            encode(derived)
        } catch (_: Exception) {
            null
        } finally {
            spec.clearPassword()
        }
    }

    /** 定长比较，避免按字节提前 return 造成的时间差 */
    fun verify(password: String, salt: String, verifier: String): Boolean {
        if (salt.isBlank() || verifier.isBlank()) return false
        val expected = decode(verifier) ?: return false
        val actual = deriveVerifier(password, salt)?.let { decode(it) } ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    /** 读取或创建 Keystore 中受生物验证保护的密钥 */
    fun getOrCreateSecretKey(): SecretKey? {
        if (!isBiometricCryptoSupported()) return null
        val keyStore = openKeyStore() ?: return null
        val existing = runCatching { keyStore.getKey(KEY_ALIAS, null) as? SecretKey }.getOrNull()
        if (existing != null) return existing
        // getKey 抛 UnrecoverableKeyException（部分机型在密钥被指纹变更作废时直接在这一步暴露）
        // 时别名仍占着位置，必须先删掉才重建得出来
        runCatching { keyStore.deleteEntry(KEY_ALIAS) }
        return generateSecretKey()
    }

    private fun openKeyStore(): KeyStore? = runCatching {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.R)
    private fun generateSecretKey(): SecretKey? = runCatching {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // 0 表示"每次使用都要验证"，只允许 Class 3 生物识别
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply { init(spec) }
            .generateKey()
    }.getOrNull()

    /** 新增/变更指纹会让密钥永久失效，此时只能删掉重建，并要求用户重新输一次密码重建信封 */
    fun resetSecretKey() {
        runCatching { openKeyStore()?.deleteEntry(KEY_ALIAS) }
    }

    fun isKeyPermanentlyInvalidated(error: Throwable?): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is KeyPermanentlyInvalidatedException) return true
            cause = cause.cause
        }
        return false
    }

    /**
     * 一次 Cipher 初始化的结果。
     *
     * 把"密钥已被指纹变更作废"和"环境不可用"分开很重要：前者必须清理凭据，后者不能。
     */
    private data class CipherAttempt(val cipher: Cipher?, val invalidated: Boolean)

    private fun attemptCipher(block: () -> Cipher): CipherAttempt = runCatching(block).fold(
        onSuccess = { CipherAttempt(cipher = it, invalidated = false) },
        onFailure = { CipherAttempt(cipher = null, invalidated = isKeyPermanentlyInvalidated(it)) },
    )

    private fun createEncryptCipherOnce(): CipherAttempt {
        if (!isBiometricCryptoSupported()) return CipherAttempt(null, false)
        val key = getOrCreateSecretKey() ?: return CipherAttempt(null, false)
        return attemptCipher {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        }
    }

    private fun createDecryptCipherOnce(ivBytes: ByteArray): CipherAttempt {
        if (!isBiometricCryptoSupported()) return CipherAttempt(null, false)
        val key = getOrCreateSecretKey() ?: return CipherAttempt(null, false)
        return attemptCipher {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, ivBytes))
            }
        }
    }

    /**
     * 用于加密密码的 Cipher（写入信封时调用）。
     *
     * 密钥被"新增/变更指纹"作废后有两副面孔：有的机型 `getKey` 直接抛
     * UnrecoverableKeyException（上面已自愈），有的机型取键成功、**只有 init 才抛**
     * [KeyPermanentlyInvalidatedException]。后者如果不处理，开关就会永远点不开——
     * 每次开启都拿到同一个失效密钥、都在这里失败。
     *
     * 这里失败即判定为作废，删掉重建再试一次：加密的目的是产出**新的**信封，
     * 重建不会让任何已有数据变得不可读，所以自愈是安全的。
     */
    fun createEncryptCipher(): Cipher? {
        val first = createEncryptCipherOnce()
        if (first.cipher != null || !first.invalidated) return first.cipher
        resetSecretKey()
        return createEncryptCipherOnce().cipher
    }

    /** 解密侧的结果：区分"该清凭据"与"环境不可用" */
    sealed interface DecryptResult {
        /** 信封与密钥都可用，可以交给 BiometricPrompt 的 CryptoObject */
        data class Ready(val cipher: Cipher) : DecryptResult

        /** 密钥被新增/变更指纹永久作废：信封已经解不开，调用方应清掉它并关闭开关 */
        data object KeyInvalidated : DecryptResult

        /** 设备能力或密钥环境不可用（无硬件、IV 非法、算法缺失） */
        data object Unavailable : DecryptResult
    }

    /**
     * 用于解密密码的 Cipher（交给 BiometricPrompt 的 CryptoObject）。
     *
     * 与加密侧不同，这里**不能**靠重建密钥自愈：已落盘的信封是用旧密钥封的，
     * 新密钥一样解不开。所以只如实报告结果，由调用方清掉信封、关闭开关、回退到手输密码。
     */
    fun createDecryptCipher(iv: String): DecryptResult {
        val ivBytes = decode(iv) ?: return DecryptResult.Unavailable
        val attempt = createDecryptCipherOnce(ivBytes)
        val cipher = attempt.cipher
        return when {
            cipher != null -> DecryptResult.Ready(cipher)
            attempt.invalidated -> DecryptResult.KeyInvalidated
            else -> DecryptResult.Unavailable
        }
    }

    data class Envelope(val ciphertext: String, val iv: String)

    /**
     * 用**已通过生物认证**的 cipher 把密码封进信封。
     *
     * 密钥是 auth-per-use（`setUserAuthenticationParameters(0, ...)`），加密与解密一样受认证约束：
     * 未认证时 doFinal 会抛 UserNotAuthenticatedException。所以信封只能在 BiometricPrompt 的
     * CryptoObject 回调里产出（见 [BiometricEnvelopeWriter]），不能"在后台悄悄加密"——
     * 那样必然失败，而且很容易被误当成"功能已开启"。
     */
    fun sealPassword(cipher: Cipher, password: String): Envelope? {
        if (password.isEmpty()) return null
        return runCatching {
            val bytes = cipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))
            Envelope(ciphertext = encode(bytes), iv = encode(cipher.iv))
        }.getOrNull()
    }

    /** 生物验证通过后，用同一个 Cipher 解出密码 */
    fun decryptPassword(cipher: Cipher, ciphertext: String): String? {
        val bytes = decode(ciphertext) ?: return null
        return runCatching {
            cipher.doFinal(bytes).toString(StandardCharsets.UTF_8)
        }.getOrNull()
    }

    // 用 java.util.Base64 而不是 android.util.Base64：前者在 JVM 单测里可直接跑，
    // 不依赖 Robolectric，密码学逻辑因此可以做纯单元测试。
    private fun encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray? = runCatching {
        Base64.getDecoder().decode(value)
    }.getOrNull()
}
