@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EdgeEffect
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.record
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import io.legado.app.help.GlideImageGetter
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.TintHelper
import io.legado.app.utils.canvasrecorder.CanvasRecorder
import io.legado.app.utils.canvasrecorder.record
import splitties.systemservices.inputMethodManager
import splitties.views.bottomPadding
import splitties.views.topPadding
import androidx.core.graphics.createBitmap
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.core.text.parseAsHtml
import androidx.core.view.postDelayed
import io.legado.app.R
import io.legado.app.help.TextViewTagHandler
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import io.noties.markwon.Markwon
import io.noties.markwon.image.AsyncDrawableSpan

private tailrec fun getCompatActivity(context: Context?): AppCompatActivity? {
    return when (context) {
        is AppCompatActivity -> context
        is androidx.appcompat.view.ContextThemeWrapper -> getCompatActivity(context.baseContext)
        is android.view.ContextThemeWrapper -> getCompatActivity(context.baseContext)
        else -> null
    }
}

val View.activity: AppCompatActivity?
    get() = getCompatActivity(context)

fun View.hideSoftInput() = run {
    inputMethodManager.hideSoftInputFromWindow(this.windowToken, 0)
}

fun EditText.showSoftInput() = run {
    requestFocus()
    inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

fun View.disableAutoFill() = run {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        this.importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }
}

fun View.applyTint(
    @ColorInt color: Int,
    isDark: Boolean = AppConfig.isNightTheme
) {
    TintHelper.setTintAuto(this, color, false, isDark)
}

fun View.applyBackgroundTint(
    @ColorInt color: Int,
    isDark: Boolean = AppConfig.isNightTheme
) {
    if (background == null) {
        setBackgroundColor(color)
    } else {
        TintHelper.setTintAuto(this, color, true, isDark)
    }
}

fun RecyclerView.setEdgeEffectColor(@ColorInt color: Int) {
    edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
        override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
            val edgeEffect = super.createEdgeEffect(view, direction)
            edgeEffect.color = color
            return edgeEffect
        }
    }
}

fun ViewPager.setEdgeEffectColor(@ColorInt color: Int) {
    try {
        val clazz = ViewPager::class.java
        for (name in arrayOf("mLeftEdge", "mRightEdge")) {
            val field = clazz.getDeclaredField(name)
            field.isAccessible = true
            val edge = field.get(this)
            (edge as EdgeEffect).color = color
        }
    } catch (ignored: Exception) {
    }
}

fun EditText.disableEdit() {
    keyListener = null
}

fun View.gone() {
    if (visibility != GONE) {
        visibility = GONE
    }
}

fun View.gone(gone: Boolean) {
    if (gone) {
        gone()
    } else {
        visibility = VISIBLE
    }
}

fun View.invisible() {
    if (visibility != INVISIBLE) {
        visibility = INVISIBLE
    }
}

fun View.visible() {
    if (visibility != VISIBLE) {
        visibility = VISIBLE
    }
}

fun View.visible(visible: Boolean) {
    if (visible && visibility != VISIBLE) {
        visibility = VISIBLE
    } else if (!visible && isVisible) {
        visibility = INVISIBLE
    }
}

fun View.screenshot(bitmap: Bitmap? = null, canvas: Canvas? = null): Bitmap? {
    return if (width > 0 && height > 0) {
        val screenshot = if (bitmap != null && bitmap.width == width && bitmap.height == height) {
            bitmap.eraseColor(Color.TRANSPARENT)
            bitmap
        } else {
            bitmap?.recycle()
            createBitmap(width, height)
        }
        val c = canvas ?: Canvas()
        c.setBitmap(screenshot)
        c.drawColor(resolveScreenshotBackgroundColor())
        c.withTranslation(-scrollX.toFloat(), -scrollY.toFloat()) {
            this@screenshot.draw(this)
        }
        c.setBitmap(null)
        screenshot.prepareToDraw()
        screenshot
    } else {
        null
    }
}

fun View.screenshot(picture: Picture) {
    if (width > 0 && height > 0) {
        picture.record(width, height) {
            drawColor(resolveScreenshotBackgroundColor())
            withTranslation(-scrollX.toFloat(), -scrollY.toFloat()) {
                draw(this)
            }
        }
    }
}

fun View.screenshot(canvasRecorder: CanvasRecorder) {
    if (width > 0 && height > 0) {
        canvasRecorder.record(width, height) {
            drawColor(resolveScreenshotBackgroundColor())
            draw(this)
        }
    }
}

private fun View.resolveScreenshotBackgroundColor(): Int {
    var target: View? = this
    while (target != null) {
        (target.background as? ColorDrawable)?.let {
            if (it.color != Color.TRANSPARENT) return it.color
        }
        target = target.parent as? View
    }
    return runCatching { context.getCompatColor(R.color.background) }.getOrDefault(Color.WHITE)
}

fun View.setPaddingBottom(bottom: Int) {
    setPadding(paddingLeft, paddingTop, paddingRight, bottom)
}

