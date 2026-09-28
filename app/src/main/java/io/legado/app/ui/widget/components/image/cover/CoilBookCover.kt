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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import org.koin.compose.koinInject
import io.legado.app.model.BookCover as BookCoverModel

private const val SharedCoverRadiusCacheMaxSize = 256

/** 用户在换封面页选的“默认封面”，与空地址一样表示这本书没有真实封面。 */
internal const val DefaultCoverPath = "use_default_cover"
private val sharedCoverRadiusCache = mutableStateMapOf<String, Dp>()

/**
 * 这本书是否有真实封面地址可加载。
 *
 * 与 [usesDefaultBookCover] 的区别：这里只看地址本身，不读 Compose 配置，
 * 因此预热这类非组合场景也能复用同一判定。
 */
internal fun isDefaultCoverPath(path: String?): Boolean =
    path.isNullOrBlank() || path == DefaultCoverPath

/**
 * 封面在源页面的圆角缓存读取入口：封面离开源页面（Visible→Visible 定格）时写入，
 * 阅读端 sharedBounds 的起始圆角由它提供，保证转场两端圆角衔接连续。
 */
internal fun sharedCoverSourceRadius(sharedCoverKey: String?): Dp? =
    sharedCoverKey?.let { sharedCoverRadiusCache[it] }

