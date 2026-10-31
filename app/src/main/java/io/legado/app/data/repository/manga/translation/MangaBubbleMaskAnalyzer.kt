// SPDX-License-Identifier: Apache-2.0
// Adapted from ciddwd/overlay-translator, commit 64a7aa2c9605c88dd35bdeea42ab5c0f1c18ce5e.
// Modifications: Legado package, geometry types and bounded Android pipeline integration.
package io.legado.app.data.repository.manga.translation

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Bounded interior estimator adapted from the overlay prototype. A detector-bounded crop,
 * member coverage checks and explicit rejection are required before persisting a local mask.
 */
internal object MangaBubbleMaskAnalyzer {

    data class Point(val x: Float, val y: Float)

    data class Polygon(val points: List<Point>) {
        init {
            require(points.size >= 3)
        }

        val bounds: MangaShapeRect
            get() = MangaShapeRect(
                left = floor(points.minOf { it.x }).toInt(),
                top = floor(points.minOf { it.y }).toInt(),
                right = ceil(points.maxOf { it.x }).toInt(),
                bottom = ceil(points.maxOf { it.y }).toInt(),
            )
    }

    data class BubbleInput(
        val contentBounds: MangaShapeRect,
        val memberIndices: List<Int>,
        val searchBounds: MangaShapeRect? = null,
        val retrySearchBounds: MangaShapeRect? = null,
        /**
         * Detector boxes already provide bounded local work areas. When true, use the supplied
         * crops instead of deriving the wider OCR-only heuristic crops. [retrySearchBounds] is
         * attempted only when the first crop leaks at its boundary.
         */
        val useExactSearchBounds: Boolean = false,
        /** Limits a detector recovery to its conservative interior; pixels still define the shape. */
        val interiorConstraint: MangaInstanceMask? = null,
    )

    data class BubbleDiagnostic(
        val roi: MangaShapeRect,
        val accepted: Boolean,
        val confidence: Float,
        val reason: String,
        val regionPixels: Int,
        val attempts: Int = 1,
        val memberCoverage: Float = 0f,
    )

    internal class BubbleTiming {
        var backgroundNs: Long = 0L
        var luminanceNs: Long = 0L
        var candidateBuildNs: Long = 0L
        var seedNs: Long = 0L
        var floodNs: Long = 0L
        var edgeNs: Long = 0L
        var fillNs: Long = 0L
        var coverageAndCopyNs: Long = 0L
    }

    internal class Workspace {
        internal val background = MangaBubbleBackgroundStatistics.Scratch()
    }

    internal fun estimateBubbleInterior(
        width: Int,
        height: Int,
        argb: IntArray,
        polygons: List<Polygon>,
        bubble: BubbleInput,
        output: BooleanArray,
        timing: BubbleTiming? = null,
        workspace: Workspace = Workspace(),
    ): BubbleDiagnostic {
        require(width > 0 && height > 0)
        require(argb.size == width * height)
        require(output.size == width * height)
        return estimateBubble(
            width = width,
            height = height,
            argb = argb,
            polygons = polygons,
            bubble = bubble,
            output = output,
            timing = timing,
            workspace = workspace,
        )
    }

    internal fun rasterizePolygons(
        width: Int,
        height: Int,
        polygons: List<Polygon>,
    ): BooleanArray {
        val output = BooleanArray(width * height)
        polygons.forEach { polygon ->
            val bounds = clamp(polygon.bounds, width, height)
            for (y in bounds.top until bounds.bottom) {
                for (x in bounds.left until bounds.right) {
                    if (pointInPolygon(x + 0.5f, y + 0.5f, polygon.points)) {
                        output[y * width + x] = true
                    }
                }
            }
        }
        return output
    }

