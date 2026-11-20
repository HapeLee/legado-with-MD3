package io.legado.app.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

/**
 * 横划顶栏模式下挂在页面底部的页签条（书架分组 / 发现分类）。
 *
 * 显隐规则：
 *
 * 1. **占位高度 = 导航栏高度 + 页签高度，收起后归零**。导航栏内边距必须算进来，
 *    否则页签会直接压在系统手势条上；完全收起时高度必须归零，正文才能像其它页签一样
 *    铺到屏幕下沿（底部可滚动留白由各页的 `LocalBottomOverlayPadding` 负责）。
 * 2. **滑动要一路滑到导航栏下面**。裁切窗口的底边始终是屏幕下沿，页签顶对齐放进窗口，
 *    窗口一收窄，页签就跟着窗口上沿往下载：先经过导航栏区域，再在屏幕下沿消失，
 *    不会停在导航栏上沿，也不会被横切一半。
 * 3. **内容不能随显隐销毁**。OxygenX 的 `TabRow` 指示器从 0 宽 / 左边缘动画到选中页签，
 *    横向滚动位置也只在首次布局时对齐；用 `AnimatedVisibility` 收起会销毁内容，
 *    于是每次重新出现，指示器都会从第一个页签"蹦"过来。
 *
 * 所以不用 `AnimatedVisibility`：页签常驻组合，只动画一个 0→1 的收起进度，
 * 由它同时决定容器高度与页签位移（页签顶对齐，位移由窗口上沿带来），两者天然同步。
 */
@Composable
fun MainPagerBottomTabBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val navigationBarBottom = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    // 0 = 完全展开，1 = 完全收起
    val collapse = remember { Animatable(if (visible) 0f else 1f) }
    var tabHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(visible) {
        val target = if (visible) 0f else 1f
        if (collapse.value == target) return@LaunchedEffect
        collapse.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        )
    }

    val tabHeight = with(density) { tabHeightPx.toDp() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 展开时留出导航栏高度给页签垫脚，收起时整条（含导航栏高度）一起归零
            .height((navigationBarBottom + tabHeight) * (1f - collapse.value))
            .clipToBounds()
    ) {
        // 顶对齐 + 按自然高度测量：容器变矮时页签跟着窗口上沿下滑，
        // 裁切边界始终是屏幕下沿，于是它会先划过导航栏区域再消失
        Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { tabHeightPx = it.height }
            ) {
                content()
            }
        }
    }
}