fun SeekBar.progressAdd(int: Int) {
    progress += int
}

fun RadioGroup.getIndexById(id: Int): Int {
    for (i in 0 until this.childCount) {
        if (id == get(i).id) {
            return i
        }
    }
    return 0
}

fun RadioGroup.getCheckedIndex(): Int {
    for (i in 0 until this.childCount) {
        if (checkedRadioButtonId == get(i).id) {
            return i
        }
    }
    return 0
}

fun RadioGroup.checkByIndex(index: Int) {
    check(get(index).id)
}

fun TextView.setHtml(html: String, imageGetter: GlideImageGetter? = null, textViewTagHandler: TextViewTagHandler? = null) {
    text = html.parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT, imageGetter, textViewTagHandler)
}

fun TextView.setHtml(html: String, imageGetter: GlideImageGetter? = null, textViewTagHandler: TextViewTagHandler? = null, imgOnLongClickListener: (source: String) -> Unit, imgOnClickListener: (click: String) -> Unit) {
    val spanned = html.parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT, imageGetter, textViewTagHandler)
    val imageSpans = spanned.getSpans(0, spanned.length, ImageSpan::class.java)
    val clickSpans = mutableListOf<Triple<Pair<Int, Int>, String, String?>>()
    for (imageSpan in imageSpans) {
        val start = spanned.getSpanStart(imageSpan)
        val end = spanned.getSpanEnd(imageSpan)
        if (start >= 0 && end >= 0) {
            val source = imageSpan.source ?: continue
            var click: String? = null
            val urlMatcher = paramPattern.matcher(source)
            if (urlMatcher.find()) {
                val urlOptionStr = source.substring(urlMatcher.end())
                GSON.fromJsonObject<Map<String, String>>(urlOptionStr).getOrNull()?.let {
                    click = it["click"]
                }
            }
            clickSpans.add(Triple((start to end),source, click))
        }
    }
    text = spanned
    if (clickSpans.isNotEmpty()) {
        movementMethod = object : android.text.method.LinkMovementMethod() {
            private var lastClickTime = 0L
            private var longClickRunnable: Runnable?= null
            private var isLongClick = false
            override fun onTouchEvent(
                widget: TextView,
                buffer: android.text.Spannable,
                event: MotionEvent
            ): Boolean {
                var x = event.x.toInt()
                var y = event.y.toInt()
                x -= widget.totalPaddingLeft
                y -= widget.totalPaddingTop
                val line = widget.layout.getLineForVertical(y)
                val off = widget.layout.getOffsetForHorizontal(line, x.toFloat())
                var durClick: String? = null
                var durSource: String? = null
                for ((position, source, click) in clickSpans) {
                    if (off in position.first..position.second) {
                        durClick = click
                        durSource = source
                        break
                    }
                }
                when(event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        cancelLongClick()
                        if (durSource != null) {
                            isLongClickable = false
                            longClickRunnable = postDelayed(600) {
                                isLongClick = true
                                imgOnLongClickListener(durSource)
                            }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        isLongClickable = true
                        if (!isLongClick) {
                            cancelLongClick()
                            if (durClick != null) {
                                val upTime = System.currentTimeMillis()
                                if (upTime - lastClickTime > 200) {
                                    lastClickTime = upTime
                                    imgOnClickListener(durClick)
                                }
                                return true
                            }
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (durSource != null) {
                            isLongClickable = false
                            return true
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isLongClickable = true
                        cancelLongClick()
                        return true
                    }
                }
                return super.onTouchEvent(widget, buffer, event)
            }

            private fun cancelLongClick() {
                longClickRunnable?.let {
                    removeCallbacks(it)
                    longClickRunnable = null
                }
                isLongClick = false
            }

        }
    }
}

fun TextView.setMarkdown(markwon: Markwon, spanned: Spanned, imgOnLongClickListener: (source: String) -> Unit) {
    val imageSpans = spanned.getSpans(0, spanned.length, AsyncDrawableSpan::class.java)
    val clickSpans = mutableListOf<Pair<Pair<Int, Int>, String>>()
    for (imageSpan in imageSpans) {
        val start = spanned.getSpanStart(imageSpan)
        val end = spanned.getSpanEnd(imageSpan)
        if (start >= 0 && end >= 0) {
            val source = imageSpan.drawable.destination
            clickSpans.add((start to end) to source)
        }
    }
    if (clickSpans.isNotEmpty()) {
        movementMethod = object : android.text.method.LinkMovementMethod() {
            private var longClickRunnable: Runnable?= null
            private var isLongClick = false
            override fun onTouchEvent(
                widget: TextView,
                buffer: android.text.Spannable,
                event: MotionEvent
            ): Boolean {
                isLongClickable = false
                var x = event.x.toInt()
                var y = event.y.toInt()
                x -= widget.totalPaddingLeft
                y -= widget.totalPaddingTop
                val line = widget.layout.getLineForVertical(y)
                val off = widget.layout.getOffsetForHorizontal(line, x.toFloat())
                var durSource: String? = null
                for ((position, source) in clickSpans) {
                    if (off in position.first..position.second) {
                        durSource = source
                        break
                    }
                }
                when(event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        cancelLongClick()
                        if (durSource != null) {
                            isLongClickable = false
                            longClickRunnable = postDelayed(600) {
                                isLongClick = true
                                imgOnLongClickListener(durSource)
                            }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        isLongClickable = true
                        if (!isLongClick) {
                            cancelLongClick()
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (durSource != null) {
                            isLongClickable = false
                            return true
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isLongClickable = true
                        cancelLongClick()
                        return true
                    }
                }
                return super.onTouchEvent(widget, buffer, event)
            }

            private fun cancelLongClick() {
                longClickRunnable?.let {
                    removeCallbacks(it)
                    longClickRunnable = null
                }
                isLongClick = false
            }
        }
    }
    markwon.setParsedMarkdown(this, spanned)
}

fun TextView.setTextIfNotEqual(charSequence: CharSequence?) {
    if (text != charSequence) {
        text = charSequence
    }
}

fun View.shouldHideSoftInput(event: MotionEvent): Boolean {
    if (this is EditText) {
        val l = intArrayOf(0, 0)
        getLocationInWindow(l)
        val left = l[0]
        val top = l[1]
        val bottom = top + getHeight()
        val right = left + getWidth()
        return !(event.x > left && event.x < right && event.y > top && event.y < bottom)
    }
    return false
}

fun View.applyStatusBarPadding(withInitialPadding: Boolean = false) {
    val initialPadding = if (withInitialPadding) topPadding else 0
    setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        if (this is StatusBarInsetAware) {
            onStatusBarInsetChanged(insets.top, initialPadding)
        } else {
            topPadding = initialPadding + insets.top
        }
        windowInsets
    }
}

interface StatusBarInsetAware {
    fun onStatusBarInsetChanged(insetTop: Int, initialPaddingTop: Int)
}

fun View.applyNavigationBarPadding(withInitialPadding: Boolean = false) {
    val initialPadding = if (withInitialPadding) bottomPadding else 0
    setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
        val navigationBarHeight = windowInsets.navigationBarHeight
        val extraPadding = if (navigationBarHeight > 0) 12.dpToPx() else 0
        bottomPadding = initialPadding + navigationBarHeight + extraPadding
        windowInsets
    }
}

fun View.applyMainBottomBarPadding(withInitialPadding: Boolean = false) {
    val initialPadding = if (withInitialPadding) bottomPadding else 0
    setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
        val bottomSpace = windowInsets.navigationBarHeight +
                resources.getDimensionPixelSize(R.dimen.main_content_bottom_bar_padding)
        if (this is RecyclerView) {
            bottomPadding = initialPadding
            updateMainBottomBarSpaceDecoration(bottomSpace)
        } else {
            bottomPadding = initialPadding
        }
        windowInsets
    }
}