    private fun estimateBubble(
        width: Int,
        height: Int,
        argb: IntArray,
        polygons: List<Polygon>,
        bubble: BubbleInput,
        output: BooleanArray,
        timing: BubbleTiming? = null,
        workspace: Workspace = Workspace(),
    ): BubbleDiagnostic {
        val members = bubble.memberIndices.mapNotNull(polygons::getOrNull)
        if (members.isEmpty()) {
            return rejected(clamp(bubble.contentBounds, width, height), "no_members")
        }

        val content = clamp(bubble.contentBounds, width, height)
        if (content.width <= 0 || content.height <= 0) {
            return rejected(content, "empty_content")
        }
        val minimumLongSide = max(
            MIN_CONTENT_LONG_SIDE_PX,
            minOf(width, height) / MIN_CONTENT_LONG_SIDE_SCREEN_DIVISOR,
        )
        val minimumContentArea = max(
            MIN_CONTENT_AREA_PX,
            width.toLong() * height / MIN_CONTENT_AREA_SCREEN_DIVISOR,
        )
        if (
            max(content.width, content.height) < minimumLongSide ||
            content.width.toLong() * content.height < minimumContentArea
        ) {
            return rejected(content, "content_too_small")
        }

        val candidateRois = if (bubble.useExactSearchBounds && bubble.searchBounds != null) {
            listOfNotNull(bubble.searchBounds, bubble.retrySearchBounds)
                .map { bounds -> clamp(bounds, width, height) }
                .distinct()
        } else {
            candidateRois(
                content = bubble.searchBounds ?: content,
                width = width,
                height = height,
            )
        }
        var preparedSeedCandidates: List<MangaSeedSearchOrder>? = null
        var lastReason = "region_leaked_to_roi"
        var lastPixels = 0
        var lastRoi = candidateRois.first()
        for ((attemptIndex, roi) in candidateRois.withIndex()) {
            val attempts = attemptIndex + 1
            lastRoi = roi
            if (roi.width < MIN_ROI_SIDE_PX || roi.height < MIN_ROI_SIDE_PX) {
                return rejected(roi, "roi_too_small", attempts = attempts)
            }

            val backgroundStartedAtNs = System.nanoTime()
            val background = estimateBackgroundAroundText(
                argb = argb,
                width = width,
                roi = roi,
                members = members,
                scratch = workspace.background,
            )
            timing?.let { it.backgroundNs += System.nanoTime() - backgroundStartedAtNs }
            if (background == null) {
                return rejected(roi, "no_light_background", attempts = attempts)
            }
            if (background.luminance < MIN_BACKGROUND_LUMINANCE) {
                return rejected(roi, "background_too_dark", attempts = attempts)
            }
            val seedCandidates = preparedSeedCandidates ?: run {
                val seedCandidateStartedAtNs = System.nanoTime()
                members.map { polygon ->
                    buildSeedCandidates(
                        polygon = polygon,
                        imageWidth = width,
                        imageHeight = height,
                    )
                }.also { prepared ->
                    preparedSeedCandidates = prepared
                    timing?.let {
                        it.seedNs += System.nanoTime() - seedCandidateStartedAtNs
                    }
                }
            }

            val luminanceStartedAtNs = System.nanoTime()
            val roiFeatures = extractRoiFeatures(
                argb = argb,
                imageWidth = width,
                roi = roi,
                background = background,
            )
            timing?.let { it.luminanceNs += System.nanoTime() - luminanceStartedAtNs }
            val candidate = BooleanArray(roi.width * roi.height)
            val allowedInterior = bubble.interiorConstraint?.let { constraint ->
                BooleanArray(candidate.size) { index ->
                    constraint.contains(roi.left + index % roi.width, roi.top + index / roi.width)
                }
            }
            val workQueue = IntArray(candidate.size)
            var bestRegion: BooleanArray? = null
            var bestRegionPixels = 0
            var bestProfileIndex = 0
            var retryWithExpandedRoi = false
            var invalidatedByLeak = false
            for ((profileIndex, profile) in CANDIDATE_PROFILES.withIndex()) {
                val candidateStartedAtNs = System.nanoTime()
                val maximumColorDistanceSquared =
                    profile.maxColorDistance * profile.maxColorDistance
                val minimumLuminance = minimumCandidateLuminance(background, profile)
                for (localIndex in candidate.indices) {
                    candidate[localIndex] =
                        (allowedInterior == null || allowedInterior[localIndex]) &&
                        roiFeatures.colorDistanceSquared[localIndex].toFloat() <=
                        maximumColorDistanceSquared &&
                        roiFeatures.luminances[localIndex] >= minimumLuminance
                }
                timing?.let { it.candidateBuildNs += System.nanoTime() - candidateStartedAtNs }

                val seedStartedAtNs = System.nanoTime()
                val seeds = seedCandidates.flatMap { it.spatialSeeds(candidate, roi) }.distinct()
                timing?.let { it.seedNs += System.nanoTime() - seedStartedAtNs }
                if (seeds.isEmpty()) {
                    lastReason = "no_background_seed"
                    continue
                }

                val floodStartedAtNs = System.nanoTime()
                val flood = floodCandidateRegion(
                    candidate = candidate,
                    width = roi.width,
                    height = roi.height,
                    seeds = seeds,
                    queue = workQueue,
                )
                timing?.let { it.floodNs += System.nanoTime() - floodStartedAtNs }
                val region = flood.mask
                val rawRegionPixels = flood.pixelCount
                lastPixels = rawRegionPixels
                if (rawRegionPixels < max(MIN_REGION_PIXELS, content.width * content.height / 5)) {
                    lastReason = "region_too_small"
                    continue
                }

                val leaked = flood.touchesBoundary
                val tooLarge = rawRegionPixels >=
                    (roi.width * roi.height * MAX_REGION_ROI_RATIO).roundToInt()
                if (leaked || tooLarge) {
                    lastReason = if (leaked) "region_leaked_to_roi" else "region_too_large"
                    retryWithExpandedRoi = attemptIndex < candidateRois.lastIndex
                    invalidatedByLeak = true
                    break
                }
                if (rawRegionPixels > bestRegionPixels) {
                    bestRegion = region
                    bestRegionPixels = rawRegionPixels
                    bestProfileIndex = profileIndex
                }
            }

            val edgeStartedAtNs = System.nanoTime()
            val edgeAttempt = findEdgeBoundedRegion(
                argb = argb,
                imageWidth = width,
                roi = roi,
                members = members,
                background = background,
                luminances = roiFeatures.luminances,
                queue = workQueue,
                seedCandidates = seedCandidates,
                allowedInterior = allowedInterior,
            )
            timing?.let { it.edgeNs += System.nanoTime() - edgeStartedAtNs }
            if (edgeAttempt.regionPixels > lastPixels) {
                lastPixels = edgeAttempt.regionPixels
                lastReason = edgeAttempt.reason
            }
            val colorRegion = if (invalidatedByLeak) null else bestRegion
            val useEdgeRegion = edgeAttempt.region != null && (
                colorRegion == null ||
                    edgeAttempt.regionPixels >
                    (bestRegionPixels * EDGE_PREFERRED_SIZE_RATIO).roundToInt()
                )
            val selectedRegion = if (useEdgeRegion) edgeAttempt.region else colorRegion
            if (selectedRegion == null) {
                if (
                    attemptIndex < candidateRois.lastIndex &&
                    (retryWithExpandedRoi || edgeAttempt.canRetryWithExpandedRoi)
                ) {
                    continue
                }
                return rejected(roi, lastReason, lastPixels, attempts)
            }

            val fillStartedAtNs = System.nanoTime()
            val filledRegion = fillEnclosedHoles(
                region = selectedRegion,
                width = roi.width,
                height = roi.height,
                queue = workQueue,
            )
            timing?.let { it.fillNs += System.nanoTime() - fillStartedAtNs }
            val filled = filledRegion.mask
            if (allowedInterior != null) {
                // Recover glyph holes only. Filling all enclosed holes could admit a closed
                // outline or artwork inside the detector's approximate ellipse.
                val textSupport = rasterizePolygons(
                    width = roi.width,
                    height = roi.height,
                    polygons = members.map { polygon ->
                        Polygon(polygon.points.map { point ->
                            Point(point.x - roi.left, point.y - roi.top)
                        })
                    },
                )
                for (index in filled.indices) {
                    filled[index] = allowedInterior[index] &&
                        (selectedRegion[index] || (filled[index] && textSupport[index]))
                }
            }
            val regionPixels = if (allowedInterior == null) filledRegion.pixelCount
                else filled.count { it }
            val coverageStartedAtNs = System.nanoTime()
            val memberCoverage = memberRegionCoverage(
                region = filled,
                roi = roi,
                members = members,
            )
            if (memberCoverage < MIN_MEMBER_REGION_COVERAGE) {
                timing?.let {
                    it.coverageAndCopyNs += System.nanoTime() - coverageStartedAtNs
                }
                return BubbleDiagnostic(
                    roi = roi,
                    accepted = false,
                    confidence = 0f,
                    reason = "member_coverage_low",
                    regionPixels = regionPixels,
                    attempts = attempts,
                    memberCoverage = memberCoverage,
                )
            }
            val coverage = regionPixels.toFloat() / (roi.width * roi.height).coerceAtLeast(1)
            val confidence = (
                1f -
                    ((coverage - IDEAL_REGION_ROI_RATIO).coerceAtLeast(0f) /
                        (MAX_REGION_ROI_RATIO - IDEAL_REGION_ROI_RATIO))
                ).coerceIn(0.1f, 1f)

            for (localY in 0 until roi.height) {
                val globalY = roi.top + localY
                val localOffset = localY * roi.width
                val globalOffset = globalY * width + roi.left
                for (localX in 0 until roi.width) {
                    if (filled[localOffset + localX]) {
                        output[globalOffset + localX] = true
                    }
                }
            }
            timing?.let { it.coverageAndCopyNs += System.nanoTime() - coverageStartedAtNs }
            return BubbleDiagnostic(
                roi = roi,
                accepted = true,
                confidence = confidence,
                reason = if (useEdgeRegion) {
                    if (attempts > 1) {
                        "accepted_edge_after_roi_expand"
                    } else {
                        "accepted_edge"
                    }
                } else {
                    acceptedReason(attempts, bestProfileIndex)
                },
                regionPixels = regionPixels,
                attempts = attempts,
                memberCoverage = memberCoverage,
            )
        }
        return rejected(
            roi = lastRoi,
            reason = lastReason,
            regionPixels = lastPixels,
            attempts = candidateRois.size,
        )
    }

