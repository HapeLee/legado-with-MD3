package io.legado.app.data.repository.manga

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.os.Build
import android.os.Process
import io.legado.app.R
import io.legado.app.data.AppDatabase
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.MangaTranslationPage
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.model.settings.MangaTranslationBackend
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiProtocol
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.joye.yakuyomi.engine.Detector
import li.joye.yakuyomi.engine.EngineConfig
import li.joye.yakuyomi.engine.Inpainter
import li.joye.yakuyomi.engine.InpainterConfig
import li.joye.yakuyomi.engine.ModelDownloader
import li.joye.yakuyomi.engine.ModelSet
import li.joye.yakuyomi.engine.Ocr
import li.joye.yakuyomi.engine.PageResult
import li.joye.yakuyomi.engine.Pipeline
import li.joye.yakuyomi.engine.Translator
import li.joye.yakuyomi.engine.RenderConfig
import li.joye.yakuyomi.engine.TextOrientation
import java.io.File
import java.io.Closeable

internal interface MangaPageProcessor : Closeable {
    suspend fun translate(record: MangaTranslationStore.Record, page: MangaTranslationPage,
                          onProgress: (String) -> Unit): String?
    suspend fun cached(record: MangaTranslationStore.Record, page: MangaTranslationPage): String?
}

internal class MangaTranslationPreparationException(message: String?, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/** Serial native inference; source files are leased and never overwritten. */
internal class MangaPageTranslator(
    private val application: Application,
    private val database: AppDatabase,
    private val profiles: AiProfileGateway,
    private val ai: AiTextGateway,
    private val settings: TranslationSettingsGateway,
    private val store: MangaTranslationStore,
    private val mangaSettings: MangaSettingsGateway,
) : MangaPageProcessor {
    private val modelFiles = MangaModelFiles(application)
    private val inference = Mutex()
    private var pipeline: Pipeline? = null
    private var pipelineConfiguration: String? = null
    private var translator: Translator? = null

    override fun close() {
        val previous = pipeline
        pipeline = null
        pipelineConfiguration = null
        val previousTranslator = translator
        translator = null
        try { previous?.close() } finally { (previousTranslator as? Closeable)?.close() }
    }

    override suspend fun translate(
        record: MangaTranslationStore.Record, page: MangaTranslationPage,
        onProgress: (String) -> Unit,
    ): String? = process(record, page, false, onProgress)

    override suspend fun cached(record: MangaTranslationStore.Record, page: MangaTranslationPage): String? =
        process(record, page, true) {}

    private suspend fun process(record: MangaTranslationStore.Record, page: MangaTranslationPage,
                                cacheOnly: Boolean, onProgress: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        inference.withLock {
            modelFiles.lock.withLock models@{
            if (!Process.is64Bit() || !Build.SUPPORTED_ABIS.contains("arm64-v8a"))
                throw MangaTranslationPreparationException(application.getString(R.string.feature_manga_translation_arm64))
            val backend = mangaSettings.currentSettings.translationBackend
            val preset = if (backend == MangaTranslationBackend.AI) profiles.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
                ?: throw MangaTranslationPreparationException(application.getString(R.string.feature_manga_translation_configure_ai)) else null
            if (preset?.model?.provider?.protocol == AiProtocol.GOOGLE_TRANSLATE)
                throw MangaTranslationPreparationException(application.getString(R.string.feature_manga_translation_configure_ai))
            val language = settings.currentSettings.targetLanguage
            if (backend == MangaTranslationBackend.LOCAL_SMALL100) {
                try { Small100Protocol.languageId(language) }
                catch (error: IllegalArgumentException) { throw MangaTranslationPreparationException(error.localizedMessage, error) }
            }
            val book = requireNotNull(database.bookDao.getBook(record.bookUrl))
            val imageUrl = page.imageUrl
            val chapterSubject = page.chapterSubject
            val source = database.bookSourceDao.getBookSource(book.origin)
            onProgress(application.getString(R.string.feature_manga_translation_loading))
            val (original, lease) = if (imageUrl.startsWith("file:") || imageUrl.startsWith("content:")) {
                // Snapshot archive/SAF pages: leaving the reader can dispose the extraction directory.
                val snapshot = File.createTempFile("manga-translation-", ".image", application.cacheDir)
                try {
                    val input = if (imageUrl.startsWith("file:")) File(java.net.URI(imageUrl)).inputStream()
                        else requireNotNull(application.contentResolver.openInputStream(android.net.Uri.parse(imageUrl)))
                    input.use { stream -> snapshot.outputStream().use { stream.copyTo(it) } }
                    snapshot to java.io.Closeable { snapshot.delete() }
                } catch (error: Throwable) { snapshot.delete(); throw error }
            } else BookHelp.acquireReadingImage(source, book, imageUrl, chapterSubject = chapterSubject)
            lease.use {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(original.path, bounds)
                require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..8192 &&
                    bounds.outWidth.toLong() * bounds.outHeight <= 12_000_000L) {
                    application.getString(R.string.feature_manga_translation_too_large)
                }
                // Exclude credentials, but include all generation parameters affecting the result.
                val model = preset?.model?.let { it.copy(provider = it.provider.copy(apiKey = "", headers = emptyMap(), customHeaders = emptyMap())) }
                val backendSignature = if (preset != null) "${LegadoMangaTranslator.PROMPT_VERSION}|" +
                    GSON.toJson(model) + GSON.toJson(preset.params) + "|${preset.runtimeOptions.maxInputChars}"
                else Small100Protocol.VERSION + modelFiles.catalog.filter { it.role == "translator" }.joinToString { it.sha256 }
                val signature = ModelDownloader.sha256(original) + "|8837370:boxfill:v3|$backend|$language|$backendSignature"
                val key = MangaTranslationStore.digest(signature)
                val cache = store.pages(record).apply { mkdirs() }
                val output = File(cache, "$key.png")
                if (output.isFile && output.length() > 0) return@models output.toURI().toString()
                if (File(cache, "$key.skip").isFile) return@models null
                val legacy = store.legacy(key)
                if (record.allowLegacy && legacy.isFile && legacy.length() > 0) {
                    val temporary = File(cache, "$key.adopt")
                    try {
                        legacy.copyTo(temporary, overwrite = true)
                        check(store.commit(record, temporary, output)) { "翻译缓存已清除" }
                        return@models output.toURI().toString()
                    } finally { temporary.delete() }
                }
                if (cacheOnly) return@models null
                val configuration = "$backend|$language|$backendSignature|${GSON.toJson(preset)}"
                if (pipelineConfiguration != configuration) {
                    try {
                        close()
                        ensureModels(backend)
                        val nextTranslator = if (preset != null) LegadoMangaTranslator(ai, preset, language)
                            else Small100MangaTranslator(modelDirectory, language)
                        try {
                            pipeline = createPipeline(nextTranslator, language)
                            translator = nextTranslator
                        } catch (error: Throwable) { (nextTranslator as? Closeable)?.close(); throw error }
                        pipelineConfiguration = configuration
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Throwable) { throw MangaTranslationPreparationException(error.localizedMessage, error) }
                }
                currentCoroutineContext().ensureActive()
                (translator as? LegadoMangaTranslator)?.textCache = MangaTranslationTextCache(store, record, page.key)
                onProgress(application.getString(R.string.feature_manga_translation_running))
                val bitmap = decodeOriginal(original)
                try {
                    requireNotNull(pipeline).let { engine ->
                        val result = engine.translatePage(bitmap)
                        try {
                            currentCoroutineContext().ensureActive()
                            val latestPreset = if (backend == MangaTranslationBackend.AI) profiles.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER) else null
                            check(settings.currentSettings.targetLanguage == language && latestPreset == preset &&
                                mangaSettings.currentSettings.translationBackend == backend) {
                                application.getString(R.string.feature_manga_translation_settings_changed)
                            }
                            when (result) {
                                is PageResult.Translated -> {
                                    val temporary = File(cache, "$key.part")
                                    try {
                                        temporary.outputStream().use { stream ->
                                            check(result.page.compress(Bitmap.CompressFormat.PNG, 100, stream))
                                            stream.fd.sync()
                                        }
                                        currentCoroutineContext().ensureActive()
                                        check(store.commit(record, temporary, output)) { "翻译缓存已清除" }
                                        output.toURI().toString()
                                    } finally { temporary.delete() }
                                }
                                is PageResult.Skipped -> {
                                    val temporary = File(cache, "$key.part")
                                    try {
                                        temporary.outputStream().use { it.write(1); it.fd.sync() }
                                        check(store.commit(record, temporary, File(cache, "$key.skip"))) { "翻译缓存已清除" }
                                    } finally { temporary.delete() }
                                    null
                                }
                                is PageResult.Failed -> error(result.reason)
                            }
                        } finally {
                            if (result is PageResult.Translated) {
                                result.page.takeUnless { it === bitmap }?.recycle()
                                result.analysis?.mask?.recycle()
                            }
                        }
                    }
                } finally { bitmap.recycle() }
            }
            }
        }
    }

    private val modelDirectory get() = modelFiles.directory

    private fun decodeOriginal(file: File): Bitmap {
        val decoded = requireNotNull(BitmapFactory.decodeFile(file.path,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }))
        try {
            val exif = ExifInterface(file)
            if (!exif.isFlipped && exif.rotationDegrees == 0) return decoded
            val matrix = Matrix().apply {
                if (exif.isFlipped) postScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
            val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (oriented !== decoded) decoded.recycle()
            return oriented
        } catch (error: java.io.IOException) {
            return decoded // PNG/WebP without EXIF keep their decoded orientation.
        } catch (error: Throwable) { decoded.recycle(); throw error }
    }

    private suspend fun ensureModels(backend: MangaTranslationBackend) {
        check(modelFiles.missing(backend).isEmpty()) {
            application.getString(R.string.feature_manga_translation_missing_models)
        }
    }

    private fun createPipeline(translator: Translator, language: String): Pipeline {
        val models = requireNotNull(ModelSet.resolve(modelDirectory.listFiles().orEmpty().map { it.name to it.path }))
        val alphabet = application.assets.open("models/alphabet-all-v5.txt").bufferedReader().use { it.readLines() }
        val cjk = listOf("zh", "ja", "ko").any { language.startsWith(it, ignoreCase = true) }
        val config = EngineConfig(inpainter = InpainterConfig(method = "boxfill"),
            render = RenderConfig(orientation = if (cjk) TextOrientation.AUTO else TextOrientation.HORIZONTAL))
        val detector = Detector(requireNotNull(models.detectorNcnn), config.detector)
        try {
            val ocr = Ocr(models.ocr, alphabet, config.ocr)
            try {
                val inpainter = Inpainter(requireNotNull(models.aotInpainterNcnn), config.inpainter)
                return Pipeline(detector, ocr, translator, inpainter, config)
            } catch (error: Throwable) { ocr.close(); throw error }
        } catch (error: Throwable) { detector.close(); throw error }
    }
}
