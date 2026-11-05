package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import java.io.Closeable
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/** Encodes a tall page row by row. No full-page bitmap or RGBA buffer is allocated. */
internal class MangaPngWriter(output: OutputStream, private val width: Int, private val height: Int) : Closeable {
    private val out = DataOutputStream(output)
    private var rows = 0
    private val compressed: DeflaterOutputStream
    init {
        out.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        val header = java.io.ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).apply { writeInt(width); writeInt(height); write(byteArrayOf(8, 6, 0, 0, 0)) }
        }.toByteArray()
        chunk("IHDR", header)
        compressed = DeflaterOutputStream(object : OutputStream() {
            override fun write(b: Int) = chunk("IDAT", byteArrayOf(b.toByte()))
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (len > 0) chunk("IDAT", b.copyOfRange(off, off + len))
            }
        })
    }

    fun append(bitmap: Bitmap, start: Int, count: Int) {
        require(bitmap.width == width && start >= 0 && count > 0 && start + count <= bitmap.height && rows + count <= height) {
            "Invalid PNG tile ${bitmap.width}x${bitmap.height}: start=$start count=$count written=$rows output=${width}x$height"
        }
        val pixels = IntArray(width)
        val row = ByteArray(width * 4 + 1)
        for (y in start until start + count) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            pixels.forEachIndexed { x, color ->
                val p = x * 4 + 1
                row[p] = (color shr 16).toByte()
                row[p + 1] = (color shr 8).toByte()
                row[p + 2] = color.toByte()
                row[p + 3] = (color ushr 24).toByte()
            }
            compressed.write(row)
        }
        rows += count
    }

    private fun chunk(type: String, data: ByteArray) {
        val tag = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(tag); update(data) }
        out.writeInt(data.size); out.write(tag); out.write(data); out.writeInt(crc.value.toInt())
    }

    override fun close() {
        try {
            require(rows == height) { "Incomplete translated image" }
            compressed.finish()
            chunk("IEND", byteArrayOf())
        } finally {
            compressed.close()
            out.close()
        }
    }
}