    /**
     * A narrow vertical OCR box can sit inside a much wider speech balloon. Retry once with a
     * larger observation window when the color region reaches the first ROI edge. A genuinely
     * open region keeps leaking after expansion, while a closed balloon becomes bounded.
     */
    private fun candidateRois(
        content: MangaShapeRect,
        width: Int,
        height: Int,
    ): List<MangaShapeRect> {
        val maximumContentSpan = max(content.width, content.height)
        val baseMargin = (maximumContentSpan * ROI_MARGIN_RATIO).roundToInt()
            .coerceIn(MIN_ROI_MARGIN_PX, MAX_ROI_MARGIN_PX)
        val expandedMargin = (baseMargin * ROI_RETRY_MARGIN_MULTIPLIER).roundToInt()
            .coerceAtMost(MAX_RETRY_ROI_MARGIN_PX)
        return listOf(baseMargin, expandedMargin)
            .distinct()
            .map { margin ->
                MangaShapeRect(
                    left = (content.left - margin).coerceAtLeast(0),
                    top = (content.top - margin).coerceAtLeast(0),
                    right = (content.right + margin).coerceAtMost(width),
                    bottom = (content.bottom + margin).coerceAtMost(height),
                )
            }
            .distinct()
    }

    private data class BackgroundColor(
        val red: Int,
        val green: Int,
        val blue: Int,
        val luminance: Int,
        val lowLuminance: Int,
    )