@Composable
internal fun usesDefaultBookCover(path: String?): Boolean {
    return LocalAppUiConfiguration.current.cover.useDefaultCover || isDefaultCoverPath(path)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookCoverImage(
    name: String?,
    author: String?,
    path: String?,
    modifier: Modifier = Modifier,
    sourceOrigin: String? = null,
    memoryCacheKey: String? = null,
    // 本书 bookUrl（别名缓存键）与书架本地优先标志，透传给 buildCoverImageRequest。
    bookUrl: String? = null,
    preferCache: Boolean = false,
    ignoreUseDefaultCover: Boolean = false,
    showLoadingPlaceholder: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    onLoadFinish: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
    sharedCoverKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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
    Box(
        modifier = modifier.then(
            with(sharedTransitionScope) {
                if (this != null && animatedVisibilityScope != null && sharedCoverKey != null) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(sharedCoverKey),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                } else {
                    Modifier
                }
            }
        )
    ) {
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
                    memoryCacheKey = coverMemoryCacheKey(
                        sharedCoverKey = sharedCoverKey,
                        explicitKey = memoryCacheKey,
                        path = finalPath,
                    ),
                    bookUrl = bookUrl,
                    preferCache = preferCache,
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
    // 本书 bookUrl + 书架本地优先标志，透传给 BookCoverImage
    bookUrl: String? = null,
    preferCache: Boolean = false,
    onLoadFinish: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
    ignoreUseDefaultCover: Boolean = false,
    showLoadingPlaceholder: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
    /**
     * 内容模糊半径，作用于封面图与占位文字这些**共享元素内部的子节点**。
     *
     * 之所以要传进来而不是让调用方在外面套 `Modifier.blur`：共享元素转场时，
     * overlay 只会搬运 sharedBounds 节点自己的内容，加在祖先上的模糊会被落下，
     * 表现就是"动画一开始模糊突然没了"。
     */
    contentBlur: Dp = 0.dp,
    /**
     * 盖在封面之上的叠加层（遮罩、点阵、锁标…），渲染在共享节点**内部**。
     *
     * 放成兄弟节点的话转场时不会被 overlay 带走，会出现"装饰停在原地、只有封面在飞"。
     */
    overlayContent: (@Composable BoxScope.() -> Unit)? = null,
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
    val contentBlurModifier = if (contentBlur > 0.dp) {
        Modifier.blur(contentBlur, BlurredEdgeTreatment.Unbounded)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(5f / 7f)
            .then(
                with(sharedTransitionScope) {
                    if (this != null && animatedVisibilityScope != null && sharedCoverKey != null) {
                        Modifier.sharedBounds(
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
            modifier = Modifier
                .fillMaxSize()
                .then(contentBlurModifier),
            sourceOrigin = sourceOrigin,
            bookUrl = bookUrl,
            preferCache = preferCache,
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
            // 占位文字（默认封面上的书名/作者）也一起模糊：
            // 它露的是真实字符串，锁定态不能比正常态更清晰
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(contentBlurModifier)
            ) {
                CoverTextOverlay(
                    name = name,
                    author = author,
                    isNight = isNight
                )
            }
        }

        // 遮罩/点阵/锁标等叠加层渲染在共享节点内部，转场时会随封面一起移动
        overlayContent?.invoke(this)
    }
}


/**
 * 转场两端的圆角：起点用源页面缓存下来的圆角，终点用本节点的 [radius]，
 * 期间随转场进度插值，避免两端圆角不一致时跳变。脱敏封面复用同一实现。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun rememberSharedCoverTransitionRadius(
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

    // Paints, StaticLayout and per-character positions are built in the cache block so they are
    // rebuilt only when the size or the settings above change, not on every draw pass.
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val viewWidth = size.width
                val viewHeight = size.height
                if (viewWidth <= 0f || viewHeight <= 0f) {
                    return@drawWithCache onDrawBehind { }
                }

                val namePaint = if (showName && !name.isNullOrBlank()) {
                    Paint().apply {
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                        textSize = viewWidth / 8f
                        color = textColor
                        if (coverSettings.showShadow) {
                            setShadowLayer(4f, 2f, 2f, shadowColor)
                        }
                    }
                } else null

                val nameMaxWidth = (viewWidth * 0.8f).toInt().coerceAtLeast(1)
                val nameTextPaint = if (namePaint != null && isHorizontal) {
                    TextPaint(namePaint).apply { textAlign = Paint.Align.LEFT }
                } else null
                val nameLayout = if (nameTextPaint != null && name != null) {
                    StaticLayout.Builder
                        .obtain(name, 0, name.length, nameTextPaint, nameMaxWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setMaxLines(3)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build()
                } else null
                val nameLayoutX = (viewWidth - nameMaxWidth) / 2f
                val nameLayoutY = viewHeight * 0.08f

                val nameStrokePaint =
                    if (namePaint != null && !isHorizontal && coverSettings.showStroke) {
                        Paint(namePaint).apply {
                            color = Color.White.toArgb()
                            style = Paint.Style.STROKE
                            strokeWidth = namePaint.textSize / 10
                            clearShadowLayer()
                        }
                    } else null
                val nameCharDraws = if (namePaint != null && name != null && !isHorizontal) {
                    val charHeight = namePaint.fontMetrics.let { it.bottom - it.top }
                    var startX = viewWidth * 0.16f
                    var startY = viewHeight * 0.16f
                    name.map { char ->
                        val draw = Triple(char.toString(), startX, startY)
                        startY += charHeight
                        if (startY > viewHeight * 0.8f) {
                            startX += namePaint.textSize * 1.2f
                            startY = viewHeight * 0.2f
                        }
                        draw
                    }
                } else emptyList()

                val authorPaint = if (showAuthor && !author.isNullOrBlank()) {
                    Paint().apply {
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                        textSize = viewWidth / 12f
                        color = textColor
                        if (coverSettings.showShadow) {
                            setShadowLayer(4f, 1f, 1f, shadowColor)
                        }
                    }
                } else null

                val authorText = if (authorPaint != null && author != null && isHorizontal) {
                    TextUtils.ellipsize(
                        author,
                        TextPaint(authorPaint),
                        viewWidth * 0.9f,
                        TextUtils.TruncateAt.END
                    ).toString()
                } else null
                val authorStrokePaint =
                    if (authorPaint != null && isHorizontal && coverSettings.showStroke) {
                        Paint(authorPaint).apply {
                            color = Color.White.toArgb()
                            style = Paint.Style.STROKE
                            strokeWidth = authorPaint.textSize / 10
                            clearShadowLayer()
                        }
                    } else null
                val authorCharDraws = if (authorPaint != null && author != null && !isHorizontal) {
                    val charHeight = authorPaint.fontMetrics.let { it.bottom - it.top }
                    val startX = viewWidth * 0.84f
                    var startY = (viewHeight * 0.16f - (author.length * charHeight))
                        .coerceAtLeast(viewHeight * 0.2f)
                    author.map { char ->
                        val draw = Triple(char.toString(), startX, startY)
                        startY += charHeight
                        draw
                    }
                } else emptyList()

                onDrawBehind {
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas

                        if (nameLayout != null && nameTextPaint != null) {
                            nativeCanvas.withSave {
                                translate(nameLayoutX, nameLayoutY)
                                if (coverSettings.showStroke) {
                                    nameTextPaint.style = Paint.Style.STROKE
                                    nameTextPaint.strokeWidth = nameTextPaint.textSize / 12
                                    val originalColor = nameTextPaint.color
                                    nameTextPaint.color = Color.White.toArgb()
                                    nameTextPaint.clearShadowLayer()
                                    nameLayout.draw(this)
                                    nameTextPaint.style = Paint.Style.FILL
                                    nameTextPaint.color = originalColor
                                    if (coverSettings.showShadow) {
                                        nameTextPaint.setShadowLayer(4f, 2f, 2f, shadowColor)
                                    }
                                }
                                nameLayout.draw(this)
                            }
                        } else if (namePaint != null) {
                            nameCharDraws.forEach { (text, x, y) ->
                                if (nameStrokePaint != null) {
                                    nativeCanvas.drawText(text, x, y, nameStrokePaint)
                                }
                                nativeCanvas.drawText(text, x, y, namePaint)
                            }
                        }

                        if (authorPaint != null) {
                            if (authorText != null) {
                                if (authorStrokePaint != null) {
                                    nativeCanvas.drawText(
                                        authorText,
                                        viewWidth / 2,
                                        viewHeight * 0.75f,
                                        authorStrokePaint
                                    )
                                }
                                nativeCanvas.drawText(
                                    authorText,
                                    viewWidth / 2,
                                    viewHeight * 0.75f,
                                    authorPaint
                                )
                            } else {
                                authorCharDraws.forEach { (text, x, y) ->
                                    nativeCanvas.drawText(text, x, y, authorPaint)
                                }
                            }
                        }
                    }
                }
            }
    )
}
