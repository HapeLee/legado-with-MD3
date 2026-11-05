package io.legado.app.data.repository.manga.engine

import android.graphics.Bitmap
import io.legado.app.data.repository.manga.translation.MangaBubbleDetection
import io.legado.app.domain.model.manga.MangaInpaintEngine
import io.legado.app.domain.model.manga.MangaLocalOcrEngine
import io.legado.app.domain.model.manga.MangaOcrOptions
import io.legado.app.domain.model.manga.MangaTranslatedRegion

/**
 * Local inference boundary for comic translation.
 *
 * Implementations own runtimes, weights and their leases; callers only exchange domain values.
 * Every entry point fails with [MangaLocalEngineUnavailableException] when the capability is
 * absent, because "this page has no lettering" and "this product has no engine" must never be
 * confused by translation, caching or automatic queues.
 *
 * [locate] is the single best-effort entry: long-strip partitioning may always fall back to
 * gutter detection, so a missing capability is reported as `null` instead of a failure.
 */
internal interface MangaLocalEngine {
    /**
     * 当前产物是否真的内置本地推理。上层据此在读取设置时把不可达的选项降级，
     * 而不是让用户停在一个必然失败的分支上。
     */
    val available: Boolean get() = true

    suspend fun detectBubbles(bitmap: Bitmap): List<MangaBubbleDetection>

    suspend fun recognize(
        bitmap: Bitmap,
        language: String,
        engine: MangaLocalOcrEngine,
        options: MangaOcrOptions,
    ): List<MangaTranslatedRegion>

    /** Fails before any paid request when the selected weights or runtime are absent. */
    suspend fun requireInpaint(engine: MangaInpaintEngine)

    suspend fun erase(bitmap: Bitmap, mask: BooleanArray, engine: MangaInpaintEngine)

    /** Auxiliary text hints for band partitioning; `null` means "partition from gutters only". */
    suspend fun locate(bitmap: Bitmap, language: String): List<MangaTranslatedRegion>?
}

/** Stable failure for an absent local capability; [message] is already user-presentable. */
internal class MangaLocalEngineUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * The runtime is present but failed (native load, corrupt weights, execution error).
 *
 * Callers must not confuse it with [MangaLocalEngineUnavailableException]: a missing capability can
 * degrade optional geometry hints, while a real inference failure keeps the pre-existing fail-fast
 * behaviour so broken detection never silently produces wrong typesetting.
 */
internal class MangaLocalEngineFailureException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Real "unavailable" product state: every inference entry refuses to run and no method pretends
 * that an empty result is a successful recognition.
 */
internal class UnavailableMangaLocalEngine(private val message: String) : MangaLocalEngine {
    override val available = false

    private fun unavailable(): Nothing = throw MangaLocalEngineUnavailableException(message)

    override suspend fun detectBubbles(bitmap: Bitmap): List<MangaBubbleDetection> = unavailable()

    override suspend fun recognize(
        bitmap: Bitmap,
        language: String,
        engine: MangaLocalOcrEngine,
        options: MangaOcrOptions,
    ): List<MangaTranslatedRegion> = unavailable()

    override suspend fun requireInpaint(engine: MangaInpaintEngine): Unit = unavailable()

    override suspend fun erase(bitmap: Bitmap, mask: BooleanArray, engine: MangaInpaintEngine): Unit = unavailable()

    /** Partitioning degrades to gutters, which keeps reading and cloud-only flows usable. */
    override suspend fun locate(bitmap: Bitmap, language: String): List<MangaTranslatedRegion>? = null
}
