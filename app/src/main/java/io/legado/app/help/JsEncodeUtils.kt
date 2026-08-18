package io.legado.app.help

import android.util.Base64
import android.webkit.JavascriptInterface
import io.legado.app.help.crypto.AsymmetricCrypto
import io.legado.app.help.crypto.Sign
import io.legado.app.help.crypto.SymmetricCryptoAndroid
import io.legado.app.help.crypto.digest
import io.legado.app.help.crypto.hmac
import io.legado.app.help.crypto.toHexString
import io.legado.app.utils.MD5Utils


/**
 * js加解密扩展类, 在js中通过java变量调用
 * 添加方法，请更新文档/legado/app/src/main/assets/help/JsHelp.md
 */
@Suppress("unused")
interface JsEncodeUtils {

    @JavascriptInterface
    fun md5Encode(str: String): String {
        return MD5Utils.md5Encode(str)
    }

    @JavascriptInterface
    fun md5Encode16(str: String): String {
        return MD5Utils.md5Encode16(str)
    }


    //******************对称加密解密************************//

    /**
     * 在js中这样使用
     * java.createSymmetricCrypto(transformation, key, iv).decrypt(data)
     * java.createSymmetricCrypto(transformation, key, iv).decryptStr(data)

     * java.createSymmetricCrypto(transformation, key, iv).encrypt(data)
     * java.createSymmetricCrypto(transformation, key, iv).encryptBase64(data)
     * java.createSymmetricCrypto(transformation, key, iv).encryptHex(data)
     */

    /* 调用SymmetricCrypto key为null时使用随机密钥*/
    @JavascriptInterface
    fun createSymmetricCrypto(
        transformation: String,
        key: ByteArray?,
        iv: ByteArray?
    ): SymmetricCryptoAndroid {
        val symmetricCrypto = SymmetricCryptoAndroid(transformation, key)
        return if (iv != null && iv.isNotEmpty()) symmetricCrypto.setIv(iv) else symmetricCrypto
    }

    @JavascriptInterface
    fun createSymmetricCrypto(
        transformation: String,
        key: ByteArray
    ): SymmetricCryptoAndroid {
        return createSymmetricCrypto(transformation, key, null)
    }

    @JavascriptInterface
    fun createSymmetricCrypto(
        transformation: String,
        key: String
    ): SymmetricCryptoAndroid {
        return createSymmetricCrypto(transformation, key, null)
    }

    @JavascriptInterface
    fun createSymmetricCrypto(
        transformation: String,
        key: String,
        iv: String?
    ): SymmetricCryptoAndroid {
        return createSymmetricCrypto(
            transformation, key.encodeToByteArray(), iv?.encodeToByteArray()
        )
    }
    //******************非对称加密解密************************//

    /* keys都为null时使用随机密钥 */
    @JavascriptInterface
    fun createAsymmetricCrypto(
        transformation: String
    ): AsymmetricCrypto {
        return AsymmetricCrypto(transformation)
    }

    //******************签名************************//
    @JavascriptInterface
    fun createSign(
        algorithm: String
    ): Sign {
        return Sign(algorithm)
    }
    //******************对称加密解密old************************//

