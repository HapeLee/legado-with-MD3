package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import android.graphics.Rect
import io.legado.app.domain.model.manga.*

/** Detection ownership, bounded mask recovery and durable analysis-resolution geometry. */
internal object MangaContainerGeometry {
    private fun normalized(rect: Rect, width: Int, height: Int) = MangaTextBox(
        (rect.left + .01f) * 1000 / width, (rect.top + .01f) * 1000 / height,
        rect.right * 1000f / width, rect.bottom * 1000f / height)

    internal fun owner(rect: Rect, bubbles: List<MangaBubbleDetection>): Int? {
        val candidates = bubbles.mapIndexedNotNull { index, bubble ->
            val b = bubble.bounds
            val overlap = maxOf(0, minOf(rect.right, b.right) - maxOf(rect.left, b.left)).toFloat() *
                maxOf(0, minOf(rect.bottom, b.bottom) - maxOf(rect.top, b.top)) / (rect.width().toFloat() * rect.height())
            if (overlap < .72f) null else index to (overlap / (b.width.toFloat() * b.height))
        }.sortedByDescending { it.second }
        if (candidates.size > 1 && candidates[0].second < candidates[1].second * 1.2f) return null
        return candidates.firstOrNull()?.first
    }

    /** Assign individual OCR lines before translation; neighboring balloons never share a request ID. */
    fun regroup(width: Int, height: Int, regions: List<MangaTranslatedRegion>, detections: List<MangaBubbleDetection>, mergeLooseLines: Boolean = false): List<MangaTranslatedRegion> {
        val bubbles = detections.filter { it.kind == 0 }
        val pieces = regions.flatMap { block ->
            if (block.sourceLines.isEmpty()) listOf(block) else block.sourceLines.map { line ->
                block.copy(original = line.text, translation = line.text, textBox = line.bounds, layoutBox = line.bounds,
                    eraseBoxes = listOf(line.bounds), sourceLines = listOf(line))
            }
        }
        val groups = linkedMapOf<String, MutableList<MangaTranslatedRegion>>()
        pieces.forEachIndexed { index, region ->
            val container = owner(region.textBox.pixels(width, height), bubbles)
            val key = container?.let { "bubble:$it" } ?: if (mergeLooseLines && region.sourceLines.isNotEmpty()) "free:ocr" else "free:${region.id}"
            groups.getOrPut(key) { mutableListOf() } += region
        }
        val partitions = groups.flatMap { (key, members) ->
            if (key.startsWith("bubble:")) listOf(MangaTextLineGrouping.order(members, width, height))
            else MangaTextLineGrouping.split(members, width, height)
        }
        val preserveIds = partitions.all { it.size == 1 } && partitions.map { it.single().id }.distinct().size == partitions.size
        val originals = regions.associateBy { it.id }
        val memberships = partitions.map { members -> members.map { it.id }.toSet() }
        fun support(index: Int, members: List<MangaTranslatedRegion>): MangaTextBox {
            val boxes = members.map { it.textBox }.toMutableList()
            for (id in memberships[index]) {
                // Retain a complete OCR block's source extent only when it was not split into
                // different bubbles/paragraphs, and does not cover another partition's source.
                if (memberships.count { id in it } != 1) continue
                val original = originals[id]?.textBox ?: continue
                val overlapsOther = partitions.indices.any { j -> j != index && partitions[j].any { other ->
                    val b = other.textBox
                    original.left < b.right && original.right > b.left && original.top < b.bottom && original.bottom > b.top
                } }
                if (!overlapsOther) boxes += original
            }
            return MangaTextBox(boxes.minOf { it.left }, boxes.minOf { it.top }, boxes.maxOf { it.right }, boxes.maxOf { it.bottom })
        }
        return partitions.mapIndexed { index, members ->
            val sourceBounds = support(index, members)
            if (members.size == 1) return@mapIndexed members.single().let {
                it.copy(id = if (preserveIds) it.id else "r$index", textBox = sourceBounds,
                    layoutBox = if (it.layoutBox == it.textBox) sourceBounds else it.layoutBox)
            }
            val vertical = members.count { it.vertical } > members.size / 2
            val ordered = MangaTextLineGrouping.order(members, width, height)
            val text = ordered.joinToString("\n") { it.original }
            val erasures = ordered.flatMap { it.eraseBoxes }; val source = ordered.flatMap { it.sourceLines }
            require(erasures.size <= 128 && source.size <= 128) { "Too many source lines in one text region" }
            MangaTranslatedRegion("r$index", text, text, sourceBounds, eraseBoxes = erasures,
                vertical = vertical, sourceLines = source)
        }
    }

