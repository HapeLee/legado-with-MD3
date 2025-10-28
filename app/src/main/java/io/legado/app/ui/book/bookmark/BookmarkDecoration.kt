package io.legado.app.ui.book.bookmark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
//import io.legado.app.lib.theme.accentColor
//import io.legado.app.lib.theme.backgroundColor
import io.legado.app.utils.dpToPx
import io.legado.app.utils.spToPx
import kotlin.math.min

class BookmarkDecoration(
    private val context: Context,
    val adapter: BookmarkAdapter) : RecyclerView.ItemDecoration() {

    private val headerLeft = 16f.dpToPx()
    private val headerHeight = 32f.dpToPx()

    private val headerPaint = Paint().apply {
        color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainer, Color.GRAY)
    }

    private val textPaint = TextPaint().apply {
        textSize = 14f.spToPx()
        color =
            MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.BLACK)
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    private val textRect = Rect()

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val count = parent.childCount
        for (i in 0 until count) {
            val view = parent.getChildAt(i)
            val position = parent.getChildLayoutPosition(view)
            val isHeader = adapter.isItemHeader(position)
            if (isHeader) {
                c.drawRect(
                    0f,
                    view.top - headerHeight,
                    parent.width.toFloat(),
                    view.top.toFloat(),
                    headerPaint
                )
                val headerText = adapter.getHeaderText(position)
                textPaint.getTextBounds(headerText, 0, headerText.length, textRect)
                c.drawText(
                    headerText,
                    headerLeft,
                    (view.top - headerHeight) + headerHeight / 2 + textRect.height() / 2,
                    textPaint
                )
            }
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val position = (parent.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        val view = parent.findViewHolderForAdapterPosition(position)?.itemView ?: return
        val isHeader = adapter.isItemHeader(position + 1)
        val headerText = adapter.getHeaderText(position)
        if (isHeader) {
            val bottom = min(headerHeight.toInt(), view.bottom)
            c.drawRect(
                0f,
                view.top - headerHeight,
                parent.width.toFloat(),
                bottom.toFloat(),
                headerPaint
            )
            textPaint.getTextBounds(headerText, 0, headerText.length, textRect)
            c.drawText(
                headerText,
                headerLeft,
                headerHeight / 2 + textRect.height() / 2 - (headerHeight - bottom),
                textPaint
            )
        } else {
            c.drawRect(
                0f,
                0f,
                parent.width.toFloat(),
                headerHeight,
                headerPaint
            )
            textPaint.getTextBounds(headerText, 0, headerText.length, textRect)
            c.drawText(
                headerText,
                headerLeft,
                headerHeight / 2 + textRect.height() / 2,
                textPaint
            )
        }
        c.save()
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildLayoutPosition(view)
        val isHeader = adapter.isItemHeader(position)
        if (isHeader) {
            outRect.top = headerHeight.toInt()
        }
    }

}