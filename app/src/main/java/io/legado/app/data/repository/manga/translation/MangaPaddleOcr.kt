// SPDX-License-Identifier: Apache-2.0
// Paddle normalization/CTC contract and DB geometry follow overlay-translator (64a7aa2).
package io.legado.app.data.repository.manga.translation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import io.legado.app.domain.model.manga.MangaSourceLine
import io.legado.app.domain.model.manga.MangaTextBox
import io.legado.app.domain.model.manga.MangaTranslatedRegion
import io.legado.app.domain.model.manga.MangaLocalOcrEngine
import io.legado.app.domain.model.manga.MangaOcrOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/** CPU DBNet + CTC. Sessions close before the installer permits files to be replaced or deleted. */
internal class MangaPaddleOcr(private val models: MangaOcrModelRepository) {
    suspend fun detect(bitmap: Bitmap, language: String, engine: MangaLocalOcrEngine = MangaLocalOcrEngine.PADDLE_V5,
        options: MangaOcrOptions = MangaOcrOptions()): List<MangaTranslatedRegion> {
        options.validate()
        return withMangaOcrInput(bitmap, options) { detectPrepared(it, language, engine, options) }
    }

    private suspend fun detectPrepared(bitmap: Bitmap, language: String, engine: MangaLocalOcrEngine,
        settings: MangaOcrOptions): List<MangaTranslatedRegion> {
        require(MangaPaddleModelCatalog.supports(engine, language)) { "${engine.name}: unsupported OCR language ($language)" }
        val owner = currentCoroutineContext()
        return models.withInstalled(engine) { root ->
            // Each forward is non-cancellable, but the owner is checked between crops. The input
            // bitmap and session remain alive until the current native call has actually returned.
            withContext(NonCancellable + Dispatchers.Default) {
                val env = OrtEnvironment.getEnvironment()
                OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                    env.createSession(File(root, "det.onnx").absolutePath, options).use { det ->
                        env.createSession(File(root, "rec.onnx").absolutePath, options).use { rec ->
                            owner.ensureActive()
                            val keys = mangaPaddleDictionary(File(root, "keys.yml").readLines())
                            val thresholds = settings.detectionThresholds(engine)
                            val ratio = minOf(1f, settings.detectionProfile.maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
                            val w = maxOf(32, (bitmap.width * ratio / 32).roundToInt() * 32)
                            val h = maxOf(32, (bitmap.height * ratio / 32).roundToInt() * 32)
                            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                            val quads = try {
                                OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor(scaled, true)), longArrayOf(1, 3, h.toLong(), w.toLong())).use { input ->
                                    det.run(mapOf(det.inputNames.single() to input)).use { result ->
                                        @Suppress("UNCHECKED_CAST")
                                        val probability = (result[0].value as Array<Array<Array<FloatArray>>>)[0][0]
                                        MangaPaddleDbPostprocessor.extractQuads(probability,
                                            bitmap.width.toFloat() / probability[0].size, bitmap.height.toFloat() / probability.size,
                                            thresholds.probability, thresholds.score, thresholds.unclip, retainSupport = true)
                                    }
                                }
                            } finally { if (scaled !== bitmap) scaled.recycle() }
                            check(quads.size <= 256) { "Too many OCR text regions" }
                            val ordered = quads.sortedWith(compareBy({ it.centerY }, { it.centerX }))
                            val supports = ordered.map { it.support ?: it }
                            fun MangaPaddleDbPostprocessor.Quad.corners() = floatArrayOf(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
                            val neighborCorners = supports.map { it.corners() }
                            supports.mapIndexedNotNull { index, quad ->
                                owner.ensureActive()
                                val fitted = MangaPaddleDbPostprocessor.fitRecognitionCropQuad(quad, bitmap.width, bitmap.height) ?: return@mapIndexedNotNull null
                                val detectorCrop = MangaPaddleDbPostprocessor.fitRecognitionCropQuad(ordered[index], bitmap.width, bitmap.height)
                                val cropPlan = mangaOcrCropPlan(fitted.corners(), neighborCorners.filterIndexed { i, _ -> i != index }, detectorCrop?.corners())
                                val roi = MangaOcrCropSampler.sourceBounds(cropPlan.corners, bitmap.width, bitmap.height)
                                val roiWidth = roi[2] - roi[0]; val roiHeight = roi[3] - roi[1]
                                check(roiWidth.toLong() * roiHeight <= 4_194_304) { "OCR crop exceeds memory budget" }
                                val pixels = IntArray(roiWidth * roiHeight)
                                bitmap.getPixels(pixels, 0, roiWidth, roi[0], roi[1], roiWidth, roiHeight)
                                val sampled = MangaOcrCropSampler.warp(pixels, roiWidth, roiHeight, roi[0], roi[1], cropPlan.corners, cropPlan.width, 48)
                                val crop = Bitmap.createBitmap(sampled, cropPlan.width, 48, Bitmap.Config.ARGB_8888)
                                val text = try { recognize(crop, rec, env, keys, engine == MangaLocalOcrEngine.PADDLE_V6_MANGA) } finally { crop.recycle() }
                                if (text.isBlank()) return@mapIndexedNotNull null
                                val original = neighborCorners[index]
                                val xs = (0..3).map { original[it * 2] }; val ys = (0..3).map { original[it * 2 + 1] }
                                val box = MangaTextBox(
                                    (xs.min() * 1000f / bitmap.width).coerceIn(0f, 999f),
                                    (ys.min() * 1000f / bitmap.height).coerceIn(0f, 999f),
                                    (xs.max() * 1000f / bitmap.width).coerceIn(1f, 1000f),
                                    (ys.max() * 1000f / bitmap.height).coerceIn(1f, 1000f),
                                ).also { it.validate() }
                                val sourceQuad = mangaSourceQuad(original, bitmap.width, bitmap.height)
                                MangaTranslatedRegion("r$index", text, text, box, layoutBox = box,
                                    vertical = cropPlan.vertical, sourceLines = listOf(MangaSourceLine(text, box, sourceQuad)))
                            }.also { owner.ensureActive() }
                        }
                    }
                }
            }
        }.also { owner.ensureActive() }
    }

    private fun recognize(bitmap: Bitmap, session: OrtSession, env: OrtEnvironment, keys: List<String>, rgb: Boolean): String {
        val targetW = maxOf(320, bitmap.width)
        val padded = Bitmap.createBitmap(targetW, 48, Bitmap.Config.ARGB_8888)
        try {
            Canvas(padded).apply { drawColor(Color.rgb(127, 127, 127)); drawBitmap(bitmap, 0f, 0f, null) }
            OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor(padded, false, rgb)), longArrayOf(1, 3, 48, targetW.toLong())).use { input ->
                session.run(mapOf(session.inputNames.single() to input)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    return mangaPaddleCtc((result[0].value as Array<Array<FloatArray>>)[0], keys)
                }
            }
        } finally { padded.recycle() }
    }

    private fun tensor(bitmap: Bitmap, detection: Boolean, rgb: Boolean = false): FloatArray {
        val count = bitmap.width * bitmap.height
        val pixels = IntArray(count).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        val output = FloatArray(count * 3)
        val mean = if (detection) floatArrayOf(.485f, .456f, .406f) else floatArrayOf(.5f, .5f, .5f)
        val std = if (detection) floatArrayOf(.229f, .224f, .225f) else floatArrayOf(.5f, .5f, .5f)
        // Official Paddle YAML specifies BGR; the manga fine-tune explicitly specifies RGB.
        for (c in 0..2) for (i in 0 until count) output[c * count + i] =
            (((pixels[i] ushr ((if (rgb) 2 - c else c) * 8)) and 255) / 255f - mean[c]) / std[c]
        return output
    }
}

