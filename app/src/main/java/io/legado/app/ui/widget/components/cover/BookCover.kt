package io.legado.app.ui.widget.components.cover

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withSave
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.legado.app.model.BookCover
import io.legado.app.ui.config.coverConfig.CoverConfig
import io.legado.app.ui.widget.components.card.TextCard
import org.koin.compose.koinInject

@Composable
fun BookCover(
    name: String?,
    author: String?,
    path: String?,
    modifier: Modifier = Modifier.width(64.dp),
    badgeText: String? = null,
    showBadgeDot: Boolean = false,
    sourceOrigin: String? = null,
    onLoadFinish: (() -> Unit)? = null,
    ignoreUseDefaultCover: Boolean = false
) {
    val context = LocalContext.current
    val isNight = isSystemInDarkTheme()

    val useDefault = !ignoreUseDefaultCover && CoverConfig.useDefaultCover
    val finalPath = if (useDefault) null else path

    // 获取随机封面路径（字符串），增加配置项作为 Key，确保设置更改后立即刷新
    val randomPath = remember(
        name, author, path, isNight,
        CoverConfig.defaultCover,
        CoverConfig.defaultCoverDark
    ) {
        BookCover.getRandomDefaultPath(
            seed = name ?: author ?: path ?: "",
            isNight = isNight
        )
    }

    // 获取随机封面 Drawable，用于占位图
    val randomDrawable = remember(
        name, author, path, isNight,
        CoverConfig.defaultCover,
        CoverConfig.defaultCoverDark
    ) {
        BookCover.getRandomDefaultDrawable(
            seed = name ?: author ?: path ?: "",
            isNight = isNight
        )
    }

    // 检查是否有自定义随机封面设置
    val hasCustomDefault = !randomPath.isNullOrBlank()

    var isOnlineCoverLoaded by remember(path) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .aspectRatio(5f / 7f)
            .then(
                if (CoverConfig.coverShowShadow) {
                    Modifier.shadow(4.dp, RoundedCornerShape(4.dp))
                } else Modifier
            )
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (!hasCustomDefault) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                } else Modifier
            )
    ) {
        // 如果没有自定义随机封面，显示书本图标作为底图
        // TODO:暂时先用猫猫头
        /*
        if (!hasCustomDefault) {
            Icon(
                Icons.Default.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center)
            )
        }
         */


        // 1. 封面图层
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(finalPath ?: randomPath)
                .placeholder(randomDrawable)
                .error(randomDrawable)
                .crossfade(true)
                .setParameter("sourceOrigin", sourceOrigin)
                .setParameter("loadOnlyWifi", CoverConfig.loadCoverOnlyWifi)
                .build(),
            contentDescription = null,
            imageLoader = koinInject(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = {
                if (finalPath != null) {
                    isOnlineCoverLoaded = true
                }
                onLoadFinish?.invoke()
            },
            onError = {
                isOnlineCoverLoaded = false
                onLoadFinish?.invoke()
            }
        )

        // 2. 文字叠加层：当没有书籍自身封面（或加载失败）时，在随机底图上绘制书名/作者
        if (!isOnlineCoverLoaded) {
            CoverTextOverlay(
                name = name,
                author = author,
                isNight = isNight
            )
        }

        // 3. 角标 (Badge)
        if (!badgeText.isNullOrEmpty()) {
            TextCard(
                text = badgeText,
                icon = if (showBadgeDot) Icons.Default.Update else null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                cornerRadius = 4.dp,
                horizontalPadding = 4.dp,
                verticalPadding = 0.dp
            )
        }
    }
}

