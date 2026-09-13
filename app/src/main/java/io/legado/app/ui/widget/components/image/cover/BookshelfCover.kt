package io.legado.app.ui.widget.components.image.cover

// ============================================================================
// [FIX-AI] 本文件由 AI 助手（Chatbox）修改（2026-09-13）。
// 搜索 [FIX-AI] 可定位本文件全部改动点，每处均注明 原版行为 -> 修复后行为。
// 问题背景与完整清单见 LegadoMD3/fix/README.md。
// ============================================================================


import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookshelfCover(
    name: String?,
    author: String?,
    path: String?,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier.fillMaxWidth(),
    isUpdating: Boolean = false,
    badgeText: String? = null,
    showBadgeDot: Boolean = false,
    leftBottomText: String? = null,
    sourceOrigin: String? = null,
    // [FIX-AI] 新增（原版无）：本书 bookUrl，供封面别名缓存键；书架组件默认本地优先
    // （preferCache=true）：有缓存（含别名命中）直接显示，不跑书源规则脚本、不联网。
    bookUrl: String? = null,
    onLoadFinish: (() -> Unit)? = null,
    showLoadingPlaceholder: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    Box(modifier = modifier) {
        CoilBookCover(
            name = name,
            author = author,
            path = path,
            modifier = coverModifier,
            sourceOrigin = sourceOrigin,
            bookUrl = bookUrl,        // [FIX-AI] 新增透传
            preferCache = true,       // [FIX-AI] 书架组件：本地优先
            onLoadFinish = onLoadFinish,
            showLoadingPlaceholder = showLoadingPlaceholder,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedCoverKey = sharedCoverKey,
        )

        // 使用 animatedVisibilityScope 的 animateEnterExit 为叠加层添加同步动画
        val overlayModifier = Modifier.then(
            if (animatedVisibilityScope != null) {
                with(animatedVisibilityScope) {
                    Modifier.animateEnterExit(
                        enter = fadeIn(),
                        exit = fadeOut()
                    )
                }
            } else Modifier
        )

        if (!badgeText.isNullOrEmpty()) {
            TextCard(
                text = badgeText,
                icon = if (showBadgeDot) Icons.Default.Update else null,
                iconSize = 12.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .then(overlayModifier),
                cornerRadius = 4.dp,
                horizontalPadding = 4.dp,
                verticalPadding = 2.dp
            )
        }

        if (!leftBottomText.isNullOrEmpty()) {
            TextCard(
                text = leftBottomText,
                backgroundColor = LegadoTheme.colorScheme.cardContainer,
                contentColor = LegadoTheme.colorScheme.onCardContainer,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(2.dp)
                    .then(overlayModifier),
                cornerRadius = 4.dp,
                horizontalPadding = 4.dp,
                verticalPadding = 2.dp
            )
        }

        if (isUpdating) {
            AppLinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .height(3.dp)
                    .then(overlayModifier)
            )
        }
    }
}
