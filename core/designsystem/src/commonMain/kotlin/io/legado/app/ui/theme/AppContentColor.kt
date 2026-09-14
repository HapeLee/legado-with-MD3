package io.legado.app.ui.theme

import androidx.compose.material3.LocalContentColor as MaterialLocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor

@Composable
fun ProvideAppContentColor(
    contentColor: Color,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        MaterialLocalContentColor provides contentColor,
        MiuixLocalContentColor provides contentColor,
        content = content
    )
}
