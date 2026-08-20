package io.legado.app.ui.book.audio

import android.app.Application
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.ReadAloudBgMode
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.removeType
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsInt
import io.legado.app.model.AudioPlay
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioPlayViewModel(
    private val application: Application,
    private val coordinator: AudioPlayCoordinator,
    private val bookRepository: BookRepository,
    private val otherSettingsGateway: OtherSettingsGateway,
    private val readAloudSettingsGateway: ReadAloudSettingsGateway,
    private val readSettingsGateway: ReadSettingsGateway,
) : ViewModel() {

    private val activeSheet = MutableStateFlow<AudioPlaySheet?>(null)

    val uiState = combine(
        coordinator.state,
        AppConfigStore.observeInt(PreferKey.audioPlayBgMode),
        activeSheet,
    ) { source, bgMode, sheet ->
        toUiState(source, bgMode ?: ReadAloudBgMode.Blur, sheet)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = toUiState(coordinator.snapshot(), readBgMode(), null),
    )

    private val _effects = MutableSharedFlow<AudioPlayEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var initialized = false

    fun init(bookUrl: String, inBookshelf: Boolean) {
        if (initialized) return
        initialized = true
        collectMediaButton()
        AudioPlay.inBookshelf = inBookshelf
        // 兼容通知栏/耳机键等无 bookUrl 的入口：兜底到当前播放的书籍
        val effectiveBookUrl = bookUrl.ifBlank { AudioPlay.book?.bookUrl.orEmpty() }
        viewModelScope.launch {
            val book = bookRepository.getBook(effectiveBookUrl) ?: return@launch
            initBook(book)
        }
    }

    /** 耳机/媒体键事件（由 MediaButtonReceiver 在有声书路由激活时投递） */
    private fun collectMediaButton() {
        viewModelScope.launch {
            eventFlow<Boolean>(EventBus.MEDIA_BUTTON).collect { play ->
                if (play) coordinator.togglePlay()
            }
        }
    }

    private inline fun <reified T> eventFlow(tag: String) = callbackFlow {
        val obs = Observer<T> { trySend(it) }
        LiveEventBus.get<T>(tag).observeForever(obs)
        awaitClose {
            LiveEventBus.get<T>(tag).removeObserver(obs)
        }
    }

    fun onLoadingChanged(loading: Boolean) {
        coordinator.setLoading(loading)
    }

    fun onIntent(intent: AudioPlayIntent) {
        when (intent) {
            is AudioPlayIntent.Init -> init(intent.bookUrl, intent.inBookshelf)
            AudioPlayIntent.Refresh -> coordinator.refresh()
            AudioPlayIntent.TogglePlay -> coordinator.togglePlay()
            AudioPlayIntent.Stop -> coordinator.stop()
            AudioPlayIntent.PreviousChapter -> coordinator.previous()
            AudioPlayIntent.NextChapter -> coordinator.next()
            is AudioPlayIntent.SelectChapter -> coordinator.skipTo(intent.index)
            is AudioPlayIntent.SeekTo -> coordinator.seekTo(intent.positionMs)
            AudioPlayIntent.ChangePlayMode -> coordinator.changePlayMode()
            is AudioPlayIntent.SetSpeed -> coordinator.setSpeed(intent.value)
            is AudioPlayIntent.SetTimer -> coordinator.setTimer(intent.minutes)
            is AudioPlayIntent.SetOpenCredits -> coordinator.setOpenCredits(intent.seconds)
            is AudioPlayIntent.SetCloseCredits -> coordinator.setCloseCredits(intent.seconds)
            is AudioPlayIntent.SetAudioGain -> coordinator.setAudioGain(intent.gainMb)
            AudioPlayIntent.CycleBgMode -> cycleBgMode()
            is AudioPlayIntent.OpenSheet -> activeSheet.value = intent.sheet
            AudioPlayIntent.DismissSheet -> activeSheet.value = null
            AudioPlayIntent.ChangeSource -> {
                val book = AudioPlay.book ?: return
                effect(AudioPlayEffect.OpenChangeSource(book.name, book.author))
            }

            AudioPlayIntent.Login -> {
                val source = AudioPlay.bookSource ?: return
                if (!source.loginUrl.isNullOrBlank()) {
                    effect(AudioPlayEffect.OpenLogin(source.bookSourceUrl))
                }
            }

            AudioPlayIntent.CopyPlayUrl -> effect(AudioPlayEffect.CopyPlayUrl)
            AudioPlayIntent.EditSource -> {
                val source = AudioPlay.bookSource ?: return
                effect(AudioPlayEffect.OpenEditSource(source.bookSourceUrl))
            }

            AudioPlayIntent.ToggleWakeLock -> viewModelScope.launch {
                otherSettingsGateway.update {
                    it.copy(audioPlayUseWakeLock = !it.audioPlayUseWakeLock)
                }
            }

            AudioPlayIntent.ToggleMediaControl -> viewModelScope.launch {
                readAloudSettingsGateway.update {
                    it.copy(
                        systemMediaControlCompatibilityChange =
                            !it.systemMediaControlCompatibilityChange
                    )
                }
            }

            AudioPlayIntent.SourceEdited -> coordinator.refreshBookSource()
            AudioPlayIntent.BackPressed -> effect(AudioPlayEffect.Finish)
        }
    }

    fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        viewModelScope.launch {
            if (!book.isAudio) {
                AudioPlay.stop()
            }
            withContext(Dispatchers.IO) {
                AudioPlay.book?.migrateTo(
                    book,
                    toc,
                    otherSettingsGateway.currentSettings.replaceEnableDefault,
                    readSettingsGateway.currentSettings.chineseConverterType,
                )
                book.removeType(BookType.updateError)
                AudioPlay.book?.delete()
                bookRepository.insert(book)
            }
            if (book.isAudio) {
                AudioPlay.book = book
                AudioPlay.bookSource = source
                bookRepository.insertChapters(*toc.toTypedArray())
                AudioPlay.upDurChapter()
                postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
                coordinator.refresh()
            } else {
                effect(AudioPlayEffect.OpenBookReader(book.bookUrl))
            }
        }
    }

    fun addToBookshelf(book: Book, toc: List<BookChapter>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    book.removeType(BookType.notShelf)
                    if (book.order == 0) {
                        book.order = bookRepository.getMinOrder() - 1
                    }
                    bookRepository.insert(book)
                    bookRepository.insertChapters(*toc.toTypedArray())
                }
                effect(AudioPlayEffect.ShowToast(application.getString(R.string.book_added_to_shelf)))
            } catch (e: Exception) {
                AppLog.put("添加书籍到书架失败", e)
                effect(AudioPlayEffect.ShowToast(application.getString(R.string.book_add_failed)))
            }
        }
    }

    private suspend fun initBook(book: Book) {
        val isSameBook = AudioPlay.book?.bookUrl == book.bookUrl
        if (isSameBook) {
            AudioPlay.upData(book)
        } else {
            AudioPlay.resetData(book)
        }
        if (book.tocUrl.isEmpty() && !loadBookInfo(book)) {
            return
        }
        if (AudioPlay.chapterSize == 0 && !loadChapterList(book)) {
            return
        }
        coordinator.refresh()
    }

    private suspend fun loadBookInfo(book: Book): Boolean {
        val bookSource = AudioPlay.bookSource ?: return true
        return try {
            WebBook.getBookInfoAwait(bookSource, book)
            true
        } catch (e: Exception) {
            AppLog.put("详情页出错: ${e.localizedMessage}", e, true)
            false
        }
    }

    private suspend fun loadChapterList(book: Book): Boolean {
        val bookSource = AudioPlay.bookSource ?: return true
        return try {
            val oldBook = book.copy()
            val cList = WebBook.getChapterListAwait(bookSource, book).getOrThrow()
            if (oldBook.bookUrl == book.bookUrl) {
                bookRepository.update(book)
            } else {
                bookRepository.replace(oldBook, book)
            }
            bookRepository.deleteChaptersByBook(book.bookUrl)
            bookRepository.insertChapters(*cList.toTypedArray())
            AudioPlay.chapterSize = cList.size
            AudioPlay.simulatedChapterSize = book.simulatedTotalChapterNum()
            AudioPlay.upDurChapter()
            true
        } catch (e: Exception) {
            effect(AudioPlayEffect.ShowToast(application.getString(R.string.error_load_toc)))
            false
        }
    }

    private fun cycleBgMode() {
        val next = when (readBgMode()) {
            ReadAloudBgMode.Solid -> ReadAloudBgMode.Blur
            ReadAloudBgMode.Blur -> ReadAloudBgMode.FlowingLight
            ReadAloudBgMode.FlowingLight -> ReadAloudBgMode.Transparent
            else -> ReadAloudBgMode.Solid
        }
        AppConfigStore.putInt(PreferKey.audioPlayBgMode, next)
    }

    private fun readBgMode(): Int =
        AppConfigStore.preferences.compatDsInt(PreferKey.audioPlayBgMode)
            ?: ReadAloudBgMode.Blur

    private fun toUiState(
        source: AudioPlaySourceState,
        bgMode: Int,
        sheet: AudioPlaySheet?,
    ): AudioPlayUiState = AudioPlayUiState(
        bookUrl = source.bookUrl,
        bookName = source.bookName,
        author = source.author,
        coverPath = source.coverPath,
        sourceOrigin = source.sourceOrigin,
        chapterIndex = source.chapterIndex,
        chapterTitle = source.chapterTitle,
        chapters = source.chapters,
        status = source.status,
        isLoading = source.isLoading,
        position = source.position,
        duration = source.duration,
        speed = source.speed,
        timerMinutes = source.timerMinutes,
        playMode = source.playMode,
        bgMode = bgMode,
        canLogin = source.canLogin,
        wakeLockEnabled = source.wakeLockEnabled,
        mediaControlEnabled = source.mediaControlEnabled,
        canPrevious = source.canPrevious,
        canNext = source.canNext,
        openCredits = source.openCredits,
        closeCredits = source.closeCredits,
        audioGain = source.audioGain,
        activeSheet = sheet,
    )

    private fun effect(value: AudioPlayEffect) {
        _effects.tryEmit(value)
    }
}