    /////AES
    /**
     * AES 解码为 ByteArray
     * @param str 传入的AES加密的数据
     * @param key AES 解密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decrypt(str)")
    )
    @JavascriptInterface
    fun aesDecodeToByteArray(
        str: String, key: String, transformation: String, iv: String
    ): ByteArray? {
        return createSymmetricCrypto(transformation, key, iv).decrypt(str)
    }

    /**
     * AES 解码为 String
     * @param str 传入的AES加密的数据
     * @param key AES 解密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(str)")
    )
    @JavascriptInterface
    fun aesDecodeToString(
        str: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).decryptStr(str)
    }

    /**
     * AES解码为String，算法参数经过Base64加密
     *
     * @param data 加密的字符串
     * @param key Base64后的密钥
     * @param mode 模式
     * @param padding 补码方式
     * @param iv Base64后的加盐
     * @return 解密后的字符串
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(data)")
    )
    @JavascriptInterface
    fun aesDecodeArgsBase64Str(
        data: String,
        key: String,
        mode: String,
        padding: String,
        iv: String
    ): String? {
        return createSymmetricCrypto(
            "AES/${mode}/${padding}",
            Base64.decode(key, Base64.NO_WRAP),
            Base64.decode(iv, Base64.NO_WRAP)
        ).decryptStr(data)
    }

    /**
     * 已经base64的AES 解码为 ByteArray
     * @param str 传入的AES Base64加密的数据
     * @param key AES 解密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decrypt(str)")
    )
    @JavascriptInterface
    fun aesBase64DecodeToByteArray(
        str: String, key: String, transformation: String, iv: String
    ): ByteArray? {
        return createSymmetricCrypto(transformation, key, iv).decrypt(str)
    }

    /**
     * 已经base64的AES 解码为 String
     * @param str 传入的AES Base64加密的数据
     * @param key AES 解密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(str)")
    )
    @JavascriptInterface
    fun aesBase64DecodeToString(
        str: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).decryptStr(str)
    }

    /**
     * 加密aes为ByteArray
     * @param data 传入的原始数据
     * @param key AES加密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decrypt(data)")
    )
    @JavascriptInterface
    fun aesEncodeToByteArray(
        data: String, key: String, transformation: String, iv: String
    ): ByteArray? {
        return createSymmetricCrypto(transformation, key, iv).encrypt(data)
    }

    /**
     * 加密aes为String
     * @param data 传入的原始数据
     * @param key AES加密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(data)")
    )
    @JavascriptInterface
    fun aesEncodeToString(
        data: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).decryptStr(data)
    }

    /**
     * 加密aes后Base64化的ByteArray
     * @param data 传入的原始数据
     * @param key AES加密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encryptBase64(data).toByteArray()")
    )
    @JavascriptInterface
    fun aesEncodeToBase64ByteArray(
        data: String, key: String, transformation: String, iv: String
    ): ByteArray? {
        return createSymmetricCrypto(transformation, key, iv).encryptBase64(data).toByteArray()
    }

    /**
     * 加密aes后Base64化的String
     * @param data 传入的原始数据
     * @param key AES加密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encryptBase64(data)")
    )
    @JavascriptInterface
    fun aesEncodeToBase64String(
        data: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).encryptBase64(data)
    }


    /**
     * AES加密并转为Base64，算法参数经过Base64加密
     *
     * @param data 被加密的字符串
     * @param key Base64后的密钥
     * @param mode 模式
     * @param padding 补码方式
     * @param iv Base64后的加盐
     * @return 加密后的Base64
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encryptBase64(data)")
    )
    @JavascriptInterface
    fun aesEncodeArgsBase64Str(
        data: String,
        key: String,
        mode: String,
        padding: String,
        iv: String
    ): String? {
        return createSymmetricCrypto("AES/${mode}/${padding}", key, iv).encryptBase64(data)
    }

    /////DES
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(data)")
    )
    @JavascriptInterface
    fun desDecodeToString(
        data: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).decryptStr(data)
    }

    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(data)")
    )
    @JavascriptInterface
    fun desBase64DecodeToString(
        data: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).decryptStr(data)
    }

    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encrypt(data)")
    )
    @JavascriptInterface
    fun desEncodeToString(
        data: String, key: String, transformation: String, iv: String
    ): String? {
        return String(createSymmetricCrypto(transformation, key, iv).encrypt(data))
    }

    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encryptBase64(data)")
    )
    @JavascriptInterface
    fun desEncodeToBase64String(
        data: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).encryptBase64(data)
    }

    //////3DES
    /**
     * 3DES解密
     *
     * @param data 加密的字符串
     * @param key 密钥
     * @param mode 模式
     * @param padding 补码方式
     * @param iv 加盐
     * @return 解密后的字符串
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(data)")
    )
    @JavascriptInterface
    fun tripleDESDecodeStr(
        data: String,
        key: String,
        mode: String,
        padding: String,
        iv: String
    ): String? {
        return createSymmetricCrypto("DESede/${mode}/${padding}", key, iv).decryptStr(data)
    }

    /**
     * 3DES解密，算法参数经过Base64加密
     *
     * @param data 加密的字符串
     * @param key Base64后的密钥
     * @param mode 模式
     * @param padding 补码方式
     * @param iv Base64后的加盐
     * @return 解密后的字符串
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(data)")
    )
    @JavascriptInterface
    fun tripleDESDecodeArgsBase64Str(
        data: String,
        key: String,
        mode: String,
        padding: String,
        iv: String
    ): String? {
        return createSymmetricCrypto(
            "DESede/${mode}/${padding}",
            Base64.decode(key, Base64.NO_WRAP),
            iv.encodeToByteArray()
        ).decryptStr(data)
    }


    /**
     * 3DES加密并转为Base64
     *
     * @param data 被加密的字符串
     * @param key 密钥
     * @param mode 模式
     * @param padding 补码方式
     * @param iv 加盐
     * @return 加密后的Base64
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encryptBase64(data)")
    )
    @JavascriptInterface
    fun tripleDESEncodeBase64Str(
        data: String,
        key: String,
        mode: String,
        padding: String,
        iv: String
    ): String? {
        return createSymmetricCrypto("DESede/${mode}/${padding}", key, iv)
            .encryptBase64(data)
    }

    /**
     * 3DES加密并转为Base64，算法参数经过Base64加密
     *
     * @param data 被加密的字符串
     * @param key Base64后的密钥
     * @param mode 模式
     * @param padding 补码方式
     * @param iv Base64后的加盐
     * @return 加密后的Base64
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encryptBase64(data)")
    )
    @JavascriptInterface
    fun tripleDESEncodeArgsBase64Str(
        data: String,
        key: String,
        mode: String,
        padding: String,
        iv: String
    ): String? {
        return createSymmetricCrypto(
            "DESede/${mode}/${padding}",
            Base64.decode(key, Base64.NO_WRAP),
            iv.encodeToByteArray()
        ).encryptBase64(data)
    }

//******************消息摘要/散列消息鉴别码************************//

