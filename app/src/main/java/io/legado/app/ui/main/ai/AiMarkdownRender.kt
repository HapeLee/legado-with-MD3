package io.legado.app.ui.main.ai

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.dpToPx
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.destination.ImageDestinationProcessor
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import java.io.File
import java.util.Locale
import kotlin.math.abs

object AiMarkdownRender {

    fun createMarkwon(context: Context, imageMaxWidth: Int = defaultImageMaxWidth(context)): Markwon {
        val safeImageMaxWidth = imageMaxWidth.coerceAtLeast(1)
        val requestManager = Glide.with(context)
        val requestOptions = RequestOptions()
            .override(safeImageMaxWidth)
            .encodeQuality(88)
        return Markwon.builder(context)
            .usePlugin(
                GlideImagesPlugin.create(object : GlideImagesPlugin.GlideStore {
                    override fun load(drawable: AsyncDrawable): RequestBuilder<Drawable> {
                        return requestManager
                            .load(drawable.destination)
                            .apply(requestOptions)
                            .placeholder(imagePlaceholder(safeImageMaxWidth))
                    }

                    override fun cancel(target: Target<*>) {
                        requestManager.clear(target)
                    }
                })
            )
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.imageDestinationProcessor(object : ImageDestinationProcessor() {
                        override fun process(destination: String): String {
                            return normalizeImageDestination(destination)
                        }
                    })
                }
            })
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    fun renderKey(
        messageId: String,
        content: String,
        pending: Boolean,
        textView: TextView,
        context: Context
    ): String {
        val widthBucket = availableImageWidth(textView, defaultImageMaxWidth(context)) / 32
        val textSizeBucket = textView.textSize.toInt()
        return buildString {
            append(messageId)
            append(':')
            append(content.hashCode())
            append(':')
            append(pending)
            append(':')
            append(if (AppConfig.isNightTheme) 1 else 0)
            append(':')
            append(widthBucket)
            append(':')
            append(textSizeBucket)
        }
    }

    fun availableImageWidth(textView: TextView, fallback: Int): Int {
        val available = textView.width - textView.paddingLeft - textView.paddingRight
        return if (available > 0) available else fallback.coerceAtLeast(1)
    }

    fun setNativeSelectionWithLinkTap(textView: TextView) {
        textView.linksClickable = true
        if (!textView.isTextSelectable) {
            textView.setTextIsSelectable(true)
        }
        if (textView.getTag(R.id.ai_markdown_link_tap_handler) != true) {
            installLinkTapHandler(textView)
            textView.setTag(R.id.ai_markdown_link_tap_handler, true)
        }
    }

    fun clearNativeSelectionWithLinkTap(textView: TextView) {
        textView.setOnTouchListener(null)
        textView.setTag(R.id.ai_markdown_link_tap_handler, null)
        textView.setTextIsSelectable(false)
        textView.linksClickable = false
        textView.movementMethod = null
    }

    fun stableId(value: String): Long {
        var result = -0x340d631b7bdddcdbL
        value.forEach { char ->
            result = result xor char.code.toLong()
            result *= 0x100000001b3L
        }
        return result
    }

    private fun defaultImageMaxWidth(context: Context): Int {
        return (context.resources.displayMetrics.widthPixels - 64.dpToPx())
            .coerceAtLeast(180.dpToPx())
    }

    private fun imagePlaceholder(imageMaxWidth: Int): Drawable {
        return GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setSize(imageMaxWidth, 160.dpToPx())
        }
    }

    private fun installLinkTapHandler(textView: TextView) {
        val touchSlop = ViewConfiguration.get(textView.context).scaledTouchSlop
        val longPressTimeout = ViewConfiguration.getLongPressTimeout()
        var downX = 0f
        var downY = 0f
        var downTime = 0L
        var downSpan: ClickableSpan? = null
        textView.setOnTouchListener { view, event ->
            val tv = view as TextView
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downTime = event.eventTime
                    downSpan = findClickableSpan(tv, event)
                    false
                }

                MotionEvent.ACTION_UP -> {
                    val span = downSpan
                    downSpan = null
                    val isTap = span != null &&
                        event.eventTime - downTime < longPressTimeout &&
                        abs(event.x - downX) <= touchSlop &&
                        abs(event.y - downY) <= touchSlop &&
                        !tv.hasActiveSelection()
                    if (isTap) {
                        span.onClick(tv)
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    downSpan = null
                    false
                }

                else -> false
            }
        }
    }

    private fun findClickableSpan(textView: TextView, event: MotionEvent): ClickableSpan? {
        val spanned = textView.text as? Spanned ?: return null
        val layout = textView.layout ?: return null
        val x = event.x.toInt() - textView.totalPaddingLeft + textView.scrollX
        val y = event.y.toInt() - textView.totalPaddingTop + textView.scrollY
        if (y < 0 || y > layout.height) return null
        val line = layout.getLineForVertical(y)
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null
        val offset = layout.getOffsetForHorizontal(line, x.toFloat())
        return spanned.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
    }

    private fun TextView.hasActiveSelection(): Boolean {
        return selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd
    }

    private fun normalizeImageDestination(destination: String): String {
        val raw = destination.trim()
        if (raw.isBlank()) return raw
        val lower = raw.lowercase(Locale.ROOT)
        if (lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("content://") ||
            lower.startsWith("file://") ||
            lower.startsWith("data:")
        ) {
            return raw
        }
        if (lower.startsWith("ai-image://")) {
            val uri = runCatching { Uri.parse(raw) }.getOrNull()
            val candidate = uri?.path?.takeIf { it.isNotBlank() }
                ?: uri?.schemeSpecificPart?.removePrefix("//")?.takeIf { it.isNotBlank() }
            val decoded = candidate?.let { Uri.decode(it) }.orEmpty()
            if (decoded.isNotBlank()) {
                val file = File(decoded)
                if (file.isAbsolute) return Uri.fromFile(file).toString()
                return decoded
            }
        }
        val file = File(raw)
        return if (file.isAbsolute) Uri.fromFile(file).toString() else raw
    }
}
