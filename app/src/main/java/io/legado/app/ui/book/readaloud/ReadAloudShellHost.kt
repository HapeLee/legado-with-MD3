package io.legado.app.ui.book.readaloud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.ui.book.read.ReadAloudCapsule
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerIntent
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerUiState
import org.koin.compose.koinInject

/**
 * 朗读悬浮胶囊的全局叠层。
 *
 * 胶囊在阅读界面之外也要可见，因此不能再挂在 `ReadBookRouteScreen` 里：阅读器只是
 * Navigation 3 的一个目的地，离开它就整棵子树被销毁。这里由宿主 Activity 叠在导航之上，
 * 只要朗读会话在跑就一直在。
 *
 * 数据来自全局的 [ReadAloudSessionStore] 与朗读设置，不依赖任何阅读器 ViewModel，
 * 所以朗读无论从书架、听书页还是通知栏启动，胶囊表现一致。
 *
 * 显隐判据用 [ReadAloudPlayerUiState.readAloudRunning]（背后是 `BaseReadAloudService.isRun`，
 * 服务创建/销毁时同步翻转）而不是 [ReadAloudSessionStore] 的 status：后者只在 play/pause/stop
 * 事件里更新，服务仍在跑时可能停在旧值，表现为「回到阅读界面胶囊要等下一段才出现」。
 */
@Composable
fun ReadAloudShellHost(
    playerState: ReadAloudPlayerUiState,
    showCapsule: Boolean,
    sessionStore: ReadAloudSessionStore = koinInject(),
    settingsGateway: ReadAloudSettingsGateway = koinInject(),
    bottomPadding: Dp = 16.dp,
    hidden: Boolean = false,
    onIntent: (ReadAloudPlayerIntent) -> Unit,
    onCapsulePositionChanged: (x: Float, y: Float) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val session by sessionStore.state.collectAsStateWithLifecycle()
    val settings by settingsGateway.settings.collectAsStateWithLifecycle(
        settingsGateway.currentSettings
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !hidden && showCapsule && playerState.readAloudRunning,
            enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.88f),
            exit = fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.88f),
        ) {
            ReadAloudCapsule(
                bookName = playerState.bookName,
                author = playerState.author,
                coverPath = playerState.coverPath,
                sourceOrigin = playerState.sourceOrigin,
                isPaused = session.status != ReadAloudSessionStatus.Playing,
                offsetXDp = settings.capsuleOffsetX,
                offsetYDp = settings.capsuleOffsetY,
                progress = session.playback.chapterPosition.toFloat() /
                        session.playback.chapterLength.coerceAtLeast(1),
                autoCollapse = settings.capsuleAutoCollapse,
                bottomPadding = bottomPadding,
                onPositionChanged = onCapsulePositionChanged,
                onTogglePause = { onIntent(ReadAloudPlayerIntent.TogglePause) },
                onStop = { onIntent(ReadAloudPlayerIntent.StopReadAloud) },
                onOpenPlayer = onOpenPlayer,
            )
        }
    }
}
