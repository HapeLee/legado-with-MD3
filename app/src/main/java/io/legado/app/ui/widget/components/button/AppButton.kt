package io.legado.app.ui.widget.components.button

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = MiuixButtonDefaults.buttonColorsPrimary()
        ) {
            MiuixText(text = text)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Text(text = text)
        }
    }
}

@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = MiuixButtonDefaults.buttonColors()
        ) {
            MiuixText(text = text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Text(text = text)
        }
    }
}
