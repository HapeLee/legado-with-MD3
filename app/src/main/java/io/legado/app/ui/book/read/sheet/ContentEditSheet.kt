package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.model.ReadBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.sendToClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ContentEditSheet(
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var content by remember { mutableStateOf("") }
    var chapterTitle by remember { mutableStateOf("") }
    var saveToSource by remember { mutableStateOf(false) }
    val book = remember { ReadBook.book }
    val isLocalTxt = remember { book?.isLocalTxt == true }

    LaunchedEffect(Unit) {
        val loadedContent = withContext(Dispatchers.IO) {
            val b = ReadBook.book ?: return@withContext null
            val chapter = appDb.bookChapterDao
                .getChapter(b.bookUrl, ReadBook.durChapterIndex)
                ?: return@withContext null
            chapterTitle = chapter.getDisplayTitle()
            val contentProcessor = ContentProcessor.get(b.name, b.origin)
            val rawContent = BookHelp.getContent(b, chapter) ?: return@withContext null
            contentProcessor.getContent(b, chapter, rawContent, includeTitle = false)
                .toString()
        }
        content = loadedContent ?: ""
        isLoading = false
    }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = {
            scope.launch(Dispatchers.IO) {
                saveContent(book, content, saveToSource)
            }
            onDismissRequest()
        },
        title = chapterTitle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        saveContent(book, content, saveToSource)
                    }
                    onDismissRequest()
                }) {
                    Text(stringResource(R.string.action_save))
                }
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val resetText = resetContent()
                        withContext(Dispatchers.Main) {
                            content = resetText
                        }
                        ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                    }
                }) {
                    Text(stringResource(R.string.reset))
                }
                TextButton(onClick = {
                    context.sendToClip("$chapterTitle\n$content")
                }) {
                    Text(stringResource(R.string.copy_all))
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(400.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }

            if (isLocalTxt) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = saveToSource,
                        onCheckedChange = { saveToSource = it },
                    )
                    Text(
                        text = stringResource(R.string.save_to_source),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private suspend fun saveContent(
    book: io.legado.app.data.entities.Book?,
    content: String,
    saveToSource: Boolean,
) {
    if (content.isEmpty()) return
    val b = book ?: return
    val chapter = appDb.bookChapterDao
        .getChapter(b.bookUrl, ReadBook.durChapterIndex)
        ?: return
    BookHelp.saveText(b, chapter, content, saveToSource)
    ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
}

private suspend fun resetContent(): String {
    val book = ReadBook.book ?: return ""
    val chapter = appDb.bookChapterDao
        .getChapter(book.bookUrl, ReadBook.durChapterIndex)
        ?: return ""
    BookHelp.delContent(book, chapter)
    if (!book.isLocal) {
        ReadBook.bookSource?.let { bookSource ->
            WebBook.getContentAwait(bookSource, book, chapter)
        }
    }
    val contentProcessor = ContentProcessor.get(book.name, book.origin)
    val rawContent = BookHelp.getContent(book, chapter)
    return if (rawContent != null) {
        contentProcessor.getContent(book, chapter, rawContent, includeTitle = false)
            .toString()
    } else {
        ""
    }
}
