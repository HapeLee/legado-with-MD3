package io.legado.app.data.repository.manga

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
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
import io.legado.app.domain.gateway.MangaRegionTranslation
import io.legado.app.domain.gateway.MangaImageModel
import io.legado.app.domain.gateway.MangaRegionFailure
import io.legado.app.domain.gateway.MangaRecognitionException
import io.legado.app.domain.gateway.MangaRecognitionStep
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.DataOutputStream
import java.io.DataInputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Bounded region decoding, overlapping OCR, masked inpainting and streaming PNG output. */
class MangaTranslationRepository(
    private val application: Application,
    private val imageLoader: ImageLoader,
    private val aiTranslator: TranslateMangaTextUseCase,
    private val imageModels: MangaImageModelRepository = MangaImageModelRepository(application),
) : MangaTranslationGateway {
    private val inference = MangaImageInference(imageModels)
    private val mutex = Mutex()
    private val cacheLimit = 128L * 1024 * 1024
    private fun directory() = File(application.cacheDir, "manga-translation-v3").apply { mkdirs() }

    override fun observeAiConfiguration() = aiTranslator.configurations()

    override suspend fun clearCache() = mutex.withLock {
        withContext(Dispatchers.IO) {
            listOf("manga-translation-v1", "manga-translation-v2", "manga-translation-v3", "manga-translation-stages-v1").forEach { name ->
                File(application.cacheDir, name).listFiles()?.forEach {
                    if (it.isDirectory && (it.name == ".dimensions" ||
                        it.name.startsWith("render-") && it.name.endsWith("-patches"))) it.deleteRecursively() else it.delete()
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
            require(request.fontScale.isFinite() && request.fontScale in .5f..2f)
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
                        val stageCache = MangaStageCache(File(application.cacheDir, "manga-translation-stages-v1"))
                        val sourceKey = MangaStageCache.key(source.contentDigest().joinToString("") { "%02x".format(it) },
                            "region-decode-1", "$sample:$width:$height")
                        val modelIdentity = imageModels.identity()
                        val ctdIdentity = imageModels.identity(MangaImageModel.CTD)
                        val bubbleIdentity = imageModels.identity(MangaImageModel.BUBBLE)
                        val aotIdentity = imageModels.identity(MangaImageModel.AOT)
                        val modelGeneration = imageModels.generation
                        val modelContext = currentCoroutineContext()
                        fun checkModels() {
                            modelContext.ensureActive()
                            if (modelGeneration != imageModels.generation || modelIdentity != imageModels.identity())
                                throw kotlinx.coroutines.CancellationException("Image models changed")
                        }
                        val stageKeys = MangaStageKeys(sourceKey, ctdIdentity, bubbleIdentity, aotIdentity, request)
                        val key = stageKeys.rendered(modelIdentity)
                        val directory = directory()
                        val output = File(directory, "$key.png")
                        val metadata = File(directory, "$key.json")
                        if (output.isFile && output.length() > 0 && metadata.isFile) {
                            val cached = runCatching { Json.decodeFromString<MangaTranslationResult>(metadata.readText()) }.getOrNull()
                            if (cached != null && cached.pendingCount == 0 && modelGeneration == imageModels.generation && modelIdentity == imageModels.identity()) return@withContext cached.copy(imagePath = output.absolutePath)
                        }
                        val available = cacheLimit - directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        check(available > 0) { application.getString(R.string.manga_translation_cache_full) }
                        stage(MangaTranslationStage.RECOGNIZING)
                        val ocrKey = stageKeys.ocr
                        val bubbleKey = stageKeys.bubbles
                        val savedOcr = stageCache.json<List<MangaSavedRegion>>("ocr", ocrKey)
                        val savedBubbles = stageCache.json<List<MangaSavedBubble>>("bubbles", bubbleKey)
                        val candidates = savedOcr?.map { it.region() }?.toMutableList() ?: mutableListOf()
                        val bubbles = savedBubbles?.map { it.bubble() }?.toMutableList() ?: mutableListOf()
                        val recognizer = if (savedOcr != null) null else mangaRecognitionStep(MangaRecognitionStep.OCR_INITIALIZATION) {
                            if (request.sourceLanguage == "ko") TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                            else TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                        }
                        try {
                            if (savedOcr == null || savedBubbles == null) for (strip in mangaOcrStrips(height)) {
                                currentCoroutineContext().ensureActive()
                                val tile = decode(decoder, Rect(0, strip.top, width, strip.bottom), sample)
                                try {
                                    val context = currentCoroutineContext()
                                    if (savedBubbles == null && bubbles.size < 256) bubbles += mangaRecognitionStep(MangaRecognitionStep.BUBBLE) {
                                        inference.bubbles(tile, strip.top, ::checkModels).take(256 - bubbles.size)
                                    }
                                    if (recognizer != null) {
                                        val detections = mangaRecognitionStep(MangaRecognitionStep.CTD) { inference.textLines(tile, ::checkModels) }
                                            ?: listOf(Rect(0, 0, tile.width, tile.height))
                                        for (detection in detections) {
                                            context.ensureActive()
                                            val input = mangaOcrInput(tile, detection)
                                            val text = input.use { mangaRecognitionStep(MangaRecognitionStep.OCR) {
                                                recognizer.process(InputImage.fromBitmap(it.bitmap, 0)).finishSafely()
                                            } }
                                            text.textBlocks.forEach { block ->
                                                val lines = block.lines.mapNotNull { line ->
                                                    val bounds = line.boundingBox?.let(::Rect) ?: return@mapNotNull null
                                                    if (bounds.isEmpty || line.text.isBlank()) return@mapNotNull null
                                                    bounds.offset(input.bounds.left, strip.top + input.bounds.top)
                                                    val glyphs = line.elements.mapNotNull { element ->
                                                        val points = element.cornerPoints ?: return@mapNotNull null
                                                        points.map { Point(it.x + input.bounds.left, it.y + strip.top + input.bounds.top) }
                                                    }.ifEmpty {
                                                        listOf(listOf(Point(bounds.left, bounds.top), Point(bounds.right, bounds.top),
                                                            Point(bounds.right, bounds.bottom), Point(bounds.left, bounds.bottom)))
                                                    }
                                                    MangaOcrLine(line.text, bounds, glyphs)
                                                }
                                                if (lines.isNotEmpty()) candidates += MangaOcrRegion(lines)
                                            }
                                            check(candidates.size <= 10_000) { application.getString(R.string.manga_translation_decode_failed) }
                                        }
                                    }
                                } finally { tile.recycle() }
                            }
                        } finally { recognizer?.close() }
                        currentCoroutineContext().ensureActive()
                        checkModels()
                        if (savedOcr == null) stageCache.writeJson("ocr", ocrKey, candidates.map { it.saved() })
                        checkModels()
                        if (savedBubbles == null) stageCache.writeJson("bubbles", bubbleKey, bubbles.map { it.saved() })
                        val mergeContext = currentCoroutineContext()
                        val merged = mergeMangaOcrRegions(candidates) { mergeContext.ensureActive() }
                        // Associate by complete line coverage, never merely the closest bubble.
                        val associations = merged.associateWith { region ->
                            bubbles.filter { bubble -> region.lines.all { line ->
                                val b = line.bounds
                                bubble.contains(b.centerX(), b.centerY()) && bubble.contains(b.left, b.centerY()) &&
                                    bubble.contains(b.right - 1, b.centerY())
                            } }.minByOrNull { it.bounds.width().toLong() * it.bounds.height() }
                        }
                        val regions = merged.groupBy { associations[it] ?: it }.values.map { group ->
                            MangaOcrRegion(group.flatMap { it.lines })
                        }
                        val layoutBubbles = regions.associateWith { region ->
                            bubbles.filter { b -> region.lines.all { b.contains(it.bounds.centerX(), it.bounds.centerY()) } }
                                .minByOrNull { it.bounds.width().toLong() * it.bounds.height() }
                        }
                        if (regions.isEmpty()) return@withContext MangaTranslationResult(null, 0)
                        val originalTexts = regions.map { it.text(request.sourceLanguage) }
                        val regionKeys = regions.map { stageKeys.region(it) }
                        val translationKey = stageKeys.translation(originalTexts)
                        val translations = stageCache.json<List<String>>("translation", translationKey)?.takeIf {
                            it.size == regions.size && it.none(String::isBlank)
                        } ?: run {
                            val translated = if (request.config.engine == MangaTranslationEngine.AI) {
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
                            require(translated.size == regions.size && translated.none(String::isBlank))
                            currentCoroutineContext().ensureActive()
                            stageCache.writeJson("translation", translationKey, translated)
                            translated
                        }
                        stage(MangaTranslationStage.RENDERING)
                        val renderRegions = regions.mapIndexed { index, region ->
                            val text = request.translationOverrides[regionKeys[index]] ?: translations[index]
                            Triple(region, text, patchBounds(region, width, height).apply { layoutBubbles[region]?.bounds?.let { union(it) } })
                        }
                        val temporary = File.createTempFile("render-", ".tmp", directory)
                        val patches = File(directory, temporary.name + "-patches").apply { mkdirs() }
                        val diagnostics = mutableMapOf<Int, MangaRegionTranslation>()
                        val prepared = mutableSetOf<Int>()
                        var patchBytes = 0L
                        try {
                            val context = currentCoroutineContext()
                            limitedOutput(temporary.outputStream(), available) { patchBytes }.use { stream ->
                                MangaPngStripWriter(stream, width, height).use { png ->
                                    val row = IntArray(width)
                                    for (top in 0 until height step 512) {
                                        context.ensureActive()
                                        val bottom = minOf(top + 512, height)
                                        val raw = decode(decoder, Rect(0, top, width, bottom), sample)
                                        val strip = try { checkNotNull(raw.copy(Bitmap.Config.ARGB_8888, true)) } finally { raw.recycle() }
                                        try {
                                            val canvas = Canvas(strip)
                                            renderRegions.forEachIndexed { index, (region, translation, bounds) ->
                                                if (bounds.top >= bottom || bounds.bottom <= top) return@forEachIndexed
                                                context.ensureActive()
                                                val file = File(patches, "$index.png")
                                                if (prepared.add(index)) {
                                                    var failure: MangaRegionFailure? = null
                                                    val patch = try {
                                                        renderPatch(decoder, region, translation, bounds, sample, width, height, layoutBubbles[region],
                                                            request, stageCache, stageKeys, ::checkModels)
                                                    } catch (error: RegionRenderException) {
                                                        failure = error.reason
                                                        null
                                                    } catch (error: ai.onnxruntime.OrtException) {
                                                        failure = MangaRegionFailure.MODEL
                                                        null
                                                    }
                                                    diagnostics[index] = MangaRegionTranslation(index, region.text(request.sourceLanguage),
                                                        translation, bounds.left, bounds.top, bounds.right, bounds.bottom, failure, regionKeys[index])
                                                    if (patch != null) try {
                                                        limitedOutput(file.outputStream(), available - patchBytes - temporary.length()).use {
                                                            check(patch.compress(Bitmap.CompressFormat.PNG, 100, it))
                                                        }
                                                        patchBytes += file.length()
                                                    } finally { patch.recycle() }
                                                }
                                                if (!file.isFile) return@forEachIndexed
                                                val patch = checkNotNull(BitmapFactory.decodeFile(file.absolutePath))
                                                try { canvas.drawBitmap(patch, bounds.left.toFloat(), (bounds.top - top).toFloat(), null) }
                                                finally { patch.recycle() }
                                                if (bounds.bottom <= bottom) {
                                                    val bytes = file.length()
                                                    if (file.delete()) patchBytes -= bytes
                                                }
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
                            if (modelGeneration != imageModels.generation || modelIdentity != imageModels.identity()) throw kotlinx.coroutines.CancellationException("Image models changed")
                            val result = MangaTranslationResult(output.absolutePath, regions.size, diagnostics.toSortedMap().values.toList())
                            val json = Json.encodeToString(result)
                            check(temporary.length() + json.toByteArray().size <= available) { application.getString(R.string.manga_translation_cache_full) }
                            val temporaryMetadata = File(patches, "result.json")
                            temporaryMetadata.writeText(json)
                            Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                            try {
                                Files.move(temporaryMetadata.toPath(), metadata.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                            } catch (error: Throwable) { output.delete(); throw error }
                            result
                        } finally { temporary.delete(); patches.deleteRecursively() }
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
        val padding = if (imageModels.states.value[io.legado.app.domain.gateway.MangaImageModel.AOT]?.status == io.legado.app.domain.gateway.MangaModelStatus.READY) 64 else 8
        inset(-padding, -padding)
        intersect(0, 0, width, height)
    }

    private suspend fun renderPatch(
        decoder: MangaRegionDecoder<Bitmap>, region: MangaOcrRegion, translation: String,
        bounds: Rect, sample: Int, pageWidth: Int, pageHeight: Int, bubble: MangaBubble?,
        request: MangaTranslationRequest, cache: MangaStageCache, keys: MangaStageKeys, checkCancellation: () -> Unit,
    ): Bitmap {
        if (bounds.height() > 4096 || bounds.width() > 4096) throw RegionRenderException(MangaRegionFailure.TOO_LARGE)
        val area = if (bubble != null) MangaLayoutArea(Rect(bubble.bounds), maxOf(3, minOf(bubble.bounds.width(), bubble.bounds.height()) / 20), bubble::contains)
            else MangaLayoutArea(region.bounds, 1)
        val geometry = bubble?.let { Json.encodeToString(it.saved()) } ?: Json.encodeToString(region.bounds.saved())
        val sourceSize = region.lines.map { minOf(it.bounds.width(), it.bounds.height()).toFloat() }.sorted().let { it[it.size / 2] }
        val probe = MangaTextLayout.paint()
        val probeBounds = Rect().also { probe.getTextBounds(translation, 0, translation.length, it) }
        val fontIdentity = "sans-serif-normal:${android.os.Build.FINGERPRINT}:${android.os.Build.VERSION.SDK_INT}:${android.os.LocaleList.getDefault().toLanguageTags()}:${probe.measureText(translation)}:$probeBounds:${probe.fontMetricsInt}"
        val layoutKey = keys.layout(translation, geometry, sourceSize, pageWidth, fontIdentity)
        val savedLayout = cache.json<MangaLayoutPlan>("layout", layoutKey)
        val layout = savedLayout ?: (
            MangaTextLayout.plan(translation, area, pageWidth, sourceSize, request.targetLanguage,
                request.layoutDirection, request.fontScale, checkCancellation) ?: throw RegionRenderException(MangaRegionFailure.LAYOUT)
            )
        val repairBounds = patchBounds(region, pageWidth, pageHeight)
        val repairKey = keys.repair(region, repairBounds)
        val cachedRepair = cache.read("repair", repairKey)?.let { bytes ->
            runCatching {
                DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                    val background = input.readInt()
                    val png = input.readBytes()
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(png, 0, png.size, options)
                    require(options.outWidth == repairBounds.width() && options.outHeight == repairBounds.height())
                    checkNotNull(BitmapFactory.decodeByteArray(png, 0, png.size)) to background
                }
            }.getOrNull()
        }
        val (repairLayer, background) = cachedRepair ?: buildRepair(decoder, region, repairBounds, sample, cache,
            keys, checkCancellation).also { (bitmap, bg) ->
            try {
                val bytes = ByteArrayOutputStream()
                DataOutputStream(bytes).use { output ->
                    output.writeInt(bg)
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                checkCancellation()
                cache.write("repair", repairKey, bytes.toByteArray())
            } catch (error: Throwable) { bitmap.recycle(); throw error }
        }
        try {
            val color = if ((background shr 16 and 255) * .299 + (background shr 8 and 255) * .587 + (background and 255) * .114 > 128) Color.BLACK else Color.WHITE
            val textLayer = MangaTextLayout.render(layout, area, bounds, color, checkCancellation)
                ?: throw RegionRenderException(MangaRegionFailure.LAYOUT)
            try {
                checkCancellation()
                if (savedLayout == null) cache.writeJson("layout", layoutKey, layout)
                val patch = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
                try {
                    Canvas(patch).apply {
                        drawBitmap(repairLayer, (repairBounds.left - bounds.left).toFloat(), (repairBounds.top - bounds.top).toFloat(), null)
                        drawBitmap(textLayer, 0f, 0f, null)
                    }
                    return patch
                } catch (error: Throwable) { patch.recycle(); throw error }
            } finally { textLayer.recycle() }
        } finally { repairLayer.recycle() }
    }

    private suspend fun buildRepair(
        decoder: MangaRegionDecoder<Bitmap>, region: MangaOcrRegion, bounds: Rect, sample: Int,
        cache: MangaStageCache, keys: MangaStageKeys, checkCancellation: () -> Unit,
    ): Pair<Bitmap, Int> {
        val original = decode(decoder, bounds, sample)
        val pixels = IntArray(original.width * original.height)
        val textMask: BooleanArray?
        try {
            val maskKey = keys.mask(bounds)
            val cachedMask = cache.read("mask", maskKey)
            textMask = if (cachedMask != null) {
                val bits = java.util.BitSet.valueOf(cachedMask)
                BooleanArray(pixels.size) { bits[it] }
            } else inference.textMask(original, checkCancellation)?.also { mask ->
                val bits = java.util.BitSet(mask.size)
                mask.indices.forEach { if (mask[it]) bits.set(it) }
                checkCancellation()
                cache.write("mask", maskKey, bits.toByteArray())
            }
            original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
        } finally { original.recycle() }
        val polygons = region.lines.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }, { it.bounds.right }, { it.bounds.bottom }))
            .flatMap { it.glyphs }.map { glyph -> glyph.map { Point(it.x - bounds.left, it.y - bounds.top) } }
        val sourcePixels = if (imageModels.states.value[MangaImageModel.AOT]?.status == io.legado.app.domain.gateway.MangaModelStatus.READY) pixels.copyOf() else null
        val repair = MangaTextInpainter.repair(pixels, bounds.width(), bounds.height(), polygons, checkCancellation, textMask)
        if (!repair.mask.any { it } || repair.unrepairedGlyphs != 0) throw RegionRenderException(MangaRegionFailure.REPAIR)
        if (sourcePixels != null) {
            val bg = repair.background
            val textured = sourcePixels.indices.any { i -> !repair.mask[i] &&
                kotlin.math.abs((sourcePixels[i] shr 16 and 255) - (bg shr 16 and 255)) > 36 }
            if (textured) inference.repair(sourcePixels, repair.mask, bounds.width(), bounds.height(), checkCancellation)?.copyInto(pixels)
        }
        pixels.indices.forEach { if (!repair.mask[it]) pixels[it] = Color.TRANSPARENT }
        return Bitmap.createBitmap(pixels, bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888) to repair.background
    }

    private fun limitedOutput(output: OutputStream, limit: Long, reserved: () -> Long = { 0 }): OutputStream = object : FilterOutputStream(output) {
        private var written = 0L
        override fun write(value: Int) { checkSize(1); out.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { checkSize(length); out.write(bytes, offset, length) }
        private fun checkSize(count: Int) {
            check(written + count + reserved() <= limit) { application.getString(R.string.manga_translation_cache_full) }
            written += count
        }
    }
}

private class RegionRenderException(val reason: MangaRegionFailure) : Exception()

internal suspend fun <T> mangaRecognitionStep(step: MangaRecognitionStep, block: suspend () -> T): T = try {
    block()
} catch (error: kotlinx.coroutines.CancellationException) {
    throw error
} catch (error: Exception) {
    val code = when (error) {
        is com.google.mlkit.common.MlKitException -> "MLKIT-${error.errorCode}"
        is ai.onnxruntime.OrtException -> "ONNX-${error.code}"
        else -> error.javaClass.simpleName
    }
    // Do not expose SDK messages: they may contain image paths, URLs or recognized text.
    val location = error.stackTrace.firstOrNull { it.className.startsWith("io.legado.app.data.repository.manga.") }
        ?.let { "@${it.fileName}:${it.lineNumber}" }.orEmpty()
    throw MangaRecognitionException(step, code + location)
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
