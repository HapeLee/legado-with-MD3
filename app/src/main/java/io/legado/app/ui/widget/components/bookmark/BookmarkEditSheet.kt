package io.legado.app.ui.widget.components.bookmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.widget.components.AppTextFieldSurface
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.button.SecondaryButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkEditSheet(
    show: Boolean,
    bookmark: Bookmark,
    onDismiss: () -> Unit,
    onSave: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit
) {

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var bookText by remember(bookmark) { mutableStateOf(bookmark.bookText) }
    var content by remember(bookmark) { mutableStateOf(bookmark.content) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        title = bookmark.chapterName,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            AppTextFieldSurface(
                value = bookText,
                onValueChange = { bookText = it },
                label = "原文",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 10
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppTextFieldSurface(
                value = content,
                onValueChange = { content = it },
                label = "摘要/笔记",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.weight(1f),
                    text = "删除"
                )

                PrimaryButton(
                    onClick = {
                        val newBookmark = bookmark.apply {
                            this.bookText = bookText
                            this.content = content
                        }
                        onSave(newBookmark)
                    },
                    modifier = Modifier.weight(1f),
                    text = "保存"
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    AppAlertDialog(
        show = showDeleteConfirmDialog,
        onDismissRequest = { showDeleteConfirmDialog = false },
        title = "确认删除",
        text = "你确定要删除这条书签吗？",
        confirmText = "删除",
        onConfirm = {
            showDeleteConfirmDialog = false
            onDelete(bookmark)
        },
        dismissText = "取消",
        onDismiss = { showDeleteConfirmDialog = false }
    )
}
