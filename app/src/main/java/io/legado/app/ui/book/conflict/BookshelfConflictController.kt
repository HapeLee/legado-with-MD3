package io.legado.app.ui.book.conflict

import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookshelfConflict
import io.legado.app.domain.usecase.AddToBookshelfResult
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.BookTocUnavailableException
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.domain.usecase.ResolveBookshelfConflictUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「加入书架」冲突流程的状态宿主，搜索、发现、首页推荐三个入口共用。
 *
 * 它编排用例并持有状态，因此放在功能目录下，与 `ui/widget/components/` 的纯展示组件分开；
 * 屏幕层只渲染 `BookshelfConflictSheet` 并把用户选择转给 [coexist] / [migrate]。
 *
 * 没有 Android 生命周期依赖，由宿主 ViewModel 传入自己的 [scope]。
 */
class BookshelfConflictController(
    private val scope: CoroutineScope,
    private val addToBookshelfUseCase: AddToBookshelfUseCase,
    private val resolveBookshelfConflictUseCase: ResolveBookshelfConflictUseCase,
) {

    private val _conflict = MutableStateFlow<BookshelfConflict?>(null)
    val conflict = _conflict.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    val isResolving = _isResolving.asStateFlow()

    private val _effects = MutableSharedFlow<Effect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var pendingBook: SearchBook? = null

    fun addToShelf(searchBook: SearchBook) {
        scope.launch(Dispatchers.IO) {
            when (val result = addToBookshelfUseCase.execute(searchBook)) {
                AddToBookshelfResult.Added ->
                    _effects.emit(Effect.ShowMessage(R.string.book_added_to_shelf))

                is AddToBookshelfResult.Conflict -> {
                    pendingBook = searchBook
                    _conflict.value = result.conflict
                }
            }
        }
    }

    fun coexist(existingBookUrl: String, options: ChangeSourceMigrationOptions) {
        resolve(existingBookUrl, options, coexist = true)
    }

    fun migrate(existingBookUrl: String, options: ChangeSourceMigrationOptions) {
        resolve(existingBookUrl, options, coexist = false)
    }

    fun dismiss() {
        _conflict.value = null
        pendingBook = null
    }

    private fun resolve(
        existingBookUrl: String,
        options: ChangeSourceMigrationOptions,
        coexist: Boolean,
    ) {
        val searchBook = pendingBook ?: return
        scope.launch(Dispatchers.IO) {
            _isResolving.value = true
            val result = runCatching {
                if (coexist) {
                    resolveBookshelfConflictUseCase.coexist(
                        existingBookUrl = existingBookUrl,
                        newBook = searchBook.toBook(),
                        options = options,
                    )
                } else {
                    resolveBookshelfConflictUseCase.migrate(
                        existingBookUrl = existingBookUrl,
                        newBook = searchBook.toBook(),
                        options = options,
                    )
                }
            }
            _isResolving.value = false
            _conflict.value = null
            pendingBook = null
            result
                .onSuccess {
                    _effects.emit(
                        Effect.ShowMessage(
                            if (coexist) R.string.bookshelf_conflict_coexist_done
                            else R.string.bookshelf_conflict_migrate_done
                        )
                    )
                }
                .onFailure {
                    AppLog.put("处理书架冲突出错\n${it.localizedMessage}", it, true)
                    _effects.emit(
                        Effect.ShowMessage(
                            if (it is BookTocUnavailableException) {
                                R.string.bookshelf_conflict_toc_failed
                            } else {
                                R.string.bookshelf_conflict_resolve_failed
                            }
                        )
                    )
                }
        }
    }

    sealed interface Effect {
        data class ShowMessage(@StringRes val messageRes: Int) : Effect
    }
}
