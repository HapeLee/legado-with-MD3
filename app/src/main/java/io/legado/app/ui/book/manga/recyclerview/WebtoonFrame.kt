package io.legado.app.ui.book.manga.recyclerview

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.findCenterViewPosition

class WebtoonFrame : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    /** 长按回调，返回手指坐标 */
    var longPressListener: ((centerPosition: Int) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val flingDetector = GestureDetector(context, FlingListener())
    private val regionRects = mutableMapOf<String, RectF>()

    var doubleTapZoom = true
        set(value) {
            field = value
            recycler?.doubleTapZoom = value
            scaleDetector.isQuickScaleEnabled = value
        }

    var disableMangaScale = false

    private val recycler: WebtoonRecyclerView?
        get() = getChildAt(0) as? WebtoonRecyclerView

    var actionHandler: ClickActionHandler? = null

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (AppConfig.mangaLongClick)
                {
                    recycler?.let {
                        val centerPos = it.findCenterViewPosition()
                        if (centerPos != RecyclerView.NO_POSITION) {
                            longPressListener?.invoke(centerPos)
                        }
                    }
                }
            }

            override fun onDown(e: MotionEvent): Boolean = true
        })

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        recycler?.tapListener = tapListener@{ ev ->
            val x = ev.rawX
            val y = ev.rawY
            val key =
                regionRects.entries.find { it.value.contains(x, y) }?.key ?: return@tapListener

            val action = when (key) {
                "TL" -> AppConfig.mangaClickActionTL
                "TC" -> AppConfig.mangaClickActionTC
                "TR" -> AppConfig.mangaClickActionTR
                "ML" -> AppConfig.mangaClickActionML
                "MC" -> AppConfig.mangaClickActionMC
                "MR" -> AppConfig.mangaClickActionMR
                "BL" -> AppConfig.mangaClickActionBL
                "BC" -> AppConfig.mangaClickActionBC
                "BR" -> AppConfig.mangaClickActionBR
                else -> -1
            }

            performClickAction(action)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cellWidth = w / 3f
        val cellHeight = h / 3f
        val keys = listOf("TL", "TC", "TR", "ML", "MC", "MR", "BL", "BC", "BR")

        keys.forEachIndexed { index, key ->
            val row = index / 3
            val col = index % 3
            regionRects[key] = RectF(
                col * cellWidth,
                row * cellHeight,
                (col + 1) * cellWidth,
                (row + 1) * cellHeight
            )
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)  // 捕获长按

        if (!disableMangaScale) {
            scaleDetector.onTouchEvent(ev)
            flingDetector.onTouchEvent(ev)
            val recyclerRect = Rect()
            recycler?.getHitRect(recyclerRect) ?: return super.dispatchTouchEvent(ev)
            recyclerRect.inset(1, 1)

            if (recyclerRect.right < recyclerRect.left || recyclerRect.bottom < recyclerRect.top) {
                return super.dispatchTouchEvent(ev)
            }

            ev.setLocation(
                ev.x.coerceIn(recyclerRect.left.toFloat(), recyclerRect.right.toFloat()),
                ev.y.coerceIn(recyclerRect.top.toFloat(), recyclerRect.bottom.toFloat())
            )
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun performClickAction(action: Int) {
        when (action) {
            -1 -> return
            0 -> actionHandler?.showMenu()
            1 -> actionHandler?.nextPage()
            2 -> actionHandler?.prevPage()
            3 -> actionHandler?.nextChapter()
            4 -> actionHandler?.prevChapter()
            else -> {}
        }
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            recycler?.onScaleBegin()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            recycler?.onScale(detector.scaleFactor)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            recycler?.onScaleEnd()
        }
    }

    inner class FlingListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            return recycler?.zoomFling(velocityX.toInt(), velocityY.toInt()) ?: false
        }
    }

    interface ClickActionHandler {
        fun showMenu()
        fun nextPage()
        fun prevPage()
        fun nextChapter()
        fun prevChapter()
    }
}