    /**
     * 生成摘要，并转为16进制字符串
     *
     * @param data 被摘要数据
     * @param algorithm 签名算法
     * @return 16进制字符串
     */
    @JavascriptInterface
    fun digestHex(
        data: String,
        algorithm: String,
    ): String {
        return digest(algorithm, data.toByteArray()).toHexString()
    }

    /**
     * 生成摘要，并转为Base64字符串
     *
     * @param data 被摘要数据
     * @param algorithm 签名算法
     * @return Base64字符串
     */
    @JavascriptInterface
    fun digestBase64Str(
        data: String,
        algorithm: String,
    ): String {
        return Base64.encodeToString(digest(algorithm, data.toByteArray()), Base64.NO_WRAP)
    }

    /**
     * 生成散列消息鉴别码，并转为16进制字符串
     *
     * @param data 被摘要数据
     * @param algorithm 签名算法
     * @param key 密钥
     * @return 16进制字符串
     */
    @Suppress("FunctionName")
    @JavascriptInterface
    fun HMacHex(
        data: String,
        algorithm: String,
        key: String
    ): String {
        return hmac(algorithm, key.toByteArray(), data.toByteArray()).toHexString()
    }

    /**
     * 生成散列消息鉴别码，并转为Base64字符串
     *
     * @param data 被摘要数据
     * @param algorithm 签名算法
     * @param key 密钥
     * @return Base64字符串
     */
    @Suppress("FunctionName")
    @JavascriptInterface
    fun HMacBase64(
        data: String,
        algorithm: String,
        key: String
    ): String {
        return Base64.encodeToString(
            hmac(algorithm, key.toByteArray(), data.toByteArray()),
            Base64.NO_WRAP
        )
    }


}
