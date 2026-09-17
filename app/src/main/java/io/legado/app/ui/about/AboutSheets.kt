package io.legado.app.ui.about

import android.os.Build
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.help.update.AppUpdate
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.text.MarkdownBlock

// M5-1c-pre：原同居本文件的 `MarkdownSheet` 已上提到 `:core:designsystem` 的
// `io.legado.app.ui.widget.components.modalBottomSheet`（6 个包外调用方，不属于 about）。
// 本文件现在只剩 about 私有的 `UpdateSheet`。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    show: Boolean,
    updateInfo: AppUpdate.UpdateInfo,
    updateToVariant: String,
    mode: UpdateMode,
    onDismissRequest: () -> Unit,
    onStartDownload: () -> Unit,
) {
    val title = when (mode) {
        UpdateMode.UPDATE -> stringResource(R.string.check_update)
        UpdateMode.VIEW_LOG -> stringResource(R.string.about_installed_version_title)
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
                        text = stringResource(R.string.about_current_version),
                        style = LegadoTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppText(
                        text = BuildConfig.VERSION_NAME,
                        style = LegadoTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Row(
                    modifier = Modifier.padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = stringResource(R.string.about_new_version),
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
                        text = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
                        style = LegadoTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = stringResource(R.string.about_update_channel),
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
                    text = BuildConfig.VERSION_NAME,
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
                    text = stringResource(R.string.about_update_action),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