    private data class RoiFeatures(
        val luminances: IntArray,
        val colorDistanceSquared: IntArray,
    )

    private data class CandidateProfile(
        val maxColorDistance: Float,
        val maxLuminanceDrop: Int,
        val useSampledLuminanceFloor: Boolean = false,
    )

    private data class EdgeRegionAttempt(
        val region: BooleanArray?,
        val regionPixels: Int,
        val reason: String,
        val canRetryWithExpandedRoi: Boolean,
    )

    private data class FloodRegion(
        val mask: BooleanArray,
        val pixelCount: Int,
        val touchesBoundary: Boolean,
    )

    private data class FilledRegion(
        val mask: BooleanArray,
        val pixelCount: Int,
    )

    private fun estimateBackgroundAroundText(
        argb: IntArray,
        width: Int,
        roi: MangaShapeRect,
        members: List<Polygon>,
        scratch: MangaBubbleBackgroundStatistics.Scratch,
    ): BackgroundColor? {
        val capacity = members.sumOf { polygon ->
            val bounds = clamp(polygon.bounds, roi.right, roi.bottom)
            val sampleWidth = (minOf(bounds.right, roi.right) - max(bounds.left, roi.left))
                .coerceAtLeast(0)
            val sampleHeight = (minOf(bounds.bottom, roi.bottom) - max(bounds.top, roi.top))
                .coerceAtLeast(0)
            ((sampleWidth + SAMPLE_STRIDE_PX - 1) / SAMPLE_STRIDE_PX) *
                ((sampleHeight + SAMPLE_STRIDE_PX - 1) / SAMPLE_STRIDE_PX)
        }
        scratch.ensureSampleCapacity(capacity)
        var sampleCount = 0
        members.forEach { polygon ->
            val bounds = clamp(polygon.bounds, roi.right, roi.bottom)
            val left = max(bounds.left, roi.left)
            val top = max(bounds.top, roi.top)
            val right = minOf(bounds.right, roi.right)
            val bottom = minOf(bounds.bottom, roi.bottom)
            var y = top
            while (y < bottom) {
                var x = left
                while (x < right) {
                    if (pointInPolygon(x + 0.5f, y + 0.5f, polygon.points)) {
                        scratch.samples[sampleCount++] = argb[y * width + x]
                    }
                    x += SAMPLE_STRIDE_PX
                }
                y += SAMPLE_STRIDE_PX
            }
        }
        if (sampleCount < MIN_BACKGROUND_SAMPLES) return null
        val estimate = scratch.estimate(
            count = sampleCount,
            brightSampleStartRatio = BRIGHT_SAMPLE_START_RATIO,
            lowSampleRatio = BACKGROUND_LOW_SAMPLE_RATIO,
        )
        return BackgroundColor(
            red = estimate.red,
            green = estimate.green,
            blue = estimate.blue,
            luminance = estimate.luminance,
            lowLuminance = estimate.lowLuminance,
        )
    }

