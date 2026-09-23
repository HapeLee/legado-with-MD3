package io.legado.app.ui.widget.components.tabRow

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText

/**
 * M5-9b-pre 从 `:core:ui` 搬进 `:core:designsystem/commonMain`（包名不变 ⇒ 10 处 `:app`
 * 消费方 import 零改动）。
 *
 * 这与同目录 [AppTabRow] 的搬运是**同一配方**，也正是 `AppTabRow.kt` 的 KDoc 当初预告的
 * 那一步 —— 它写着「`CardTabRow.kt` 刻意没有跟着搬：目前只有 `:app` 的 10 处消费方，
 * 没有非 Android 消费者。**等真出现时按同一配方再搬**」。本片的 `backupConfig` 页面本体
 * 迁进 `:feature:settings`（它在 `IgnoreItemsSheet` 里用本组件做「配置项 / 数据库」两个页签）
 * ⇒ 第一个非 Android 消费者出现，前提不再成立。
 *
 * 本文件本来就零 Android 依赖：只用 Compose foundation/runtime 与 designsystem 自己的
 * `LegadoTheme` / `NormalCard` / `AppText` ⇒ 零源码改动、零新增依赖。
 */
@Composable
fun CardTabRow(
    tabTitles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabLongClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    tabEndContent: (@Composable (Int) -> Unit)? = null,
) {
    var lastSelectedTabIndex by remember { mutableIntStateOf(selectedTabIndex) }
    val isSelectionChanged = selectedTabIndex != lastSelectedTabIndex

    SideEffect {
        lastSelectedTabIndex = selectedTabIndex
    }

    val animSpec = if (isSelectionChanged) tween<Color>(durationMillis = 200) else snap()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabTitles.forEachIndexed { index, title ->
            val selected = selectedTabIndex == index
            val containerColor by animateColorAsState(
                targetValue = if (selected) {
                    LegadoTheme.colorScheme.secondaryContainer
                } else {
                    LegadoTheme.colorScheme.surfaceContainerLow
                },
                animationSpec = animSpec,
                label = "tabColor",
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) {
                    LegadoTheme.colorScheme.onSecondaryContainer
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = animSpec,
                label = "tabContentColor",
            )

            NormalCard(
                onClick = { onTabSelected(index) },
                onLongClick = onTabLongClick?.let { { it(index) } },
                modifier = Modifier.weight(1f),
                containerColor = containerColor,
                contentColor = contentColor,
                cornerRadius = 12.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = title,
                        style = LegadoTheme.typography.labelMediumEmphasized,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = contentColor,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (tabEndContent == null) 0.dp else 20.dp),
                        textAlign = TextAlign.Center,
                    )
                    if (tabEndContent != null) {
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            tabEndContent(index)
                        }
                    }
                }
            }
        }
    }
}
