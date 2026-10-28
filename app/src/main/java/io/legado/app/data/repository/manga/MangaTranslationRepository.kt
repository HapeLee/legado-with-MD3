package io.legado.app.data.repository.manga

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import io.legado.app.R
import io.legado.app.domain.gateway.MangaTranslationGateway
import io.legado.app.domain.gateway.MangaTranslationRequest
import io.legado.app.domain.gateway.MangaTranslationResult
import io.legado.app.domain.gateway.MangaTranslationStage
import io.legado.app.domain.model.MangaTranslationEngine
import io.legado.app.domain.usecase.TranslateMangaTextUseCase
import io.legado.app.domain.reader.MangaImageRegion
import io.legado.app.domain.reader.MangaRegionDecoder
import io.legado.app.help.coil.CoverExtras
import io.legado.app.help.coil.MangaImageFileOwner
import io.legado.app.help.coil.mangaWebtoonTileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Bounded region decoding, overlapping OCR, masked inpainting and streaming PNG output. */
class MangaTranslationRepository(
    private val application: Application,
    private val imageLoader: ImageLoader,
    private val aiTranslator: TranslateMangaTextUseCase,
) : MangaTranslationGateway {
    private val mutex = Mutex()
    private val cacheLimit = 128L * 1024 * 1024
    private fun directory() = File(application.cacheDir, "manga-translation-v2").apply { mkdirs() }

    override fun observeAiConfiguration() = aiTranslator.configurations()

    override suspend fun clearCache() = mutex.withLock {
        withContext(Dispatchers.IO) {
            listOf("manga-translation-v1", "manga-translation-v2").forEach { name ->
                File(application.cacheDir, name).listFiles()?.forEach {
                    if (it.name == ".dimensions" && it.isDirectory) it.deleteRecursively() else it.delete()
                }
            }
            Unit
        }
    }

    override suspend fun translate(
        request: MangaTranslationRequest,
        onStage: (MangaTranslationStage) -> Unit,
    ): MangaTranslationResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            suspend fun stage(value: MangaTranslationStage) {
                currentCoroutineContext().ensureActive()
                onStage(value)
            }
            require(request.sourceLanguage in setOf("ja", "en", "ko"))
            require(request.targetLanguage in setOf("zh", "en", "ja", "ko"))
            require(request.sourceLanguage != request.targetLanguage)
            check(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(application) ==
                ConnectionResult.SUCCESS) { application.getString(R.string.manga_translation_gms) }
            stage(MangaTranslationStage.PREPARING)
            val owner = MangaImageFileOwner()
            try {
                // The small preview only acquires the source. OCR always decodes original regions.
                val loaded = imageLoader.execute(ImageRequest.Builder(application).data(request.imageUrl)
                    .allowHardware(false).size(256, 256).apply {
                        extras[CoverExtras.Manga] = true
                        extras[CoverExtras.MangaBookUrl] = request.bookUrl
                        extras[CoverExtras.SourceOrigin] = request.sourceOrigin
                        extras[CoverExtras.MangaChapterSubject] = request.chapterSubject
                        extras[CoverExtras.MangaFileOwner] = owner
                    }.build()) as? SuccessResult ?: error(application.getString(R.string.manga_translation_failed))
                val source = withContext(NonCancellable) { mangaWebtoonTileSource(loaded, owner, application) }
                    ?: error(application.getString(R.string.manga_translation_decode_failed))
                source.use {
                    val decoder = withContext(NonCancellable) { source.open() }
                    try {
                        currentCoroutineContext().ensureActive()
                        var sample = 1
                        while ((decoder.imageSize.width + sample - 1) / sample > 1536) sample *= 2
                        val width = (decoder.imageSize.width + sample - 1) / sample
                        val height = (decoder.imageSize.height + sample - 1) / sample
                        require(height <= 1_000_000) { application.getString(R.string.manga_translation_decode_failed) }
                        val digest = MessageDigest.getInstance("SHA-256")
                        digest.update(source.contentDigest())
                        digest.update("mlkit-ja-ko16.0.1-t17.0.3-strip2-mask1:$sample:${request.sourceLanguage}:${request.targetLanguage}".toByteArray())
                        digest.update(request.config.cacheIdentity().toByteArray())
                        val key = digest.digest().joinToString("") { "%02x".format(it) }
                        val directory = directory()
                        val output = File(directory, "$key.png")
                        if (output.isFile && output.length() > 0) return@withContext MangaTranslationResult(output.absolutePath, null)
                        val available = cacheLimit - directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        check(available > 0) { application.getString(R.string.manga_translation_cache_full) }
                        stage(MangaTranslationStage.RECOGNIZING)
                        val recognizer = if (request.sourceLanguage == "ko") {
                            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                        } else TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                        val candidates = mutableListOf<MangaOcrRegion>()
                        try {
                            val modules = ModuleInstall.getClient(application)
                            if (!modules.areModulesAvailable(recognizer).awaitDownload().areModulesAvailable()) {
                                modules.installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).build()).awaitDownload()
                                error(application.getString(R.string.manga_translation_ocr_download))
                            }
                            for (strip in mangaOcrStrips(height)) {
                                currentCoroutineContext().ensureActive()
                                val tile = decode(decoder, Rect(0, strip.top, width, strip.bottom), sample)
                                try {
                                    val text = recognizer.process(InputImage.fromBitmap(tile, 0)).finishSafely()
                                    text.textBlocks.forEach { block ->
                                        val lines = block.lines.mapNotNull { line ->
                                            val bounds = line.boundingBox?.let(::Rect) ?: return@mapNotNull null
                                            if (bounds.isEmpty || line.text.isBlank()) return@mapNotNull null
                                            bounds.offset(0, strip.top)
                                            val glyphs = line.elements.mapNotNull { element ->
                                                val points = element.cornerPoints ?: return@mapNotNull null
                                                points.map { Point(it.x, it.y + strip.top) }
                                            }.ifEmpty {
                                                listOf(listOf(Point(bounds.left, bounds.top), Point(bounds.right, bounds.top),
                                                    Point(bounds.right, bounds.bottom), Point(bounds.left, bounds.bottom)))
                                            }
                                            MangaOcrLine(line.text, bounds, glyphs)
                                        }
                                        if (lines.isNotEmpty()) candidates += MangaOcrRegion(lines)
                                    }
                                    check(candidates.size <= 10_000) { application.getString(R.string.manga_translation_decode_failed) }
                                } finally { tile.recycle() }
                            }
                        } finally { recognizer.close() }
                        val mergeContext = currentCoroutineContext()
                        val regions = mergeMangaOcrRegions(candidates) { mergeContext.ensureActive() }
                        if (regions.isEmpty()) return@withContext MangaTranslationResult(null, 0)
                        val translations = if (request.config.engine == MangaTranslationEngine.AI) {
                            stage(MangaTranslationStage.TRANSLATING)
                            aiTranslator.translate(regions.map { it.text(request.sourceLanguage) },
                                request.sourceLanguage, request.targetLanguage, request.config)
                        } else {
                            stage(MangaTranslationStage.DOWNLOADING)
                            val translator = Translation.getClient(TranslatorOptions.Builder()
                                .setSourceLanguage(request.sourceLanguage).setTargetLanguage(request.targetLanguage).build())
                            try {
                                translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()).awaitDownload()
                                stage(MangaTranslationStage.TRANSLATING)
                                regions.map { region ->
                                    currentCoroutineContext().ensureActive()
                                    translator.translate(region.text(request.sourceLanguage)).finishSafely()
                                }
                            } finally { translator.close() }
                        }
                        stage(MangaTranslationStage.RENDERING)
                        val renderRegions = regions.zip(translations).map { (region, text) ->
                            Triple(region, text, patchBounds(region, width, height))
                        }
                        val temporary = File.createTempFile("render-", ".tmp", directory)
                        try {
                            val context = currentCoroutineContext()
                            limitedOutput(temporary.outputStream(), available).use { stream ->
                                MangaPngStripWriter(stream, width, height).use { png ->
                                    val row = IntArray(width)
                                    for (top in 0 until height step 512) {
                                        context.ensureActive()
                                        val bottom = minOf(top + 512, height)
                                        val raw = decode(decoder, Rect(0, top, width, bottom), sample)
                                        val strip = try { checkNotNull(raw.copy(Bitmap.Config.ARGB_8888, true)) } finally { raw.recycle() }
                                        try {
                                            val canvas = Canvas(strip)
                                            renderRegions.forEach { (region, translation, bounds) ->
                                                if (bounds.top >= bottom || bounds.bottom <= top) return@forEach
                                                context.ensureActive()
                                                val patch = renderPatch(decoder, region, translation, bounds, sample) { context.ensureActive() }
                                                try { canvas.drawBitmap(patch, bounds.left.toFloat(), (bounds.top - top).toFloat(), null) }
                                                finally { patch.recycle() }
                                            }
                                            repeat(strip.height) { y ->
                                                context.ensureActive()
                                                strip.getPixels(row, 0, width, 0, y, width, 1)
                                                png.writeRow(row)
                                            }
                                        } finally { strip.recycle() }
                                    }
                                }
                            }
                            currentCoroutineContext().ensureActive()
                            check(temporary.renameTo(output))
                        } finally { temporary.delete() }
                        MangaTranslationResult(output.absolutePath, regions.size)
                    } finally { decoder.close() }
                }
            } finally { owner.close() }
        }
    }

    private suspend fun decode(decoder: MangaRegionDecoder<Bitmap>, bounds: Rect, sample: Int): Bitmap {
        // Acquire ownership before observing cancellation at a suspend return boundary.
        val bitmap = withContext(NonCancellable) {
            decoder.decodeRegion(MangaImageRegion(bounds.left * sample, bounds.top * sample,
                minOf(bounds.right.toLong() * sample, decoder.imageSize.width.toLong()).toInt(),
                minOf(bounds.bottom.toLong() * sample, decoder.imageSize.height.toLong()).toInt()), sample)
        }
        try {
            currentCoroutineContext().ensureActive()
            if (bitmap.width == bounds.width() && bitmap.height == bounds.height()) return bitmap
            return Bitmap.createScaledBitmap(bitmap, bounds.width(), bounds.height(), true).also { if (it !== bitmap) bitmap.recycle() }
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }

    private fun patchBounds(region: MangaOcrRegion, width: Int, height: Int) = region.bounds.apply {
        inset(-8, -8)
        intersect(0, 0, width, height)
    }

    private suspend fun renderPatch(
        decoder: MangaRegionDecoder<Bitmap>, region: MangaOcrRegion, translation: String,
        bounds: Rect, sample: Int, checkCancellation: () -> Unit,
    ): Bitmap {
        check(bounds.height() <= 4096) { application.getString(R.string.manga_translation_layout_failed) }
        val original = decode(decoder, bounds, sample)
        val pixels = IntArray(original.width * original.height)
        try { original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height) }
        finally { original.recycle() }
        val polygons = region.lines.flatMap { it.glyphs }.map { glyph -> glyph.map { Point(it.x - bounds.left, it.y - bounds.top) } }
        val repair = MangaTextInpainter.repair(pixels, bounds.width(), bounds.height(), polygons, checkCancellation)
        check(repair.mask.any { it } && repair.unrepairedGlyphs == 0) { application.getString(R.string.manga_translation_repair_failed) }
        pixels.indices.forEach { if (!repair.mask[it]) pixels[it] = Color.TRANSPARENT }
        val patch = Bitmap.createBitmap(pixels, bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888).let {
            try { checkNotNull(it.copy(Bitmap.Config.ARGB_8888, true)) } finally { it.recycle() }
        }
        try {
            val textBounds = region.bounds.apply { offset(-bounds.left, -bounds.top) }
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            val bg = repair.background
            paint.color = if ((bg shr 16 and 255) * .299 + (bg shr 8 and 255) * .587 + (bg and 255) * .114 > 128) Color.BLACK else Color.WHITE
            var size = (textBounds.height() / 2f).coerceIn(10f, 48f)
            fun layout(): StaticLayout {
                paint.textSize = size
                return StaticLayout.Builder.obtain(translation, 0, translation.length, paint, textBounds.width().coerceAtLeast(1))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
            }
            var layout = layout()
            while (layout.height > textBounds.height() && size > 8) { size--; layout = layout() }
            check(layout.height <= textBounds.height()) { application.getString(R.string.manga_translation_layout_failed) }
            Canvas(patch).apply {
                translate(textBounds.left.toFloat(), textBounds.top + (textBounds.height() - layout.height) / 2f)
                layout.draw(this)
            }
            return patch
        } catch (error: Throwable) { patch.recycle(); throw error }
    }

    private fun limitedOutput(output: OutputStream, limit: Long): OutputStream = object : FilterOutputStream(output) {
        private var written = 0L
        override fun write(value: Int) { checkSize(1); out.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { checkSize(length); out.write(bytes, offset, length) }
        private fun checkSize(count: Int) {
            check(written + count <= limit) { application.getString(R.string.manga_translation_cache_full) }
            written += count
        }
    }
}

// ML Kit Tasks cannot interrupt native inference. Finish before releasing its bitmap/client;
// callers check cancellation between stages and the mutex prevents overlapping heavy work.
private suspend fun <T> Task<T>.finishSafely(): T = withContext(NonCancellable) {
    suspendCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.resumeWithException(kotlinx.coroutines.CancellationException()) }
    }
}

private suspend fun <T> Task<T>.awaitDownload(): T = withTimeout(120_000) {
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
}
