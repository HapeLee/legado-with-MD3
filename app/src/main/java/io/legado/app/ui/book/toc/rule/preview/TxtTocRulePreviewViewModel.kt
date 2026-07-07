package io.legado.app.ui.book.toc.rule.preview

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.repository.TxtTocRuleRepository
import io.legado.app.help.DefaultData
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.Utf8BomUtils
import io.legado.app.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class TxtTocRulePreviewViewModel(
    private val app: Application,
    private val repository: TxtTocRuleRepository,
) : ViewModel() {

    private val context get() = app.applicationContext

    private val _uiState = MutableStateFlow(TxtTocRulePreviewUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TxtTocRulePreviewEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var book: Book? = null
    private var lazyComputeJob: Job? = null

    fun init(bookUrl: String, currentTocRegex: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            loadRules(bookUrl, currentTocRegex)
        }
    }

    fun onIntent(intent: TxtTocRulePreviewIntent) {
        when (intent) {
            is TxtTocRulePreviewIntent.SelectRule -> {
                _uiState.update { it.copy(selectedRule = intent.rule) }
            }
            is TxtTocRulePreviewIntent.ShowChapterList -> {
                _uiState.update { it.copy(activeSheet = TxtTocRulePreviewSheet.ChapterList(intent.item)) }
            }
            is TxtTocRulePreviewIntent.DismissSheet -> {
                _uiState.update { it.copy(activeSheet = null) }
            }
            is TxtTocRulePreviewIntent.ToggleLayout -> {
                _uiState.update { it.copy(isGridLayout = !it.isGridLayout) }
            }
            is TxtTocRulePreviewIntent.OpenManagePage -> {
                _effects.tryEmit(TxtTocRulePreviewEffect.OpenManagePage)
            }
            is TxtTocRulePreviewIntent.EditRule -> {
                _uiState.update { it.copy(activeSheet = null, editingRule = intent.rule) }
            }
            is TxtTocRulePreviewIntent.DismissEditDialog -> {
                _uiState.update { it.copy(editingRule = null) }
            }
            is TxtTocRulePreviewIntent.SaveRule -> {
                viewModelScope.launch(Dispatchers.IO) {
                    saveRuleAndRefresh(intent.rule)
                }
            }
            is TxtTocRulePreviewIntent.ToggleSearch -> {
                _uiState.update {
                    it.copy(
                        showSearch = !it.showSearch,
                        searchQuery = if (it.showSearch) "" else it.searchQuery,
                    )
                }
            }
            is TxtTocRulePreviewIntent.UpdateSearchQuery -> {
                _uiState.update { it.copy(searchQuery = intent.query) }
            }
            is TxtTocRulePreviewIntent.ApplyRule -> {
                val selectedRule = _uiState.value.selectedRule
                if (selectedRule.isNotEmpty()) {
                    _effects.tryEmit(TxtTocRulePreviewEffect.ApplyRule(selectedRule))
                }
            }
        }
    }

    private suspend fun loadRules(bookUrl: String, currentTocRegex: String?) {
        _uiState.update { it.copy(loading = true) }

        val book = runCatching { appDb.bookDao.getBook(bookUrl) }.getOrNull()
        this.book = book
        val currentRule = currentTocRegex ?: book?.tocUrl ?: ""

        val allRules = getAllRules()

        // Placeholders: totalCount = -1 means not computed yet, 0 means no book / computed empty
        val previewItems = allRules.map { tocRule ->
            TocRulePreviewItem(rule = tocRule, totalCount = if (book != null) -1 else 0)
        }.toMutableList()

        _uiState.update {
            it.copy(
                loading = false,
                rules = previewItems.toImmutableList(),
                currentRule = currentRule,
                selectedRule = currentRule,
            )
        }

        // Lazy compute chapter counts in background
        if (book != null) {
            computeChaptersLazy(book, allRules)
        }
    }

    private fun computeChaptersLazy(book: Book, rules: List<TxtTocRule>) {
        lazyComputeJob?.cancel()
        lazyComputeJob = viewModelScope.launch(Dispatchers.IO) {
            val resultMap = mutableMapOf<Long, TocRulePreviewItem>()
            for (tocRule in rules) {
                ensureActive()
                val item = computePreview(book, tocRule)
                resultMap[item.rule.id] = item
            }
            _uiState.update { state ->
                val newRules = state.rules.map { existing ->
                    resultMap[existing.rule.id] ?: existing
                }.toImmutableList()
                state.copy(rules = newRules)
            }
        }
    }

    private suspend fun computePreview(book: Book?, tocRule: TxtTocRule): TocRulePreviewItem {
        if (book == null) return TocRulePreviewItem(rule = tocRule)
        return try {
            val pattern = try {
                tocRule.rule.toPattern(Pattern.MULTILINE)
            } catch (e: PatternSyntaxException) {
                return TocRulePreviewItem(rule = tocRule, totalCount = 0)
            }
            val (chapters, total) = analyzeWithPattern(book, pattern)
            TocRulePreviewItem(
                rule = tocRule,
                chapterCount = chapters.size,
                totalCount = total,
                chapters = chapters.take(500).toImmutableList(),
            )
        } catch (e: Exception) {
            TocRulePreviewItem(rule = tocRule, totalCount = 0)
        }
    }

    private suspend fun saveRuleAndRefresh(updatedRule: TxtTocRule) {
        // Validate
        if (updatedRule.name.isBlank() || updatedRule.rule.isBlank()) {
            _effects.tryEmit(TxtTocRulePreviewEffect.ShowToast(context.getString(R.string.cannot_empty)))
            _uiState.update { it.copy(editingRule = null) }
            return
        }
        if (runCatching { updatedRule.rule.toPattern(Pattern.MULTILINE) }.isFailure) {
            _effects.tryEmit(TxtTocRulePreviewEffect.ShowToast(context.getString(R.string.invalid_format)))
            _uiState.update { it.copy(editingRule = null) }
            return
        }

        // Save to DB
        val existing = runCatching { appDb.txtTocRuleDao.getByIds(setOf(updatedRule.id)) }.getOrNull()?.firstOrNull()
        if (existing != null) {
            repository.update(updatedRule)
        } else {
            repository.insert(updatedRule)
        }

        // Cancel pending lazy compute to avoid race condition
        lazyComputeJob?.cancel()

        // Refresh the specific rule preview
        val currentRules = _uiState.value.rules.toMutableList()
        val index = currentRules.indexOfFirst { it.rule.id == updatedRule.id }
        val book = this.book
        if (index >= 0 && book != null) {
            val refreshed = computePreview(book, updatedRule)
            currentRules[index] = refreshed
            _uiState.update {
                it.copy(
                    rules = currentRules.toImmutableList(),
                    editingRule = null,
                )
            }
        } else {
            _uiState.update { it.copy(editingRule = null) }
        }

        // Restart lazy compute for remaining rules
        if (book != null) {
            val remainingRules = currentRules.filter { it.totalCount < 0 }.map { it.rule }
            if (remainingRules.isNotEmpty()) {
                computeChaptersLazy(book, remainingRules)
            }
        }
    }

    private suspend fun getAllRules(): List<TxtTocRule> {
        var rules = repository.all()
        if (repository.count() == 0) {
            val defaultRules = DefaultData.txtTocRules
            repository.insert(*defaultRules.toTypedArray())
            rules = repository.all()
        }
        return rules.filter { it.rule.isNotBlank() }.sortedBy { it.serialNumber }
    }

    private suspend fun analyzeWithPattern(book: Book, pattern: Pattern): Pair<List<String>, Int> {
        val chapters = mutableListOf<String>()
        var totalCount = 0
        val charset = book.fileCharset()
        val blank = 0x0a.toByte()
        val bufferSize = 512000

        runCatching {
            LocalBook.getBookInputStream(book).use { bis ->
                val buffer = ByteArray(bufferSize)
                var bufferStart = 3
                bis.read(buffer, 0, 3)
                if (Utf8BomUtils.hasBom(buffer)) {
                    bufferStart = 0
                }
                var length: Int
                while (bis.read(buffer, bufferStart, bufferSize - bufferStart).also { length = it } > 0) {
                    coroutineContext.ensureActive()
                    var end = bufferStart + length
                    if (end == bufferSize) {
                        for (i in bufferStart + length - 1 downTo (bufferStart + length - 4096).coerceAtLeast(0)) {
                            if (buffer[i] == blank) {
                                end = i
                                break
                            }
                        }
                    }
                    val blockContent = String(buffer, 0, end, charset)
                    buffer.copyInto(buffer, 0, end, bufferStart + length)
                    bufferStart = bufferStart + length - end

                    val matcher = pattern.matcher(blockContent)
                    while (matcher.find()) {
                        totalCount++
                        if (chapters.size < 500) {
                            chapters.add(matcher.group())
                        }
                    }
                }
            }
        }
        return chapters to totalCount
    }
}

private fun String.toPattern(flags: Int): Pattern = Pattern.compile(this, flags)
