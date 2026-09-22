package io.legado.app.ui.widget.components.privacy

import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import io.legado.app.ui.widget.components.image.cover.rememberSharedCoverTransitionRadius
import io.legado.app.ui.widget.components.text.AppText

/** 运行时模糊（`Modifier.blur`）从 API 31 起才生效，低版本是 no-op */
val RuntimeBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * 脱敏封面的模糊深度。
 *
 * 书架卡片锁定态与详情页脱敏态共用这一个值：两端糊得不一样的话，
 * 共享元素转场飞到一半会看到一次"糊度跳变"。
 */
val PrivateLockedCoverBlurRadius: Dp = 20.dp

private val LOCKED_COVER_RADIUS = 4.dp

/**
 * 锁定封面上的"已隐藏"叠层：遮罩 + 动态点阵 + 锁标。
 *
 * **必须渲染在共享元素节点内部**：转场时 overlay 只搬运 sharedBounds 节点自己的内容，
 * 放成兄弟节点就会出现"装饰留在原地、只有封面在飞"。所以它由 [PrivateLockedCover]
 * 与书架卡片共用同一份实现，而不是各自拼装三层。
 *
 * [modifier] 供调用方控制整体淡入淡出（卡片解锁时的 alpha、详情页转场的 enter/exit）。
 */
@Composable
fun PrivateLockedCoverOverlay(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LegadoTheme.colorScheme.surface.copy(alpha = 0.45f))
        )
        // 点阵画在模糊层之外，所以它本身是清晰的，不会被封面的模糊一起糊掉
        PrivateDotMatrix(
            color = LegadoTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxSize(),
        )
        PrivateLockIcon(modifier = Modifier.size(20.dp))
    }
}

