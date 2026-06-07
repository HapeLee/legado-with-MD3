package io.legado.app.ui.book.read.page.entities.column

import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.Keep
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextLine.Companion.emptyTextLine
import io.legado.app.ui.book.read.page.provider.ChapterProvider

@Keep
data class TextColumn(
    override var start: Float,
    override var end: Float,
    override val charData: String,
    override val textColor: Int? = null,
    override val bgColor: Int? = null,
    override val underlineMode: Int = 0,
    override val underlineColor: Int? = null,
    override val underlineWidth: Float = 1f,
    override val underlineOffset: Float = 2f,
    override val underlineSvgPath: String = "",
    override val bgImage: String = "",
    override val bgImageFit: Int = 0,
    override val bgImageScale: Float = 1f,
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
        val drawColor = if (textLine.isReadAloud || isSearchResult) {
            ReadBookConfig.textAccentColor
        } else {
            textColor ?: if (textLine.isTitle && ReadBookConfig.titleColor != 0) {
                ReadBookConfig.titleColor
            } else {
                ReadBookConfig.textColor
            }
        }
        val needRestoreSize = textLine.titleTextSize != null
        val needRestoreColor = textPaint.color != drawColor
        val customTypeface = getCustomTypeface()
        val needRestoreTypeface = customTypeface != null
        if (needRestoreSize) {
            val originalSize = textPaint.textSize
            textPaint.textSize = textLine.titleTextSize!!
            if (needRestoreColor) textPaint.color = drawColor
            if (needRestoreTypeface) textPaint.typeface = customTypeface
            val y = textLine.lineBase - textLine.lineTop
            drawText(canvas, y, textPaint)
            textPaint.textSize = originalSize
        } else if (needRestoreColor || needRestoreTypeface) {
            val originalColor = textPaint.color
            val originalTypeface = textPaint.typeface
            if (needRestoreColor) textPaint.color = drawColor
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

    private fun getCustomTypeface(): Typeface? {
        // TODO: HighlightRule 不再存储 fontPath，需要重新设计自定义字体方案
        return null
    }
}
