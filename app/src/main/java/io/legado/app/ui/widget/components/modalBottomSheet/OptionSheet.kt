package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.text.AppText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable RowScope.() -> Unit
) {
    AppModalBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 2
            ) {
                content()
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun RowScope.OptionCard(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    NormalCard(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(100.dp),
        cornerRadius = 12.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = LegadoTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppText(
                text = text,
                style = LegadoTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}