package io.legado.app.data.rate

import io.legado.app.core.platform.systemTimeMillis
import java.util.concurrent.ConcurrentHashMap

/**
 * 并发率登记表（M2-4b 从 `:app` `help.ConcurrentRateLimiter` 的 companion 下沉）。
 *
 * 迁移前 `BaseSource.putConcurrent` 只能经 `SourceRuntimeProvider.current.updateConcurrentRate`
 * 调到 `:app` 的 `ConcurrentRateLimiter.Companion`。登记表本身只依赖
 * `ConcurrentHashMap` + [systemTimeMillis]，不依赖任何 `:app` 状态，所以整体搬到 `:core:data`：
 * `BaseSource`（共享层）与 `:app` 的 `ConcurrentRateLimiter` 各自直连，Provider 少一项。
 *
 * ⚠️ **`java.util.concurrent.ConcurrentHashMap` 是 JVM-only**：本仓 `commonMain` 里已有先例
 * （`domain/usecase/AiTaskManager.kt` 用同一个类），G2 `checkSharedPurity` 目前**不拦 `java.util.*`**，
 * 且 `compileCommonMainKotlinMetadata` 被跳过 ⇒ 这一处越界不会被任何构建任务发现。
 * 将来真正加非 JVM 目标（iOS）时，这里要换成 `expect` 的并发容器（见 memory 的门禁盲区记录）。
 * 本次是**沿用既有状态**，不是新开一类越界。
 */
object ConcurrentRateRegistry {

    /** key = `BaseSource.getKey()`，value = 该源的并发记录。 */
    val records = ConcurrentHashMap<String, ConcurrentRecord>()

    /**
     * 更新并发率。内容与迁移前 `ConcurrentRateLimiter.updateConcurrentRate` 逐字一致：
     * `"次数/间隔"` 或纯数字 `"间隔"`（此时次数为 1），非法值保持原记录不变。
     */
    fun update(key: String, concurrentRate: String) {
        records.compute(key) { _, record ->
            try {
                val rateIndex = concurrentRate.indexOf("/")
                when {
                    rateIndex > 0 -> {
                        val accessLimit = concurrentRate.take(rateIndex).toInt()
                        val interval = concurrentRate.substring(rateIndex + 1).toInt()
                        if (accessLimit <= 0 || interval <= 0) throw NumberFormatException()
                        ConcurrentRecord(
                            record?.time ?: systemTimeMillis(),
                            accessLimit,
                            interval,
                            record?.frequency ?: 0
                        )
                    }

                    concurrentRate.toInt() > 0 -> {
                        ConcurrentRecord(
                            record?.time ?: systemTimeMillis(),
                            1,
                            concurrentRate.toInt(),
                            record?.frequency ?: 0
                        )
                    }

                    else -> record
                }
            } catch (_: NumberFormatException) {
                record
            }
        }
    }
}
