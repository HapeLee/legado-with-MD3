package io.legado.app.help

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.Html
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import java.lang.ref.WeakReference

class GlideImageGetter private constructor(
    private val context: Context,
    textView: TextView,
    private val originalHtml: String?,
    private val lifecycle: Lifecycle?,
    private val availableWidth: Int,
    private val sourceOrigin: String?
) : Html.ImageGetter {
    companion object {
        fun create(context: Context, textView: TextView, html: String): GlideImageGetter {
            return GlideImageGetter(
                context = context,
                textView = textView,
                originalHtml = html,
                lifecycle = null,
                availableWidth = 0,
                sourceOrigin = null
            )
        }

        private fun createEmptyDrawable(): Drawable {
            return object : Drawable() {
                override fun draw(canvas: android.graphics.Canvas) = Unit
                override fun setAlpha(alpha: Int) = Unit
                override fun setColorFilter(colorFilter: ColorFilter?) = Unit
                @Deprecated("Deprecated in Java")
                override fun getOpacity(): Int = PixelFormat.TRANSPARENT
            }
        }
    }

    constructor(
        context: Context,
        textView: TextView,
        originalHtml: String
    ) : this(
        context = context,
        textView = textView,
        originalHtml = originalHtml,
        lifecycle = null,
        availableWidth = 0,
        sourceOrigin = null
    )

    constructor(
        context: Context,
        textView: TextView,
        lifecycle: Lifecycle,
        availableWidth: Int,
        sourceOrigin: String? = null
    ) : this(
        context = context,
        textView = textView,
        originalHtml = null,
        lifecycle = lifecycle,
        availableWidth = availableWidth,
        sourceOrigin = sourceOrigin
    )

    private val loadedDrawables = mutableMapOf<String, Drawable>()
    private val textViewRef = WeakReference(textView)
    private val targets = mutableSetOf<CustomTarget<*>>()

    override fun getDrawable(source: String?): Drawable {
        if (source.isNullOrBlank()) {
            return createEmptyDrawable()
        }
        val urlDrawable = GlideUrlDrawable()
        val target = createImageTarget(urlDrawable, source)
        targets.add(target)
        val requestManager = Glide.with(context)
        val requestBuilder = if (lifecycle != null) {
            requestManager.lifecycle(lifecycle).load(source)
        } else {
            requestManager.load(source)
        }
        requestBuilder.into(target)
        return urlDrawable
    }

    private fun createImageTarget(
        urlDrawable: GlideUrlDrawable,
        source: String
    ): CustomTarget<Drawable> {
        return object : CustomTarget<Drawable>() {
            override fun onResourceReady(
                resource: Drawable,
                transition: Transition<in Drawable>?
            ) {
                targets.remove(this)
                val textView = textViewRef.get() ?: return
                val maxWidth = if (availableWidth > 0) {
                    availableWidth
                } else {
                    textView.width - textView.paddingLeft - textView.paddingRight
                }.takeIf { it > 0 } ?: 700
                val drawableWidth = resource.intrinsicWidth.coerceAtLeast(1)
                val drawableHeight = resource.intrinsicHeight.coerceAtLeast(1)
                val scale = if (drawableWidth > maxWidth) {
                    maxWidth.toFloat() / drawableWidth
                } else {
                    1f
                }
                val width = (drawableWidth * scale).toInt()
                val height = (drawableHeight * scale).toInt()
                resource.setBounds(0, 0, width, height)
                urlDrawable.setDrawable(resource)
                loadedDrawables[source] = urlDrawable
                refreshTextView()
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                targets.remove(this)
                urlDrawable.setDrawable(placeholder)
                refreshTextView()
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                targets.remove(this)
                val placeholder = errorDrawable ?: createEmptyDrawable()
                urlDrawable.setDrawable(placeholder)
                loadedDrawables[source] = urlDrawable
                refreshTextView()
            }

            override fun onLoadStarted(placeholder: Drawable?) {
                urlDrawable.setDrawable(placeholder)
            }
        }
    }

    fun clear() {
        val snapshot = targets.toList()
        targets.clear()
        snapshot.forEach {
            Glide.with(context).clear(it)
        }
        textViewRef.clear()
    }

    private fun refreshTextView() {
        val textView = textViewRef.get() ?: return
        textView.post {
            if (originalHtml != null) {
                val cachedImageGetter = CachedImageGetter(loadedDrawables)
                val newHtml =
                    Html.fromHtml(originalHtml, Html.FROM_HTML_MODE_COMPACT, cachedImageGetter, null)
                textView.text = newHtml
            } else {
                // Trigger a redraw without re-parsing (compatible with 5-parameter constructor)
                textView.text = textView.text
            }
        }
    }

    private class CachedImageGetter(
        private val drawableCache: Map<String, Drawable>
    ) : Html.ImageGetter {
        override fun getDrawable(source: String?): Drawable {
            if (source.isNullOrBlank()) {
                return createEmptyDrawable()
            }
            drawableCache[source]?.let { return it }
            return createEmptyDrawable().apply {
                setBounds(0, 0, 1, 1)
            }
        }
    }
}
