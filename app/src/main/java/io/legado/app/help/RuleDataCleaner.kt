package io.legado.app.help

import io.legado.app.data.appDb
import io.legado.app.data.bigdata.RuleDataFileStore
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

/**
 * 规则大变量（ruleData）清理：删除已不在库中的书 / RSS 源留下的目录。
 *
 * M2-2 之前这是 `RuleBigDataHelp.clearInvalid`。存储实现下沉 `:core:data` 的
 * [RuleDataFileStore] 后，这里只剩「查 `appDb` 判断条目是否还有效」这一件 host 侧的事
 * ——`appDb` 属于 host，不进共享层。
 *
 * 判断规则与迁移前逐条一致：
 * - 非目录（遗留散落文件）⇒ 删；
 * - 目录缺标记文件（`bookUrl.txt` / `origin.txt`）⇒ 删；
 * - 标记里的标识在库中不存在 ⇒ 删。
 */
object RuleDataCleaner {

    suspend fun clearInvalid() {
        withContext(IO) {
            RuleDataFileStore.listBookOwners().forEach { owner ->
                if (isInvalid(owner.isDirectory, owner.id) { appDb.bookDao.has(it) }) {
                    RuleDataFileStore.deleteBookEntry(owner.name)
                }
            }
            RuleDataFileStore.listRssOwners().forEach { owner ->
                if (isInvalid(owner.isDirectory, owner.id) { appDb.rssSourceDao.has(it) }) {
                    RuleDataFileStore.deleteRssEntry(owner.name)
                }
            }
        }
    }

    private inline fun isInvalid(
        isDirectory: Boolean,
        id: String?,
        exists: (String) -> Boolean,
    ): Boolean = when {
        !isDirectory -> true
        id == null -> true
        else -> !exists(id)
    }
}
