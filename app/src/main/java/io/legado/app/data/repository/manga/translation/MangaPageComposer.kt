package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import io.legado.app.domain.model.manga.MangaTextBox
import io.legado.app.domain.model.manga.MangaTranslationPlan
import io.legado.app.domain.model.manga.MangaTranslatedRegion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun MangaTextBox.pixels(width: Int, height: Int): Rect = Rect(
    (left * width / 1000).toInt().coerceIn(0, width - 1),
    (top * height / 1000).toInt().coerceIn(0, height - 1),
    (right * width / 1000).roundToInt().coerceIn(1, width),
    (bottom * height / 1000).roundToInt().coerceIn(1, height),
)

/** Layout edges share one monotone projection: disjoint normalized areas stay disjoint.
 * Source/erasure boxes keep the conservative coverage policy in pixels().
 */
internal fun MangaTextBox.layoutPixels(width: Int, height: Int): Rect = Rect(
    (left * width / 1000).roundToInt().coerceIn(0, width),
    (top * height / 1000).roundToInt().coerceIn(0, height),
    (right * width / 1000).roundToInt().coerceIn(0, width),
    (bottom * height / 1000).roundToInt().coerceIn(0, height),
)

/** Local pixel operations. Ink cleanup stays near source lettering, independent of layout space. */
internal object MangaPageComposer {
    const val GEOMETRY_VERSION = 9

    fun prepare(bitmap: Bitmap, input: MangaTranslationPlan, containers: List<MangaBubbleDetection> = emptyList()): MangaTranslationPlan {
        val plan = input.copy(regions = input.regions.map { region ->
            if (!region.vertical || region.sourceColumnCount != null) region else
                region.copy(sourceColumnCount = mangaSourceColumnCount(region) ?: measureMangaSourceColumns(bitmap, region))
        })
        if (plan.regions.isNotEmpty() && plan.regions.all { it.typesetting != null })
            return separateLayouts(bitmap, plan).copy(localGeometryVersion = GEOMETRY_VERSION).validate()
        if (plan.localGeometryVersion >= GEOMETRY_VERSION) return plan
        // Re-estimate old baselines from the original pixels, including caches inflated in previews.
        val unmeasured = plan.copy(regions = plan.regions.map { it.copy(sourceFontSize = it.fontMeasurement?.glyphHeight, bubbleShape = null) })
        val refined = MangaContainerGeometry.attach(bitmap, refineMangaLettering(bitmap, recoverMangaBubbles(bitmap, unmeasured)), containers)
        return constrainMangaAiSafety(bitmap, planLayout(bitmap, anchor(bitmap, refined)))
            .copy(localGeometryVersion = GEOMETRY_VERSION).validate()
    }

    /** Persist a source-size estimate before erasing; never infer it from a repaired background. */
    fun anchor(bitmap: Bitmap, plan: MangaTranslationPlan): MangaTranslationPlan = plan.copy(regions = plan.regions.map {
        if (it.sourceFontSize != null) it else it.copy(sourceFontSize = (sourceSize(bitmap, it) * 1000 / bitmap.height).coerceIn(0.01f, 1000f))
    })

