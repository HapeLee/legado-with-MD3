package io.legado.app.ui.book.read.page.entities.column

import android.graphics.Canvas
import android.graphics.RectF
import androidx.annotation.Keep
import io.legado.app.data.entities.Book
import io.legado.app.model.ImageProvider
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextLine.Companion.emptyTextLine
import io.legado.app.utils.dpToPx
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

/**
 * 图片列
 */
@Keep
data class ImageColumn(
    override var start: Float,
    override var end: Float,
    var src: String,
    /**
     * 解析图片缓存路径要用的书，由排版时定下（Track D·D2）。
     *
     * 以前是 `draw()` 里读 `ReadBook.book`：换书那一瞬间旧页面重绘会拿**新书**的目录
     * 去找**旧页**的图。持有创建时那一本才是对的。
     */
    val book: Book,
    var click: String? = null
) : BaseColumn {

    override var textLine: TextLine = emptyTextLine
    override fun draw(view: ContentTextView, canvas: Canvas) {
        val height = textLine.height

        val bitmap = ImageProvider.getImage(
            book,
            src,
            (end - start).toInt(),
            height.toInt()
        )

        val rectF = if (textLine.isImage) {
            RectF(start, 0f, end, height)
        } else {
            /*以宽度为基准保持图片的原始比例叠加，当div为负数时，允许高度比字符更高*/
            val h = (end - start) / bitmap.width * bitmap.height
            val div = (height - h) / 2
            RectF(start, div, end, height - div)
        }
        kotlin.runCatching {
            canvas.drawBitmap(bitmap, null, rectF, view.imagePaint)
        }.onFailure { e ->
            appCtx.toastOnUi(e.localizedMessage)
        }
    }

    override fun isTouch(x: Float): Boolean {
        return x > start && x < end + 20.dpToPx()
    }

}