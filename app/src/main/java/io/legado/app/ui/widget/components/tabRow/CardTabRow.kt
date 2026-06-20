package io.legado.app.ui.widget.components.tabRow

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun CardTabRow(
    tabTitles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
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
                modifier = Modifier.weight(1f),
                containerColor = containerColor,
                contentColor = contentColor,
                cornerRadius = 12.dp
            ) {
                AppText(
                    text = title,
                    style = LegadoTheme.typography.labelMediumEmphasized,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .align(Alignment.CenterHorizontally),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
