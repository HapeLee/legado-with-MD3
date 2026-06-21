package io.legado.app.ui.book.character

import android.app.Activity
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import io.legado.app.base.BaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.help.character.BookCharacterIdentityMigrator
import io.legado.app.help.readaloud.ReadAloudConfigChangeNotifier
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.character.compose.CharacterManageScreen
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookCharacterManageActivity : BaseActivity<ViewBinding>(
    fullScreen = false,
    imageBg = false
) {

    private lateinit var composeView: ComposeView
    override val binding: ViewBinding by lazy {
        composeView = ComposeView(this)
        SimpleViewBinding(composeView)
    }

    private var bookUrl: String = ""
    private var characterBookKey: String = ""
    private var book: Book? = null
    private var characters by mutableStateOf<List<BookCharacter>>(emptyList())

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookUrl = intent.getStringExtra(EXTRA_BOOK_URL)
            ?: ReadBook.book?.bookUrl
            ?: ""
        book = appDb.bookDao.getBook(bookUrl)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            CharacterManageScreen(
                bookName = book?.name.orEmpty(),
                characters = characters,
                onBack = ::finish,
                onAdd = { openEdit() },
                onOpenCard = { openCard(it.id) },
                onEdit = { openEdit(it.id) },
                onDelete = ::delete,
                onOpenRelations = ::openRelations
            )
        }
        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        if (bookUrl.isBlank()) {
            characters = emptyList()
            return
        }
        lifecycleScope.launch {
            val data = withContext(IO) {
                val key = BookCharacterIdentityMigrator.migrate(book)
                key to appDb.bookCharacterDao.characters(key)
            }
            characterBookKey = data.first
            characters = data.second
        }
    }

    private fun openCard(id: Long) {
        if (bookUrl.isBlank() || id <= 0L) {
            toastOnUi("当前角色不存在")
            return
        }
        startActivity<BookCharacterCardActivity> {
            putExtra(EXTRA_BOOK_URL, bookUrl)
            putExtra(EXTRA_CHARACTER_BOOK_KEY, characterBookKey)
            putExtra(EXTRA_CHARACTER_ID, id)
        }
    }

    private fun openEdit(id: Long = 0L) {
        if (bookUrl.isBlank()) {
            toastOnUi("当前书籍不存在")
            return
        }
        startActivity<BookCharacterEditActivity> {
            putExtra(EXTRA_BOOK_URL, bookUrl)
            putExtra(EXTRA_CHARACTER_BOOK_KEY, characterBookKey)
            if (id > 0) putExtra(EXTRA_CHARACTER_ID, id)
        }
    }

    private fun openRelations() {
        if (characters.size < 2) {
            toastOnUi("至少需要两个角色才能编辑关系网")
            return
        }
        startActivity<BookCharacterRelationActivity> {
            putExtra(EXTRA_BOOK_URL, bookUrl)
            putExtra(EXTRA_CHARACTER_BOOK_KEY, characterBookKey)
        }
    }

    private fun delete(character: BookCharacter) {
        alert("删除角色") {
            setMessage("确定删除「${character.displayName()}」？相关关系也会删除。")
            yesButton {
                lifecycleScope.launch {
                    withContext(IO) {
                        appDb.bookCharacterDao.deleteCharacterWithRelations(character)
                    }
                    ReadAloudConfigChangeNotifier.notifySpeech()
                    load()
                    setResult(Activity.RESULT_OK)
                }
            }
            noButton()
        }
    }

    companion object {
        const val EXTRA_BOOK_URL = "bookUrl"
        const val EXTRA_CHARACTER_BOOK_KEY = "characterBookKey"
        const val EXTRA_CHARACTER_ID = "characterId"
    }
}