    private fun minimumCandidateLuminance(
        background: BackgroundColor,
        profile: CandidateProfile,
    ): Int = if (profile.useSampledLuminanceFloor) {
        max(
            MIN_CANDIDATE_LUMINANCE,
            background.lowLuminance - SAMPLED_LUMINANCE_FLOOR_TOLERANCE,
        )
    } else {
        max(
            MIN_CANDIDATE_LUMINANCE,
            background.luminance - profile.maxLuminanceDrop,
        )
    }

    private fun acceptedReason(attempts: Int, profileIndex: Int): String = when {
        attempts > 1 && profileIndex > 0 -> "accepted_gradient_after_roi_expand"
        attempts > 1 -> "accepted_after_roi_expand"
        profileIndex > 0 -> "accepted_gradient"
        else -> "accepted"
    }

    /**
     * Color ranges cannot cover both ends of a strong gray gradient without also admitting
     * anti-aliased outline pixels. This fallback treats very dark pixels and strong local
     * luminance gradients as barriers, then floods the area around the OCR members. It is used
     * only when it produces a larger closed region than color segmentation.
     */
    private fun findEdgeBoundedRegion(
        argb: IntArray,
        imageWidth: Int,
        roi: MangaShapeRect,
        members: List<Polygon>,
        background: BackgroundColor,
        luminances: IntArray? = null,
        queue: IntArray? = null,
        seedCandidates: List<MangaSeedSearchOrder>? = null,
        allowedInterior: BooleanArray? = null,
    ): EdgeRegionAttempt {
        val localLuminances = luminances ?: extractLuminances(argb, imageWidth, roi)
        require(localLuminances.size == roi.width * roi.height)
        val darkBarrierThreshold = (
            background.lowLuminance - EDGE_DARK_BACKGROUND_GAP
            ).coerceIn(EDGE_MIN_DARK_THRESHOLD, EDGE_MAX_DARK_THRESHOLD)
        val barrier = BooleanArray(localLuminances.size)
        for (y in 0 until roi.height) {
            for (x in 0 until roi.width) {
                val index = y * roi.width + x
                val left = localLuminances[y * roi.width + (x - 1).coerceAtLeast(0)]
                val right = localLuminances[y * roi.width + (x + 1).coerceAtMost(roi.width - 1)]
                val top = localLuminances[(y - 1).coerceAtLeast(0) * roi.width + x]
                val bottom = localLuminances[(y + 1).coerceAtMost(roi.height - 1) * roi.width + x]
                val gradient = kotlin.math.abs(right - left) + kotlin.math.abs(bottom - top)
                barrier[index] = localLuminances[index] <= darkBarrierThreshold ||
                    gradient >= EDGE_GRADIENT_THRESHOLD
            }
        }
        val sealedBarrier = BinarySquareDilation.dilate(
            input = barrier,
            width = roi.width,
            height = roi.height,
            radius = EDGE_BARRIER_DILATION_PX,
        )
        val walkable = BooleanArray(sealedBarrier.size) { index ->
            !sealedBarrier[index] && (allowedInterior == null || allowedInterior[index])
        }
        val reusableSeeds = seedCandidates ?: members.map { polygon ->
            buildSeedCandidates(
                polygon = polygon,
                imageWidth = imageWidth,
                imageHeight = argb.size / imageWidth,
            )
        }
        val seeds = reusableSeeds.flatMap { it.spatialSeeds(walkable, roi) }.distinct()
        if (seeds.isEmpty()) {
            return EdgeRegionAttempt(null, 0, "edge_no_seed", false)
        }
        val flood = floodCandidateRegion(
            candidate = walkable,
            width = roi.width,
            height = roi.height,
            seeds = seeds,
            queue = queue,
        )
        val region = flood.mask
        val regionPixels = flood.pixelCount
        if (regionPixels < MIN_REGION_PIXELS) {
            return EdgeRegionAttempt(null, regionPixels, "edge_region_too_small", false)
        }
        if (flood.touchesBoundary) {
            return EdgeRegionAttempt(null, regionPixels, "edge_region_leaked", true)
        }
        if (regionPixels >= (roi.width * roi.height * MAX_REGION_ROI_RATIO).roundToInt()) {
            return EdgeRegionAttempt(null, regionPixels, "edge_region_too_large", true)
        }
        return EdgeRegionAttempt(region, regionPixels, "accepted_edge", false)
    }

