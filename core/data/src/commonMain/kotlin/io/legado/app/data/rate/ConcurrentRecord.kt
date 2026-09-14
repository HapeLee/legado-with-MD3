package io.legado.app.data.rate

/**
 * 单个源的并发率记录（M2-4b 从 `:app` 的 `model.analyzeRule.AnalyzeUrl.ConcurrentRecord`
 * 原样抽出为共享类型）。
 *
 * 抽出的原因：它的持有者 [ConcurrentRateRegistry] 必须下沉到共享层，才能让
 * `BaseSource.putConcurrent`（`:core:data` 的 commonMain）不再经 `SourceRuntimeProvider`
 * 绕到 `:app` 的 `ConcurrentRateLimiter`。字段与非空语义与迁移前逐字一致——
 * 消费方 `ConcurrentRateLimiter.fetchStart` 会**原地修改**这些 `var`。
 */
data class ConcurrentRecord(
    /**
     * 开始访问时间
     */
    var time: Long,
    /**
     * 限制次数
     */
    var accessLimit: Int,
    /**
     * 间隔时间
     */
    var interval: Int,
    /**
     * 正在访问的个数
     */
    var frequency: Int
)
