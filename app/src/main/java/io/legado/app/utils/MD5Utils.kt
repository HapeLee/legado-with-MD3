package io.legado.app.utils

import io.legado.app.help.crypto.digest
import io.legado.app.help.crypto.toHexString
import java.io.InputStream

/**
 * 将字符串转化为MD5
 */
@Suppress("unused")
object MD5Utils {

    fun md5Encode(str: String?): String {
        return digest("MD5", str.orEmpty().toByteArray()).toHexString()
    }

    fun md5Encode(inputStream: InputStream): String {
        return digest("MD5", inputStream).toHexString()
    }

    fun md5Encode16(str: String): String {
        var reStr = md5Encode(str)
        reStr = reStr.substring(8, 24)
        return reStr
    }
}
