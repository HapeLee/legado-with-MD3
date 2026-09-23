package io.legado.app.feature.settings.backup

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.backup
import io.legado.app.feature.settings.res.backup_to_local
import io.legado.app.feature.settings.res.backup_to_local_and_network
import io.legado.app.feature.settings.res.backup_to_network
import io.legado.app.feature.settings.res.restore
import io.legado.app.feature.settings.res.restore_from_local
import io.legado.app.feature.settings.res.restore_from_network
import io.legado.app.ui.widget.components.modalBottomSheet.OptionCard
import io.legado.app.ui.widget.components.modalBottomSheet.OptionSheet
import org.jetbrains.compose.resources.stringResource

// M5-9b：随页面本体一起从 `:app` 的 `io.legado.app.ui.config.backupConfig` 迁来。
// 两个选项底部弹层，零平台依赖 —— 只把 `androidx.compose.ui.res.stringResource` 换成
// CMP 的、`R.string.*` 换成 `Res.string.*`（7 条）。
// 本文件没有数组，故不需要 `.toTypedArray()`（那是 `BackupConfigScreen` 的事）。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupOptionSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onBackupToLocal: () -> Unit,
    onBackupToNetwork: () -> Unit,
    onBackupToLocalAndNetwork: () -> Unit,
) {
    OptionSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.backup),
    ) {
        OptionCard(
            icon = Icons.Default.PhoneAndroid,
            text = stringResource(Res.string.backup_to_local),
            onClick = onBackupToLocal,
        )
        OptionCard(
            icon = Icons.Default.Cloud,
            text = stringResource(Res.string.backup_to_network),
            onClick = onBackupToNetwork,
        )
        OptionCard(
            icon = Icons.Default.Lan,
            text = stringResource(Res.string.backup_to_local_and_network),
            onClick = onBackupToLocalAndNetwork,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreOptionSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onRestoreFromLocal: () -> Unit,
    onRestoreFromNetwork: () -> Unit,
) {
    OptionSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.restore),
    ) {
        OptionCard(
            icon = Icons.Default.PhoneAndroid,
            text = stringResource(Res.string.restore_from_local),
            onClick = onRestoreFromLocal,
        )
        OptionCard(
            icon = Icons.Default.Cloud,
            text = stringResource(Res.string.restore_from_network),
            onClick = onRestoreFromNetwork,
        )
    }
}
