package io.legado.app.help.readaloud

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.tanh

class ReadAloudLoudnessAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetGain = 1f
    private var currentGain = 1f
    private var rampSamplesRemaining = 0

    fun setGain(gain: Float) {
        val next = gain.coerceIn(0.35f, 2.4f)
        if (abs(next - targetGain) < 0.01f) return
        targetGain = next
        rampSamplesRemaining = 1_600
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining())
        while (inputBuffer.remaining() >= 2) {
            val lo = inputBuffer.get().toInt() and 0xFF
            val hi = inputBuffer.get().toInt()
            val sample = ((hi shl 8) or lo).toShort().toInt()
            val gain = nextGain()
            val normalized = (sample / 32768f) * gain
            val shaped = softLimit(normalized)
            val out = (shaped * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.put((out and 0xFF).toByte())
            output.put(((out shr 8) and 0xFF).toByte())
        }
        while (inputBuffer.hasRemaining()) {
            output.put(inputBuffer.get())
        }
        output.flip()
    }

    override fun onFlush() {
        currentGain = targetGain
        rampSamplesRemaining = 0
    }

    override fun onReset() {
        targetGain = 1f
        currentGain = 1f
        rampSamplesRemaining = 0
    }

    private fun nextGain(): Float {
        val remaining = rampSamplesRemaining
        if (remaining <= 0) {
            currentGain = targetGain
            return currentGain
        }
        currentGain += (targetGain - currentGain) / remaining
        rampSamplesRemaining = remaining - 1
        return currentGain
    }

    private fun softLimit(value: Float): Float {
        val absValue = abs(value)
        if (absValue <= 0.92f) return value
        val sign = if (value < 0f) -1f else 1f
        val excess = absValue - 0.92f
        val limited = 0.92f + (0.08f * tanh((excess / 0.08f).toDouble()).toFloat())
        return sign * limited.coerceAtMost(1f)
    }
}