    fun attach(bitmap: Bitmap, plan: MangaTranslationPlan, detections: List<MangaBubbleDetection>): MangaTranslationPlan {
        val aiContainers = plan.regions.mapNotNull { it.aiGeometry }.filter { it.containerBox != null }.distinctBy { it.containerId }
        val aiBubbles = aiContainers.map { geometry ->
            val r = checkNotNull(geometry.containerBox).pixels(bitmap.width, bitmap.height)
            MangaBubbleDetection(MangaShapeRect(r.left, r.top, r.right, r.bottom), 1f, 0)
        }
        // AI instances remain distinct even when touching. Local boxes are only additional hints.
        val bubbles = aiBubbles + detections.filter { detection -> detection.kind == 0 && aiBubbles.none { ai ->
            val a = ai.bounds; val b = detection.bounds
            maxOf(0, minOf(a.right, b.right) - maxOf(a.left, b.left)).toFloat() *
                maxOf(0, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)) >= b.width.toFloat() * b.height * .5f
        } }
        if (bubbles.isEmpty() || plan.regions.isEmpty()) return plan
        val owners = plan.regions.map { region ->
            aiContainers.indexOfFirst { it.containerId == region.aiGeometry?.containerId }.takeIf { it >= 0 }
                ?: owner((region.recognitionBox ?: region.textBox).pixels(bitmap.width, bitmap.height), bubbles)
        }
        var budget = minOf(4L * 1024 * 1024, bitmap.width.toLong() * bitmap.height * 2)
        val shapes = bubbles.indices.associateWith { index ->
            val members = plan.regions.indices.filter { owners[it] == index }.map { plan.regions[it] }
            if (members.isEmpty()) return@associateWith null
            val bounds = bubbles[index].bounds
            val crop = Rect(maxOf(0, bounds.left - 12), maxOf(0, bounds.top - 12),
                minOf(bitmap.width, bounds.right + 12), minOf(bitmap.height, bounds.bottom + 12))
            val area = crop.width().toLong() * crop.height()
            if (area > 1024 * 1024 || area > budget) return@associateWith null
            budget -= area
            val pixels = IntArray(area.toInt())
            bitmap.getPixels(pixels, 0, crop.width(), crop.left, crop.top, crop.width(), crop.height())
            val bg = MangaPageComposer.background(bitmap, members.first().textBox.pixels(bitmap.width, bitmap.height))
            if (((bg ushr 16 and 255) + (bg ushr 8 and 255) + (bg and 255)) < 384) {
                for (i in pixels.indices) pixels[i] = pixels[i] xor 0x00ffffff
            }
            val polygons = members.flatMap { it.eraseBoxes }.map { box ->
                val r = box.pixels(bitmap.width, bitmap.height).apply { offset(-crop.left, -crop.top) }
                MangaBubbleMaskAnalyzer.Polygon(listOf(
                    MangaBubbleMaskAnalyzer.Point(r.left.toFloat(), r.top.toFloat()),
                    MangaBubbleMaskAnalyzer.Point(r.right.toFloat(), r.top.toFloat()),
                    MangaBubbleMaskAnalyzer.Point(r.right.toFloat(), r.bottom.toFloat()),
                    MangaBubbleMaskAnalyzer.Point(r.left.toFloat(), r.bottom.toFloat())))
            }
            val content = MangaShapeRect(polygons.minOf { it.bounds.left }, polygons.minOf { it.bounds.top },
                polygons.maxOf { it.bounds.right }, polygons.maxOf { it.bounds.bottom })
            val output = BooleanArray(pixels.size)
            val input = MangaBubbleMaskAnalyzer.BubbleInput(content, polygons.indices.toList(),
                MangaShapeRect(0, 0, crop.width(), crop.height()), useExactSearchBounds = true)
            var result = MangaBubbleMaskAnalyzer.estimateBubbleInterior(crop.width(), crop.height(), pixels, polygons, input, output)
            if (!result.accepted) {
                // The ellipse is a search constraint, never accepted as the final contour on its own.
                val ellipse = BooleanArray(output.size) { p ->
                    val x = (p % crop.width() + crop.left - (bounds.left + bounds.right) / 2f) / (bounds.width / 2f - 2).coerceAtLeast(1f)
                    val y = (p / crop.width() + crop.top - (bounds.top + bounds.bottom) / 2f) / (bounds.height / 2f - 2).coerceAtLeast(1f)
                    x * x + y * y < 1
                }
                output.fill(false)
                result = MangaBubbleMaskAnalyzer.estimateBubbleInterior(crop.width(), crop.height(), pixels, polygons,
                    input.copy(interiorConstraint = MangaInstanceMask(0, 0, crop.width(), crop.height(), ellipse)), output)
            }
            if (!result.accepted) return@associateWith null
            // Partition shared interiors by detector centers; a connecting neck must not merge instances.
            for (p in output.indices) {
                if (!output[p]) continue
                val x = p % crop.width() + crop.left; val y = p / crop.width() + crop.top
                fun distance(b: MangaShapeRect): Float {
                    val dx = (x - (b.left + b.right) / 2f) / b.width
                    val dy = (y - (b.top + b.bottom) / 2f) / b.height
                    return dx * dx + dy * dy
                }
                val ownDistance = distance(bounds)
                if (bubbles.indices.any { j -> j != index && owners.contains(j) &&
                        x in bubbles[j].bounds.left until bubbles[j].bounds.right && y in bubbles[j].bounds.top until bubbles[j].bounds.bottom &&
                        (distance(bubbles[j].bounds) < ownDistance || distance(bubbles[j].bounds) == ownDistance && j < index) }) output[p] = false
            }
            // A split interior is useful only if it still contains the assigned source lettering.
            if (members.flatMap { it.eraseBoxes }.any { box ->
                val source = box.pixels(bitmap.width, bitmap.height)
                var covered = 0
                for (y in maxOf(source.top, crop.top) until minOf(source.bottom, crop.bottom)) {
                    for (x in maxOf(source.left, crop.left) until minOf(source.right, crop.right)) {
                        if (output[(y - crop.top) * crop.width() + x - crop.left]) covered++
                    }
                }
                covered < source.width().toFloat() * source.height() * .72f
            }) return@associateWith null
            encode("bubble-$index", crop, bitmap.width, bitmap.height, output)
        }
        return plan.copy(regions = plan.regions.mapIndexed { i, region ->
            val shape = owners[i]?.let(shapes::get) ?: return@mapIndexed region
            region.copy(bubbleShape = shape, layoutBox = shape.bounds)
        })
    }

    private fun encode(id: String, crop: Rect, width: Int, height: Int, pixels: BooleanArray): MangaBubbleShape? {
        var l = crop.width(); var r = 0; var t = crop.height(); var b = 0
        for (i in pixels.indices) if (pixels[i]) {
            val x = i % crop.width(); val y = i / crop.width()
            l = minOf(l, x); r = maxOf(r, x + 1); t = minOf(t, y); b = maxOf(b, y + 1)
        }
        if (r <= l || b <= t) return null
        val w = r - l; val h = b - t
        val runs = mutableListOf<Int>()
        var start = -1
        for (p in 0..w * h) {
            val inside = p < w * h && pixels[(p / w + t) * crop.width() + p % w + l]
            if (inside && start < 0) start = p
            if (!inside && start >= 0) { runs += start; runs += p - start; start = -1 }
            if (runs.size > 65536) return null
        }
        return MangaBubbleShape(id, normalized(Rect(crop.left + l, crop.top + t, crop.left + r, crop.top + b), width, height), w, h, runs)
    }

    fun raster(shape: MangaBubbleShape, width: Int, height: Int, area: Rect): BooleanArray {
        val source = BooleanArray(shape.width * shape.height)
        for (i in shape.runs.indices step 2) source.fill(true, shape.runs[i], shape.runs[i] + shape.runs[i + 1])
        val bounds = shape.bounds.pixels(width, height)
        return BooleanArray(area.width() * area.height()) { p ->
            val x = p % area.width() + area.left; val y = p / area.width() + area.top
            if (x !in bounds.left until bounds.right || y !in bounds.top until bounds.bottom) false else {
                val sx = ((x - bounds.left + .5f) * shape.width / bounds.width()).toInt().coerceIn(0, shape.width - 1)
                val sy = ((y - bounds.top + .5f) * shape.height / bounds.height()).toInt().coerceIn(0, shape.height - 1)
                source[sy * shape.width + sx]
            }
        }
    }
}