    /** Grow tight source anchors into nearby flat background; stop at artwork or other blocks. */
    fun planLayout(bitmap: Bitmap, plan: MangaTranslationPlan): MangaTranslationPlan {
        val stripPixels = IntArray(max(bitmap.width, bitmap.height))
        val reserved = plan.regions.map { region ->
            region.layoutBox.layoutPixels(bitmap.width, bitmap.height).apply { union(region.textBox.pixels(bitmap.width, bitmap.height)) }
        }.toMutableList()
        val positioned = plan.copy(regions = plan.regions.mapIndexed { index, region ->
            val masks = region.eraseBoxes.map { it.pixels(bitmap.width, bitmap.height) }
            val source = Rect(masks.first()).apply { masks.drop(1).forEach(::union) }
            val area = region.layoutBox.layoutPixels(bitmap.width, bitmap.height)
            val vertical = MangaTextTypesetter.vertical(region.translation, region.vertical)
            val font = region.sourceFontSize?.let { it * bitmap.height / 1000 } ?: sourceSize(bitmap, region)
            fun fits() = MangaOverlayTypesetter.fitsAtSize(region.translation, vertical, area.width(), area.height(), font)
            // A short translation does not need extra blank space. Keep relocated legacy anchors.
            if (region.bubbleShape != null || fits() || region.layoutBox != region.textBox && !area.contains(source)) return@mapIndexed region
            val bg = background(bitmap, source)
            val baseMargin = min(font * 0.8f, min(bitmap.width, bitmap.height) * 0.08f).roundToInt().coerceIn(2, 48)
            val margin = min(font * 3f, min(bitmap.width, bitmap.height) * 0.2f).roundToInt().coerceIn(baseMargin, 96)
            fun blank(strip: Rect): Boolean {
                val count = strip.width() * strip.height()
                bitmap.getPixels(stripPixels, 0, strip.width(), strip.left, strip.top, strip.width(), strip.height())
                for (i in 0 until count) if (mangaInkContrast(stripPixels[i], bg, 25)) return false
                return true
            }
            val stopped = BooleanArray(4)
            for (distance in 1..margin) {
                for (side in 0..3) {
                    if (stopped[side]) continue
                    val candidate = Rect(area)
                    val strip = when (side) {
                        0 -> Rect(area.left - 1, area.top, area.left, area.bottom).also { candidate.left-- }
                        1 -> Rect(area.left, area.top - 1, area.right, area.top).also { candidate.top-- }
                        2 -> Rect(area.right, area.top, area.right + 1, area.bottom).also { candidate.right++ }
                        else -> Rect(area.left, area.bottom, area.right, area.bottom + 1).also { candidate.bottom++ }
                    }
                    val withinGrowth = when (side) {
                        0 -> candidate.left >= source.left - margin
                        1 -> candidate.top >= source.top - margin
                        2 -> candidate.right <= source.right + margin
                        else -> candidate.bottom <= source.bottom + margin
                    }
                    if (!withinGrowth || candidate.left < 0 || candidate.top < 0 || candidate.right > bitmap.width || candidate.bottom > bitmap.height ||
                        reserved.indices.any { it != index && Rect.intersects(candidate, reserved[it]) } || !blank(strip)) {
                        stopped[side] = true
                    } else area.set(candidate)
                }
                if (stopped.all { it } || distance >= baseMargin && (distance - baseMargin) % 4 == 0 && fits()) break
            }
            reserved[index] = area
            val previous = region.layoutBox.layoutPixels(bitmap.width, bitmap.height)
            if (area == previous) region else region.copy(layoutBox = MangaTextBox(
                if (area.left == previous.left) region.layoutBox.left else (area.left + 0.01f) * 1000 / bitmap.width,
                if (area.top == previous.top) region.layoutBox.top else (area.top + 0.01f) * 1000 / bitmap.height,
                if (area.right == previous.right) region.layoutBox.right else area.right * 1000f / bitmap.width,
                if (area.bottom == previous.bottom) region.layoutBox.bottom else area.bottom * 1000f / bitmap.height,
            ))
        })
        return separateLayouts(bitmap, positioned)
    }

    fun hasLayoutOverlap(plan: MangaTranslationPlan): Boolean = plan.regions.indices.any { i ->
        val a = plan.regions[i].layoutBox
        (i + 1 until plan.regions.size).any { j ->
            val b = plan.regions[j].layoutBox
            a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
        }
    }

