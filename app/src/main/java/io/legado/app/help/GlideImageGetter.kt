package io.legado.app.help

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Html
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import java.lang.ref.WeakReference

class GlideImageGetter(private val context: Context, textView: TextView, private val originalHtml: String) :
    Html.ImageGetter {
    companion object {
        fun create(context: Context, textView: TextView, html: String): GlideImageGetter {
            return GlideImageGetter(context, textView, html)
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
        Glide.with(context)
            .load(source)
            .into(target)
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
                val availableWidth = textView.width - textView.paddingLeft - textView.paddingRight
                val maxWidth = availableWidth.takeIf { it > 0 } ?: 700
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
        targets.forEach {
            Glide.with(context).clear(it)
        }
        targets.clear()
        textViewRef.clear()
    }

    private fun refreshTextView() {
        val textView = textViewRef.get() ?: return
        textView.post {
            // 创建新的ImageGetter，但使用缓存
            val cachedImageGetter = CachedImageGetter(loadedDrawables)
            val newHtml =
                Html.fromHtml(originalHtml,  Html.FROM_HTML_MODE_COMPACT, cachedImageGetter, null)
            textView.text = newHtml
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