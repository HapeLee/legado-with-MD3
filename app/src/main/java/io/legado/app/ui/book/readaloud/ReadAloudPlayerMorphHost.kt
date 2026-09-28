package io.legado.app.ui.book.readaloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.core.ui.player.PlayerMorphAppearance
import io.legado.app.core.ui.player.PlayerMorphHost
import io.legado.app.ui.book.read.sheet.ReadAloudConfigContent
import io.legado.app.ui.book.read.sheet.asReadBookUiState
import io.legado.app.ui.book.readaloud.morph.ReadAloudMorphState
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerEffect
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerScreenContent
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerUiState
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerViewModel
import io.legado.app.ui.book.readaloud.player.applyReadBookConfigIntent
import io.legado.app.ui.book.readaloud.player.rememberPlayerThemeOverride
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

/** 朗读特有的设置与经典控制；几何、封面和返回手势由共用宿主处理。 */
@Composable
fun ReadAloudPlayerMorphHost(
    playerViewModel: ReadAloudPlayerViewModel,
    playerState: ReadAloudPlayerUiState,
    morph: ReadAloudMorphState,
    visible: Boolean,
    awaitCapsuleAnchor: Boolean = false,
    predictiveBackEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onSwitchToClassic: (bookUrl: String) -> Unit,
) {
    val settingsState by playerViewModel.readAloudSettings.collectAsStateWithLifecycle()
    var configVisible by rememberSaveable { mutableStateOf(false) }
    val expanded by remember { derivedStateOf { morph.expanded } }
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentSwitch by rememberUpdatedState(onSwitchToClassic)
    LaunchedEffect(expanded) { if (!expanded) configVisible = false }
    LaunchedEffect(playerViewModel) {
        playerViewModel.effects.collect { effect ->
            when (effect) {
                ReadAloudPlayerEffect.ReturnToClassic -> {
                    configVisible = false
                    morph.animateTo(0f)
                    currentDismiss()
                    currentSwitch(playerViewModel.uiState.value.bookUrl)
                }
            }
        }
    }
    PlayerMorphHost(
        appearance = PlayerMorphAppearance(
            playerState.bookName, playerState.author, playerState.coverPath,
            playerState.sourceOrigin, playerState.bgMode,
        ),
        playerTheme = rememberPlayerThemeOverride(playerState),
        morph = morph,
        visible = visible,
        awaitCapsuleAnchor = awaitCapsuleAnchor,
        predictiveBackEnabled = predictiveBackEnabled,
        backEnabled = !configVisible && playerState.activeSheet == null,
        onDismiss = onDismiss,
    ) { onCollapse ->
        ReadAloudPlayerScreenContent(
            state = playerState,
            onIntent = playerViewModel::onIntent,
            onBack = onCollapse,
            onOpenConfig = { configVisible = true },
        )
    }
    AppModalBottomSheet(
        show = expanded && configVisible,
        onDismissRequest = { configVisible = false },
        title = stringResource(R.string.aloud_config),
    ) {
        ReadAloudConfigContent(
            state = settingsState.asReadBookUiState(),
            playerState = playerState,
            onIntent = playerViewModel::applyReadBookConfigIntent,
            onPlayerIntent = playerViewModel::onIntent,
        )
    }
}
