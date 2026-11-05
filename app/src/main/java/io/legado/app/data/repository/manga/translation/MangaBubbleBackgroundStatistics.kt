// SPDX-License-Identifier: Apache-2.0
// Adapted from ciddwd/overlay-translator, commit 64a7aa2c9605c88dd35bdeea42ab5c0f1c18ce5e.
// Modifications: Legado package, geometry types and bounded Android pipeline integration.
package io.legado.app.data.repository.manga.translation

import kotlin.math.roundToInt

/** Primitive, stable color statistics used by manga bubble-background estimation. */
internal object MangaBubbleBackgroundStatistics {

    data class Estimate(
        val red: Int,
        val green: Int,
        val blue: Int,
        val luminance: Int,
        val lowLuminance: Int,
    )

    class Scratch {
        var samples = IntArray(0)
            private set
        private var sorted = IntArray(0)
        private val luminanceCounts = IntArray(CHANNEL_VALUES)
        private val writeOffsets = IntArray(CHANNEL_VALUES)
        private val redCounts = IntArray(CHANNEL_VALUES)
        private val greenCounts = IntArray(CHANNEL_VALUES)
        private val blueCounts = IntArray(CHANNEL_VALUES)

        fun ensureSampleCapacity(required: Int) {
            if (samples.size >= required) return
            val capacity = maxOf(required, samples.size.coerceAtLeast(64) * 2)
            samples = IntArray(capacity)
            sorted = IntArray(capacity)
        }

        fun estimate(
            count: Int,
            brightSampleStartRatio: Float,
            lowSampleRatio: Float,
        ): Estimate {
            require(count in 1..samples.size)
            luminanceCounts.fill(0)
            for (index in 0 until count) luminanceCounts[luminance(samples[index])]++

            var offset = 0
            for (value in 0 until CHANNEL_VALUES) {
                writeOffsets[value] = offset
                offset += luminanceCounts[value]
            }
            // Counting sort is stable because samples are visited in their original order.
            for (index in 0 until count) {
                val color = samples[index]
                val value = luminance(color)
                sorted[writeOffsets[value]++] = color
            }

            val brightestStart = (count * brightSampleStartRatio).roundToInt()
                .coerceIn(0, count - 1)
            redCounts.fill(0)
            greenCounts.fill(0)
            blueCounts.fill(0)
            for (index in brightestStart until count) {
                val color = sorted[index]
                redCounts[color ushr 16 and 0xff]++
                greenCounts[color ushr 8 and 0xff]++
                blueCounts[color and 0xff]++
            }
            val brightestCount = count - brightestStart
            val medianRank = brightestCount / 2
            val red = valueAtRank(redCounts, medianRank)
            val green = valueAtRank(greenCounts, medianRank)
            val blue = valueAtRank(blueCounts, medianRank)
            val lowIndex = ((count - 1) * lowSampleRatio).roundToInt().coerceIn(0, count - 1)
            return Estimate(
                red = red,
                green = green,
                blue = blue,
                luminance = luminance(red, green, blue),
                lowLuminance = luminance(sorted[lowIndex]),
            )
        }
    }

    private fun valueAtRank(histogram: IntArray, rank: Int): Int {
        var cumulative = 0
        histogram.forEachIndexed { value, count ->
            cumulative += count
            if (cumulative > rank) return value
        }
        return CHANNEL_VALUES - 1
    }

    private fun luminance(color: Int): Int = luminance(
        red = color ushr 16 and 0xff,
        green = color ushr 8 and 0xff,
        blue = color and 0xff,
    )

    private fun luminance(red: Int, green: Int, blue: Int): Int =
        (red * 299 + green * 587 + blue * 114) / 1000

    private const val CHANNEL_VALUES = 256
}
