package io.legado.app.ui.book.read.page.entities.column

import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.Keep
import androidx.core.net.toUri
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextLine.Companion.emptyTextLine
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.isContentScheme
import splitties.init.appCtx
import java.io.File

@Keep
data class TextColumn(
    override var start: Float,
    override var end: Float,
    override val charData: String,
    var color: Int? = null,
    var fontPath: String? = null,
) : TextBaseColumn {

    override var textLine: TextLine = emptyTextLine

    override var selected: Boolean = false
        set(value) {
            if (field != value) {
                textLine.invalidate()
            }
            field = value
        }
    override var isSearchResult: Boolean = false
        set(value) {
            if (field != value) {
                textLine.invalidate()
                if (value) {
                    textLine.searchResultColumnCount++
                } else {
                    textLine.searchResultColumnCount--
                }
            }
            field = value
        }

    override fun draw(view: ContentTextView, canvas: Canvas) {
        val textPaint = if (textLine.isTitle) {
            ChapterProvider.titlePaint
        } else {
            ChapterProvider.contentPaint
        }
        val textColor = color ?: if (!textLine.useUnderline && (textLine.isReadAloud || isSearchResult)) {
            ReadBookConfig.textAccentColor
        } else if (textLine.isTitle && ReadBookConfig.titleColor != 0) {
            ReadBookConfig.titleColor
        } else {
            ReadBookConfig.textColor
        }
        val needRestoreSize = textLine.titleTextSize != null
        val needRestoreColor = textPaint.color != textColor
        val customTypeface = fontPath?.let { getTypeface(it) }
        val needRestoreTypeface = customTypeface != null
        if (needRestoreSize) {
            val originalSize = textPaint.textSize
            textPaint.textSize = textLine.titleTextSize!!
            if (needRestoreColor) textPaint.color = textColor
            if (needRestoreTypeface) textPaint.typeface = customTypeface
            val y = textLine.lineBase - textLine.lineTop
            drawText(canvas, y, textPaint)
            textPaint.textSize = originalSize
        } else if (needRestoreColor || needRestoreTypeface) {
            val originalColor = textPaint.color
            val originalTypeface = textPaint.typeface
            if (needRestoreColor) textPaint.color = textColor
            if (needRestoreTypeface) textPaint.typeface = customTypeface
            val y = textLine.lineBase - textLine.lineTop
            drawText(canvas, y, textPaint)
            if (needRestoreColor) textPaint.color = originalColor
            if (needRestoreTypeface) textPaint.typeface = originalTypeface
        } else {
            val y = textLine.lineBase - textLine.lineTop
            drawText(canvas, y, textPaint)
        }
        if (selected) {
            canvas.drawRect(start, 0f, end, textLine.height, view.selectedPaint)
        }
    }

    private fun drawText(canvas: Canvas, y: Float, textPaint: android.text.TextPaint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val letterSpacing = textPaint.letterSpacing * textPaint.textSize
            val letterSpacingHalf = letterSpacing * 0.5f
            canvas.drawText(charData, start + letterSpacingHalf, y, textPaint)
        } else {
            canvas.drawText(charData, start, y, textPaint)
        }
    }

    companion object {
        private val typefaceCache = mutableMapOf<String, Typeface?>()

        private fun getTypeface(fontPath: String): Typeface? {
            return typefaceCache.getOrPut(fontPath) {
                kotlin.runCatching {
                    when {
                        fontPath.isContentScheme() -> {
                            appCtx.contentResolver
                                .openFileDescriptor(fontPath.toUri(), "r")!!
                                .use {
                                    Typeface.Builder(it.fileDescriptor).build()
                                }
                        }

                        fontPath.isNotEmpty() -> {
                            Typeface.Builder(File(fontPath)).build()
                        }

                        else -> null
                    }
                }.getOrNull()
            }
        }
    }

}
