package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import io.legado.app.domain.model.manga.MangaTextBox
import io.legado.app.domain.model.manga.MangaTranslationPlan

/** AI rectangles restrict layout; they never enlarge a locally verified interior or erasure. */
internal fun constrainMangaAiSafety(bitmap: Bitmap, plan: MangaTranslationPlan): MangaTranslationPlan =
    plan.copy(regions = plan.regions.map { region ->
        val safe = region.aiGeometry?.safeBox ?: return@map region
        val x = minOf(1000f / bitmap.width, (safe.right - safe.left) / 10)
        val y = minOf(1000f / bitmap.height, (safe.bottom - safe.top) / 10)
        val area = region.layoutBox
        val l = maxOf(area.left, safe.left + x); val r = minOf(area.right, safe.right - x)
        val t = maxOf(area.top, safe.top + y); val b = minOf(area.bottom, safe.bottom - y)
        require(r > l && b > t) { "AI safe area conflicts with the local text area; recognize again" }
        region.copy(layoutBox = MangaTextBox(l, t, r, b))
    })
