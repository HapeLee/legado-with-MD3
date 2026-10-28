package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

/** Little-endian managed scratch arena. Addresses are array offsets, never native pointers. */
internal class KernelMemory {
    private val words = LongArray(KernelLayout.Workspace.ARENA_BYTES / 8)

    fun clear() {
        words.fill(0L)
    }

    fun long(address: Long): Long {
        val index = (address ushr 3).toInt()
        val shift = (address.toInt() and 7) * 8
        return if (shift == 0) words[index]
        else (words[index] ushr shift) or (words[index + 1] shl (64 - shift))
    }

    fun long(address: Long, value: Long) {
        val index = (address ushr 3).toInt()
        val shift = (address.toInt() and 7) * 8
        if (shift == 0) words[index] = value
        else {
            words[index] = (words[index] and (-1L ushr (64 - shift))) or (value shl shift)
            words[index + 1] = (words[index + 1] and (-1L shl shift)) or (value ushr (64 - shift))
        }
    }

    fun byte(address: Long): Long =
        (words[(address ushr 3).toInt()] ushr ((address.toInt() and 7) * 8)) and 255

    fun byte(address: Long, value: Long) {
        val index = (address ushr 3).toInt()
        val shift = (address.toInt() and 7) * 8
        words[index] = (words[index] and (255L shl shift).inv()) or ((value and 255) shl shift)
    }

    fun word(address: Long): Long = (long(address) and KernelLayout.WORD_MASK)

    fun word(address: Long, value: Long) {
        val index = (address ushr 3).toInt()
        val shift = (address.toInt() and 7) * 8
        if (shift <= 32)
            words[index] =
                (words[index] and (KernelLayout.WORD_MASK shl shift).inv()) or
                    ((value and KernelLayout.WORD_MASK) shl shift)
        else for (i in 0..3) byte(address + i, value ushr (i * 8))
    }

    fun short(address: Long, value: Long) {
        byte(address, value)
        byte(address + 1, value ushr 8)
    }

    fun copy(to: Long, from: Long, count: Long) {
        if (to > from && to < from + count)
            for (i in count.toInt() - 1 downTo 0) byte(
                to + i,
                byte(from + i),
            )
        else for (i in 0 until count.toInt()) byte(to + i, byte(from + i))
    }

    fun bytes(address: Long, size: Int) = ByteArray(size) { byte(address + it).toByte() }
}

/** A value vector; copies deliberately do not alias their source. */
internal class KernelVector(private var low: Long = 0, private var high: Long = 0) {
    /** Capture the packed source before overwriting any float lane (source may be this). */
    fun unpackHalfColorFrom(source: KernelVector) {
        val packed = source.longBits(0)
        for (channel in 0 until 4) {
            float(
                channel,
                halfFloat(
                    (packed ushr (channel * KernelLayout.HALF_BITS)) and KernelLayout.HALF_MASK
                ),
            )
        }
    }

    fun packHalfColorFrom(source: KernelVector) {
        var packed = 0L
        for (channel in 0 until 4) {
            packed =
                packed or (floatHalf(source.float(channel)) shl (channel * KernelLayout.HALF_BITS))
        }
        longBits(0, packed)
    }

    /** Component-wise premultiplied RGBA modulation, at binary32 precision. */
    fun multiplyColorsFrom(first: KernelVector, second: KernelVector) {
        for (channel in 0 until 4) float(channel, first.float(channel) * second.float(channel))
    }

    fun longBits(i: Int): Long = if (i == 0) low else high

    fun longBits(i: Int, v: Long) {
        if (i == 0) low = v else high = v
    }

    fun double(i: Int): Double = Double.fromBits(longBits(i))

    fun double(i: Int, v: Double) = longBits(i, v.toRawBits())

    fun wordBits(i: Int): Long = (longBits(i / 2) ushr ((i and 1) * 32)) and KernelLayout.WORD_MASK

    fun wordBits(i: Int, v: Long) {
        val shift = (i and 1) * 32
        longBits(
            i / 2,
            (longBits(i / 2) and (KernelLayout.WORD_MASK shl shift).inv()) or
                ((v and KernelLayout.WORD_MASK) shl shift),
        )
    }

    fun float(i: Int): Float = Float.fromBits(wordBits(i).toInt())

    fun float(i: Int, v: Float) = wordBits(i, v.toRawBits().toLong())

    fun halfBits(i: Int): Long = (longBits(i / 4) ushr ((i and 3) * 16)) and 65535

    fun halfBits(i: Int, v: Long) {
        val shift = (i and 3) * 16
        longBits(i / 4, (longBits(i / 4) and (65535L shl shift).inv()) or ((v and 65535) shl shift))
    }

    fun copy() = KernelVector(low, high)

    fun set(other: KernelVector) {
        low = other.low
        high = other.high
    }

    fun clear() {
        low = 0
        high = 0
    }

    fun load(memory: KernelMemory, offset: Long) {
        low = memory.long(offset)
        high = memory.long(offset + 8)
    }

    fun store(memory: KernelMemory, offset: Long) {
        memory.long(offset, low)
        memory.long(offset + 8, high)
    }
}
