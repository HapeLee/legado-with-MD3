package io.legado.app.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.feature.about.res.Res
import io.legado.app.feature.about.res.copy_text
import io.legado.app.feature.about.res.crash_report
import io.legado.app.feature.about.res.crash_report_empty
import io.legado.app.feature.about.res.crash_report_message
import io.legado.app.feature.about.res.restart_app
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.jetbrains.compose.resources.stringResource

/**
 * 崩溃报告页（M5-1c-2 从 `:app` 的 `ui/about/CrashReportScreen.kt` 搬进 `commonMain`）。
 *
 * 它能整页搬过来，因为没有一处 Android 专有依赖：图形走 `AppIcons`（`:core:designsystem` 的
 * `ImageVector` 委托），文案走本模块的 `composeResources`，宿主动作（复制/重启/关闭）本来就
 * 是回调。**这正是「先审计依赖闭包再决定切法」的一个正例**——本页与同包的 `MiuixAboutScreen`
 * （519 行、直连 `miuix-blur`）形成对照：同目录不等于同可搬性。
 *
 * ⚠️ 承载它的 `CrashReportActivity` 仍留在 `:app`：`CrashHandler` 里硬编码了
 * `"io.legado.app.ui.about.CrashReportActivity"` 这个 ABI，换包名会破坏崩溃处理路径。
 * 本文件只提供 Screen，Activity 是薄宿主。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CrashReportScreen(
    errorText: String,
    onCopy: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val displayText = errorText.ifBlank {
        stringResource(Res.string.crash_report_empty)
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(Res.string.crash_report),
                navigationIcon = {
                    TopBarNavigationButton(
                        onClick = onClose,
                        imageVector = AppIcons.Close
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Icon(
                imageVector = AppIcons.BugReport,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = LegadoTheme.colorScheme.error
            )
            AppText(
                text = stringResource(Res.string.crash_report_message),
                style = LegadoTheme.typography.titleMedium
            )


            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        color = LegadoTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                AppText(
                    text = displayText,
                    style = LegadoTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.sp,
                        lineHeight = 16.sp
                    ),
                    softWrap = false
                )
            }

            ConfirmDismissButtonsRow(
                onDismiss = onRestart,
                onConfirm = onCopy,
                dismissText = stringResource(Res.string.restart_app),
                confirmText = stringResource(Res.string.copy_text),
                confirmEnabled = errorText.isNotBlank()
            )
        }
    }
}
