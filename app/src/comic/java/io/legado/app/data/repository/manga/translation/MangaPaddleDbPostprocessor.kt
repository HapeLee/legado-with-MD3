// SPDX-License-Identifier: Apache-2.0
// Adapted from ciddwd/overlay-translator (64a7aa2); optional column splitting omitted.
package io.legado.app.data.repository.manga.translation

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * DBNet 后处理纯 Kotlin 实现，对齐 PaddleOCR 官方流水线：
 *   prob_map → bin → BFS 联通域 → 边界点 → 凸包 → 最小外接旋转矩形 → box 得分复核 → unclip 外推。
 *
 * No OpenCV/pyclipper runtime dependency. Weak probability bridges may still join nearby columns.
 */
internal object MangaPaddleDbPostprocessor {

    /**
     * 旋转矩形 4 个角，按 left-top / right-top / right-bottom / left-bottom 排（针对
     * 接近水平的文字行）。对倾斜矩形，p0->p1 方向就是文字基线方向。
     */
    data class Quad @JvmOverloads constructor(
        val p0: PointF, val p1: PointF, val p2: PointF, val p3: PointF,
        // Unexpanded detector support, distinct from the recognition crop's padding.
        val support: Quad? = null,
    ) {
        val centerX: Float get() = (p0.x + p1.x + p2.x + p3.x) / 4f
        val centerY: Float get() = (p0.y + p1.y + p2.y + p3.y) / 4f
        val width: Float get() = hypot(p1.x - p0.x, p1.y - p0.y)
        val height: Float get() = hypot(p3.x - p0.x, p3.y - p0.y)

        fun axisAlignedBounds(): IntArray {
            val xs = floatArrayOf(p0.x, p1.x, p2.x, p3.x)
            val ys = floatArrayOf(p0.y, p1.y, p2.y, p3.y)
            return intArrayOf(xs.min().toInt(), ys.min().toInt(), xs.max().toInt(), ys.max().toInt())
        }
    }

    private data class IntPoint(val x: Int, val y: Int)

    /**
     * 主入口：从 prob_map 提取所有候选 Quad（已映射到原图坐标系）。
     *
     * @param probMap DBNet 输出 [H][W] 概率图
     * @param scaleX 把 prob_map X 坐标还原到原图坐标
     * @param scaleY 把 prob_map Y 坐标还原到原图坐标
     * @param binThresh 二值化阈值
     * @param scoreThresh box 平均概率阈值（低于丢）
     * @param unclipRatio 外扩比例（pyclipper unclip 公式：dist = area * ratio / perim）
     * @param minSide 旋转矩形短边阈值（prob_map 尺度下像素）
     */
    @JvmOverloads
    fun extractQuads(
        probMap: Array<FloatArray>,
        scaleX: Float,
        scaleY: Float,
        binThresh: Float,
        scoreThresh: Float,
        unclipRatio: Float,
        minSide: Int = 3,
        retainSupport: Boolean = false,
    ): List<Quad> {
        val h = probMap.size
        val w = probMap[0].size
        val visited = Array(h) { BooleanArray(w) }
        val result = mutableListOf<Quad>()

        for (y0 in 0 until h) {
            for (x0 in 0 until w) {
                if (visited[y0][x0] || probMap[y0][x0] < binThresh) continue
                val component = mutableListOf<IntPoint>()
                val boundary = mutableListOf<IntPoint>()
                val stack = ArrayDeque<IntArray>()
                stack.addLast(intArrayOf(x0, y0))
                visited[y0][x0] = true
                while (stack.isNotEmpty()) {
                    val (cx, cy) = stack.removeLast()
                    component.add(IntPoint(cx, cy))
                    // 该点 8-邻接里有任何超出 binThresh 的"边界（与背景接触）"点，则记入 boundary
                    var isBoundary = false
                    for ((dx, dy) in NEIGHBORS_8) {
                        val nx = cx + dx; val ny = cy + dy
                        if (nx !in 0 until w || ny !in 0 until h) { isBoundary = true; continue }
                        if (probMap[ny][nx] < binThresh) { isBoundary = true; continue }
                        if (!visited[ny][nx]) {
                            visited[ny][nx] = true
                            stack.addLast(intArrayOf(nx, ny))
                        }
                    }
                    if (isBoundary) boundary.add(IntPoint(cx, cy))
                }
                if (component.size < 16) continue
                val hull = convexHull(if (boundary.isNotEmpty()) boundary else component)
                if (hull.size < 3) continue
                val quad = minAreaRect(hull) ?: continue
                val side = minOf(quad.width, quad.height)
                if (side < minSide) continue
                val score = boxScore(probMap, quad)
                if (score < scoreThresh) continue
                val unclipped = unclip(quad, unclipRatio)
                fun mapped(q: Quad) = Quad(
                    PointF(q.p0.x * scaleX, q.p0.y * scaleY), PointF(q.p1.x * scaleX, q.p1.y * scaleY),
                    PointF(q.p2.x * scaleX, q.p2.y * scaleY), PointF(q.p3.x * scaleX, q.p3.y * scaleY),
                )
                result.add(mapped(unclipped).copy(support = if (retainSupport) mapped(quad) else null))
            }
        }
        return result
    }

