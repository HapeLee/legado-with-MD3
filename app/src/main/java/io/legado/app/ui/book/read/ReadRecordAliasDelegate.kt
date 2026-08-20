package io.legado.app.ui.book.read

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordAliasAction
import io.legado.app.data.entities.readRecord.ReadRecordAliasDecision
import io.legado.app.data.entities.readRecord.ReadRecordIdentity
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 未知作者阅读记录的归属确认与合并域。 */
class ReadRecordAliasDelegate(
    private val scope: CoroutineScope,
    private val localPreferencesRepository: SettingsRepository,
    private val readRecordRepository: ReadRecordRepository,
    private val hasActiveDialog: () -> Boolean,
    private val showConflict: (Book, Long) -> Unit,
    private val dismissDialog: () -> Unit,
) {
    private var pendingBook: Book? = null
    private var pendingSources: List<ReadRecord> = emptyList()
    private var pendingKey: String? = null

    /** 打开书籍时检查未知作者记录；已有决定则自动执行，否则请求显示确认框。 */
    suspend fun check(book: Book) {
        if (hasActiveDialog()) return
        val sources = readRecordRepository.getUnknownAuthorRecords(book.name)
        if (sources.isEmpty()) return
        val key = ReadRecordIdentity.key(book.name, book.author)
        val decision = localPreferencesRepository
            .getString(LocalPreferencesKeys.READ_RECORD_ALIAS_DECISIONS.name)
            .first()
            .split('\n')
            .mapNotNull { ReadRecordAliasDecision.decode(it, key) }
            .firstOrNull()
        if (decision != null) {
            if (decision == ReadRecordAliasAction.MERGE) merge(book, sources)
            return
        }
        pendingBook = book
        pendingSources = sources
        pendingKey = key
        showConflict(book, sources.sumOf { it.readTime })
    }

    /** 处理用户的合并/保留选择，并按需记住决定。 */
    fun resolve(merge: Boolean, rememberChoice: Boolean) {
        val book = pendingBook
        val sources = pendingSources
        val key = pendingKey
        pendingBook = null
        pendingSources = emptyList()
        pendingKey = null
        dismissDialog()
        if (book == null) return
        scope.launch {
            if (rememberChoice && key != null) {
                val old = localPreferencesRepository
                    .getString(LocalPreferencesKeys.READ_RECORD_ALIAS_DECISIONS.name)
                    .first()
                val cleaned = ReadRecordAliasDecision.removeForKey(old, key)
                localPreferencesRepository.putString(
                    LocalPreferencesKeys.READ_RECORD_ALIAS_DECISIONS.name,
                    listOfNotNull(
                        cleaned.takeIf { it.isNotBlank() },
                        ReadRecordAliasDecision.encode(
                            key,
                            if (merge) ReadRecordAliasAction.MERGE else ReadRecordAliasAction.KEEP,
                        ),
                    ).joinToString("\n"),
                )
            }
            if (merge) merge(book, sources)
        }
    }

    /** 清除所有已记住的未知作者归属决定。 */
    fun clearDecisions() {
        scope.launch {
            localPreferencesRepository.putString(
                LocalPreferencesKeys.READ_RECORD_ALIAS_DECISIONS.name,
                "",
            )
        }
    }

    private suspend fun merge(book: Book, sources: List<ReadRecord>) {
        sources.groupBy { it.deviceId }.forEach { (deviceId, records) ->
            readRecordRepository.mergeIndependentReadRecordsInto(
                ReadRecord(deviceId, book.name, book.author),
                records,
            )
        }
    }
}
