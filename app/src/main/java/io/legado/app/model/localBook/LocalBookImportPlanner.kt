package io.legado.app.model.localBook

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isArchive
import io.legado.app.help.book.isLocal

/**
 * 导入本地文件时，判断能否复用书架里那条同名、但文件已经读不到的记录。
 *
 * 源文件被外部删掉时，书架里那条记录仍在：它只剩下分组、阅读进度等用户数据，而
 * `bookUrl` 指向的文件已经失效。此时重新导入同名文件若直接 insert，会再裂出一条同名书
 * ——旧记录指向已删除的文件（读不了），新记录没有阅读进度。正确做法是把旧记录迁到这次
 * 导入的文件上（复用），而不是新增。
 *
 * 只在**能确认旧文件读不到**时复用：
 * * 旧文件仍可读：用户确实另有一份同名文件，两份各自成书；
 * * 文件名不同：不是同一本书；
 * * 压缩包来源（`loc_book::xxx.rar`）或 webDav 来源：它们的文件可以由压缩包重新解压、
 *   或从远端重新下载，"当前读不到"不代表记录失效，位置探测不能作为判据。
 *
 * @return 可复用的旧记录；null 表示应当新建。
 */
internal fun planLocalBookImportReuse(
    existingBook: Book?,
    existingFileReadable: Boolean,
    importedFileName: String,
    importedBookUrl: String,
): Book? {
    val book = existingBook ?: return null
    if (book.bookUrl == importedBookUrl) return null
    if (existingFileReadable) return null
    if (!book.matchesImportedFile(importedFileName)) return null
    return book
}

private fun Book.matchesImportedFile(importedFileName: String): Boolean =
    isLocal &&
            !isArchive &&
            origin == BookType.localTag &&
            originName.isNotBlank() &&
            originName == importedFileName
