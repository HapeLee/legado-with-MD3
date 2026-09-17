package io.legado.app.feature.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.feature.about.res.Res
import io.legado.app.feature.about.res.about_current_version
import io.legado.app.feature.about.res.about_installed_version_title
import io.legado.app.feature.about.res.about_new_version
import io.legado.app.feature.about.res.about_update_action
import io.legado.app.feature.about.res.about_update_channel
import io.legado.app.feature.about.res.check_update
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.text.MarkdownBlock
import org.jetbrains.compose.resources.stringResource

// M5-1c-pre：原同居此文件的 `MarkdownSheet` 已上提到 `:core:designsystem` 的
// `io.legado.app.ui.widget.components.modalBottomSheet`（6 个包外调用方，不属于 about）。
// M5-1c-2：文件本体从 `:app` 的 `ui/about/AboutSheets.kt` 搬进本模块的 `commonMain`。
//
// 两处**必做**的类型变化（迁移前直连 Android）：
//   - `BuildConfig.VERSION_NAME` → 参数 [versionName]；
//   - `Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"` → 参数 [abi]（回落逻辑原样由宿主承担，
//     即在 `:app` 侧仍然是 `Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"`）。
// 文案走 `Res.string.*`，取自本模块 `composeResources`（与 `:app` 同名资源逐字一致）。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    show: Boolean,
    updateInfo: UpdateInfo,
    updateToVariant: String,
    mode: UpdateMode,
    versionName: String,
    abi: String,
    onDismissRequest: () -> Unit,
    onStartDownload: () -> Unit,
) {
    val title = when (mode) {
        UpdateMode.UPDATE -> stringResource(Res.string.check_update)
        UpdateMode.VIEW_LOG -> stringResource(Res.string.about_installed_version_title)
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (mode == UpdateMode.UPDATE) {
                Row(
                    modifier = Modifier.padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = stringResource(Res.string.about_current_version),
                        style = LegadoTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppText(
                        text = versionName,
                        style = LegadoTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Row(
                    modifier = Modifier.padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = stringResource(Res.string.about_new_version),
                        style = LegadoTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppText(
                        text = updateInfo.tagName,
                        style = LegadoTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = "ABI",
                        style = LegadoTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppText(
                        text = abi,
                        style = LegadoTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = stringResource(Res.string.about_update_channel),
                        style = LegadoTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppText(
                        text = updateToVariant,
                        style = LegadoTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                AppText(
                    text = versionName,
                    style = LegadoTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            val updateLog = updateInfo.updateLog
            if (updateLog.isNotBlank()) {
                MarkdownBlock(
                    content = updateLog,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (mode == UpdateMode.UPDATE) {
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    onClick = onStartDownload,
                    text = stringResource(Res.string.about_update_action),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
