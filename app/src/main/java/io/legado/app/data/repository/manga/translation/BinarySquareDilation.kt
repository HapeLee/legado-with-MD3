// SPDX-License-Identifier: Apache-2.0
// Adapted from ciddwd/overlay-translator (64a7aa2).
package io.legado.app.data.repository.manga.translation

/** Linear-time dilation with the same square structuring element as a naive radius scan. */
internal object BinarySquareDilation {
    fun dilate(
        input: BooleanArray,
        width: Int,
        height: Int,
        radius: Int,
    ): BooleanArray {
        require(width > 0 && height > 0)
        require(input.size == width * height)
        require(radius >= 0)
        if (radius == 0) return input.copyOf()

        val horizontal = BooleanArray(input.size)
        for (y in 0 until height) {
            val rowOffset = y * width
            var active = 0
            for (x in 0..minOf(radius, width - 1)) {
                if (input[rowOffset + x]) active++
            }
            for (x in 0 until width) {
                horizontal[rowOffset + x] = active > 0
                val leavingX = x - radius
                if (leavingX >= 0 && input[rowOffset + leavingX]) active--
                val enteringX = x + radius + 1
                if (enteringX < width && input[rowOffset + enteringX]) active++
            }
        }

        val output = BooleanArray(input.size)
        for (x in 0 until width) {
            var active = 0
            for (y in 0..minOf(radius, height - 1)) {
                if (horizontal[y * width + x]) active++
            }
            for (y in 0 until height) {
                output[y * width + x] = active > 0
                val leavingY = y - radius
                if (leavingY >= 0 && horizontal[leavingY * width + x]) active--
                val enteringY = y + radius + 1
                if (enteringY < height && horizontal[enteringY * width + x]) active++
            }
        }
        return output
    }
}
