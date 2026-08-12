package io.legado.app.ui.book.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.card.SettingCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem

@Composable
internal fun MangaReaderSourceActionsSheet(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = true,
        onDismissRequest = { onIntent(MangaReaderIntent.DismissSheet) },
        title = stringResource(R.string.manga_reader_source_actions),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
            ) {
                ClickableSettingItem(
                    title = stringResource(R.string.login),
                    onClick = { onIntent(MangaReaderIntent.OpenSourceLogin) },
                )
                ClickableSettingItem(
                    title = stringResource(R.string.manga_reader_buy_chapter),
                    onClick = { onIntent(MangaReaderIntent.RequestPayCurrentChapter) },
                )
                ClickableSettingItem(
                    title = stringResource(R.string.edit_source),
                    onClick = { onIntent(MangaReaderIntent.OpenSourceEdit) },
                )
                ClickableSettingItem(
                    title = stringResource(R.string.disable_source),
                    onClick = {
                        onIntent(MangaReaderIntent.DisableCurrentSource)
                        onIntent(MangaReaderIntent.DismissSheet)
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
