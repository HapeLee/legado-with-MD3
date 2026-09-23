package io.legado.app.feature.settings.readconfig

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.bookmark_add
import io.legado.app.feature.settings.res.chapter_list
import io.legado.app.feature.settings.res.edit_content
import io.legado.app.feature.settings.res.menu
import io.legado.app.feature.settings.res.next_chapter
import io.legado.app.feature.settings.res.next_page
import io.legado.app.feature.settings.res.non_action
import io.legado.app.feature.settings.res.prev_page
import io.legado.app.feature.settings.res.previous_chapter
import io.legado.app.feature.settings.res.read_aloud_next_paragraph
import io.legado.app.feature.settings.res.read_aloud_pause_resume
import io.legado.app.feature.settings.res.read_aloud_prev_paragraph
import io.legado.app.feature.settings.res.replace_state_change
import io.legado.app.feature.settings.res.search_content
import io.legado.app.feature.settings.res.select_action
import io.legado.app.feature.settings.res.sync_book_progress_t
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.text.AppText
import org.jetbrains.compose.resources.stringResource

/**
 * 点击区域（九宫格）动作配置。阅读菜单与阅读设置共用这一份。
 *
 * M5-11c：从 `:app` 的 `ui/book/read/sheet` 迁来。与 `PageKeySheet` 不同，这个文件**有两处
 * 结构性改动**，都是因为共享层不该有的东西：
 *
 * 1. **去掉 `BackHandler`**。迁移前它自带 `androidx.activity.compose.BackHandler`；
 *    而 M5-11b 实测本项目依赖集里**没有**跨端的 `androidx.compose.ui.backhandler.BackHandler`
 *    （遍历 Gradle 缓存所有 compose jar，0 命中）⇒ 按那次定的策略，**由宿主按共享 state 接线**：
 *
 *    ```kotlin
 *    BackHandler(enabled = state.activeSheet == ReadConfigSheet.ClickActions) { … }
 *    ```
 *
 *    ⚠️ 两个宿主都补了（`ReadConfigRouteScreen` 与阅读器的 `ReadBookScreen`）—— 否则阅读器
 *    那个入口会**丢掉返回键关闭**这个行为。
 *
 * 2. **去掉 `koinInject()`，改显式参数**。迁移前它自己 `koinInject<ReadSettingsRepository>()`
 *    并 `collectAsStateWithLifecycle` 取 `preferences`、在协程里 `setClickAction`。
 *    `:feature:settings` **刻意没有 koin 依赖**（build 文件注明「没有任何调用方」），
 *    且共享层的既有约定是**显式注入**（参 `AiProviderStringSource`）而非在 composable 里
 *    服务定位 ⇒ 改为 `preferences` + `onSetClickAction` 两个参数，由调用方提供。
 *
 *    ⚠️ 语义等价性：迁移前是 `scope.launch { setClickAction(...); selectingPrefKey = null }`；
 *    现在是**同步**调 `onSetClickAction` 再置空 —— 对话框一样会关，但**写入由调用方决定怎么
 *    调度**（`setClickAction` 是 suspend，两个宿主各自 `rememberCoroutineScope().launch`）。
 *
 * 其余（九宫格布局、`actions` 映射表、选中弹层）**逐字保留**。
 */
@Composable
fun ClickActionConfigSheet(
    preferences: ReadPreferences,
    onDismissRequest: () -> Unit,
    onSetClickAction: (String, Int) -> Unit,
) {
    val actions = linkedMapOf(
        -1 to stringResource(Res.string.non_action),
        0 to stringResource(Res.string.menu),
        1 to stringResource(Res.string.next_page),
        2 to stringResource(Res.string.prev_page),
        3 to stringResource(Res.string.next_chapter),
        4 to stringResource(Res.string.previous_chapter),
        5 to stringResource(Res.string.read_aloud_prev_paragraph),
        6 to stringResource(Res.string.read_aloud_next_paragraph),
        7 to stringResource(Res.string.bookmark_add),
        8 to stringResource(Res.string.edit_content),
        9 to stringResource(Res.string.replace_state_change),
        10 to stringResource(Res.string.chapter_list),
        11 to stringResource(Res.string.search_content),
        12 to stringResource(Res.string.sync_book_progress_t),
        13 to stringResource(Res.string.read_aloud_pause_resume),
    )

    var selectingPrefKey by remember { mutableStateOf<String?>(null) }

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
    val actionKeys = actions.keys.toList()
    val actionValues = actions.values.toList()
    AppAlertDialog(
        show = selectingPrefKey != null,
        onDismissRequest = { selectingPrefKey = null },
        title = stringResource(Res.string.select_action),
        content = {
            Column {
                actionValues.forEachIndexed { index, label ->
                    AppText(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val selectedAction = actionKeys[index]
                                selectingPrefKey?.let { key ->
                                    onSetClickAction(key, selectedAction)
                                    selectingPrefKey = null
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        style = LegadoTheme.typography.bodyLarge,
                    )
                }
            }
        },
    )
}

@Composable
private fun ClickAreaCell(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = modifier,
        onClick = onClick,
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
}
