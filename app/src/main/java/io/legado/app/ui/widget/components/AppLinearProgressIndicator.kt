package io.legado.app.ui.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
// 这里建议使用 as 关键字为两个库的控件起别名，避免同名冲突
import androidx.compose.material3.LinearProgressIndicator
// 请替换为实际的 MIUIX 控件包名
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator

@Composable
fun AppLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixLinearProgressIndicator(
            modifier = modifier,
            progress = progress
        )
    } else {
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = modifier
            )
        } else {
            LinearProgressIndicator(
                modifier = modifier
            )
        }
    }
}