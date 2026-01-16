package io.legado.app.ui.book.read.page.entities

import android.graphics.Canvas
import android.os.Build
import android.text.TextPaint
import androidx.annotation.Keep
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.TextBaseColumn
import io.legado.app.ui.book.read.page.entities.TextLine.Companion.emptyTextLine
import io.legado.app.ui.book.read.page.provider.ChapterProvider

/**
 * 带html样式的文字列
 */
@Keep
data class TextHtmlColumn(
    override var start: Float,
    override var end: Float,
    override val charData: String,
    val mTextSize: Float,
    val mTextColor: Int,
    val linkUrl: String?
) : TextBaseColumn {

    override var textLine: TextLine = emptyTextLine

    private val textPaint: TextPaint by lazy {
        TextPaint(ChapterProvider.contentPaint).apply {
            textSize = mTextSize
        }
    }

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
        val y = textLine.lineBase - textLine.lineTop
        if (linkUrl != null) {
            textPaint.run {
                color = ReadBookConfig.textAccentColor
                isUnderlineText = true
            }
            drawText(view, canvas, y, textPaint)
            return
        }
        textPaint.run {
            color = if (textLine.isReadAloud || isSearchResult) {
                ReadBookConfig.textAccentColor
            } else {
                mTextColor
            }
            isUnderlineText = false
        }
        drawText(view, canvas, y, textPaint)
    }

    private fun drawText(view: ContentTextView, canvas: Canvas, y: Float, textPaint: TextPaint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val letterSpacing = textPaint.letterSpacing * textPaint.textSize
            val letterSpacingHalf = letterSpacing * 0.5f
            canvas.drawText(charData, start + letterSpacingHalf, y, textPaint)
        } else {
            canvas.drawText(charData, start, y, textPaint)
        }
        if (selected) {
            canvas.drawRect(start, 0f, end, textLine.height, view.selectedPaint)
        }
    }

}
