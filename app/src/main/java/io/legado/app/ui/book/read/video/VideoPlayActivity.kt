package io.legado.app.ui.book.read.video

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseComposeActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.model.ReadBook
import io.legado.app.ui.theme.AppTheme
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class VideoChapterItem(
    val index: Int,
    val title: String,
    val videoUrl: String,
    val posterUrl: String? = null
)

class VideoPlayActivity : BaseComposeActivity() {

    private var bookUrl: String = ""
    private var currentBook: Book? = null

    private val chaptersFlow = MutableStateFlow<List<VideoChapterItem>>(emptyList())
    private val currentIndexFlow = MutableStateFlow(0)
    private val isLoadingFlow = MutableStateFlow(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookUrl = intent.getStringExtra("bookUrl") ?: return
        lifecycleScope.launch {
            val book = withContext(Dispatchers.IO) {
                appDb.bookDao.getBook(bookUrl)
            }
            if (book == null) {
                toastOnUi("Book not found")
                finish()
                return@launch
            }
            currentBook = book
            ReadBook.upData(book)
            ReadBook.upWebBook(book)
            loadChapters(book)
        }
    }

    @androidx.compose.runtime.Composable
    override fun Content() {
        VideoReadScreen(
            bookName = currentBook?.name ?: "",
            chapters = chaptersFlow,
            currentIndex = currentIndexFlow,
            isLoading = isLoadingFlow,
            onBack = { finish() },
            onChapterClick = { idx ->
                currentIndexFlow.value = idx
                currentBook?.let { book ->
                    chaptersFlow.value.getOrNull(idx)?.let { chapter ->
                        saveProgress(book, chapter.index)
                    }
                }
            }
        )
    }

    private fun loadChapters(book: Book) {
        isLoadingFlow.value = true
        lifecycleScope.launch {
            val chapterList = withContext(Dispatchers.IO) {
                appDb.bookChapterDao.getChapterList(book.bookUrl)
            }
            val videoChapters = ArrayList<VideoChapterItem>()
            for (chapter in chapterList) {
                val content = try {
                    BookHelp.getContent(book, chapter)
                } catch (e: Exception) {
                    null
                }
                val videoUrl = extractVideoUrl(content, chapter)
                val posterUrl = extractImageUrl(content)
                videoChapters.add(
                    VideoChapterItem(
                        index = chapter.index,
                        title = chapter.title,
                        videoUrl = videoUrl ?: "",
                        posterUrl = posterUrl
                    )
                )
            }
            chaptersFlow.value = videoChapters
            if (videoChapters.isNotEmpty()) {
                currentIndexFlow.value = book.durChapterIndex.coerceIn(0, videoChapters.size - 1)
            }
            isLoadingFlow.value = false
        }
    }

    private fun saveProgress(book: Book, chapterIndex: Int) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                book.durChapterIndex = chapterIndex
                book.durChapterPos = 0
                appDb.bookDao.update(book)
            }
        }
    }

    private fun extractVideoUrl(content: String?, chapter: BookChapter?): String? {
        if (content.isNullOrBlank()) return chapter?.resourceUrl
        // Try direct video URL pattern
        val videoPattern = Pattern.compile(
            "https?://[^\"'<>\\s]+\\.(mp4|webm|m3u8|flv|mkv|avi|mov|m4v)(\\?[^\"'<>\\s]*)?",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = videoPattern.matcher(content)
        if (matcher.find()) {
            return matcher.group(0)
        }
        // Try href/src attributes
        val attrPattern = Pattern.compile(
            "(?:href|src)=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        )
        val attrMatcher = attrPattern.matcher(content)
        if (attrMatcher.find()) {
            return attrMatcher.group(1)
        }
        // Check if content itself is a URL
        if (content.matches(Regex("^https?://\\S+$", RegexOption.IGNORE_CASE))) {
            return content.trim()
        }
        return chapter?.resourceUrl
    }

    private fun extractImageUrl(content: String?): String? {
        if (content.isNullOrBlank()) return null
        val pattern = Pattern.compile(
            "<img[^>]*src=[\"']([^\"']+)[\"']|poster=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(content)
        if (matcher.find()) {
            return matcher.group(1) ?: matcher.group(2)
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        currentBook?.let {
            ReadBook.uploadProgress(false)
        }
    }
}
