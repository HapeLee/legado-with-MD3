package io.legado.app.data.entities.readRecord

/** 主动合并时计算汇总中未被阅读时段覆盖的旧版历史时长。备份恢复不使用此策略。 */
object ReadRecordTimeTotals {
    fun legacy(readTime: Long, sessionTime: Long): Long =
        (readTime - sessionTime).coerceAtLeast(0L)

    fun total(sessionTime: Long, legacyReadTime: Long): Long =
        sessionTime + legacyReadTime.coerceAtLeast(0L)
}
