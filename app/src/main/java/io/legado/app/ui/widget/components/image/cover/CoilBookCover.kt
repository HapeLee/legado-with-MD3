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
import coil.request.ImageRequest
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import org.koin.compose.koinInject
import io.legado.app.model.BookCover as BookCoverModel

private const val SharedCoverRadiusCacheMaxSize = 256
private const val DefaultCoverPath = "use_default_cover"
private val sharedCoverRadiusCache = mutableStateMapOf<String, Dp>()

@Composable
internal fun usesDefaultBookCover(path: String?): Boolean {
    return LocalAppUiConfiguration.current.cover.useDefaultCover ||
            path.isNullOrBlank() ||
            path == DefaultCoverPath
}

@Composable
fun BookCoverImage(
    name: String?,
    author: String?,
    path: String?,
    modifier: Modifier = Modifier,
    sourceOrigin: String? = null,
    memoryCacheKey: String? = null,
    ignoreUseDefaultCover: Boolean = false,
    showLoadingPlaceholder: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    onLoadFinish: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
    sharedCoverKey: String? = null,
    requestBuilder: ImageRequest.Builder.() -> Unit = {},
) {
    val context = LocalContext.current
    val isNight = LegadoTheme.isDark
    val coverSettings = LocalAppUiConfiguration.current.cover

    val useDefault = (!ignoreUseDefaultCover && coverSettings.useDefaultCover) ||
            path.isNullOrBlank() ||
            path == DefaultCoverPath
    val finalPath = if (useDefault) null else path
    val defaultCoverPaths =
        if (isNight) coverSettings.defaultCoverDark else coverSettings.defaultCover

    val randomPath = remember(name, author, path, isNight, defaultCoverPaths) {
        BookCoverModel.getRandomDefaultPath(
            seed = name ?: author ?: path ?: "",
            isNight = isNight
        )
    }

    val hasCustomDefault = !randomPath.isNullOrBlank()
    val customDefaultMemoryCacheKey =
        if (finalPath == null && sharedCoverKey != null) {
            "$sharedCoverKey:default:$randomPath"
        } else {
            randomPath
        }
    var isOnlineCoverLoaded by remember(finalPath) { mutableStateOf(false) }
    var onlineCoverLoadFailed by remember(finalPath) { mutableStateOf(false) }

    LaunchedEffect(finalPath) {
        if (finalPath == null) {
            isOnlineCoverLoaded = false
            onlineCoverLoadFailed = false
        }
    }

    val isUsingDefaultCover = finalPath == null || onlineCoverLoadFailed
    val showLoadingDefault = sharedCoverKey == null && !isOnlineCoverLoaded
    val showCustomDefault = hasCustomDefault &&
        !isOnlineCoverLoaded &&
        (isUsingDefaultCover || showLoadingDefault)
    val showDefaultIcon = !hasCustomDefault &&
        (
            isUsingDefaultCover ||
                (showLoadingPlaceholder && showLoadingDefault)
        )
    Box(modifier = modifier) {
        if (showCustomDefault) {
            AsyncImage(
                model = buildCoverImageRequest(
                    context = context,
                    data = randomPath,
                    sourceOrigin = null,
                    loadOnlyWifi = false,
                    crossfade = showLoadingPlaceholder,
                    memoryCacheKey = customDefaultMemoryCacheKey,
                ),
                contentDescription = null,
                imageLoader = koinInject(),
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showDefaultIcon) {
            Icon(
                Icons.Default.Book,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxSize(0.35f)
                    .align(Alignment.Center)
            )
        }

        if (finalPath != null) {
            AsyncImage(
                model = buildCoverImageRequest(
                    context = context,
                    data = finalPath,
                    sourceOrigin = sourceOrigin,
                    loadOnlyWifi = coverSettings.loadOnlyOnWifi,
                    crossfade = showLoadingPlaceholder,
                    memoryCacheKey = sharedCoverKey?.let {
                        "$it:cover:${memoryCacheKey ?: finalPath}"
                    } ?: memoryCacheKey ?: finalPath,
                    configure = requestBuilder,
                ),
                contentDescription = null,
                imageLoader = koinInject(),
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onSuccess = {
                    isOnlineCoverLoaded = true
                    onlineCoverLoadFailed = false
                    onSuccess?.invoke()
                    onLoadFinish?.invoke()
                },
                onError = {
                    isOnlineCoverLoaded = false
                    onlineCoverLoadFailed = true
                    onError?.invoke()
                    onLoadFinish?.invoke()
                }
            )
        } else {
            LaunchedEffect(Unit) {
                onLoadFinish?.invoke()
            }
        }
    }
}

// 改成BookCover
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
    onError: (() -> Unit)? = null,
    ignoreUseDefaultCover: Boolean = false,
    showLoadingPlaceholder: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    val coverSettings = LocalAppUiConfiguration.current.cover
    val isNight = LegadoTheme.isDark

    val useDefault = (!ignoreUseDefaultCover && coverSettings.useDefaultCover) ||
            path.isNullOrBlank() ||
            path == DefaultCoverPath
    val finalPath = if (useDefault) null else path
    val defaultCoverPaths =
        if (isNight) coverSettings.defaultCoverDark else coverSettings.defaultCover

    val randomPath = remember(name, author, path, isNight, defaultCoverPaths) {
        BookCoverModel.getRandomDefaultPath(
            seed = name ?: author ?: path ?: "",
            isNight = isNight
        )
    }

    val hasCustomDefault = !randomPath.isNullOrBlank()
    var isOnlineCoverLoaded by remember(finalPath) { mutableStateOf(false) }
    var onlineCoverLoadFailed by remember(finalPath) { mutableStateOf(false) }

    LaunchedEffect(finalPath) {
        if (finalPath == null) {
            isOnlineCoverLoaded = false
            onlineCoverLoadFailed = false
        }
    }

    val transitionRadius = rememberSharedCoverTransitionRadius(
        sharedCoverKey = sharedCoverKey,
        radius = radius,
        animatedVisibilityScope = animatedVisibilityScope
    )
    val shape = remember(transitionRadius) { RoundedCornerShape(transitionRadius) }

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
                if (coverSettings.showShadow) {
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
        BookCoverImage(
            name = name,
            author = author,
            path = path,
            modifier = Modifier.fillMaxSize(),
            sourceOrigin = sourceOrigin,
            ignoreUseDefaultCover = ignoreUseDefaultCover,
            showLoadingPlaceholder = showLoadingPlaceholder,
            onSuccess = {
                isOnlineCoverLoaded = true
                onlineCoverLoadFailed = false
                onLoadFinish?.invoke()
            },
            onError = {
                isOnlineCoverLoaded = false
                onlineCoverLoadFailed = true
                onError?.invoke()
                onLoadFinish?.invoke()
            },
            sharedCoverKey = sharedCoverKey
        )

        if (
            finalPath == null ||
            onlineCoverLoadFailed ||
            (
                sharedCoverKey == null &&
                    showLoadingPlaceholder &&
                    !isOnlineCoverLoaded
                )
        ) {
            CoverTextOverlay(
                name = name,
                author = author,
                isNight = isNight
            )
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

/**
 * Determine if text is primarily Latin-script.
 * Returns true if more than 30% of characters are Latin letters.
 */
private fun isLatinBasedText(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val latinRatio = text.count { it in 'A'..'Z' || it in 'a'..'z' }.toFloat() / text.length
    return latinRatio > 0.3f
}

@Composable
private fun CoverTextOverlay(
    name: String?,
    author: String?,
    isNight: Boolean
) {
    val coverSettings = LocalAppUiConfiguration.current.cover
    val showName = if (isNight) coverSettings.showNameDark else coverSettings.showName
    val showAuthor =
        (if (isNight) coverSettings.showAuthorDark else coverSettings.showAuthor) && showName

    if (!showName && !showAuthor) return

    val secondaryColor = MaterialTheme.colorScheme.secondary.toArgb()
    val textColor = if (coverSettings.useDefaultColor) {
        secondaryColor
    } else {
        if (isNight) coverSettings.textColorDark else coverSettings.textColor
    }
    val shadowColor =
        if (isNight) coverSettings.shadowColorDark else coverSettings.shadowColor
    val configIsHorizontal = coverSettings.infoOrientation == "1"
    // If text contains Latin letters, force horizontal layout
    val isHorizontal = configIsHorizontal || isLatinBasedText(name)

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
                    if (coverSettings.showShadow) {
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
                        if (coverSettings.showStroke) {
                            textPaint.style = Paint.Style.STROKE
                            textPaint.strokeWidth = textPaint.textSize / 12
                            val originalColor = textPaint.color
                            textPaint.color = Color.White.toArgb()
                            textPaint.clearShadowLayer()
                            layout.draw(this)
                            textPaint.style = Paint.Style.FILL
                            textPaint.color = originalColor
                            if (coverSettings.showShadow) {
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
                        if (coverSettings.showStroke) {
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
                    if (coverSettings.showShadow) {
                        setShadowLayer(4f, 1f, 1f, shadowColor)
                    }
                }
                if (isHorizontal) {
                    val authorText = TextUtils.ellipsize(author, TextPaint(paint), viewWidth * 0.9f, TextUtils.TruncateAt.END)
                    if (coverSettings.showStroke) {
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
