package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.widget.components.AppContainerBackgroundType
import io.legado.app.ui.widget.components.appContainerBackground
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults

/**
 * 玻璃卡片：[NormalCard] 的「带条目容器背景」变体，容器整体按 `containerOpacity` 调透明度。
 *
 * 与 [NormalCard] 的差别只有两点：透明度取主题的 `containerOpacity`，以及把**条目背景层**
 * 交给 `Modifier.appContainerBackground(Item)`。卡片表面本身由 [AppCardSurface] 提供，
 * 圆角/描边决议见 [resolveCardDecoration]——两处不再各写一份主题覆盖语义。
 *
 * **M1-3p 起本文件在 `:core:designsystem/commonMain`**。此前被钉在 `:core:ui` 的原因是
 * `appContainerBackground` 的闭环里有 `BitmapFactory`/`NinePatch` 读九宫格；那段已下沉为
 * `:core:platform` 的 `NinePatchLoader` 窄契约（desktop 侧不注入 ⇒ 按原路径加载），
 * 于是这条边不再跨不过去。包名沿用 `io.legado.app.ui.*`（共享命名空间）⇒ 调用方 import 零改动。
 *
 * 搬动时删掉了 `@OptIn(ExperimentalMaterialApi::class)` 与对应 import：那是 **material2**
 * 的注解，本文件从来没有用到 material2 的 API（`BorderStroke` 属 foundation），
 * 只是历史遗留；designsystem 不应为它引入 material2 依赖（同 M1-3f 对
 * `AppModalBottomSheet` 的处理）。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCardSurface(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        cornerRadius = cornerRadius,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        border = border,
        alpha = LocalAppUiConfiguration.current.theme.containerOpacity / 100f,
        itemBackground = Modifier.appContainerBackground(type = AppContainerBackgroundType.Item),
        content = content
    )
}
