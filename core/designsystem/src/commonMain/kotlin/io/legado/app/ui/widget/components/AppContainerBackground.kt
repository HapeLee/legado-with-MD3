package io.legado.app.ui.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import io.legado.app.core.platform.NinePatchLoaderProvider
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppContainerBackgroundType {
    Large,
    Item,
}

/**
 * Paints the configured container image behind this layout's content.
 *
 * M1-3p 从 `:core:ui` 搬进 `:core:designsystem/commonMain`（包名不变 ⇒ 消费方 import 零改动）。
 * 搬动只换掉了两处「平台绑定」，主题/透明度/绘制语义与搬动前逐字一致：
 *
 * 1. **`.9.png` 的解码**原先直调 `BitmapFactory`/`NinePatch`/`NinePatchDrawable`，现在走
 *    `:core:platform` 的 [NinePatchLoaderProvider] 窄契约。desktop / iOS 不注入 ⇒ 一律 `null`
 *    ⇒ 回落成「按原路径交给 Coil」，与 Android 上「该文件不是九宫格」走的是同一条路径
 *    （差别只是那张图会按普通位图缩放，而不是按九宫格拉伸）。
 * 2. **`LocalContext` → `coil3.compose.LocalPlatformContext`**、**`LocalConfiguration` →
 *    `LocalWindowInfo.containerSize`**。前者在 Android 上取到的就是同一个 `Context`；后者在
 *    Android 上由 `WindowMetricsCalculator` 算出窗口像素尺寸、desktop 上是窗口组件的
 *    `sizeInPx`，而它只用作 Coil 的**解码目标尺寸**（不再是 `screenWidthDp`；多窗口下更贴合
 *   实际窗口，绘制结果不变）。
 *
 * 另外去掉了 `koinInject<ImageLoader>()`：`App` 实现 `coil3.SingletonImageLoader.Factory` 且
 * `newImageLoader(context)` 就是 `get()`（返回 Koin 里那个单例），所以省略该参数后
 * `rememberAsyncImagePainter` 经 `SingletonImageLoader` 拿到的**是同一个实例**，
 * 配置（crossfade / 各 Decoder / `CoverInterceptor`）一字不差。
 */
@Composable
fun Modifier.appContainerBackground(
    type: AppContainerBackgroundType = AppContainerBackgroundType.Large,
    backgroundImage: String? = null,
    useThemeBackground: Boolean = true,
    backgroundAlpha: Float? = null,
    contentScale: ContentScale = ContentScale.Crop,
): Modifier {
    val theme = LocalAppUiConfiguration.current.theme
    val configuredImage = if (useThemeBackground && theme.enableContainerBackgroundImage) {
        when (type) {
            AppContainerBackgroundType.Large -> if (LegadoTheme.isDark) {
                theme.largeContainerBackgroundImageDark
            } else {
                theme.largeContainerBackgroundImageLight
            }
            AppContainerBackgroundType.Item -> if (LegadoTheme.isDark) {
                theme.itemBackgroundImageDark
            } else {
                theme.itemBackgroundImageLight
            }
        }
    } else {
        null
    }
    val path = backgroundImage ?: configuredImage
    if (path.isNullOrBlank()) return this

    val alpha = backgroundAlpha ?: when (type) {
        AppContainerBackgroundType.Large -> theme.appColumnBackgroundOpacity / 100f
        AppContainerBackgroundType.Item -> theme.glassCardBackgroundOpacity / 100f
    }
    // 平台能力：Android 上是 `BitmapFactory` + `NinePatch` + `NinePatchDrawable`，其余平台是
    // 「一律 null」的兜底。它做磁盘 I/O，所以调用点仍在 IO 调度器上（搬动前后没变）。
    val ninePatch by produceState<Any?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { NinePatchLoaderProvider.current.load(path) }
    }
    val context = LocalPlatformContext.current
    val containerSize = LocalWindowInfo.current.containerSize
    val request = remember(context, path, ninePatch, containerSize) {
        ImageRequest.Builder(context)
            .data(ninePatch ?: path)
            .apply {
                // 窗口尚未布局时 `containerSize` 可能是 0（平台 impl 的初值）。搬动前读的是
                // `LocalConfiguration.screenWidthDp`，恒为正；这里同样只在拿到正尺寸时指定
                // 解码目标，否则交给 Coil 自己的约束尺寸——避免解析出 1×1。
                if (containerSize.width > 0 && containerSize.height > 0) {
                    size(containerSize.width, containerSize.height)
                }
            }
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request)
    return this
        .paint(
            painter = painter,
            sizeToIntrinsics = false,
            contentScale = if (ninePatch != null) ContentScale.FillBounds else contentScale,
            alpha = alpha.coerceIn(0f, 1f),
        )
}
