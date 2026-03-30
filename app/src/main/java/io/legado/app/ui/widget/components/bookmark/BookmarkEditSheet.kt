package io.legado.app.ui.widget.components.bookmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkEditSheet(
    bookmark: Bookmark,
    onDismiss: () -> Unit,
    onSave: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit
) {

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var bookText by remember { mutableStateOf(bookmark.bookText) }
    var content by remember { mutableStateOf(bookmark.content) }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            AppText(
                text = bookmark.chapterName,
                style = LegadoTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = bookText,
                onValueChange = { bookText = it },
                label = { AppText("原文") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 10
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { AppText("摘要/笔记") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    AppText("删除")
                }

                Button(
                    onClick = {
                        val newBookmark = bookmark.apply {
                            this.bookText = bookText
                            this.content = content
                        }
                        onSave(newBookmark)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    AppText("保存")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { AppText("确认删除") },
            text = { AppText("你确定要删除这条书签吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(bookmark)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    AppText("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    AppText("取消")
                }
            }
        )
    }
}
