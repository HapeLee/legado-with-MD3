package io.legado.app.domain.gateway

import kotlinx.coroutines.flow.Flow

data class CheckSourceSettings(
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val threadCount: Int = DEFAULT_THREAD_COUNT,
    val writeSourceComment: Boolean = true,
    /**
     * 搜索与发现并发校验。任一分支失败即判定该源失效。
     */
    val quickMode: Boolean = false,
    /**
     * 先探测源地址端口连通性，不可达时直接判失效，不再发起规则请求。
     */
    val checkDomain: Boolean = false,
    val checkSearch: Boolean = true,
    val checkDiscovery: Boolean = true,
    val checkInfo: Boolean = true,
    val checkCategory: Boolean = true,
    val checkContent: Boolean = true,
) {
    /**
     * 收敛到可用区间。校验入口不再依赖 `require` 抛异常：
     * 超时为 0 会让每个书源立刻超时，线程数越界会直接创建异常大小的线程池，
     * 搜索与发现同时关闭则任何书源都不会被真正检查（却会被判成功）。
     */
    fun normalized(): CheckSourceSettings = copy(
        timeoutMillis = timeoutMillis.coerceIn(MIN_TIMEOUT_MILLIS, MAX_TIMEOUT_MILLIS),
        threadCount = threadCount.coerceIn(MIN_THREAD_COUNT, MAX_THREAD_COUNT),
        checkSearch = checkSearch || !checkDiscovery,
    )

    companion object {
        const val MIN_TIMEOUT_SECONDS = 1L
        const val MAX_TIMEOUT_SECONDS = 300L
        const val MIN_TIMEOUT_MILLIS = MIN_TIMEOUT_SECONDS * 1000
        const val MAX_TIMEOUT_MILLIS = MAX_TIMEOUT_SECONDS * 1000
        const val DEFAULT_TIMEOUT_MILLIS = 180_000L

        const val MIN_THREAD_COUNT = 1
        const val MAX_THREAD_COUNT = 64
        const val DEFAULT_THREAD_COUNT = 8
    }
}

interface CheckSourceSettingsGateway {
    val currentSettings: CheckSourceSettings
    val settings: Flow<CheckSourceSettings>

    suspend fun update(settings: CheckSourceSettings)
}