    /** Only shrink overlapping layouts; never change erasure or move source lettering anchors. */
    private fun separateLayouts(bitmap: Bitmap, plan: MangaTranslationPlan): MangaTranslationPlan {
        val areas = plan.regions.map { it.layoutBox.layoutPixels(bitmap.width, bitmap.height) }
        val sources = plan.regions.map { region ->
            val masks = region.eraseBoxes.map { it.pixels(bitmap.width, bitmap.height) }
            Rect(masks.first()).apply { masks.drop(1).forEach(::union) }
        }
        val limits = areas.map { Rect(0, 0, bitmap.width, bitmap.height) }
        for (i in areas.indices) for (j in i + 1 until areas.size) {
            if (!Rect.intersects(areas[i], areas[j])) continue
            val dx = sources[j].exactCenterX() - sources[i].exactCenterX()
            val dy = sources[j].exactCenterY() - sources[i].exactCenterY()
            if (dx == 0f && dy == 0f) continue // Indistinguishable anchors require new recognition.
            val separatedX = sources[i].right <= sources[j].left || sources[j].right <= sources[i].left
            val separatedY = sources[i].bottom <= sources[j].top || sources[j].bottom <= sources[i].top
            val horizontal = when {
                dx == 0f -> false
                dy == 0f -> true
                separatedX && !separatedY -> true
                separatedY && !separatedX -> false
                else -> kotlin.math.abs(dx) / max(1, sources[i].width() + sources[j].width()) >=
                    kotlin.math.abs(dy) / max(1, sources[i].height() + sources[j].height())
            }
            val first = if (if (horizontal) dx > 0 else dy > 0) i else j
            val second = if (first == i) j else i
            val start = if (horizontal) areas[first].left else areas[first].top
            val end = if (horizontal) areas[second].right else areas[second].bottom
            if (end - start < 3) continue
            val near = if (horizontal) sources[first].right else sources[first].bottom
            val far = if (horizontal) sources[second].left else sources[second].top
            val target = if (near <= far) (near + far) / 2 else
                ((if (horizontal) sources[first].exactCenterX() + sources[second].exactCenterX()
                    else sources[first].exactCenterY() + sources[second].exactCenterY()) / 2).roundToInt()
            val cut = target.coerceIn(start + 1, end - 2)
            if (horizontal) {
                limits[first].right = min(limits[first].right, cut)
                limits[second].left = max(limits[second].left, cut + 1)
            } else {
                limits[first].bottom = min(limits[first].bottom, cut)
                limits[second].top = max(limits[second].top, cut + 1)
            }
        }
        return plan.copy(regions = plan.regions.mapIndexed { index, region ->
            val area = Rect(areas[index])
            if (!area.intersect(limits[index]) || area == areas[index]) region else region.copy(layoutBox = MangaTextBox(
                (area.left + 0.01f) * 1000 / bitmap.width, (area.top + 0.01f) * 1000 / bitmap.height,
                area.right * 1000f / bitmap.width, area.bottom * 1000f / bitmap.height))
        })
    }

    fun eraseRects(region: MangaTranslatedRegion, width: Int, height: Int): List<Rect> {
        val source = region.textBox.pixels(width, height)
        val anchors = region.eraseBoxes.map { it.pixels(width, height) }
        val top = anchors.minOf { it.top }; val bottom = anchors.maxOf { it.bottom }
        val left = anchors.minOf { it.left }; val right = anchors.maxOf { it.right }
        return anchors.map { anchor ->
            val glyphSize = erasureGlyphSize(region, height)
            val thickness = glyphSize?.roundToInt()?.coerceAtLeast(1)
                ?: (if (region.vertical) anchor.width() else anchor.height()).coerceAtLeast(1)
            // A tight crop cuts curved outlines into glyph-sized fragments. Observe enough of
            // their connected shape to reject them; this is context, never a rectangular mask.
            val pad = if (glyphSize != null) (thickness * 2f).roundToInt().coerceIn(16, 64)
                else (thickness * .25f).roundToInt().coerceIn(3, 32)
            // Search complete source rows/columns, not just the span recognized by the OCR line.
            // Selection inside this envelope still requires source-ink evidence.
            val l = if (!region.vertical || anchor.left == left) min(source.left, anchor.left) else anchor.left
            val r = if (!region.vertical || anchor.right == right) max(source.right, anchor.right) else anchor.right
            val t = if (region.vertical || anchor.top == top) min(source.top, anchor.top) else anchor.top
            val b = if (region.vertical || anchor.bottom == bottom) max(source.bottom, anchor.bottom) else anchor.bottom
            Rect(max(0, l - pad), max(0, t - pad), min(width, r + pad), min(height, b + pad))
        }
    }

    private fun erasureGlyphSize(region: MangaTranslatedRegion, height: Int): Float? =
        (region.sourceFontSize?.takeIf { region.fontMeasurement != null } ?: region.typesetting?.fontSize)?.let { it * height / 1000 }

    fun erasureMask(bitmap: Bitmap, plan: MangaTranslationPlan, protectedRects: List<Rect> = emptyList()): BooleanArray =
        BooleanArray(bitmap.width * bitmap.height).also { erase(bitmap, plan, protectedRects = protectedRects, maskOnly = it) }

