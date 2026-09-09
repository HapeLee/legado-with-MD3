package io.legado.app.ui.book.info.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.result.LocalResultEventBus
import io.legado.app.ui.main.BookInfoEdited
import io.legado.app.ui.main.MainRoute
import io.legado.app.ui.main.MainRouteBookCharacterList
import io.legado.app.ui.main.MainRouteBookCharacterNetwork
import io.legado.app.ui.main.MainRouteBookEventList
import io.legado.app.ui.main.MainRouteBookKnowledgeList
import org.koin.compose.viewmodel.koinViewModel

/**
 * 书籍信息编辑页的 nav3 入口（原 `BookInfoEditActivity`，已删）。
 *
 * 与原 Activity 的逐条对应关系：
 * - `onCreate` 读 `intent.getStringExtra("bookUrl")` 后 `loadBook` → 这里按 [bookUrl] 加载；
 * - 保存成功后 `setResult(RESULT_OK)` + `finish()` → 往 [resultKey] 发 [BookInfoEdited] 再出栈；
 *   保存失败/校验不通过时 `save` 不回调，页面不出栈（与原行为一致）；
 * - 四个「人物/关系/知识/事件」入口原本 `startActivity(MainIntent.createXxxIntent(...))`，
 *   编辑页进入返回栈后改为栈内导航，不再另起一个 MainActivity。
 */
@Composable
fun BookInfoEditRouteScreen(
    bookUrl: String,
    resultKey: String?,
    onBack: () -> Unit,
    onNavigateToRoute: (MainRoute) -> Unit,
) {
    val viewModel = koinViewModel<BookInfoEditViewModel>()
    val resultBus = LocalResultEventBus.current

    LaunchedEffect(bookUrl, viewModel) {
        viewModel.loadBook(bookUrl)
    }

    BookInfoEditScreen(
        viewModel = viewModel,
        onBack = onBack,
        onSave = {
            viewModel.save {
                resultKey?.let { resultBus.sendResult(it, BookInfoEdited) }
                onBack()
            }
        },
        onOpenCharacterList = { onNavigateToRoute(MainRouteBookCharacterList(it)) },
        onOpenCharacterNetwork = { onNavigateToRoute(MainRouteBookCharacterNetwork(it)) },
        onOpenKnowledgeList = { onNavigateToRoute(MainRouteBookKnowledgeList(it)) },
        onOpenEventList = { onNavigateToRoute(MainRouteBookEventList(it)) },
    )
}
