package io.legado.app.ui.book.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.ConflictBookSummary
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.conflict.ConflictBookCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

/**
 * 已入架书籍的「书架操作」Sheet：其他副本 / 分组 / 删除。
 *
 * 为什么不再直接弹删除确认框：已入架的书可能还有同名同作者的其他副本（共存），
 * 而原来的交互只有「点一下 → 问是否删除」，用户没有别的去处，也没法切到另一个副本。
 * 这里把三种意图平铺出来，删除仍复用原有的确认框（本地书带「删除原文件」选项，
 * 且照旧受「删除前提醒」设置影响）。
 *
 * 长按卡片直接进「分组」的老路保持不变（见 `BookInfoActions`）。
 */
@Composable
fun ShelfActionsSheet(
    show: Boolean,
    copies: List<ConflictBookSummary>,
    onOpenCopy: (ConflictBookSummary) -> Unit,
    onGroup: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.shelf_actions_title),
    ) {
        if (copies.isNotEmpty()) {
            AppText(
                text = stringResource(R.string.shelf_duplicate_count, copies.size),
                style = LegadoTheme.typography.titleSmallEmphasized,
            )
            AppText(
                text = stringResource(R.string.bookshelf_conflict_open_hint),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
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
            Spacer(modifier = Modifier.height(12.dp))
        }
        // 两个动作同排、均分宽度。删除不铺底色，用 error 色的图标 + 文字表达危险性，
        // 免得一个删除按钮在面板里显得比「分组」更重。
        Row(
            modifier = Modifier.fillMaxWidth(),
            // 两个按钮的最小交互高度不同（plain 会被撑到 48dp），居中对齐免得看着错位
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MediumTonalButton(
                onClick = onGroup,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Group,
                text = stringResource(R.string.group),
            )
            MediumTonalButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Delete,
                text = stringResource(R.string.delete),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
