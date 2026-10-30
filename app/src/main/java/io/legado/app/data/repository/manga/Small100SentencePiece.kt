package io.legado.app.data.repository.manga

import androidx.annotation.Keep
import java.io.Closeable

/** Pass UTF-8 bytes across JNI: JNI's modified UTF-8 cannot represent supplementary characters. */
@Keep
internal class Small100SentencePiece(path: String) : Closeable {
    private var handle = create(path)
    fun encode(text: String): List<String> {
        check(handle != 0L)
        return encodeNative(handle, text.toByteArray(Charsets.UTF_8)).map { it.toString(Charsets.UTF_8) }
    }
    fun decode(pieces: List<String>): String {
        check(handle != 0L)
        return decodeNative(handle, pieces.map { it.toByteArray(Charsets.UTF_8) }.toTypedArray()).toString(Charsets.UTF_8)
    }
    override fun close() { if (handle != 0L) { destroy(handle); handle = 0L } }
    private external fun create(path: String): Long
    private external fun encodeNative(handle: Long, text: ByteArray): Array<ByteArray>
    private external fun decodeNative(handle: Long, pieces: Array<ByteArray>): ByteArray
    private external fun destroy(handle: Long)
    companion object { init { System.loadLibrary("legado_sentencepiece") } }
}
