package io.legado.app.ui.theme

import androidx.compose.material3.LocalContentColor as MaterialLocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor
import ui.feice.mochi.theme.LocalContentColor as MochiLocalContentColor

@Composable
fun ProvideAppContentColor(
    contentColor: Color,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        MaterialLocalContentColor provides contentColor,
        MiuixLocalContentColor provides contentColor,
        MochiLocalContentColor provides contentColor,
        content = content
    )
}
