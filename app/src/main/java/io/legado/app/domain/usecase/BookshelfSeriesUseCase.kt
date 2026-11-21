package io.legado.app.domain.usecase

import io.legado.app.domain.repository.BookDomainRepository

/**
 * 书架折叠收纳（同系列多卷）的写入入口。
 *
 * 系列归属即 books.seriesId（系列名），因此没有"建系列"的独立记录：
 * - 用已存在的名字收纳 = 把书追加到那个系列末尾（移入）；
 * - 用新名字收纳 = 新建系列，卷序从 0 开始；
 * - 改名是整组批量改名，解散是把整组的 seriesId 清空。
 */
class BookshelfSeriesUseCase(
    private val bookRepository: BookDomainRepository
) {

    /**
     * @param bookUrls 按书架当前顺序传入，决定系列内卷序
     * @return 实际写入的系列名；空输入返回 null
     */
    suspend fun createSeries(bookUrls: List<String>, name: String): String? {
        val seriesName = name.trim()
        val urls = bookUrls.distinct()
        if (seriesName.isEmpty() || urls.isEmpty()) return null
        val exists = seriesName in bookRepository.getSeriesNames()
        val startOrder = if (exists) {
            bookRepository.getMaxSeriesOrder(seriesName) + 1
        } else {
            0
        }
        bookRepository.assignSeries(urls, seriesName, startOrder)
        // 表里先占一行，后面设置独立封面时不必再区分"新建/已存在"
        bookRepository.ensureSeries(seriesName)
        return seriesName
    }

    suspend fun setCover(seriesName: String, cover: String?) {
        if (seriesName.isBlank()) return
        bookRepository.setSeriesCover(seriesName, cover)
    }

    /** @return false 表示目标名已被其他系列占用，调用方应提示而不是静默合并 */
    suspend fun renameSeries(oldName: String, newName: String): Boolean {
        val target = newName.trim()
        if (target.isEmpty() || target == oldName) return false
        if (target in bookRepository.getSeriesNames()) return false
        bookRepository.renameSeries(oldName, target)
        return true
    }

    suspend fun dissolveSeries(seriesName: String) {
        if (seriesName.isBlank()) return
        bookRepository.dissolveSeries(seriesName)
    }

    suspend fun removeBooksFromSeries(bookUrls: Set<String>) {
        bookRepository.removeBooksFromSeries(bookUrls)
    }
}