    /**
     * PaddleX recognition-crop geometry: DBPostProcess rounds every corner and clamps it to
     * [0, width] x [0, height]; CropByPolys then refits `cv2.minAreaRect` on the integer corners
     * and orders them LT, RT, RB, LB. Returns null when the clamped box is degenerate.
     */
    internal fun fitRecognitionCropQuad(quad: Quad, imageWidth: Int, imageHeight: Int): Quad? {
        val corners = listOf(quad.p0, quad.p1, quad.p2, quad.p3).map { point ->
            IntPoint(
                Math.round(point.x).coerceIn(0, imageWidth),
                Math.round(point.y).coerceIn(0, imageHeight),
            )
        }
        return minAreaRect(convexHull(corners))?.copy(support = quad.support)
    }

    /** Andrew monotone chain 凸包，返回逆时针顺序点集。 */
    private fun convexHull(pts: List<IntPoint>): List<IntPoint> {
        if (pts.size <= 2) return pts
        val sorted = pts.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (sorted.size <= 2) return sorted
        val hull = mutableListOf<IntPoint>()
        // 下半
        for (p in sorted) {
            while (hull.size >= 2 && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0) {
                hull.removeAt(hull.size - 1)
            }
            hull.add(p)
        }
        val lower = hull.size + 1
        // 上半
        for (i in sorted.size - 2 downTo 0) {
            val p = sorted[i]
            while (hull.size >= lower && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0) {
                hull.removeAt(hull.size - 1)
            }
            hull.add(p)
        }
        hull.removeAt(hull.size - 1)
        return hull
    }

    private fun cross(o: IntPoint, a: IntPoint, b: IntPoint): Long =
        (a.x - o.x).toLong() * (b.y - o.y) - (a.y - o.y).toLong() * (b.x - o.x)

