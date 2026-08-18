package io.legado.app.utils

import java.security.MessageDigest
import kotlin.uuid.Uuid

fun nameUuidFromBytes(bytes: ByteArray): Uuid {
    val md5 = MessageDigest.getInstance("MD5").digest(bytes)
    md5[6] = (md5[6].toInt() and 0x0F or 0x30).toByte()
    md5[8] = (md5[8].toInt() and 0x3F or 0x80).toByte()
    return Uuid.fromByteArray(md5)
}
