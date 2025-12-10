package io.legado.app.ui.widget.image

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.constant.AppPattern
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.model.BookCover
import io.legado.app.utils.spToPx
import io.legado.app.utils.textHeight
import io.legado.app.utils.themeColor
import io.legado.app.utils.toStringArray

/**
 * 封面
 */
@Suppress("unused")
class CoverImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    private var filletPath = Path()
    private var viewWidth: Float = 0f
    private var viewHeight: Float = 0f
    private var defaultCover = true
    var bitmapPath: String? = null
        private set
    private var name: String? = null
    private var author: String? = null
    private var nameHeight = 0f
    private var authorHeight = 0f
    private val isNightTheme = AppConfig.isNightTheme
    val colorKey = if (isNightTheme) BookCover.coverTextColorN else BookCover.coverTextColor
    val shadowKey = if (isNightTheme) BookCover.coverShadowColorN else BookCover.coverShadowColor
    private val namePaint by lazy {
        val textPaint = TextPaint()
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.isAntiAlias = true
        textPaint.textAlign = Paint.Align.CENTER
        textPaint
    }
    private val authorPaint by lazy {
        val textPaint = TextPaint()
        textPaint.typeface = Typeface.DEFAULT
        textPaint.isAntiAlias = true
        textPaint.textAlign = Paint.Align.CENTER
        textPaint
    }

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        if (params != null) {
            val width = params.width
            if (width >= 0) {
                params.height = width * 7 / 5
            } else {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        super.setLayoutParams(params)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = measuredWidth * 7 / 5
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        )
    }

    private val cornerRadius = 24f

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        viewWidth = width.toFloat()
        viewHeight = height.toFloat()
        filletPath.reset()

        if (viewWidth > cornerRadius * 2 && viewHeight > cornerRadius * 2) {
            val r = cornerRadius
            filletPath.apply {
                moveTo(r, 0f)
                lineTo(viewWidth - r, 0f)
                quadTo(viewWidth, 0f, viewWidth, r)
                lineTo(viewWidth, viewHeight - r)
                quadTo(viewWidth, viewHeight, viewWidth - r, viewHeight)
                lineTo(r, viewHeight)
                quadTo(0f, viewHeight, 0f, viewHeight - r)
                lineTo(0f, r)
                quadTo(0f, 0f, r, 0f)
                close()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!filletPath.isEmpty) {
            canvas.clipPath(filletPath)
        }
        super.onDraw(canvas)
        if (defaultCover && !isInEditMode) {
            drawNameAuthor(canvas)
        }
    }

    private fun drawNameAuthor(canvas: Canvas) {
        var startX = width * 0.2f
        var startY = viewHeight * 0.2f
        if (BookCover.drawBookName) {
            name?.toStringArray()?.let { nameChars ->
                val textSize = viewWidth / 8
                namePaint.textSize = textSize
                namePaint.strokeWidth = textSize / 8

                if (BookCover.drawBookShadow) {
                    namePaint.setShadowLayer(
                        4f,
                        0f,
                        0f,
                        shadowKey
                    )
                } else {
                    namePaint.clearShadowLayer()
                }

                nameChars.forEach { char ->
                    if (BookCover.drawBookStroke) {
                        namePaint.color = Color.WHITE
                        namePaint.style = Paint.Style.STROKE
                        canvas.drawText(char, startX, startY, namePaint)
                    }
                    if (BookCover.coverDefaultColor)
                        namePaint.color = context.themeColor(com.google.android.material.R.attr.colorSecondary)
                    else
                        namePaint.color = colorKey
                    namePaint.style = Paint.Style.FILL
                    canvas.drawText(char, startX, startY, namePaint)

                    startY += namePaint.textHeight

                    if (startY > viewHeight * 0.8f) {
                        startX += textSize + 4.spToPx()
                        startY = viewHeight * 0.2f
                    }
                }
                namePaint.clearShadowLayer()
            }
        }

        if (BookCover.drawBookAuthor) {
            author?.toStringArray()?.let { author ->
                authorPaint.textSize = viewWidth / 10
                authorPaint.strokeWidth = authorPaint.textSize / 5
                startX = width * 0.8f
                startY = viewHeight * 0.95f - author.size * authorPaint.textHeight
                startY = maxOf(startY, viewHeight * 0.2f)
                if (BookCover.drawBookShadow) {
                    authorPaint.setShadowLayer(
                        4f,
                        0f,
                        0f,
                        shadowKey
                    )
                } else {
                    authorPaint.clearShadowLayer()
                }

                author.forEach {
                    if (BookCover.drawBookStroke) {
                        authorPaint.color = Color.WHITE
                        authorPaint.style = Paint.Style.STROKE
                        canvas.drawText(it, startX, startY, authorPaint)
                    }
                    if (BookCover.coverDefaultColor)
                        authorPaint.color = context.themeColor(com.google.android.material.R.attr.colorSecondary)
                    else
                        authorPaint.color = colorKey
                    authorPaint.style = Paint.Style.FILL
                    canvas.drawText(it, startX, startY, authorPaint)

                    startY += authorPaint.textHeight

                    if (startY > viewHeight * 0.95) {
                        return@let
                    }
                }
                authorPaint.clearShadowLayer()
            }
        }
    }


    fun setHeight(height: Int) {
        val width = height * 5 / 7
        minimumWidth = width
    }

    private val glideListener by lazy {
        object : RequestListener<Drawable> {

            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                defaultCover = true
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                defaultCover = false
                return false
            }

        }
    }

    fun load(
        path: String? = null,
        name: String? = null,
        author: String? = null,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null,
        onLoadFinish: (() -> Unit)? = null
    ) {
        this.bitmapPath = path
        this.name = name?.replace(AppPattern.bdRegex, "")?.trim()
        this.author = author?.replace(AppPattern.bdRegex, "")?.trim()
        defaultCover = true
        invalidate()
        if (AppConfig.useDefaultCover) {
            ImageLoader.load(context, BookCover.defaultDrawable)
                .centerCrop()
                .into(this)
        } else {
            var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
            if (sourceOrigin != null) {
                options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
            }
            var builder = if (fragment != null && lifecycle != null) {
                ImageLoader.load(fragment, lifecycle, path)
            } else {
                ImageLoader.load(context, path)//Glide自动识别http://,content://和file://
            }
            builder = builder.apply(options)
                .placeholder(BookCover.defaultDrawable)
                .error(BookCover.defaultDrawable)
                .listener(glideListener)
            if (onLoadFinish != null) {
                builder = builder.addListener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable?>,
                        isFirstResource: Boolean
                    ): Boolean {
                        onLoadFinish.invoke()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable?>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        onLoadFinish.invoke()
                        return false
                    }
                })
            }
            builder
                .centerCrop()
                .into(this)
        }
    }

}