/** Pinned Paddle YAML uses a scalar list; preserve quoted punctuation and literal backslashes. */
internal fun mangaPaddleDictionary(lines: List<String>): List<String> {
    val start = lines.indexOfFirst { it.trim() == "character_dict:" }
    require(start >= 0)
    return lines.drop(start + 1).takeWhile { it.startsWith("  - ") }.map { line ->
        val raw = line.substring(4).trim(' ', '\t')
        when {
            raw.startsWith("'") && raw.endsWith("'") -> raw.substring(1, raw.lastIndex).replace("''", "'")
            raw.startsWith('"') -> kotlinx.serialization.json.Json.decodeFromString<String>(raw)
            else -> raw
        }
    }.also { require(it.isNotEmpty() && it.all(String::isNotEmpty)) }
}

internal fun mangaPaddleCtc(steps: Array<FloatArray>, keys: List<String>): String {
    val text = StringBuilder()
    var previous = -1
    var score = 0f
    var count = 0
    for (step in steps) {
        require(step.size == keys.size + 2) { "OCR dictionary does not match model" }
        val best = step.indices.maxBy { step[it] }
        if (best > 0 && best != previous) {
            text.append(if (best == keys.size + 1) " " else keys[best - 1])
            score += step[best]; count++
        }
        previous = best
    }
    return if (count > 0 && score / count >= .5f) text.toString().trim() else ""
}