private fun RecyclerView.updateMainBottomBarSpaceDecoration(bottomSpace: Int) {
    (getTag(R.id.main_bottom_bar_space_decoration) as? MainBottomBarSpaceDecoration)?.let {
        if (it.bottomSpace != bottomSpace) {
            it.bottomSpace = bottomSpace
            invalidateItemDecorations()
        }
        return
    }
    val decoration = MainBottomBarSpaceDecoration(bottomSpace)
    addItemDecoration(decoration)
    setTag(R.id.main_bottom_bar_space_decoration, decoration)
}

private class MainBottomBarSpaceDecoration(var bottomSpace: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position != RecyclerView.NO_POSITION && position == state.itemCount - 1) {
            outRect.bottom = bottomSpace
        }
    }
}

fun View.applyNavigationBarMargin(withInitialMargin: Boolean = false) {
    val initialMargin = if (withInitialMargin) marginBottom else 0
    setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = initialMargin + windowInsets.navigationBarHeight
        }
        windowInsets
    }
}

fun View.setBackgroundKeepPadding(@DrawableRes backgroundResId: Int) {
    val paddingLeft = paddingLeft
    val paddingTop = paddingTop
    val paddingRight = paddingRight
    val paddingBottom = paddingBottom
    setBackgroundResource(backgroundResId)
    setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
}

fun View.canScroll(direction: Int): Boolean {
    return canScrollVertically(direction) || canScrollHorizontally(direction)
}

private val requestLayoutBroken = Build.VERSION.SDK_INT <= Build.VERSION_CODES.M
        || Build.VERSION.SDK_INT in Build.VERSION_CODES.O..Build.VERSION_CODES.Q

fun View.setOnApplyWindowInsetsListenerCompat(listener: (View, WindowInsetsCompat) -> WindowInsetsCompat) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val windowInsets = listener(view, insets)
        if (requestLayoutBroken && isLayoutRequested) {
            post {
                requestLayout()
            }
        }
        windowInsets
    }
}

fun Spinner.setSelectionSafely(position: Int) {
    val count = adapter?.count ?: 0
    if (count > 0) {
        setSelection(position.coerceIn(0, count - 1))
    }
}
