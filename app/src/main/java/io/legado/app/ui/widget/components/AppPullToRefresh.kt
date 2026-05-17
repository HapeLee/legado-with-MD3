package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState as miuixRememberPullToRefreshState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    topPadding: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        // Let the miuix library handle gesture/animation, but skip the persistent
        // Refreshing state that blocks all scroll events.
        // localIsRefreshing is true only briefly so the indicator shows then hides,
        // while the actual refresh runs independently.
        var localIsRefreshing by remember { mutableStateOf(false) }

        LaunchedEffect(localIsRefreshing) {
            if (localIsRefreshing) {
                delay(300)
                localIsRefreshing = false
            }
        }

        MiuixPullToRefresh(
            isRefreshing = localIsRefreshing,
            onRefresh = {
                localIsRefreshing = true
                onRefresh()
            },
            modifier = modifier,
            contentPadding = PaddingValues(top = topPadding + 16.dp),
            pullToRefreshState = miuixRememberPullToRefreshState(),
            refreshTexts = listOf(
                "下拉刷新",
                "松开刷新",
                "正在刷新",
                "正在刷新"
            )
        ) {
            content()
        }
    } else {
        val state = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            state = state,
            enabled = enabled,
            indicator = {
                if (enabled) {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = state,
                        isRefreshing = isRefreshing,
                        modifier = Modifier
                            .padding(top = topPadding)
                            .align(Alignment.TopCenter),
                    )
                }
            }
        ) {
            content()
        }
    }
}
