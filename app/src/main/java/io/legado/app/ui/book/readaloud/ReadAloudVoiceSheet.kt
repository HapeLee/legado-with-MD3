package io.legado.app.ui.book.readaloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.readaloud.player.ReadAloudVoiceItemUi
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ReadAloudVoiceSheet(
    show: Boolean,
    voices: ImmutableList<ReadAloudVoiceItemUi>,
    onDismissRequest: () -> Unit,
    onSelectVoice: (String) -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.cloud_tts_step_voice),
    ) {
        if (voices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = stringResource(R.string.cloud_tts_no_saved_voices),
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(voices, key = { it.id }) { voice ->
                    TinyClickableSettingItem(
                        title = voice.name,
                        description = voice.engineName,
                        imageVector = Icons.Default.RecordVoiceOver,
                        color = if (voice.isSelected) {
                            LegadoTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            LegadoTheme.colorScheme.surfaceContainerLow
                        },
                        trailingContent = if (voice.isSelected) {
                            {
                                AppIcon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.cloud_tts_selected),
                                    tint = LegadoTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        } else null,
                        onClick = {
                            onSelectVoice(voice.id)
                            onDismissRequest()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
