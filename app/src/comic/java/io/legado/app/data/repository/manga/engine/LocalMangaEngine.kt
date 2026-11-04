package io.legado.app.data.repository.manga.engine

import android.content.Context
import android.graphics.Bitmap
import io.legado.app.R
import io.legado.app.data.repository.manga.translation.MangaBubbleDetection
import io.legado.app.data.repository.manga.translation.MangaBubbleDetector
import io.legado.app.data.repository.manga.translation.MangaLocalInpainter
import io.legado.app.data.repository.manga.translation.MangaLocalTextDetector
import io.legado.app.data.repository.manga.translation.MangaPaddleModelCatalog
import io.legado.app.data.repository.manga.translation.MangaPaddleOcr
import io.legado.app.data.repository.manga.translation.MangaVisionModelRepository
import io.legado.app.data.repository.manga.translation.withMangaOcrInput
import io.legado.app.domain.model.manga.MangaInpaintEngine
import io.legado.app.domain.model.manga.MangaLocalOcrEngine
import io.legado.app.domain.model.manga.MangaOcrOptions
import io.legado.app.domain.model.manga.MangaTranslatedRegion
import kotlinx.coroutines.CancellationException

/**
 * The shipped engine: ML Kit recognizers, Paddle OCR and the ONNX runtimes behind them.
 *
 * This class is the only place that names those libraries, so products without them only have to
 * swap the [MangaLocalEngine] binding for [UnavailableMangaLocalEngine].
 */
internal class LocalMangaEngine(
    private val context: Context,
    private val paddle: MangaPaddleOcr,
    private val inpainter: MangaLocalInpainter,
    private val visionModels: MangaVisionModelRepository,
) : MangaLocalEngine {

    override suspend fun detectBubbles(bitmap: Bitmap): List<MangaBubbleDetection> {
        // 权重缺失是能力缺失（排版可降级），必须在 try 之外冒泡，不能被下面的兜底转成运行时故障。
        val model = visionModels.requireModel()
        return try {
            MangaBubbleDetector.detect(model, bitmap)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: LinkageError) {
            throw MangaLocalEngineFailureException(
                context.getString(R.string.manga_translation_detector_unavailable), failure)
        } catch (failure: Exception) {
            throw MangaLocalEngineFailureException(
                context.getString(R.string.manga_translation_detector_unavailable), failure)
        }
    }

    override suspend fun recognize(
        bitmap: Bitmap,
        language: String,
        engine: MangaLocalOcrEngine,
        options: MangaOcrOptions,
    ): List<MangaTranslatedRegion> = when (engine) {
        MangaLocalOcrEngine.ML_KIT -> withMangaOcrInput(bitmap, options) {
            MangaLocalTextDetector.detect(it, language)
        }
        else -> try {
            require(MangaPaddleModelCatalog.supports(engine, language)) {
                context.getString(R.string.manga_ocr_language_unsupported)
            }
            paddle.detect(bitmap, language, engine, options)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: LinkageError) {
            throw MangaLocalEngineFailureException(
                context.getString(R.string.manga_ocr_runtime_unavailable), failure)
        }
    }

    override suspend fun requireInpaint(engine: MangaInpaintEngine) = inpainter.requireReady(engine)

    override suspend fun erase(bitmap: Bitmap, mask: BooleanArray, engine: MangaInpaintEngine) =
        inpainter.erase(bitmap, mask, engine)

    override suspend fun locate(bitmap: Bitmap, language: String): List<MangaTranslatedRegion>? {
        // Auxiliary partitioning only protects text blocks before any paid request. Unsupported
        // languages and unavailable runtimes keep the gutter fallback; actual LOCAL recognition
        // failures still propagate from recognize().
        if (language !in MangaLocalOcrEngine.ML_KIT.sourceLanguages) return null
        return try {
            MangaLocalTextDetector.detect(bitmap, language)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
