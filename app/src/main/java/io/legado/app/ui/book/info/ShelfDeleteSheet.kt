package io.legado.app.ui.book.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.ConflictBookSummary
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.conflict.ConflictBookCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

/**
 * 已入架书籍的删除 Sheet：展示当前书籍确认删除，若有其他副本则给出消歧提示及跳转途径。
 *
 * 底部为纯粹的「取消 / 删除」；长按书架卡片直接进入分组选择（保持在详情页常态长按）。
 */
@Composable
fun ShelfDeleteSheet(
    show: Boolean,
    book: ConflictBookSummary?,
    copies: List<ConflictBookSummary>,
    isLocal: Boolean,
    initialDeleteOriginal: Boolean,
    onOpenCopy: (ConflictBookSummary) -> Unit,
    onDelete: (deleteOriginal: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // 纯展示态：每次打开 Sheet 都从设置里的当前值起步，退出组合后随之丢弃。
    var deleteOriginal by rememberSaveable { mutableStateOf(initialDeleteOriginal) }
    var showCopiesList by rememberSaveable { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.sure_del),
    ) {
        book?.let {
            ConflictBookCard(
                summary = it,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (copies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            val sourcesText = remember(copies) {
                copies.take(3).joinToString("】、【") { it.sourceName } +
                        if (copies.size > 3) "等" else ""
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = LegadoTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = LegadoTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp),
                        )
                        AppText(
                            text = stringResource(
                                R.string.shelf_delete_other_copies_hint,
                                copies.size,
                                sourcesText,
                            ),
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (copies.size == 1) {
                            MediumTonalButton(
                                onClick = { onOpenCopy(copies.first()) },
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                text = stringResource(R.string.shelf_delete_view_copy),
                            )
                        } else {
                            MediumTonalButton(
                                onClick = { showCopiesList = !showCopiesList },
                                icon = if (showCopiesList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                text = stringResource(
                                    R.string.shelf_delete_view_copies,
                                    copies.size
                                ),
                            )
                        }
                    }
                }
            }

            if (copies.size > 1 && showCopiesList) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(copies, key = { it.bookUrl }) { copy ->
                        ConflictBookCard(
                            summary = copy,
                            onClick = { onOpenCopy(copy) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        if (isLocal) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = deleteOriginal,
                    onCheckedChange = { deleteOriginal = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = LegadoTheme.colorScheme.primary,
                        checkmarkColor = LegadoTheme.colorScheme.onPrimary,
                        uncheckedColor = LegadoTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                AppText(
                    text = stringResource(R.string.delete_book_file),
                    style = LegadoTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        ConfirmDismissButtonsRow(
            onDismiss = onDismissRequest,
            onConfirm = { onDelete(deleteOriginal) },
            dismissText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.delete),
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}