@Composable
private fun CoverTextOverlay(
    name: String?,
    author: String?,
    isNight: Boolean
) {
    val showName = if (isNight) CoverConfig.coverShowNameN else CoverConfig.coverShowName
    val showAuthor =
        (if (isNight) CoverConfig.coverShowAuthorN else CoverConfig.coverShowAuthor) && showName

    if (!showName && !showAuthor) return

    val secondaryColor = MaterialTheme.colorScheme.secondary.toArgb()
    val textColor = if (CoverConfig.coverDefaultColor) {
        secondaryColor
    } else {
        if (isNight) CoverConfig.coverTextColorN else CoverConfig.coverTextColor
    }
    val shadowColor = if (isNight) CoverConfig.coverShadowColorN else CoverConfig.coverShadowColor
    val isHorizontal = CoverConfig.coverInfoOrientation == "1"

    Canvas(modifier = Modifier.fillMaxSize()) {
        val viewWidth = size.width
        val viewHeight = size.height

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas

            if (showName && !name.isNullOrBlank()) {
                val paint = Paint().apply {
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = viewWidth / 8f
                    color = textColor
                    if (CoverConfig.coverShowShadow) {
                        setShadowLayer(4f, 2f, 2f, shadowColor)
                    }
                }

                if (isHorizontal) {
                    val maxWidth = (viewWidth * 0.8f).toInt()
                    val textPaint = TextPaint(paint).apply {
                        textAlign = Paint.Align.LEFT
                    }

                    val layout = StaticLayout.Builder
                        .obtain(name, 0, name.length, textPaint, maxWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setMaxLines(3)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build()

                    nativeCanvas.withSave {
                        val textX = (viewWidth - maxWidth) / 2f
                        val textY = viewHeight * 0.08f
                        translate(textX, textY)
                        if (CoverConfig.coverShowStroke) {
                            textPaint.style = Paint.Style.STROKE
                            textPaint.strokeWidth = textPaint.textSize / 12
                            val originalColor = textPaint.color
                            textPaint.color = Color.White.toArgb()
                            textPaint.clearShadowLayer()
                            layout.draw(this)
                            textPaint.style = Paint.Style.FILL
                            textPaint.color = originalColor
                            if (CoverConfig.coverShowShadow) {
                                textPaint.setShadowLayer(4f, 2f, 2f, shadowColor)
                            }
                        }
                        layout.draw(this)
                    }
                } else {
                    var startX = viewWidth * 0.16f
                    var startY = viewHeight * 0.16f
                    val fm = paint.fontMetrics
                    val charHeight = fm.bottom - fm.top
                    name.forEach { char ->
                        if (CoverConfig.coverShowStroke) {
                            val strokePaint = Paint(paint).apply {
                                color = Color.White.toArgb()
                                style = Paint.Style.STROKE
                                strokeWidth = paint.textSize / 10
                                clearShadowLayer()
                            }
                            nativeCanvas.drawText(char.toString(), startX, startY, strokePaint)
                        }
                        nativeCanvas.drawText(char.toString(), startX, startY, paint)
                        startY += charHeight
                        if (startY > viewHeight * 0.8f) {
                            startX += paint.textSize * 1.2f
                            startY = viewHeight * 0.2f
                        }
                    }
                }
            }

            if (showAuthor && !author.isNullOrBlank()) {
                val paint = Paint().apply {
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    textSize = viewWidth / 12f
                    color = textColor
                    if (CoverConfig.coverShowShadow) {
                        setShadowLayer(4f, 1f, 1f, shadowColor)
                    }
                }
                if (isHorizontal) {
                    val authorText = TextUtils.ellipsize(
                        author,
                        TextPaint(paint),
                        viewWidth * 0.9f,
                        TextUtils.TruncateAt.END
                    )
                    if (CoverConfig.coverShowStroke) {
                        val strokePaint = Paint(paint).apply {
                            color = Color.White.toArgb()
                            style = Paint.Style.STROKE
                            strokeWidth = paint.textSize / 10
                            clearShadowLayer()
                        }
                        nativeCanvas.drawText(
                            authorText.toString(),
                            viewWidth / 2,
                            viewHeight * 0.75f,
                            strokePaint
                        )
                    }
                    nativeCanvas.drawText(
                        authorText.toString(),
                        viewWidth / 2,
                        viewHeight * 0.75f,
                        paint
                    )
                } else {
                    val startX = viewWidth * 0.84f
                    val fm = paint.fontMetrics
                    val charHeight = fm.bottom - fm.top
                    var startY = viewHeight * 0.16f - (author.length * charHeight)
                    startY = startY.coerceAtLeast(viewHeight * 0.2f)
                    author.forEach { char ->
                        nativeCanvas.drawText(char.toString(), startX, startY, paint)
                        startY += charHeight
                    }
                }
            }
        }
    }
}
