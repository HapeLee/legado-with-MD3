package io.legado.app.ui.widget.components.button

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun SmallTextButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            colors = MiuixButtonDefaults.buttonColors()
        ) {
            MiuixText(
                text = text
            )
        }
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.small
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            AppText(
                text = text,
                style = LegadoTheme.typography.labelMedium
            )
        }
    }
}