    private fun extractLuminances(
        argb: IntArray,
        imageWidth: Int,
        roi: MangaShapeRect,
    ): IntArray {
        val luminances = IntArray(roi.width * roi.height)
        for (localY in 0 until roi.height) {
            val globalOffset = (roi.top + localY) * imageWidth + roi.left
            val localOffset = localY * roi.width
            for (localX in 0 until roi.width) {
                luminances[localOffset + localX] = luminance(argb[globalOffset + localX])
            }
        }
        return luminances
    }

    private fun extractRoiFeatures(
        argb: IntArray,
        imageWidth: Int,
        roi: MangaShapeRect,
        background: BackgroundColor,
    ): RoiFeatures {
        val luminances = IntArray(roi.width * roi.height)
        val colorDistanceSquared = IntArray(luminances.size)
        for (localY in 0 until roi.height) {
            val globalOffset = (roi.top + localY) * imageWidth + roi.left
            val localOffset = localY * roi.width
            for (localX in 0 until roi.width) {
                val localIndex = localOffset + localX
                val color = argb[globalOffset + localX]
                val red = color ushr 16 and 0xff
                val green = color ushr 8 and 0xff
                val blue = color and 0xff
                val dr = red - background.red
                val dg = green - background.green
                val db = blue - background.blue
                luminances[localIndex] = luminance(red, green, blue)
                colorDistanceSquared[localIndex] = dr * dr + dg * dg + db * db
            }
        }
        return RoiFeatures(
            luminances = luminances,
            colorDistanceSquared = colorDistanceSquared,
        )
    }

    private fun buildSeedCandidates(
        polygon: Polygon,
        imageWidth: Int,
        imageHeight: Int,
    ): MangaSeedSearchOrder {
        val bounds = clamp(polygon.bounds, imageWidth, imageHeight)
        val centerX = polygon.points.map { it.x }.average().toFloat()
        val centerY = polygon.points.map { it.y }.average().toFloat()
        val capacity = bounds.width * bounds.height
        val globalIndices = IntArray(capacity)
        val distances = FloatArray(capacity)
        var count = 0
        for (y in bounds.top until bounds.bottom) {
            for (x in bounds.left until bounds.right) {
                if (!pointInPolygon(x + 0.5f, y + 0.5f, polygon.points)) continue
                val dx = x + 0.5f - centerX
                val dy = y + 0.5f - centerY
                globalIndices[count] = y * imageWidth + x
                distances[count] = dx * dx + dy * dy
                count++
            }
        }
        return MangaSeedSearchOrder.prepare(
            imageWidth = imageWidth,
            globalIndices = globalIndices,
            distanceSquared = distances,
            count = count,
        )
    }

