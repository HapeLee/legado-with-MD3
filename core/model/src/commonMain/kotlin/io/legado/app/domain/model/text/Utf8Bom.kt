package io.legado.app.domain.model.text

/**
 * UTF-8 byte-order-mark handling shared by the local-book and HTTP decoding paths.
 *
 * M2-5：从 `io.legado.app.utils.Utf8BomUtils` 迁来并**去掉 `Utils` 后缀**
 * （AGENTS.md 目标态：`help` / `utils` 这类以形态命名的包与 `XxxUtils` 类型都要退场）。
 * 行为一字未改，只是换了个能说明"它是什么"的名字与包。
 */
object Utf8Bom {
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
