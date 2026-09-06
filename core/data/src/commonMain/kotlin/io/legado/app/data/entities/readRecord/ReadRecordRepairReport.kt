package io.legado.app.data.entities.readRecord

/** 阅读记录问题扫描或修复后的汇总结果。 */
data class ReadRecordRepairReport(
    /** 扫描时为发现的可合并重复项数量，修复时为实际合并的记录数量。 */
    val mergedCount: Int = 0,
    /** 处理过程中发生异常的记录数量。 */
    val exceptionCount: Int = 0,
    /** 扫描时为发现的字段重复阅读时段记录数，修复时为实际删除的重复记录数。 */
    val duplicateSessionCount: Int = 0,
    /** 被规范化书名或作者字段的记录数量。 */
    val normalizedRecordCount: Int = 0,
)
