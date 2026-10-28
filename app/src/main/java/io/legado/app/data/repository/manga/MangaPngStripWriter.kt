package io.legado.app.data.repository.manga

import java.io.Closeable
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/** One zlib stream across bounded IDAT chunks. Memory is independent of image height. */
internal class MangaPngStripWriter(
    output: OutputStream,
    private val width: Int,
    private val height: Int,
) : Closeable {
    private val out = DataOutputStream(output)
    private val row = ByteArray(1 + width * 4)
    private var rows = 0
    private val deflater: java.util.zip.Deflater
    private val compressed: DeflaterOutputStream

    init {
        require(width in 1..4096 && height > 0)
        out.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        val header = java.io.ByteArrayOutputStream()
        DataOutputStream(header).use {
            it.writeInt(width); it.writeInt(height)
            it.write(byteArrayOf(8, 6, 0, 0, 0)) // RGBA, no interlace
        }
        chunk("IHDR", header.toByteArray())
        deflater = java.util.zip.Deflater(4)
        compressed = DeflaterOutputStream(object : OutputStream() {
            override fun write(value: Int) = chunk("IDAT", byteArrayOf(value.toByte()))
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                if (length > 0) chunk("IDAT", bytes, offset, length)
            }
        }, deflater, 32 * 1024)
    }

    fun writeRow(argb: IntArray) {
        require(argb.size == width && rows < height)
        row[0] = 1 // Sub filter improves compression of manga's flat backgrounds.
        var previous = 0
        argb.forEachIndexed { x, color ->
            val offset = 1 + x * 4
            row[offset] = ((color shr 16 and 255) - (previous shr 16 and 255)).toByte()
            row[offset + 1] = ((color shr 8 and 255) - (previous shr 8 and 255)).toByte()
            row[offset + 2] = ((color and 255) - (previous and 255)).toByte()
            row[offset + 3] = ((color ushr 24) - (previous ushr 24)).toByte()
            previous = color
        }
        compressed.write(row)
        rows++
    }

    override fun close() {
        try {
            check(rows == height) { "Incomplete translated image" }
            compressed.finish()
            chunk("IEND", byteArrayOf())
            out.flush()
        } finally {
            try { compressed.close() } finally { deflater.end(); out.close() }
        }
    }

    private fun chunk(type: String, bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        val tag = type.toByteArray(Charsets.US_ASCII)
        out.writeInt(length); out.write(tag); out.write(bytes, offset, length)
        val crc = CRC32().apply { update(tag); update(bytes, offset, length) }
        out.writeInt(crc.value.toInt())
    }
}
