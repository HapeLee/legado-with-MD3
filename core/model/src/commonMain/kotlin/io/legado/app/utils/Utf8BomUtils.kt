package io.legado.app.utils

/** UTF-8 byte-order-mark handling shared by local-book and HTTP decoding paths. */
object Utf8BomUtils {
    private val utf8BomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun removeUTF8BOM(xmlText: String): String {
        val bytes = xmlText.encodeToByteArray()
        return if (hasBom(bytes)) bytes.decodeToString(startIndex = utf8BomBytes.size) else xmlText
    }

    fun removeUTF8BOM(bytes: ByteArray): ByteArray =
        if (hasBom(bytes)) bytes.copyOfRange(utf8BomBytes.size, bytes.size) else bytes

    fun hasBom(bytes: ByteArray): Boolean =
        bytes.size > utf8BomBytes.size &&
            bytes[0] == utf8BomBytes[0] &&
            bytes[1] == utf8BomBytes[1] &&
            bytes[2] == utf8BomBytes[2]
}
