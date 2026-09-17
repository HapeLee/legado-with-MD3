package io.legado.app.ui.widget.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import io.legado.app.ui.widget.components.text.AnimatedTextLine

// M5-1c-pre：本文件原住 `:core:ui/src/main`（Android-only）。上提的理由是**调用方分布**——
// `CrashLogSheet`（M5-1c 要搬进 `:feature:about`）的空态用它，而它自身有 ~40 个调用方，
// 不该在 about 里重写一份（那是可见 UI 变化）。机械判据同 M5-1a/1b：文件内零 `android.*` /
// 零 `R.`，依赖闭包全在 designsystem（`AnimatedTextLine` / `AppIcons` /
// `AppContainedLoadingIndicator` / `SmallTonalButton` / `LegadoTheme`）。
//
// ⚠️ **`@StringRes` 的 `Int` 重载不在本文件**：它依赖 `androidx.compose.ui.res.stringResource`
// （Android-only，且 CMP 的 `org.jetbrains.compose.resources.stringResource` 接的是
// `StringResource` 而不是 `Int`），故留到 `androidMain` 的同名文件里。包名不变 ⇒
// `:app` 侧的 ~40 个调用方 import 零改动。

@Composable
fun EmptyMessage(
    message: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    buttonText: String? = null,
    buttonImageVector: ImageVector = AppIcons.Search,
    onButtonClick: (() -> Unit)? = null,
    faces: List<String> = listOf(
        "(；′⌒`)", "(つ﹏⊂)", "(•̀ᴗ•́)و", "(๑•́ ₃ •̀๑)",
        "(눈‸눈)", "(ಥ﹏ಥ)", "(｡•́︿•̀｡)"
    ),
    faceTextSize: TextUnit = 32.sp,
    onFaceClick: (() -> Unit)? = null
) {
    var currentFace by remember { mutableStateOf(faces.random()) }

    Column(
        modifier = modifier
            .wrapContentSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedContent(
            targetState = isLoading,
            label = "LoadingStateAnimation"
        ) { loading ->
            if (loading) {
                AppContainedLoadingIndicator()
            } else {
                AnimatedTextLine(
                    text = currentFace,
                    fontSize = faceTextSize,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable {
                            currentFace = faces.random()
                            onFaceClick?.invoke()
                        }
                )
            }
        }

        AnimatedTextLine(
            text = message,
            style = LegadoTheme.typography.labelMediumEmphasized,
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
            modifier = Modifier.widthIn(max = 240.dp)
        )

        if (buttonText != null && onButtonClick != null) {
            Spacer(modifier = Modifier.height(8.dp))
            SmallTonalButton(
                onClick = onButtonClick,
                text = buttonText,
                icon = buttonImageVector
            )
        }
    }
}
