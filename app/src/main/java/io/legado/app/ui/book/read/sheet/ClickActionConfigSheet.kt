package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.ui.theme.LegadoTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ClickActionConfigSheet(
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val readSettingsRepository: ReadSettingsRepository = koinInject()
    val preferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = ReadPreferences()
    )
    val scope = rememberCoroutineScope()

    val actions = remember {
        linkedMapOf(
            -1 to context.getString(R.string.non_action),
            0 to context.getString(R.string.menu),
            1 to context.getString(R.string.next_page),
            2 to context.getString(R.string.prev_page),
            3 to context.getString(R.string.next_chapter),
            4 to context.getString(R.string.previous_chapter),
            5 to context.getString(R.string.read_aloud_prev_paragraph),
            6 to context.getString(R.string.read_aloud_next_paragraph),
            7 to context.getString(R.string.bookmark_add),
            8 to context.getString(R.string.edit_content),
            9 to context.getString(R.string.replace_state_change),
            10 to context.getString(R.string.chapter_list),
            11 to context.getString(R.string.search_content),
            12 to context.getString(R.string.sync_book_progress_t),
            13 to context.getString(R.string.read_aloud_pause_resume),
        )
    }

    var selectingPrefKey by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .clickable(onClick = onDismissRequest),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.click_regional_config),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.close))
                }
            }

            // 3x3 grid
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                ClickAreaCell(
                    label = actions[preferences.clickActionTL] ?: "",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(3.dp),
                    onClick = { selectingPrefKey = PreferKey.clickActionTL },
                )
                ClickAreaCell(
                    label = actions[preferences.clickActionTC] ?: "",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(3.dp),
                    onClick = { selectingPrefKey = PreferKey.clickActionTC },
                )
                ClickAreaCell(
                    label = actions[preferences.clickActionTR] ?: "",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(3.dp),
                    onClick = { selectingPrefKey = PreferKey.clickActionTR },
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                ClickAreaCell(
                    label = actions[preferences.clickActionML] ?: "",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(3.dp),
                    onClick = { selectingPrefKey = PreferKey.clickActionML },
                )
                ClickAreaCell(
                    label = actions[preferences.clickActionMC] ?: "",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(3.dp),
                    onClick = { selectingPrefKey = PreferKey.clickActionMC },
                )
                ClickAreaCell(
                    label = actions[preferences.clickActionMR] ?: "",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(3.dp),
                    onClick = { selectingPrefKey = PreferKey.clickActionMR },
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                ClickAreaCell(
                    label = actions[preferences.clickActionBL] ?: "",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(3.dp),
                    onClick = { selectingPrefKey = PreferKey.clickActionBL },
                )
                ClickAreaCell(
                    label = actions[preferences.clickActionBC] ?: "",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(3.dp),
                    onClick = { selectingPrefKey = PreferKey.clickActionBC },
                )
                ClickAreaCell(
                    label = actions[preferences.clickActionBR] ?: "",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(3.dp),
                    onClick = { selectingPrefKey = PreferKey.clickActionBR },
                )
            }
        }
    }

    // Action selector dialog
    if (selectingPrefKey != null) {
        val actionKeys = actions.keys.toList()
        val actionValues = actions.values.toList()
        AlertDialog(
            onDismissRequest = { selectingPrefKey = null },
            containerColor = LegadoTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.select_action)) },
            text = {
                Column {
                    actionValues.forEachIndexed { index, label ->
                        TextButton(
                            onClick = {
                                val selectedAction = actionKeys[index]
                                selectingPrefKey?.let { key ->
                                    scope.launch {
                                        readSettingsRepository.setClickAction(key, selectedAction)
                                    }
                                }
                                selectingPrefKey = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun ClickAreaCell(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
