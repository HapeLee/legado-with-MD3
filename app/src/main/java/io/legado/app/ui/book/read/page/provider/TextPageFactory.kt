package io.legado.app.ui.book.read.page.provider

import io.legado.app.R
import io.legado.app.ui.book.read.page.ReaderPageSource
import io.legado.app.ui.book.read.page.api.DataSource
import io.legado.app.ui.book.read.page.api.PageFactory
import io.legado.app.ui.book.read.page.entities.TextPage
import splitties.init.appCtx

/**
 * 分页取页器。
 *
 * Track D·D2：页位置命令与加载消息经 [pageSource] 出入，本类不认识 `ReadBook`——
 * 渲染层不直接向业务单例下达命令。护栏见 `ReadViewBoundaryTest`。
 */
class TextPageFactory(
    dataSource: DataSource,
    private val pageSource: ReaderPageSource,
) : PageFactory<TextPage>(dataSource) {

    private val keepSwipeTip = appCtx.getString(R.string.keep_swipe_tip)

    override fun hasPrev(): Boolean = with(dataSource) {
        return hasPrevChapter() || pageIndex > 0
    }

    override fun hasNext(): Boolean = with(dataSource) {
        return hasNextChapter() || (currentChapter != null && currentChapter?.isLastIndex(pageIndex) != true)
    }

    override fun hasNextPlus(): Boolean = with(dataSource) {
        return hasNextChapter() || pageIndex < (currentChapter?.pageSize ?: 1) - 2
    }

    override fun moveToFirst() {
        pageSource.setPageIndex(0)
    }

    override fun moveToLast() = with(dataSource) {
        currentChapter?.let {
            if (it.pageSize == 0) {
                pageSource.setPageIndex(0)
            } else {
                pageSource.setPageIndex(it.pageSize.minus(1))
            }
        } ?: pageSource.setPageIndex(0)
    }

    override fun moveToNext(upContent: Boolean): Boolean = with(dataSource) {
        return if (hasNext()) {
            val pageIndex = pageIndex
            if (currentChapter == null || currentChapter?.isLastIndex(pageIndex) == true) {
                if ((currentChapter == null || isScroll) && nextChapter == null) {
                    return@with false
                }
                pageSource.moveToNextChapter(upContent)
            } else {
                if (pageIndex < 0 || currentChapter?.isLastIndexCurrent(pageIndex) == true) {
                    return@with false
                }
                pageSource.setPageIndex(pageIndex.plus(1))
            }
            if (upContent) upContent(resetPageOffset = false)
            true
        } else
            false
    }

    override fun moveToPrev(upContent: Boolean): Boolean = with(dataSource) {
        return if (hasPrev()) {
            if (pageIndex <= 0) {
                if (currentChapter == null && prevChapter == null) {
                    return@with false
                }
                if (prevChapter != null && prevChapter?.isCompleted == false) {
                    return@with false
                }
                pageSource.moveToPrevChapter(upContent)
            } else {
                if (currentChapter == null) {
                    return@with false
                }
                pageSource.setPageIndex(pageIndex.minus(1))
            }
            if (upContent) upContent(resetPageOffset = false)
            true
        } else
            false
    }

    override val curPage: TextPage
        get() = with(dataSource) {
            pageSource.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                return@with it.getPage(pageIndex)
                    ?: TextPage(title = it.title).apply { textChapter = it }.format()
            }
            return TextPage().format()
        }

    override val nextPage: TextPage
        get() = with(dataSource) {
            pageSource.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                val pageIndex = pageIndex
                if (pageIndex < it.pageSize - 1) {
                    return@with it.getPage(pageIndex + 1)?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
                if (!it.isCompleted) {
                    return@with TextPage(title = it.title).format()
                }
            }
            nextChapter?.let {
                return@with it.getPage(0)?.removePageAloudSpan()
                    ?: TextPage(title = it.title).format()
            }
            return TextPage().format()
        }

    override val prevPage: TextPage
        get() = with(dataSource) {
            pageSource.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                val pageIndex = pageIndex
                if (pageIndex > 0) {
                    return@with it.getPage(pageIndex - 1)?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
                if (!it.isCompleted) {
                    return@with TextPage(title = it.title).format()
                }
            }
            prevChapter?.let {
                return@with it.lastPage?.removePageAloudSpan()
                    ?: TextPage(title = it.title).format()
            }
            return TextPage().format()
        }

    override val nextPlusPage: TextPage
        get() = with(dataSource) {
            currentChapter?.let {
                val pageIndex = pageIndex
                if (pageIndex < it.pageSize - 2) {
                    return@with it.getPage(pageIndex + 2)?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
                if (!it.isCompleted) {
                    return@with TextPage(title = it.title).format()
                }
                nextChapter?.let { nc ->
                    if (pageIndex < it.pageSize - 1) {
                        return@with nc.getPage(0)?.removePageAloudSpan()
                            ?: TextPage(title = nc.title).format()
                    }
                    return@with nc.getPage(1)?.removePageAloudSpan()
                        ?: TextPage(text = keepSwipeTip).format()
                }
            }
            return TextPage().format()
        }
}
