package io.legado.app.ui.widget.components.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import kotlinx.coroutines.delay

/** 悬浮按钮：停手后再等这么久才收（沉浸式，不是停手即消失 —— 否则想点的时候按钮正好没了）。 */
private const val HideDelayMillis = 1500L

/**
 * 滚动悬浮按钮的状态：滚动中出现、停手 [HideDelayMillis] 后收起，并记住最近一次滚动方向。
 *
 * **只负责状态记录**；具体动作（定位 / 回顶 / 回底）由宿主页面自己实现 —— 见 [rememberScrollFabState]。
 */
@Stable
class ScrollFabState internal constructor() {
    /** 当前是否该显示（滚动中出现、且还没到收起时刻）。 */
    var visible by mutableStateOf(false)
        internal set

    /** 最近一次滚动方向：true = 上滑（在看更靠后的内容）⇒ 箭头朝下、点了去底部。 */
    var forward by mutableStateOf(true)
        internal set
}

/**
 * 订阅宿主的滚动来源，产出 [ScrollFabState]。
 *
 * @param sourceKey 滚动来源的标识（例如"当前布局是否流式"）：变化时重新订阅。
 * @param scrollingProvider 当前是否正在滚动（`LazyListState` / `LazyGridState` 的 `isScrollInProgress`，
 *  `ScrollState` 同款）。
 * @param positionProvider 当前位置：Lazy 列表/网格 = `下标 to item 内偏移`；普通滚动 = `像素 to 0`。
 *  两种来源都是"递增有序"的数值对，所以判方向的逻辑可以共用。
 *
 * 注意：provider 只在 [sourceKey] 变化时被重新捕获 —— 宿主传进来的 lambda 每次重组都会新建，
 * 但里面读的滚动状态对象是稳定的，所以不会读到旧数据。
 */
@Composable
fun rememberScrollFabState(
    sourceKey: Any?,
    scrollingProvider: () -> Boolean,
    positionProvider: () -> Pair<Int, Int>,
): ScrollFabState {
    val state = remember { ScrollFabState() }
    val scrolling by remember(sourceKey) { derivedStateOf { scrollingProvider() } }
    // 位置差分只用来判方向 + 点亮按钮
    LaunchedEffect(sourceKey) {
        var previous: Pair<Int, Int>? = null
        snapshotFlow(positionProvider).collect { current ->
            val prev = previous
            previous = current
            if (prev != null && current != prev) {
                state.forward = current.first > prev.first ||
                    (current.first == prev.first && current.second > prev.second)
                state.visible = true
            }
        }
    }
    // 沉浸式：停手后过一会儿才收
    LaunchedEffect(scrolling) {
        if (!scrolling) {
            delay(HideDelayMillis)
            state.visible = false
        }
    }
    return state
}

/**
 * 右下角滚动悬浮按钮：定位（可选）+ 回顶/回底（图标与动作都按最近一次滚动方向切）。
 *
 * @param visible 宿主的总开关 —— 多选态、找不到当前项等场合传 false ⇒ 整组隐藏。
 * @param onLocate 定位动作；null ⇒ 不画定位钮（例如流式布局没有下标定位）。
 * @param locateContentDescription 定位钮的无障碍文案（默认"定位至当前阅读"）。
 */
@Composable
fun ScrollFabButtons(
    state: ScrollFabState,
    visible: Boolean,
    onScrollTop: () -> Unit,
    onScrollBottom: () -> Unit,
    modifier: Modifier = Modifier,
    onLocate: (() -> Unit)? = null,
    locateContentDescription: String = stringResource(R.string.locate_current_reading),
) {
    AnimatedVisibility(
        visible = visible && state.visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onLocate != null) {
                SmallTonalButton(
                    onClick = onLocate,
                    icon = Icons.Outlined.MyLocation,
                    containerColor = LegadoTheme.colorScheme.secondaryContainer,
                    contentDescription = locateContentDescription,
                )
            }
            val jumpToBottom = state.forward
            SmallTonalButton(
                onClick = { if (jumpToBottom) onScrollBottom() else onScrollTop() },
                icon = if (jumpToBottom) {
                    Icons.Default.VerticalAlignBottom
                } else {
                    Icons.Default.VerticalAlignTop
                },
                containerColor = LegadoTheme.colorScheme.secondaryContainer,
                contentDescription = stringResource(
                    if (jumpToBottom) R.string.theme_list_back_to_bottom
                    else R.string.theme_list_back_to_top
                ),
            )
        }
    }
}
