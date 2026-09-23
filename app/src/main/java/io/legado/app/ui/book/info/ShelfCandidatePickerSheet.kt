package io.legado.app.ui.book.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.ConflictBookSummary
import io.legado.app.ui.widget.components.conflict.ConflictBookCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

/**
 * 书架里有多本**确定为同一部作品**的在架书籍时，让用户选择去看哪一本。
 *
 * 与 [io.legado.app.ui.widget.components.conflict.BookshelfConflictSheet] 的区别：
 * 那里问的是「共存还是迁移」，这里只是「去看哪一本」，因此没有迁移选项，点中即跳转。
 *
 * 候选已由 `FindShelfSameBookUseCase` 按最后阅读时间降序排列，本地书也在其中，
 * 靠卡片上的书源名区分。
 */
@Composable
fun ShelfCandidatePickerSheet(
    show: Boolean,
    candidates: List<ConflictBookSummary>,
    onDismissRequest: () -> Unit,
    onSelect: (ConflictBookSummary) -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.shelf_candidate_picker_title),
    ) {
        if (candidates.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(candidates, key = { it.bookUrl }) { candidate ->
                    ConflictBookCard(
                        summary = candidate,
                        onClick = { onSelect(candidate) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
