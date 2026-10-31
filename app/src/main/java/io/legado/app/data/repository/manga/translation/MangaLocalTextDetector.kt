package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.legado.app.domain.model.manga.MangaTextBox
import io.legado.app.domain.model.manga.MangaTranslatedRegion
import io.legado.app.domain.model.manga.MangaSourceLine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal object MangaLocalTextDetector {
    suspend fun detect(bitmap: Bitmap, language: String): List<MangaTranslatedRegion> {
        val recognizer = when (language) {
            "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            "en" -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            else -> error("Unsupported local recognition language: $language")
        }
        try {
            // ML Kit's native task cannot be cancelled. Retain the input bitmap and recognizer
            // until it actually finishes, then observe coroutine cancellation before publication.
            val text = suspendCoroutine<Text> { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            currentCoroutineContext().ensureActive()
            fun normalized(rect: Rect) = MangaTextBox(
                (rect.left * 1000f / bitmap.width).coerceIn(0f, 999f),
                (rect.top * 1000f / bitmap.height).coerceIn(0f, 999f),
                (rect.right * 1000f / bitmap.width).coerceIn(1f, 1000f),
                (rect.bottom * 1000f / bitmap.height).coerceIn(1f, 1000f),
            )
            return text.textBlocks.filter { it.text.isNotBlank() && it.boundingBox != null }.mapIndexed { i, block ->
                val rect = checkNotNull(block.boundingBox)
                val box = normalized(rect)
                val lines = block.lines.mapNotNull { it.boundingBox }
                MangaTranslatedRegion(
                    id = "r$i", original = block.text, translation = block.text,
                    textBox = box, layoutBox = box,
                    eraseBoxes = lines.map(::normalized).ifEmpty { listOf(box) },
                    vertical = mangaVerticalText(lines),
                    sourceLines = block.lines.filter { it.text.isNotBlank() && it.boundingBox != null }
                        .take(128).map { line ->
                            val quad = line.cornerPoints?.takeIf { it.size == 4 }?.let { points ->
                                mangaSourceQuad(FloatArray(8) { i -> if (i % 2 == 0) points[i / 2].x.toFloat() else points[i / 2].y.toFloat() }, bitmap.width, bitmap.height)
                            }
                            MangaSourceLine(line.text, normalized(checkNotNull(line.boundingBox)), quad)
                        },
                )
            }
        } finally { recognizer.close() }
    }
}

/** A tall paragraph of horizontal lines is not vertical lettering. */
internal fun mangaVerticalText(lines: List<Rect>): Boolean = lines.isNotEmpty() &&
    lines.count { it.height() > it.width() * 1.4f } > lines.size / 2