    private fun memberRegionCoverage(
        region: BooleanArray,
        roi: MangaShapeRect,
        members: List<Polygon>,
    ): Float {
        var memberPixels = 0
        var coveredPixels = 0
        members.forEach { polygon ->
            val bounds = polygon.bounds
            val left = max(bounds.left, roi.left)
            val top = max(bounds.top, roi.top)
            val right = minOf(bounds.right, roi.right)
            val bottom = minOf(bounds.bottom, roi.bottom)
            for (y in top until bottom) {
                for (x in left until right) {
                    if (!pointInPolygon(x + 0.5f, y + 0.5f, polygon.points)) continue
                    memberPixels++
                    if (region[(y - roi.top) * roi.width + (x - roi.left)]) {
                        coveredPixels++
                    }
                }
            }
        }
        return if (memberPixels == 0) 0f else coveredPixels.toFloat() / memberPixels
    }

    private fun floodCandidateRegion(
        candidate: BooleanArray,
        width: Int,
        height: Int,
        seeds: List<Int>,
        queue: IntArray? = null,
    ): FloodRegion {
        val region = BooleanArray(candidate.size)
        val workQueue = queue ?: IntArray(candidate.size)
        require(workQueue.size >= candidate.size)
        var head = 0
        var tail = 0
        var reachedBoundary = false
        seeds.forEach { seed ->
            if (seed in candidate.indices && candidate[seed] && !region[seed]) {
                region[seed] = true
                workQueue[tail++] = seed
            }
        }
        while (head < tail) {
            val index = workQueue[head++]
            val x = index % width
            val y = index / width
            if (x == 0 || x + 1 == width || y == 0 || y + 1 == height) {
                reachedBoundary = true
            }
            if (x > 0) tail = enqueue(index - 1, candidate, region, workQueue, tail)
            if (x + 1 < width) tail = enqueue(index + 1, candidate, region, workQueue, tail)
            if (y > 0) tail = enqueue(index - width, candidate, region, workQueue, tail)
            if (y + 1 < height) tail = enqueue(index + width, candidate, region, workQueue, tail)
        }
        return FloodRegion(
            mask = region,
            pixelCount = tail,
            touchesBoundary = reachedBoundary,
        )
    }

    private fun enqueue(
        index: Int,
        candidate: BooleanArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int,
    ): Int {
        if (!candidate[index] || visited[index]) return tail
        visited[index] = true
        queue[tail] = index
        return tail + 1
    }

    private fun fillEnclosedHoles(
        region: BooleanArray,
        width: Int,
        height: Int,
        queue: IntArray? = null,
    ): FilledRegion {
        val exterior = BooleanArray(region.size)
        val workQueue = queue ?: IntArray(region.size)
        require(workQueue.size >= region.size)
        var head = 0
        var tail = 0

        fun seed(index: Int) {
            if (!region[index] && !exterior[index]) {
                exterior[index] = true
                workQueue[tail++] = index
            }
        }
        for (x in 0 until width) {
            seed(x)
            seed((height - 1) * width + x)
        }
        for (y in 0 until height) {
            seed(y * width)
            seed(y * width + width - 1)
        }
        while (head < tail) {
            val index = workQueue[head++]
            val x = index % width
            val y = index / width
            if (x > 0) tail = enqueueComplement(index - 1, region, exterior, workQueue, tail)
            if (x + 1 < width) {
                tail = enqueueComplement(index + 1, region, exterior, workQueue, tail)
            }
            if (y > 0) tail = enqueueComplement(index - width, region, exterior, workQueue, tail)
            if (y + 1 < height) {
                tail = enqueueComplement(index + width, region, exterior, workQueue, tail)
            }
        }
        var pixelCount = 0
        val filled = BooleanArray(region.size) { index ->
            (region[index] || !exterior[index]).also { included ->
                if (included) pixelCount++
            }
        }
        return FilledRegion(filled, pixelCount)
    }

