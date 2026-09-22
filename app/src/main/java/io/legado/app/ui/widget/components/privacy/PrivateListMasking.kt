package io.legado.app.ui.widget.components.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.domain.gateway.PrivateAccessGateway
import io.legado.app.domain.gateway.PrivateContentGateway
import io.legado.app.domain.model.PrivateAccessState
import io.legado.app.domain.model.PrivateBookFacts
import io.legado.app.domain.model.settings.PrivateAccessSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * 列表页共用：这批书里**此刻需要脱敏**的是哪些。
 *
 * 只服务"列表里的条目"：命中集合里的 bookUrl 应换成模糊封面 + 不显示任何文本
 * （见 [PrivateLockedCover] 与书架卡片的做法）。判定与书架、详情页、阅读器闸门共用同一套
 * 规则（私密 ∪ 分组 + 授权 + 打开书籍时验证），不另立第二份。
 *
 * 放在 composable 里而不是各页 ViewModel：列表页只需要一个"要不要脱敏"的布尔，
 * 让四处列表各写一遍 VM 管线反而更容易漂移。网关访问收在这一个组件内，调用方不碰数据层。
 */
@Composable
fun rememberPrivateLockedBookUrls(bookUrls: List<String>): Set<String> {
    val privateContentGateway: PrivateContentGateway = koinInject()
    val privateAccessGateway: PrivateAccessGateway = koinInject()
    val access by privateAccessGateway.state.collectAsStateWithLifecycle(PrivateAccessState())
    val settings by privateAccessGateway.settings.collectAsStateWithLifecycle(PrivateAccessSettings())

    val facts by produceState<Map<String, PrivateBookFacts>>(
        initialValue = emptyMap(),
        key1 = bookUrls,
    ) {
        // 一次查回整批：列表页按条查会变成 N 次 IO
        value = withContext(Dispatchers.IO) {
            bookUrls.distinct().associateWith { privateContentGateway.factsOf(it) }
        }
    }

    return remember(bookUrls, facts, access, settings) {
        if (!settings.verifyOnOpenBook) {
            emptySet()
        } else {
            bookUrls.filterTo(mutableSetOf()) { url ->
                val itemFacts = facts[url] ?: return@filterTo false
                itemFacts.isPrivate && !access.isTargetGranted(url, itemFacts.groupMask)
            }
        }
    }
}

/**
 * 只有书名 + 作者的列表条目（阅读记录）的标识。
 *
 * 这类表里没存 bookUrl，所以只能拿书名+作者当身份。
 */
data class PrivateRecordKey(val name: String, val author: String)

/**
 * 与 [rememberPrivateLockedBookUrls] 同义，只是换成"按名+作者"的入口。
 *
 * 阅读记录、书签这类表里没有 bookUrl，只能反查 books；反查不到按"与私密无关"处理。
 */
@Composable
fun rememberPrivateLockedRecords(records: List<PrivateRecordKey>): Set<PrivateRecordKey> {
    val privateContentGateway: PrivateContentGateway = koinInject()
    val privateAccessGateway: PrivateAccessGateway = koinInject()
    val access by privateAccessGateway.state.collectAsStateWithLifecycle(PrivateAccessState())
    val settings by privateAccessGateway.settings.collectAsStateWithLifecycle(PrivateAccessSettings())

    val facts by produceState<Map<PrivateRecordKey, PrivateBookFacts>>(
        initialValue = emptyMap(),
        key1 = records,
    ) {
        value = withContext(Dispatchers.IO) {
            records.distinct().mapNotNull { key ->
                privateContentGateway.factsOfByName(key.name, key.author)?.let { key to it }
            }.toMap()
        }
    }

    return remember(records, facts, access, settings) {
        if (!settings.verifyOnOpenBook) {
            emptySet()
        } else {
            records.filterTo(mutableSetOf()) { key ->
                val itemFacts = facts[key] ?: return@filterTo false
                itemFacts.isPrivate && !access.isTargetGranted(null, itemFacts.groupMask)
            }
        }
    }
}

