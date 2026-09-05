package io.legado.app.domain.model

class TranslationResultPreviewParser {

    private val raw = StringBuilder()
    private var resultStart = -1
    private var scanIndex = 0
    private var lastPublishedBoundaryEnd = 0
    private var lastSnapshot: String? = null

    fun feed(delta: String): List<String> {
        if (delta.isEmpty()) return emptyList()
        raw.append(delta)
        findResultMarker()
        if (resultStart < 0) return emptyList()
        return consumeBoundaries(final = false)
    }

    fun finish(): List<String> {
        findResultMarker()
        if (resultStart < 0) return emptyList()
        val snapshots = consumeBoundaries(final = true).toMutableList()
        val finalSnapshot = normalizeSnapshot(raw.substring(resultStart))
        if (finalSnapshot.isNotEmpty() && finalSnapshot != lastSnapshot) {
            lastSnapshot = finalSnapshot
            snapshots += finalSnapshot
        }
        return snapshots
    }

    private fun findResultMarker() {
        if (resultStart >= 0) return
        val markerIndex = raw.indexOf(RESULT_MARKER, ignoreCase = true)
        if (markerIndex >= 0) {
            resultStart = markerIndex + RESULT_MARKER.length
            scanIndex = resultStart
            lastPublishedBoundaryEnd = resultStart
        }
    }

    private fun consumeBoundaries(final: Boolean): List<String> {
        val snapshots = mutableListOf<String>()
        while (scanIndex < raw.length) {
            val current = raw[scanIndex]
            val boundaryEnd = when (current) {
                '\n' -> scanIndex + 1
                '\r' -> when {
                    scanIndex + 1 < raw.length && raw[scanIndex + 1] == '\n' -> scanIndex + 2
                    scanIndex + 1 < raw.length || final -> scanIndex + 1
                    else -> break
                }
                else -> {
                    scanIndex++
                    continue
                }
            }
            val pendingCharCount = scanIndex - lastPublishedBoundaryEnd
            val snapshot = if (pendingCharCount >= MIN_PREVIEW_BATCH_CHARS) {
                normalizeSnapshot(raw.substring(resultStart, scanIndex))
            } else {
                null
            }
            scanIndex = boundaryEnd
            if (!snapshot.isNullOrEmpty() && snapshot != lastSnapshot) {
                lastSnapshot = snapshot
                lastPublishedBoundaryEnd = boundaryEnd
                snapshots += snapshot
            }
        }
        return snapshots
    }

    private fun normalizeSnapshot(value: String): String =
        value.replace("\r\n", "\n").replace('\r', '\n').trim()

    private companion object {
        const val RESULT_MARKER = "[result]"
        const val MIN_PREVIEW_BATCH_CHARS = 1_024
    }
}