    private fun enqueueComplement(
        index: Int,
        region: BooleanArray,
        exterior: BooleanArray,
        queue: IntArray,
        tail: Int,
    ): Int {
        if (region[index] || exterior[index]) return tail
        exterior[index] = true
        queue[tail] = index
        return tail + 1
    }

    private fun touchesBoundary(mask: BooleanArray, width: Int, height: Int): Boolean {
        for (x in 0 until width) {
            if (mask[x] || mask[(height - 1) * width + x]) return true
        }
        for (y in 0 until height) {
            if (mask[y * width] || mask[y * width + width - 1]) return true
        }
        return false
    }

    private fun rejected(
        roi: MangaShapeRect,
        reason: String,
        regionPixels: Int = 0,
        attempts: Int = 1,
    ): BubbleDiagnostic = BubbleDiagnostic(
        roi = roi,
        accepted = false,
        confidence = 0f,
        reason = reason,
        regionPixels = regionPixels,
        attempts = attempts,
    )

    internal fun pointInPolygon(x: Float, y: Float, points: List<Point>): Boolean {
        var inside = false
        var previous = points.last()
        for (current in points) {
            if (
                (current.y > y) != (previous.y > y) &&
                x < (previous.x - current.x) * (y - current.y) /
                    (previous.y - current.y + 1e-6f) + current.x
            ) {
                inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun clamp(rect: MangaShapeRect, width: Int, height: Int): MangaShapeRect = MangaShapeRect(
        left = rect.left.coerceIn(0, width),
        top = rect.top.coerceIn(0, height),
        right = rect.right.coerceIn(0, width),
        bottom = rect.bottom.coerceIn(0, height),
    )

    private fun luminance(color: Int): Int = luminance(
        red = (color ushr 16) and 0xFF,
        green = (color ushr 8) and 0xFF,
        blue = color and 0xFF,
    )

    private fun luminance(red: Int, green: Int, blue: Int): Int =
        (red * 299 + green * 587 + blue * 114) / 1000

    private const val ROI_MARGIN_RATIO = 0.35f
    private const val MIN_ROI_MARGIN_PX = 32
    private const val MAX_ROI_MARGIN_PX = 160
    private const val ROI_RETRY_MARGIN_MULTIPLIER = 2f
    private const val MAX_RETRY_ROI_MARGIN_PX = 320
    private const val MIN_ROI_SIDE_PX = 12
    private const val MIN_CONTENT_LONG_SIDE_PX = 24
    private const val MIN_CONTENT_LONG_SIDE_SCREEN_DIVISOR = 40
    private const val MIN_CONTENT_AREA_PX = 256L
    private const val MIN_CONTENT_AREA_SCREEN_DIVISOR = 4_000L
    private const val SAMPLE_STRIDE_PX = 3
    private const val MIN_BACKGROUND_SAMPLES = 12
    private const val BRIGHT_SAMPLE_START_RATIO = 0.45f
    private const val BACKGROUND_LOW_SAMPLE_RATIO = 0.30f
    private const val MIN_BACKGROUND_LUMINANCE = 72
    private const val MIN_CANDIDATE_LUMINANCE = 40
    private const val SAMPLED_LUMINANCE_FLOOR_TOLERANCE = 24
    private val CANDIDATE_PROFILES = listOf(
        CandidateProfile(
            maxColorDistance = 165f,
            maxLuminanceDrop = 110,
        ),
        CandidateProfile(
            maxColorDistance = 225f,
            maxLuminanceDrop = 0,
            useSampledLuminanceFloor = true,
        ),
    )
    private const val MIN_REGION_PIXELS = 24
    private const val MIN_MEMBER_REGION_COVERAGE = 0.72f
    private const val EDGE_DARK_BACKGROUND_GAP = 32
    private const val EDGE_MIN_DARK_THRESHOLD = 24
    private const val EDGE_MAX_DARK_THRESHOLD = 64
    private const val EDGE_GRADIENT_THRESHOLD = 72
    private const val EDGE_BARRIER_DILATION_PX = 1
    private const val EDGE_PREFERRED_SIZE_RATIO = 1.05f
    private const val IDEAL_REGION_ROI_RATIO = 0.35f
    private const val MAX_REGION_ROI_RATIO = 0.82f
}