    /**
     * Rotating calipers 找最小面积外接矩形。
     * 思路：最小外接矩形必有一边与凸包某条边重合，枚举凸包每条边，按该方向投影所有点找
     * axis-aligned bbox，取面积最小的。
     */
    private fun minAreaRect(hull: List<IntPoint>): Quad? {
        if (hull.size < 3) return null
        var bestArea = Double.MAX_VALUE
        var bestQuad: Quad? = null
        val n = hull.size
        for (i in 0 until n) {
            val a = hull[i]
            val b = hull[(i + 1) % n]
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-6) continue
            val ux = dx / len; val uy = dy / len      // 沿边方向单位向量
            val vx = -uy; val vy = ux                 // 垂直方向单位向量（逆时针 90 度）
            var minU = Double.MAX_VALUE; var maxU = -Double.MAX_VALUE
            var minV = Double.MAX_VALUE; var maxV = -Double.MAX_VALUE
            for (p in hull) {
                val px = p.x.toDouble(); val py = p.y.toDouble()
                val u = px * ux + py * uy
                val v = px * vx + py * vy
                if (u < minU) minU = u
                if (u > maxU) maxU = u
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }
            val w = maxU - minU; val h = maxV - minV
            val area = w * h
            if (area < bestArea && w > 1 && h > 1) {
                bestArea = area
                fun toXY(u: Double, v: Double) = PointF(
                    (u * ux + v * vx).toFloat(),
                    (u * uy + v * vy).toFloat()
                )
                val c00 = toXY(minU, minV)
                val c10 = toXY(maxU, minV)
                val c11 = toXY(maxU, maxV)
                val c01 = toXY(minU, maxV)
                // 按 left-top / right-top / right-bottom / left-bottom 排序，规则同 PaddleOCR 官方
                bestQuad = sortCornersLikePaddle(c00, c10, c11, c01)
            }
        }
        return bestQuad
    }

    /**
     * PaddleOCR 官方的 4 角排序方法：先按 x 升序排，取前 2 为左、后 2 为右；
     * 左中按 y 升序得到 LT/LB，右中按 y 升序得到 RT/RB。最终顺序：LT, RT, RB, LB。
     */
    private fun sortCornersLikePaddle(a: PointF, b: PointF, c: PointF, d: PointF): Quad {
        val sorted = listOf(a, b, c, d).sortedBy { it.x }
        val leftPair = sorted.take(2).sortedBy { it.y }
        val rightPair = sorted.drop(2).sortedBy { it.y }
        return Quad(leftPair[0], rightPair[0], rightPair[1], leftPair[1])
    }

    /** prob_map 内 quad 多边形内的像素平均概率（用扫描线快速做）。 */
    private fun boxScore(probMap: Array<FloatArray>, quad: Quad): Float {
        val bounds = quad.axisAlignedBounds()
        val h = probMap.size; val w = probMap[0].size
        val x0 = bounds[0].coerceAtLeast(0)
        val y0 = bounds[1].coerceAtLeast(0)
        val x1 = bounds[2].coerceAtMost(w - 1)
        val y1 = bounds[3].coerceAtMost(h - 1)
        if (x1 <= x0 || y1 <= y0) return 0f
        var sum = 0.0
        var count = 0
        val poly = listOf(quad.p0, quad.p1, quad.p2, quad.p3)
        for (y in y0..y1) for (x in x0..x1) {
            if (pointInQuad(x.toFloat() + 0.5f, y.toFloat() + 0.5f, poly)) {
                sum += probMap[y][x]
                count++
            }
        }
        return if (count == 0) 0f else (sum / count).toFloat()
    }

    /** 射线法判断点是否在 4 点多边形内。 */
    private fun pointInQuad(x: Float, y: Float, poly: List<PointF>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val xi = poly[i].x; val yi = poly[i].y
            val xj = poly[j].x; val yj = poly[j].y
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi + 1e-9f) + xi)) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * 旋转矩形向外扩展：dist = area * ratio / perim，与官方 pyclipper JT_ROUND 偏移后再取
     * get_mini_boxes 的结果等价（圆角偏移的最小外接矩形即各边外推 dist）。官方没有上限。
     * 实现：沿矩形 u (=p0p1) / v (=p0p3) 方向各扩展 dist。
     */
    internal fun unclip(quad: Quad, ratio: Float): Quad {
        val w = quad.width
        val h = quad.height
        if (w <= 1f || h <= 1f) return quad
        val area = w * h
        val perim = 2 * (w + h)
        val dist = area * ratio / perim
        // u 方向（沿 p0->p1）
        val ux = (quad.p1.x - quad.p0.x) / w
        val uy = (quad.p1.y - quad.p0.y) / w
        // v 方向（沿 p0->p3）
        val vx = (quad.p3.x - quad.p0.x) / h
        val vy = (quad.p3.y - quad.p0.y) / h
        fun shift(p: PointF, du: Float, dv: Float) = PointF(
            p.x + du * ux + dv * vx,
            p.y + du * uy + dv * vy
        )
        return Quad(
            shift(quad.p0, -dist, -dist),
            shift(quad.p1, dist, -dist),
            shift(quad.p2, dist, dist),
            shift(quad.p3, -dist, dist)
        )
    }

    private val NEIGHBORS_8 = arrayOf(
        intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1),
        intArrayOf(-1, 0),                       intArrayOf(1, 0),
        intArrayOf(-1, 1),  intArrayOf(0, 1),  intArrayOf(1, 1)
    )
}
