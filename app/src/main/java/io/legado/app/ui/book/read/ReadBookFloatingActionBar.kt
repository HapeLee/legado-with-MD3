package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * 阅读界面底部悬浮胶囊组：独立悬浮窗纵向堆叠（朗读脱离提示、阅读锚点、主题切换提醒）。
 *
 * 每个胶囊为「按钮组」形态；多个胶囊同时出现时上下排列，
 * 各自条件独立、可并存；都为空时不显示。
 */
@Composable
fun ReadBookFloatingActionBar(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val anchorVisible = state.readingAnchorAvailable &&
        !state.menuVisible && !state.isShowingSearchResult
    val reminder = state.activeReminder?.takeIf { !state.menuVisible }
    val readAloudDetached = state.isReadAloudRunning && !state.readAloudFollow &&
        !state.menuVisible && !state.isShowingSearchResult
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = anchorVisible || reminder != null || readAloudDetached,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (readAloudDetached) {
                    ReadAloudDetachedCapsule(onIntent = onIntent)
                }
                if (anchorVisible) {
                    ReadingAnchorCapsule(onIntent = onIntent)
                }
                if (reminder != null) {
                    ThemeReminderCapsule(reminder = reminder, onIntent = onIntent)
                }
            }
        }
    }
}

/** 朗读位置脱离当前显示页时：跳回朗读位置，或从当前页重新朗读。 */
@Composable
private fun ReadAloudDetachedCapsule(onIntent: (ReadBookIntent) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediumTonalButton(
            onClick = { onIntent(ReadBookIntent.BackToSpeakingPosition) },
            icon = Icons.Default.VolumeUp,
            text = stringResource(R.string.back_to_speaking_position),
            contentDescription = stringResource(R.string.back_to_speaking_position),
        )
        MediumTonalButton(
            onClick = { onIntent(ReadBookIntent.ReadAloudFromHere) },
            icon = Icons.Default.PlayArrow,
            text = stringResource(R.string.read_aloud_from_here),
            contentDescription = stringResource(R.string.read_aloud_from_here),
        )
    }
}

@Composable
private fun ReadingAnchorCapsule(onIntent: (ReadBookIntent) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediumTonalButton(
            onClick = { onIntent(ReadBookIntent.RestoreLastBookProgress) },
            icon = Icons.Default.History,
            text = stringResource(R.string.return_reading_position),
            contentDescription = stringResource(R.string.return_reading_position),
        )
        MediumTonalButton(
            onClick = { onIntent(ReadBookIntent.KeepCurrentBookProgress) },
            icon = Icons.Default.Close,
            text = stringResource(R.string.dismiss_reading_anchor),
            contentDescription = stringResource(R.string.dismiss_reading_anchor),
        )
    }
}

@Composable
private fun ThemeReminderCapsule(
    reminder: ReminderUiState,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val isDark = (reminder.type as? ReminderType.DayNightReminder)?.targetIsNight == true
    val actionText = stringResource(
        if (isDark) R.string.switch_to_dark_mode_action else R.string.switch_to_light_mode_action
    )
    LaunchedEffect(reminder.id) {
        delay(5.seconds)
        onIntent(ReadBookIntent.DismissReminder)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediumTonalButton(
            onClick = {
                onIntent(ReadBookIntent.DismissReminder)
                reminder.actionIntent?.let { onIntent(it) }
            },
            icon = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
            text = actionText,
            contentDescription = actionText,
        )
        MediumTonalButton(
            onClick = { onIntent(ReadBookIntent.DismissReminder) },
            icon = Icons.Default.Close,
            text = stringResource(R.string.dismiss_reading_anchor),
            contentDescription = stringResource(R.string.dismiss_reading_anchor),
        )
    }
}
