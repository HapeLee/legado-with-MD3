package io.legado.app.ui.book.changesource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun ChangeSourceMigrationOptionsSheet(
    show: Boolean,
    title: String,
    subtitle: String? = null,
    onDismissRequest: () -> Unit,
    onConfirm: (ChangeSourceMigrationOptions) -> Unit,
) {
    var migrateReadingProgress by rememberSaveable(show) { mutableStateOf(true) }
    var migrateGroup by rememberSaveable(show) { mutableStateOf(true) }
    var migrateCover by rememberSaveable(show) { mutableStateOf(true) }
    var migrateCategory by rememberSaveable(show) { mutableStateOf(true) }
    var migrateRemark by rememberSaveable(show) { mutableStateOf(true) }
    var migrateReadConfig by rememberSaveable(show) { mutableStateOf(true) }
    var deleteDownloadedChapters by rememberSaveable(show) { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            subtitle?.takeIf { it.isNotBlank() }?.let {
                AppText(
                    text = it,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            CheckboxItem("Reading progress", checked = migrateReadingProgress) {
                migrateReadingProgress = it
            }
            if (migrateReadingProgress) {
                AppText(
                    text = "If the new source has fewer chapters than progress, reading progress adjusts to the last chapter.",
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            CheckboxItem("Group and sorting", checked = migrateGroup) { migrateGroup = it }
            CheckboxItem("Custom cover", checked = migrateCover) { migrateCover = it }
            CheckboxItem("Categories and tags", checked = migrateCategory) { migrateCategory = it }
            CheckboxItem("Remark and custom summary", checked = migrateRemark) { migrateRemark = it }
            CheckboxItem("Read settings", checked = migrateReadConfig) { migrateReadConfig = it }
            CheckboxItem("Delete downloaded chapters", checked = deleteDownloadedChapters) {
                deleteDownloadedChapters = it
            }
            Spacer(modifier = Modifier.height(8.dp))
            ConfirmDismissButtonsRow(
                onDismiss = onDismissRequest,
                onConfirm = {
                    onConfirm(
                        ChangeSourceMigrationOptions(
                            migrateChapters = true,
                            migrateReadingProgress = migrateReadingProgress,
                            migrateGroup = migrateGroup,
                            migrateCover = migrateCover,
                            migrateCategory = migrateCategory,
                            migrateRemark = migrateRemark,
                            migrateReadConfig = migrateReadConfig,
                            deleteDownloadedChapters = deleteDownloadedChapters,
                        )
                    )
                },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(android.R.string.ok),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