    fun erase(bitmap: Bitmap, plan: MangaTranslationPlan, patch: Boolean = false, protectedRects: List<Rect> = emptyList(), maskOnly: BooleanArray? = null, coverage: BooleanArray? = null) {
        plan.regions.forEach { region ->
            val neighbors = protectedRects + plan.regions.filter { it !== region }.map { it.textBox.pixels(bitmap.width, bitmap.height) }
            eraseRects(region, bitmap.width, bitmap.height).zip(region.eraseBoxes).forEach { (area, anchor) ->
                val allowed = region.bubbleShape?.let { MangaContainerGeometry.raster(it, bitmap.width, bitmap.height, area) }
                val localAnchor = anchor.pixels(bitmap.width, bitmap.height).apply { offset(-area.left, -area.top) }
                val source = region.textBox.pixels(bitmap.width, bitmap.height).apply { offset(-area.left, -area.top) }
                val protectedAreas = neighbors.filter { Rect.intersects(it, area) }.map { Rect(it).apply { offset(-area.left, -area.top) } }
                eraseRect(bitmap, area, localAnchor, source, region.vertical, allowed, protectedAreas,
                    region.original.codePointCount(0, region.original.length), patch,
                    erasureGlyphSize(region, bitmap.height), maskOnly, coverage)
            }
        }
    }

    private fun eraseRect(bitmap: Bitmap, rect: Rect, anchor: Rect, source: Rect, vertical: Boolean,
        allowed: BooleanArray?, protectedAreas: List<Rect>, characters: Int, patch: Boolean, glyphSize: Float?, maskOnly: BooleanArray?, coverage: BooleanArray?) {
        val w = rect.width(); val h = rect.height()
        if (w <= 0 || h <= 0) return
        val colors = IntArray(w * h)
        bitmap.getPixels(colors, 0, w, rect.left, rect.top, w, h)
        // Sample immediately outside the source lettering, not outside a padded search ROI.
        // The latter may land on a black burst outline or beyond a colored caption.
        val bg = if (glyphSize == null) background(bitmap, rect)
            else background(bitmap, Rect(source).apply { offset(rect.left, rect.top) }, samplePadding = 1)
        val mask = BooleanArray(colors.size) { mangaInkContrast(colors[it], bg, 25) }
        val grown = mangaSourceInkCoverage(colors, w, h, bg, anchor, source, vertical, allowed, protectedAreas, characters, patch, glyphSize)
        if (grown.none { it }) return
        coverage?.let { target -> grown.forEachIndexed { i, selected ->
            if (selected) target[(rect.top + i / w) * bitmap.width + rect.left + i % w] = true
        } }
        if (maskOnly != null) {
            grown.forEachIndexed { i, selected -> if (selected) maskOnly[(rect.top + i / w) * bitmap.width + rect.left + i % w] = true }
            return
        }
        if (patch) {
            for (i in colors.indices) if (grown[i]) colors[i] = bg
            bitmap.setPixels(colors, 0, w, rect.left, rect.top, w, h)
            return
        }
        val queue = IntArray(colors.size)
        var head: Int
        var tail: Int
        // Multi-source propagation borrows the nearest non-lettering pixel, retaining local tones.
        val visited = BooleanArray(grown.size) { !grown[it] }
        head = 0; tail = 0
        visited.forEachIndexed { i, known ->
            if (known && (!mask[i] || glyphSize != null && !mangaInkContrast(colors[i], bg, 64))) queue[tail++] = i
        }
        // Even a tightly cropped solid glyph has a known exterior background. The old density
        // cutoff left exactly these short/small captions completely untouched.
        if (tail == 0) {
            bitmap.setPixels(IntArray(colors.size) { if (grown[it]) bg else colors[it] }, 0, w, rect.left, rect.top, w, h)
            return
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w; val y = i / w
            fun visit(n: Int) {
                if (!visited[n]) { visited[n] = true; colors[n] = colors[i]; queue[tail++] = n }
            }
            if (x > 0) visit(i - 1)
            if (x + 1 < w) visit(i + 1)
            if (y > 0) visit(i - w)
            if (y + 1 < h) visit(i + w)
        }
        bitmap.setPixels(colors, 0, w, rect.left, rect.top, w, h)
    }

