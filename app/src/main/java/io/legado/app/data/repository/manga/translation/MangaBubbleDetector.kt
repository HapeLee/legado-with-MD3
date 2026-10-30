// SPDX-License-Identifier: Apache-2.0
// RT-DETR contract and buffering adapted from ciddwd/overlay-translator (64a7aa2).
package io.legado.app.data.repository.manga.translation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

data class MangaBubbleDetection(val bounds: MangaShapeRect, val confidence: Float, val kind: Int)

/** CPU-only image analysis. One session/workspace, serialized forwards, released after idle. */
internal object MangaBubbleDetector {
    const val MODEL_BYTES = 11_120_765L
    const val MODEL_SHA256 = "5FE9E4F576E49D4E7E8B0E029D6D3CDC252ABD4694113E1CAE120E62C931EA79"
    private const val MODEL = "detector-v4-s_int8.onnx"
    private const val SIDE = 640
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var releaseJob: Job? = null
    private var session: OrtSession? = null
    private var workspace: Workspace? = null
    private var verified: File? = null
    private var lastUse = 0L

    suspend fun detect(context: Context, bitmap: Bitmap): List<MangaBubbleDetection> = lock.withLock {
        val entered = SystemClock.elapsedRealtime()
        Log.d("MangaDetector", "phase=locked")
        currentCoroutineContext().ensureActive()
        releaseJob?.cancel()
        try {
            // Native inference cannot be interrupted; retain the caller's bitmap until it returns.
            val output = withContext(NonCancellable + Dispatchers.Default) {
                Log.d("MangaDetector", "phase=worker dispatchMs=${SystemClock.elapsedRealtime() - entered}")
                val modelStarted = SystemClock.elapsedRealtime()
                val model = prepare(context)
                Log.d("MangaDetector", "phase=weights modelMs=${SystemClock.elapsedRealtime() - modelStarted}")
                val environmentStarted = SystemClock.elapsedRealtime()
                val environment = OrtEnvironment.getEnvironment()
                Log.d("MangaDetector", "phase=environment loadMs=${SystemClock.elapsedRealtime() - environmentStarted}")
                val sessionStarted = SystemClock.elapsedRealtime()
                val active = session ?: OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads((Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4))
                    options.setInterOpNumThreads(1)
                    options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                    environment.createSession(model.absolutePath, options).also { session = it }
                }
                Log.d("MangaDetector", "phase=session prepareMs=${SystemClock.elapsedRealtime() - sessionStarted}")
                val io = workspace ?: Workspace().also { workspace = it }
                val resized = Bitmap.createScaledBitmap(bitmap, SIDE, SIDE, true)
                try { resized.getPixels(io.pixels, 0, SIDE, 0, 0, SIDE, SIDE) }
                finally { if (resized !== bitmap) resized.recycle() }
                for (i in io.pixels.indices) {
                    val p = io.pixels[i]
                    io.values[i] = (p ushr 16 and 255) / 255f
                    io.values[io.pixels.size + i] = (p ushr 8 and 255) / 255f
                    io.values[io.pixels.size * 2 + i] = (p and 255) / 255f
                }
                io.image.clear(); io.image.put(io.values); io.image.flip()
                // This export expects width, height, matching the reference adapter.
                io.size.clear(); io.size.put(bitmap.width.toLong()).put(bitmap.height.toLong()); io.size.flip()
                val env = OrtEnvironment.getEnvironment()
                val forwardStarted = SystemClock.elapsedRealtime()
                OnnxTensor.createTensor(env, io.image, longArrayOf(1, 3, SIDE.toLong(), SIDE.toLong())).use { image ->
                    OnnxTensor.createTensor(env, io.size, longArrayOf(1, 2)).use { size ->
                        active.run(mapOf("images" to image, "orig_target_sizes" to size)).use { result ->
                            Log.d("MangaDetector", "phase=forward inferenceMs=${SystemClock.elapsedRealtime() - forwardStarted}")
                            @Suppress("UNCHECKED_CAST")
                            val labels = (result[0].value as Array<LongArray>).single()
                            @Suppress("UNCHECKED_CAST")
                            val boxes = (result[1].value as Array<Array<FloatArray>>).single()
                            @Suppress("UNCHECKED_CAST")
                            val scores = (result[2].value as Array<FloatArray>).single()
                            postprocess(bitmap.width, bitmap.height, labels, boxes, scores)
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            output
        } finally {
            lastUse = SystemClock.elapsedRealtime()
            releaseJob = scope.launch {
                delay(30_000)
                lock.withLock {
                    if (SystemClock.elapsedRealtime() - lastUse >= 30_000) {
                        session?.close(); session = null; workspace = null
                    }
                }
            }
        }
    }

    private fun prepare(context: Context): File {
        verified?.takeIf { it.isFile && it.length() == MODEL_BYTES }?.let { return it }
        val target = File(context.noBackupFilesDir, "manga-vision/$MODEL")
        fun valid(file: File): Boolean = file.isFile && file.length() == MODEL_BYTES &&
            file.inputStream().buffered().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                digest.digest().joinToString("") { "%02X".format(it) } == MODEL_SHA256
            }
        if (!valid(target)) {
            target.parentFile!!.mkdirs()
            val temp = File(target.parentFile, "$MODEL.tmp")
            try {
                context.assets.open("manga-vision/$MODEL").use { input -> temp.outputStream().use(input::copyTo) }
                check(valid(temp)) { "Invalid bundled manga detector" }
                java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally { temp.delete() }
        }
        return target.also { verified = it }
    }

    internal fun postprocess(width: Int, height: Int, labels: LongArray, boxes: Array<FloatArray>, scores: FloatArray): List<MangaBubbleDetection> {
        require(width > 0 && height > 0 && labels.size == boxes.size && boxes.size == scores.size)
        val candidates = labels.indices.mapNotNull { i ->
            val b = boxes[i]; val score = scores[i]
            if (labels[i] !in 0L..2L || !score.isFinite() || score < .30f || b.size != 4 || b.any { !it.isFinite() }) return@mapNotNull null
            val rect = MangaShapeRect(b[0].toInt().coerceIn(0, width), b[1].toInt().coerceIn(0, height),
                kotlin.math.ceil(b[2]).toInt().coerceIn(0, width), kotlin.math.ceil(b[3]).toInt().coerceIn(0, height))
            if (rect.width < 4 || rect.height < 4) null else MangaBubbleDetection(rect, score, labels[i].toInt())
        }.sortedByDescending { it.confidence }
        return buildList<MangaBubbleDetection> {
            for (candidate in candidates) if (none { other ->
                val a = candidate.bounds; val b = other.bounds
                val overlap = maxOf(0, minOf(a.right, b.right) - maxOf(a.left, b.left)).toLong() *
                    maxOf(0, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
                other.kind == candidate.kind && overlap.toDouble() / (a.width.toLong() * a.height + b.width.toLong() * b.height - overlap) >= .9
            }) add(candidate)
        }.take(256)
    }

    private class Workspace {
        val pixels = IntArray(SIDE * SIDE)
        val values = FloatArray(pixels.size * 3)
        val image = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val size = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asLongBuffer()
    }
}