/**
 * 锁定态的封面：真实封面只以强模糊形式出现，并叠一层遮罩与锁标。
 *
 * 模糊有两条互斥路径，选择权在这里而不是调用方：
 * - API 31+ 走运行时 `Modifier.blur(blurRadius)`，作用在封面图这个**共享元素内部的子节点**上，
 *   所以转场期间它带着模糊一起飞，并且半径是连续值、可以被调用方动画（书架卡片的聚焦效果）；
 * - 低版本 `Modifier.blur` 是 no-op，退回 Coil 变换烘出的静态模糊图（放弃动画）。
 *   此时缓存键必须与清晰封面区分，否则模糊结果会污染正常封面的缓存。
 *
 * 共享元素按 [CoilBookCover] 的写法挂在这一层：
 * 1. 带 `clipInOverlayDuringTransition`，动画期间 overlay 里也有圆角；
 * 2. 圆角用 `rememberSharedCoverTransitionRadius` 与源端插值，两端衔接不跳变。
 * 叠加层（遮罩/点阵/锁标）也必须留在 sharedBounds **内部**，否则飞起来会掉队。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PrivateLockedCover(
    name: String?,
    author: String?,
    path: String?,
    modifier: Modifier = Modifier,
    sourceOrigin: String? = null,
    bookUrl: String? = null,
    coverSize: Size = Size(96, 134),
    radius: Dp = LOCKED_COVER_RADIUS,
    blurRadius: Dp = PrivateLockedCoverBlurRadius,
    sharedCoverKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val transitionRadius = rememberSharedCoverTransitionRadius(
        sharedCoverKey = sharedCoverKey,
        radius = radius,
        animatedVisibilityScope = animatedVisibilityScope
    )
    val shape = remember(transitionRadius) { RoundedCornerShape(transitionRadius) }
    val blurEnabled = blurRadius > 0.dp
    val useRuntimeBlur = blurEnabled && RuntimeBlurSupported
    val useCoilBlur = blurEnabled && !RuntimeBlurSupported
    Box(
        modifier = modifier
            .then(
                with(sharedTransitionScope) {
                    if (this != null && animatedVisibilityScope != null && sharedCoverKey != null) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(sharedCoverKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                            clipInOverlayDuringTransition = OverlayClip(shape)
                        )
                    } else {
                        Modifier
                    }
                }
            )
            .clip(shape)
    ) {
        // sharedCoverKey 这里只当缓存键用；sharedBounds 由上面的 Box 承担，
        // 避免同一 key 出现两个持有者。
        //
        // preferCache = true 是有意的权衡：锁定态还需要显示"真实封面的模糊版"（否则用户
        // 连自己有没有这本书都认不出），但**不应该**因此联网拉新链接或执行书源规则。
        // 代价是锁定期间不会刷新封面，等解锁后由正常封面路径补上。
        BookCoverImage(
            name = name,
            author = author,
            path = path,
            sourceOrigin = sourceOrigin,
            bookUrl = bookUrl,
            preferCache = true,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (useRuntimeBlur) {
                        Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded)
                    } else {
                        Modifier
                    }
                ),
            // 独立缓存键：运行时模糊用的是清晰图，但请求尺寸（见下）与书架卡片不同，
            // 共用键会让卡片拿到小图而发虚
            memoryCacheKey = path?.let { "$it#private-locked" },
            showLoadingPlaceholder = false,
            sharedCoverKey = sharedCoverKey,
            requestBuilder = {
                allowHardware(false)
                size(coverSize)
                if (useCoilBlur) {
                    transformations(PrivateCoverBlurTransformation())
                }
            },
        )
        // 叠加层走 enter/exit 动画跟随转场；渲染位置在共享节点内部，由组件统一保证
        val overlayModifier = Modifier.then(
            if (animatedVisibilityScope != null) {
                with(animatedVisibilityScope) {
                    Modifier.animateEnterExit(enter = fadeIn(), exit = fadeOut())
                }
            } else {
                Modifier
            }
        )
        PrivateLockedCoverOverlay(modifier = overlayModifier)
    }
}

@Composable
fun PrivateLockIcon(
    modifier: Modifier = Modifier,
    tint: Color = LegadoTheme.colorScheme.onSurfaceVariant,
) {
    AppIcon(
        modifier = modifier,
        imageVector = Icons.Default.Lock,
        contentDescription = null,
        tint = tint
    )
}

/**
 * 文本脱敏占位条。
 *
 * 锁定态绝不渲染真实书名/作者/简介，一律用这种圆角条代替——
 * 文字无法像图片那样可靠地"模糊"（低版本 blur 会失效），所以直接不渲染。
 */
@Composable
fun PrivateMaskLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.6f,
    thickness: Dp = 10.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(thickness)
            .clip(RoundedCornerShape(thickness / 2))
            .background(LegadoTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    )
}

/** 列表模式下替代书名/作者/简介的一组占位条 */
@Composable
fun PrivateMaskTextLines(
    modifier: Modifier = Modifier,
    lineWidths: List<Float> = listOf(0.5f, 0.32f),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lineWidths.forEachIndexed { index, width ->
            PrivateMaskLine(
                widthFraction = width,
                thickness = if (index == 0) 12.dp else 9.dp
            )
        }
    }
}

/**
 * 锁定提示块：锁标 + 标题 + 说明 + 验证按钮。
 *
 * 抽出来是为了让"私密分组整页隐藏"与"详情页就地脱敏"用同一套提示，不再各写一份。
 */
@Composable
fun PrivateLockedHint(
    title: String,
    description: String?,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(LegadoTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            PrivateLockIcon(
                modifier = Modifier.size(32.dp),
                tint = LegadoTheme.colorScheme.onSurfaceVariant
            )
        }
        AppText(
            text = title,
            style = LegadoTheme.typography.titleMediumEmphasized,
            textAlign = TextAlign.Center
        )
        if (!description.isNullOrBlank()) {
            AppText(
                text = description,
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        PrimaryButton(
            onClick = onAction,
            text = actionText,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** 整页锁定态：私密分组的内容区复用它 */
@Composable
fun PrivateLockedPage(
    title: String,
    description: String?,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        PrivateLockedHint(
            title = title,
            description = description,
            actionText = actionText,
            onAction = onAction,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