    /** Each source mask is evaluated against the same original, then contact seams are joined. */
    fun eraseSharedBackground(bitmap: Bitmap, plan: MangaTranslationPlan, patch: Boolean, protectedRects: List<Rect>) {
        val width = bitmap.width; val height = bitmap.height
        val original = IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
        val output = original.copyOf()
        val contacts = MangaBackgroundContacts(width, height)
        plan.regions.forEach { region ->
            val layer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(original, 0, width, 0, 0, width, height)
            }
            try {
                val mask = BooleanArray(original.size)
                val neighbors = protectedRects + plan.regions.filter { it !== region }.map { it.textBox.pixels(width, height) }
                erase(layer, MangaTranslationPlan(listOf(region)), patch, neighbors, coverage = mask)
                val colors = IntArray(original.size).also { layer.getPixels(it, 0, width, 0, 0, width, height) }
                mask.forEachIndexed { i, selected -> if (selected) output[i] = colors[i] }
                contacts.add(mask)
            } finally { layer.recycle() }
        }
        contacts.blend(output)
        bitmap.setPixels(output, 0, width, 0, 0, width, height)
    }

    fun draw(bitmap: Bitmap, plan: MangaTranslationPlan, patch: Boolean, fontScale: Float, protectedRects: List<Rect> = emptyList()) {
        plan.regions.forEach { region ->
            if (region.typesetting != null) {
                MangaAiTypesetter.draw(bitmap, region, fontScale, patch, protectedRects)
                return@forEach
            }
            val bg = background(bitmap, region.textBox.pixels(bitmap.width, bitmap.height))
            val originalSize = region.sourceFontSize?.let { it * bitmap.height / 1000 } ?: sourceSize(bitmap, region)
            MangaOverlayTypesetter.draw(bitmap, region, originalSize, fontScale, patch, bg, protectedRects)
        }
    }
    internal fun sourceSize(bitmap: Bitmap, region: MangaTranslatedRegion): Float {
        if (region.sourceLines.isNotEmpty()) {
            val lines = region.sourceLines.map { it.bounds.pixels(bitmap.width, bitmap.height) }
            val edges = region.sourceLines.map { it.quad?.edgeLengths(bitmap.width, bitmap.height) }
            return translationSourceGlyphHeight(lines.mapIndexed { index, box ->
                edges[index]?.let { if (region.vertical) it.first else it.second }?.roundToInt()
                    ?: if (region.vertical) box.width() else box.height()
            }, 1,
                if (region.vertical) region.original.codePoints().filter { !Character.isWhitespace(it) }.count().toInt() else 0,
                lines.mapIndexed { index, box -> edges[index]?.second?.roundToInt() ?: box.height() })
        }
        val cjk = MangaTextTypesetter.vertical(region.original, true)
        val sourceLines = region.original.lines().count { it.isNotBlank() }.coerceAtLeast(1)
        val linesPerMask = ((sourceLines + region.eraseBoxes.size - 1) / region.eraseBoxes.size).coerceAtLeast(1)
        val sizes = region.eraseBoxes.mapNotNull { box ->
            val rect = box.pixels(bitmap.width, bitmap.height)
            val area = rect.width().toLong() * rect.height()
            if (area > 1024 * 1024) return@mapNotNull null
            val colors = IntArray(area.toInt())
            bitmap.getPixels(colors, 0, rect.width(), rect.left, rect.top, rect.width(), rect.height())
            val bg = background(bitmap, rect)
            val length = if (region.vertical) rect.width() else rect.height()
            val occupied = BooleanArray(length)
            colors.forEachIndexed { index, color ->
                if (mangaInkContrast(color, bg, 48)) occupied[if (region.vertical) index % rect.width() else index / rect.width()] = true
            }
            val first = occupied.indexOfFirst { it }
            val last = occupied.indexOfLast { it }
            if (first < 0) return@mapNotNull null
            val runs = mutableListOf<Int>()
            var start = -1; var previous = -1
            val gap = max(1, min(rect.width(), rect.height()) / 16).coerceAtMost(4)
            occupied.forEachIndexed { index, ink ->
                if (ink) {
                    if (start >= 0 && index - previous > gap + 1) { runs += previous - start + 1; start = -1 }
                    if (start < 0) start = index
                    previous = index
                }
            }
            if (start >= 0) runs += previous - start + 1
            var projection = runs.sorted().let { it[it.size / 2].toFloat() }
            if (linesPerMask > 1) projection = min(projection, (last - first + 1).toFloat() / linesPerMask)
            val component = componentSpan(colors, rect.width(), rect.height(), bg, region.vertical, cjk)
            if (component != null) {
                // Closely spaced lines can be one projection run; CJK radicals can instead
                // fragment a single glyph. Components disambiguate only large discrepancies.
                projection = if (projection > max(component * 1.7f, component + 3f)) component else max(projection, component)
            }
            projection
        }.sorted()
        if (sizes.isNotEmpty()) {
            val thickness = sizes[sizes.size / 2]
            return if (!region.vertical) thickness else translationSourceGlyphHeight(sizes.map { it.roundToInt() }, thickness.roundToInt(),
                region.original.codePoints().filter { !Character.isWhitespace(it) }.count().toInt(),
                region.eraseBoxes.map { it.pixels(bitmap.width, bitmap.height).height() }, thickness)
        }
        // Unmeasurable ink is a low-confidence estimate, not permission to use the whole block
        // height as one character. The cap is proportional to this preview, never a fixed 8px.
        val rect = region.textBox.pixels(bitmap.width, bitmap.height)
        val lines = region.original.lines().filter { it.isNotBlank() }
        val longest = lines.maxOfOrNull { it.codePointCount(0, it.length) }?.coerceAtLeast(1) ?: 1
        val extent = if (region.vertical) rect.height() else rect.width()
        val minor = if (region.vertical) rect.width() else rect.height()
        val estimate = min(minor.toFloat() / sourceLines, extent / (longest * if (cjk) 1f else 0.6f))
        val conservative = min(estimate, min(bitmap.width, bitmap.height) * 0.04f).coerceAtLeast(0.5f)
        return conservative
    }

    private fun componentSpan(colors: IntArray, width: Int, height: Int, bg: Int, vertical: Boolean, cjk: Boolean): Float? {
        val seen = BooleanArray(colors.size)
        val queue = IntArray(colors.size)
        val components = mutableListOf<Pair<Float, Int>>()
        for (seed in colors.indices) {
            if (seen[seed] || !mangaInkContrast(colors[seed], bg, 48)) continue
            var head = 0; var tail = 1
            queue[0] = seed; seen[seed] = true
            val bounds = Rect(width, height, 0, 0)
            while (head < tail) {
                val index = queue[head++]; val x = index % width; val y = index / width
                bounds.left = min(bounds.left, x); bounds.right = max(bounds.right, x + 1)
                bounds.top = min(bounds.top, y); bounds.bottom = max(bounds.bottom, y + 1)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val next = ny * width + nx
                    if (!seen[next] && mangaInkContrast(colors[next], bg, 48)) { seen[next] = true; queue[tail++] = next }
                }
            }
            if (tail < 2) continue
            var span = if (vertical) bounds.width().toFloat() else bounds.height().toFloat()
            val cross = if (vertical) bounds.height().toFloat() else bounds.width().toFloat()
            if (cjk && span <= 3 && cross >= span * 3) span = cross // 一 / 丨 are not 2px fonts.
            components += span to tail
        }
        if (components.isEmpty()) return null
        val target = components.sumOf { it.second } * 0.75f
        var total = 0
        for ((span, pixels) in components.sortedBy { it.first }) {
            total += pixels
            if (total >= target) return span
        }
        return components.last().first
    }
    fun background(bitmap: Bitmap, rect: Rect, samplePadding: Int = 3): Int {
        val samples = mutableMapOf<Int, Int>()
        val representatives = mutableMapOf<Int, Int>()
        val pad = samplePadding
        val l = (rect.left - pad).coerceAtLeast(0); val r = (rect.right + pad).coerceAtMost(bitmap.width - 1)
        val t = (rect.top - pad).coerceAtLeast(0); val b = (rect.bottom + pad).coerceAtMost(bitmap.height - 1)
        fun sample(x: Int, y: Int) {
            val color = bitmap.getPixel(x, y) or Color.BLACK
            val key = color and 0xFFF0F0F0.toInt()
            samples[key] = (samples[key] ?: 0) + 1
            representatives.putIfAbsent(key, color)
        }
        for (x in l..r step max(1, (r - l) / 32)) { sample(x, t); sample(x, b) }
        for (y in t..b step max(1, (b - t) / 32)) { sample(l, y); sample(r, y) }
        return samples.maxByOrNull { it.value }?.key?.let(representatives::get) ?: Color.WHITE
    }
    private fun luminance(color: Int): Int = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
}
