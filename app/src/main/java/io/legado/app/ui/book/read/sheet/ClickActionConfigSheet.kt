package io.legado.app.ui.book.read.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ClickActionConfigSheet(
    onDismissRequest: () -> Unit,
) {
    BackHandler(onBack = onDismissRequest)

    val readSettingsRepository: ReadSettingsRepository = koinInject()
    val preferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = ReadPreferences()
    )
    val scope = rememberCoroutineScope()

    val actions = linkedMapOf(
        -1 to stringResource(R.string.non_action),
        0 to stringResource(R.string.menu),
        1 to stringResource(R.string.next_page),
        2 to stringResource(R.string.prev_page),
        3 to stringResource(R.string.next_chapter),
        4 to stringResource(R.string.previous_chapter),
        5 to stringResource(R.string.read_aloud_prev_paragraph),
        6 to stringResource(R.string.read_aloud_next_paragraph),
        7 to stringResource(R.string.bookmark_add),
        8 to stringResource(R.string.edit_content),
        9 to stringResource(R.string.replace_state_change),
        10 to stringResource(R.string.chapter_list),
        11 to stringResource(R.string.search_content),
        12 to stringResource(R.string.sync_book_progress_t),
        13 to stringResource(R.string.read_aloud_pause_resume),
    )
    val actionKeys = actions.keys.toList()
    val actionValues = actions.values.toList()

    // 顺序即九宫格顺序：左上 → 右下
    val cells = listOf(
        PreferKey.clickActionTL to preferences.clickActionTL,
        PreferKey.clickActionTC to preferences.clickActionTC,
        PreferKey.clickActionTR to preferences.clickActionTR,
        PreferKey.clickActionML to preferences.clickActionML,
        PreferKey.clickActionMC to preferences.clickActionMC,
        PreferKey.clickActionMR to preferences.clickActionMR,
        PreferKey.clickActionBL to preferences.clickActionBL,
        PreferKey.clickActionBC to preferences.clickActionBC,
        PreferKey.clickActionBR to preferences.clickActionBR,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LegadoTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f))
            .clickable(onClick = onDismissRequest),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Spacer(
                modifier = Modifier.padding(top = 36.dp)
            )
            cells.chunked(3).forEach { rowCells ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    rowCells.forEach { (prefKey, action) ->
                        ClickAreaCell(
                            label = actions[action] ?: "",
                            actionKeys = actionKeys,
                            actionValues = actionValues,
                            selectedAction = action,
                            onPick = { picked ->
                                scope.launch {
                                    readSettingsRepository.setClickAction(prefKey, picked)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 单个点击区域：显示当前动作，点击弹出动作下拉菜单。 */
@Composable
private fun ClickAreaCell(
    label: String,
    actionKeys: List<Int>,
    actionValues: List<String>,
    selectedAction: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        GlassCard(
            modifier = Modifier.fillMaxSize(),
            onClick = { expanded = true },
            containerColor = LegadoTheme.colorScheme.surfaceContainer
                .copy(alpha = 0.9f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = label,
                    style = LegadoTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) { dismiss ->
            actionKeys.forEachIndexed { index, action ->
                RoundDropdownMenuItem(
                    text = actionValues[index],
                    isSelected = action == selectedAction,
                    onClick = {
                        onPick(action)
                        dismiss()
                    },
                )
            }
        }
    }
}
