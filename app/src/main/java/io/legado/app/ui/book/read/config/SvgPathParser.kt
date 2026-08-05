package io.legado.app.ui.book.read.config

import android.graphics.Path
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object SvgPathParser {

    private val cache = android.util.LruCache<String, Path>(32)

    fun parse(svgPath: String): Path? {
        if (svgPath.isBlank()) return null
        cache.get(svgPath)?.let { return it }
        val path = parseInternal(svgPath) ?: return null
        cache.put(svgPath, path)
        return path
    }

    private fun parseInternal(svgPath: String): Path? {
        if (svgPath.isBlank()) return null

        val path = Path()
        val tokens = tokenize(svgPath)
        if (tokens.isEmpty()) return null

        var currentX = 0f
        var currentY = 0f
        var startX = 0f
        var startY = 0f
        var lastControlX = 0f
        var lastControlY = 0f
        var lastCommand = ""
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]

            when (token) {
                "M" -> {
                    index++
                    if (index + 1 < tokens.size) {
                        currentX = tokens[index].toFloatOrNull() ?: currentX
                        currentY = tokens[index + 1].toFloatOrNull() ?: currentY
                        startX = currentX
                        startY = currentY
                        path.moveTo(currentX, currentY)
                        index += 2
                    }
                    lastCommand = "M"
                }
                "m" -> {
                    index++
                    if (index + 1 < tokens.size) {
                        val dx = tokens[index].toFloatOrNull() ?: 0f
                        val dy = tokens[index + 1].toFloatOrNull() ?: 0f
                        currentX += dx
                        currentY += dy
                        startX = currentX
                        startY = currentY
                        path.moveTo(currentX, currentY)
                        index += 2
                    }
                    lastCommand = "m"
                }
                "L" -> {
                    index++
                    while (index + 1 < tokens.size && !isCommand(tokens[index])) {
                        currentX = tokens[index].toFloatOrNull() ?: currentX
                        currentY = tokens[index + 1].toFloatOrNull() ?: currentY
                        path.lineTo(currentX, currentY)
                        index += 2
                    }
                    lastCommand = "L"
                }
                "l" -> {
                    index++
                    while (index + 1 < tokens.size && !isCommand(tokens[index])) {
                        val dx = tokens[index].toFloatOrNull() ?: 0f
                        val dy = tokens[index + 1].toFloatOrNull() ?: 0f
                        currentX += dx
                        currentY += dy
                        path.lineTo(currentX, currentY)
                        index += 2
                    }
                    lastCommand = "l"
                }
                "H" -> {
                    index++
                    while (index < tokens.size && !isCommand(tokens[index])) {
                        currentX = tokens[index].toFloatOrNull() ?: currentX
                        path.lineTo(currentX, currentY)
                        index++
                    }
                    lastCommand = "H"
                }
                "h" -> {
                    index++
                    while (index < tokens.size && !isCommand(tokens[index])) {
                        val dx = tokens[index].toFloatOrNull() ?: 0f
                        currentX += dx
                        path.lineTo(currentX, currentY)
                        index++
                    }
                    lastCommand = "h"
                }
                "V" -> {
                    index++
                    while (index < tokens.size && !isCommand(tokens[index])) {
                        currentY = tokens[index].toFloatOrNull() ?: currentY
                        path.lineTo(currentX, currentY)
                        index++
                    }
                    lastCommand = "V"
                }
                "v" -> {
                    index++
                    while (index < tokens.size && !isCommand(tokens[index])) {
                        val dy = tokens[index].toFloatOrNull() ?: 0f
                        currentY += dy
                        path.lineTo(currentX, currentY)
                        index++
                    }
                    lastCommand = "v"
                }
                "C" -> {
                    index++
                    while (index + 5 < tokens.size && !isCommand(tokens[index])) {
                        val x1 = tokens[index].toFloatOrNull() ?: currentX
                        val y1 = tokens[index + 1].toFloatOrNull() ?: currentY
                        val x2 = tokens[index + 2].toFloatOrNull() ?: currentX
                        val y2 = tokens[index + 3].toFloatOrNull() ?: currentY
                        val x = tokens[index + 4].toFloatOrNull() ?: currentX
                        val y = tokens[index + 5].toFloatOrNull() ?: currentY
                        path.cubicTo(x1, y1, x2, y2, x, y)
                        lastControlX = x2
                        lastControlY = y2
                        currentX = x
                        currentY = y
                        index += 6
                    }
                    lastCommand = "C"
                }
                "c" -> {
                    index++
                    while (index + 5 < tokens.size && !isCommand(tokens[index])) {
                        val x1 = currentX + (tokens[index].toFloatOrNull() ?: 0f)
                        val y1 = currentY + (tokens[index + 1].toFloatOrNull() ?: 0f)
                        val x2 = currentX + (tokens[index + 2].toFloatOrNull() ?: 0f)
                        val y2 = currentY + (tokens[index + 3].toFloatOrNull() ?: 0f)
                        val x = currentX + (tokens[index + 4].toFloatOrNull() ?: 0f)
                        val y = currentY + (tokens[index + 5].toFloatOrNull() ?: 0f)
                        path.cubicTo(x1, y1, x2, y2, x, y)
                        lastControlX = x2
                        lastControlY = y2
                        currentX = x
                        currentY = y
                        index += 6
                    }
                    lastCommand = "c"
                }
                "S" -> {
                    index++
                    while (index + 3 < tokens.size && !isCommand(tokens[index])) {
                        val x2 = tokens[index].toFloatOrNull() ?: currentX
                        val y2 = tokens[index + 1].toFloatOrNull() ?: currentY
                        val x = tokens[index + 2].toFloatOrNull() ?: currentX
                        val y = tokens[index + 3].toFloatOrNull() ?: currentY
                        val x1 = if (lastCommand == "C" || lastCommand == "c" || lastCommand == "S" || lastCommand == "s") {
                            2 * currentX - lastControlX
                        } else {
                            currentX
                        }
                        val y1 = if (lastCommand == "C" || lastCommand == "c" || lastCommand == "S" || lastCommand == "s") {
                            2 * currentY - lastControlY
                        } else {
                            currentY
                        }
                        path.cubicTo(x1, y1, x2, y2, x, y)
                        lastControlX = x2
                        lastControlY = y2
                        currentX = x
                        currentY = y
                        index += 4
                    }
                    lastCommand = "S"
                }
                "s" -> {
                    index++
                    while (index + 3 < tokens.size && !isCommand(tokens[index])) {
                        val x2 = currentX + (tokens[index].toFloatOrNull() ?: 0f)
                        val y2 = currentY + (tokens[index + 1].toFloatOrNull() ?: 0f)
                        val x = currentX + (tokens[index + 2].toFloatOrNull() ?: 0f)
                        val y = currentY + (tokens[index + 3].toFloatOrNull() ?: 0f)
                        val x1 = if (lastCommand == "C" || lastCommand == "c" || lastCommand == "S" || lastCommand == "s") {
                            2 * currentX - lastControlX
                        } else {
                            currentX
                        }
                        val y1 = if (lastCommand == "C" || lastCommand == "c" || lastCommand == "S" || lastCommand == "s") {
                            2 * currentY - lastControlY
                        } else {
                            currentY
                        }
                        path.cubicTo(x1, y1, x2, y2, x, y)
                        lastControlX = x2
                        lastControlY = y2
                        currentX = x
                        currentY = y
                        index += 4
                    }
                    lastCommand = "s"
                }
                "Q" -> {
                    index++
                    while (index + 3 < tokens.size && !isCommand(tokens[index])) {
                        val x1 = tokens[index].toFloatOrNull() ?: currentX
                        val y1 = tokens[index + 1].toFloatOrNull() ?: currentY
                        val x = tokens[index + 2].toFloatOrNull() ?: currentX
                        val y = tokens[index + 3].toFloatOrNull() ?: currentY
                        path.quadTo(x1, y1, x, y)
                        lastControlX = x1
                        lastControlY = y1
                        currentX = x
                        currentY = y
                        index += 4
                    }
                    lastCommand = "Q"
                }
                "q" -> {
                    index++
                    while (index + 3 < tokens.size && !isCommand(tokens[index])) {
                        val x1 = currentX + (tokens[index].toFloatOrNull() ?: 0f)
                        val y1 = currentY + (tokens[index + 1].toFloatOrNull() ?: 0f)
                        val x = currentX + (tokens[index + 2].toFloatOrNull() ?: 0f)
                        val y = currentY + (tokens[index + 3].toFloatOrNull() ?: 0f)
                        path.quadTo(x1, y1, x, y)
                        lastControlX = x1
                        lastControlY = y1
                        currentX = x
                        currentY = y
                        index += 4
                    }
                    lastCommand = "q"
                }
                "A", "a" -> {
                    val isRelative = token == "a"
                    index++
                    while (index + 6 < tokens.size && !isCommand(tokens[index])) {
                        val arcRx = abs(tokens[index].toFloatOrNull() ?: 0f)
                        val arcRy = abs(tokens[index + 1].toFloatOrNull() ?: 0f)
                        val xAxisRotation = tokens[index + 2].toFloatOrNull() ?: 0f
                        val largeArcFlag = tokens[index + 3].toIntOrNull() ?: 0
                        val sweepFlag = tokens[index + 4].toIntOrNull() ?: 0
                        val x = if (isRelative) currentX + (tokens[index + 5].toFloatOrNull() ?: 0f) else tokens[index + 5].toFloatOrNull() ?: currentX
                        val y = if (isRelative) currentY + (tokens[index + 6].toFloatOrNull() ?: 0f) else tokens[index + 6].toFloatOrNull() ?: currentY

                        drawArc(path, currentX, currentY, x, y, arcRx, arcRy, xAxisRotation, largeArcFlag == 1, sweepFlag == 1)
                        currentX = x
                        currentY = y
                        index += 7
                    }
                    lastCommand = token
                }
                "Z", "z" -> {
                    path.close()
                    currentX = startX
                    currentY = startY
                    index++
                    lastCommand = token
                }
                else -> {
                    index++
                }
            }
        }

        return path
    }

    private fun tokenize(svgPath: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0

        while (i < svgPath.length) {
            val c = svgPath[i]

            when {
                c.isWhitespace() || c == ',' -> {
                    if (sb.isNotEmpty()) {
                        tokens.add(sb.toString())
                        sb.clear()
                    }
                }
                c.isLetter() -> {
                    if (sb.isNotEmpty()) {
                        tokens.add(sb.toString())
                        sb.clear()
                    }
                    tokens.add(c.toString())
                }
                c == '-' -> {
                    if (sb.isNotEmpty() && !sb.endsWith('e', ignoreCase = true)) {
                        tokens.add(sb.toString())
                        sb.clear()
                    }
                    sb.append(c)
                }
                c == '.' -> {
                    if (sb.contains('.')) {
                        tokens.add(sb.toString())
                        sb.clear()
                    }
                    sb.append(c)
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }

        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }

        return tokens
    }

    private fun isCommand(token: String): Boolean {
        return token.length == 1 && token[0].isLetter()
    }

    private fun drawArc(
        path: Path,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        arcRx: Float, arcRy: Float,
        phi: Float,
        largeArc: Boolean,
        sweep: Boolean
    ) {
        if (arcRx == 0f || arcRy == 0f) {
            path.lineTo(x2, y2)
            return
        }

        var localRx = arcRx
        var localRy = arcRy

        val phiRad = Math.toRadians(phi.toDouble())
        val cosPhi = cos(phiRad).toFloat()
        val sinPhi = sin(phiRad).toFloat()

        val dx = (x1 - x2) / 2f
        val dy = (y1 - y2) / 2f

        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy

        var rxSq = localRx * localRx
        var rySq = localRy * localRy
        val x1pSq = x1p * x1p
        val y1pSq = y1p * y1p

        var cr = x1pSq / rxSq + y1pSq / rySq
        if (cr > 1f) {
            val sqrtCr = kotlin.math.sqrt(cr.toDouble()).toFloat()
            localRx *= sqrtCr
            localRy *= sqrtCr
            rxSq = localRx * localRx
            rySq = localRy * localRy
        }

        val rq = rxSq * rySq - rxSq * y1pSq - rySq * x1pSq
        val cq = rxSq * y1pSq + rySq * x1pSq
        val sqrtVal = kotlin.math.sqrt(kotlin.math.max(0.0, rq.toDouble()) / kotlin.math.max(1e-10, cq.toDouble())).toFloat()
        val sign = if (largeArc != sweep) 1f else -1f
        val cxp = sign * sqrtVal * (localRx * y1p / localRy)
        val cyp = -sign * sqrtVal * (localRy * x1p / localRx)

        val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2f
        val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2f

        val theta1 = angle(1f, 0f, (x1p - cxp) / localRx, (y1p - cyp) / localRy)
        var dtheta = angle(
            (x1p - cxp) / localRx, (y1p - cyp) / localRy,
            (-x1p - cxp) / localRx, (-y1p - cyp) / localRy
        )

        if (!sweep && dtheta > 0) dtheta -= 360f
        if (sweep && dtheta < 0) dtheta += 360f

        val sweepAngle = dtheta
        path.arcTo(
            android.graphics.RectF(cx - localRx, cy - localRy, cx + localRx, cy + localRy),
            theta1,
            sweepAngle,
            false
        )
    }

    private fun angle(ux: Float, uy: Float, vx: Float, vy: Float): Float {
        val n = kotlin.math.sqrt((ux * ux + uy * uy).toDouble()) * kotlin.math.sqrt((vx * vx + vy * vy).toDouble())
        val c = (ux * vx + uy * vy) / kotlin.math.max(n, 1e-10)
        val angle = Math.toDegrees(kotlin.math.acos(c.coerceIn(-1.0, 1.0)))
        return if (ux * vy - uy * vx < 0) -angle.toFloat() else angle.toFloat()
    }
}
