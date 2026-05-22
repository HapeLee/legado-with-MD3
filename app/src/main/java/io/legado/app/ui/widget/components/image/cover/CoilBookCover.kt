package io.legado.app.ui.widget.components.image.cover

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withSave
import coil.compose.AsyncImage
import io.legado.app.ui.config.coverConfig.CoverConfig
import io.legado.app.ui.theme.LegadoTheme
import org.koin.compose.koinInject
import io.legado.app.model.BookCover as BookCoverModel

private const val SharedCoverRadiusCacheMaxSize = 256
private val sharedCoverRadiusCache = mutableStateMapOf<String, Dp>()

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CoilBookCover(
    name: String?,
    author: String?,
    path: String?,
    radius: Dp = 4.dp,
    modifier: Modifier = Modifier.width(64.dp),
    sourceOrigin: String? = null,
    onLoadFinish: (() -> Unit)? = null,
    ignoreUseDefaultCover: Boolean = false,
    showLoadingPlaceholder: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    val context = LocalContext.current
    val isNight = isSystemInDarkTheme()

    val useDefault = !ignoreUseDefaultCover && CoverConfig.useDefaultCover
    val finalPath = if (useDefault) null else path

    val randomPath = remember(name, author, path, isNight) {
        BookCoverModel.getRandomDefaultPath(
            seed = name ?: author ?: path ?: "",
            isNight = isNight
        )
    }

    val hasCustomDefault = !randomPath.isNullOrBlank()
    var isOnlineCoverLoaded by remember(path, sharedCoverKey, finalPath) {
        mutableStateOf(sharedCoverKey != null && finalPath != null)
    }

    val transitionRadius = rememberSharedCoverTransitionRadius(
        sharedCoverKey = sharedCoverKey,
        radius = radius,
        animatedVisibilityScope = animatedVisibilityScope
    )
    val shape = remember(transitionRadius) { RoundedCornerShape(transitionRadius) }

    key(path, sharedCoverKey) {
        Box(
            modifier = modifier
                .aspectRatio(5f / 7f)
                .then(
                    with(sharedTransitionScope) {
                        if (this != null && animatedVisibilityScope != null && sharedCoverKey != null) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(sharedCoverKey),
                                animatedVisibilityScope = animatedVisibilityScope,
                                clipInOverlayDuringTransition = OverlayClip(shape)
                            )
                        } else Modifier
                    }
                )
                .then(
                    if (CoverConfig.coverShowShadow) {
                        Modifier.shadow(4.dp, shape)
                    } else Modifier
                )
                .background(
                    if (!hasCustomDefault && !isOnlineCoverLoaded) {
                        LegadoTheme.colorScheme.surfaceContainerLow
                    } else Color.Transparent,
                    shape
                )
                .clip(shape)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasCustomDefault && !isOnlineCoverLoaded) {
                    AsyncImage(
                        model = buildCoverImageRequest(
                            context = context,
                            data = randomPath,
                            sourceOrigin = null,
                            loadOnlyWifi = false,
                            crossfade = showLoadingPlaceholder,
                            memoryCacheKey = randomPath,
                        ),
                        contentDescription = null,
                        imageLoader = koinInject(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                    )
                }

                if (finalPath != null) {
                    AsyncImage(
                        model = buildCoverImageRequest(
                            context = context,
                            data = finalPath,
                            sourceOrigin = sourceOrigin,
                            loadOnlyWifi = CoverConfig.loadCoverOnlyWifi,
                            crossfade = showLoadingPlaceholder,
                            memoryCacheKey = finalPath,
                        ),
                        contentDescription = null,
                        imageLoader = koinInject(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = {
                            isOnlineCoverLoaded = true
                            onLoadFinish?.invoke()
                        },
                        onError = {
                            isOnlineCoverLoaded = false
                            onLoadFinish?.invoke()
                        }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        onLoadFinish?.invoke()
                    }
                }
            }

            if (showLoadingPlaceholder && !isOnlineCoverLoaded) {
                if (!hasCustomDefault) {
                    Icon(
                        Icons.Default.Book,
                        contentDescription = null,
                        tint = LegadoTheme.colorScheme.secondary,
                        modifier = Modifier
                            .fillMaxSize(0.35f)
                            .align(Alignment.Center)
                    )
                }
                CoverTextOverlay(
                    name = name,
                    author = author,
                    isNight = isNight
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun rememberSharedCoverTransitionRadius(
    sharedCoverKey: String?,
    radius: Dp,
    animatedVisibilityScope: AnimatedVisibilityScope?
): Dp {
    if (sharedCoverKey == null || animatedVisibilityScope == null) {
        return radius
    }

    val transition = animatedVisibilityScope.transition
    val startRadius = sharedCoverRadiusCache[sharedCoverKey] ?: radius
    val animatedRadiusValue by transition.animateFloat(
        label = "book-cover-corner-radius"
    ) { state ->
        if (state == EnterExitState.Visible) radius.value else startRadius.value
    }

    LaunchedEffect(
        sharedCoverKey,
        radius,
        transition.currentState,
        transition.targetState
    ) {
        if (
            transition.currentState == EnterExitState.Visible &&
            transition.targetState == EnterExitState.Visible
        ) {
            sharedCoverRadiusCache[sharedCoverKey] = radius
            if (sharedCoverRadiusCache.size > SharedCoverRadiusCacheMaxSize) {
                sharedCoverRadiusCache.keys
                    .firstOrNull { it != sharedCoverKey }
                    ?.let(sharedCoverRadiusCache::remove)
            }
        }
    }

    return animatedRadiusValue.dp
}

@Composable
private fun CoverTextOverlay(
    name: String?,
    author: String?,
    isNight: Boolean
) {
    val showName = if (isNight) CoverConfig.coverShowNameN else CoverConfig.coverShowName
    val showAuthor = (if (isNight) CoverConfig.coverShowAuthorN else CoverConfig.coverShowAuthor) && showName

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
                    val textPaint = TextPaint(paint).apply { textAlign = Paint.Align.LEFT }
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
                    val authorText = TextUtils.ellipsize(author, TextPaint(paint), viewWidth * 0.9f, TextUtils.TruncateAt.END)
                    if (CoverConfig.coverShowStroke) {
                        val strokePaint = Paint(paint).apply {
                            color = Color.White.toArgb()
                            style = Paint.Style.STROKE
                            strokeWidth = paint.textSize / 10
                            clearShadowLayer()
                        }
                        nativeCanvas.drawText(authorText.toString(), viewWidth / 2, viewHeight * 0.75f, strokePaint)
                    }
                    nativeCanvas.drawText(authorText.toString(), viewWidth / 2, viewHeight * 0.75f, paint)
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
