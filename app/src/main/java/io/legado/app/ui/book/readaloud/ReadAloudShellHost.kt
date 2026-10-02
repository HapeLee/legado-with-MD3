package io.legado.app.ui.book.readaloud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.domain.gateway.PlaybackCapsuleGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.PlaybackCapsuleState
import io.legado.app.ui.book.read.ReadAloudCapsule
import io.legado.app.ui.book.readaloud.morph.LocalReadAloudMorph
import org.koin.compose.koinInject

/** 应用内播放胶囊宿主，共用朗读和有声书状态；播放器初始化仍由各自页面负责。 */
@Composable
fun ReadAloudShellHost(
    showCapsule: Boolean,
    playbackGateway: PlaybackCapsuleGateway = koinInject(),
    settingsGateway: ReadAloudSettingsGateway = koinInject(),
    hidden: Boolean = false,
    anchorPreview: PlaybackCapsuleState? = null,
    onCapsulePositionChanged: (x: Float, y: Float) -> Unit,
    onOpenPlayer: (PlaybackCapsuleState) -> Unit,
) {
    val playerState by playbackGateway.state.collectAsStateWithLifecycle()
    val morph = LocalReadAloudMorph.current
    val settings by settingsGateway.settings.collectAsStateWithLifecycle(
        settingsGateway.currentSettings
    )

    // 初次朗读还没有服务会话时，按保存位置布局同一个胶囊，仅测量、不绘制。
    val previewOnly = playerState.source == null
    val displayState = if (previewOnly) anchorPreview ?: playerState else playerState
    val visible = !hidden && showCapsule && (playerState.source != null || anchorPreview != null)
    CompositionLocalProvider(LocalReadAloudMorph provides morph?.takeIf { visible }) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = visible,
                modifier = if (previewOnly) Modifier
                    .graphicsLayer { alpha = 0f }
                    .clearAndSetSemantics { } else Modifier,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
            ) {
                ReadAloudCapsule(
                    bookName = displayState.bookName,
                    author = displayState.author,
                    coverPath = displayState.coverPath,
                    sourceOrigin = displayState.sourceOrigin,
                    isPaused = displayState.isPaused,
                    offsetXDp = settings.capsuleOffsetX,
                    offsetYDp = settings.capsuleOffsetY,
                    progress = displayState.progress,
                    autoCollapse = settings.capsuleAutoCollapse,
                    onPositionChanged = onCapsulePositionChanged,
                    onTogglePause = { playerState.source?.let(playbackGateway::togglePause) },
                    onStop = { playerState.source?.let(playbackGateway::stop) },
                    onOpenPlayer = { if (!previewOnly) onOpenPlayer(playerState) },
                )
            }
        }
    }
}
