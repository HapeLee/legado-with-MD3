package io.legado.app.help.config

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.data.repository.dataStore
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import splitties.init.appCtx
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * DataStore 写入序列化队列，所有经 [AppConfigStore] 的异步写入按提交顺序串行落盘。
 */
object DsSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val writeDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val pendingCount = AtomicInteger(0)

    /** 当前排队的写入任务数（含正在执行的那一个）。*/
    val pendingWriteCount: Int get() = pendingCount.get()

    /**
     * 串行写队列：所有经 [AppConfigStore] 的写入按提交顺序落盘。
     * 写入失败仅吞掉异常（与旧 PrefDelegate 行为一致），此时值仍在内存快照中，重启后丢失。
     */
    fun launchWrite(block: suspend () -> Unit): Job {
        pendingCount.incrementAndGet()
        return scope.launch(writeDispatcher) {
            try {
                val elapsed = measureTime { runCatching { block() } }
                if (elapsed > 500.milliseconds) {
                    LogUtils.d(
                        "DsSync",
                        "单次 edit 耗时 ${elapsed.inWholeMilliseconds}ms, 队列剩余 ${pendingCount.get() - 1}"
                    )
                }
            } finally {
                pendingCount.decrementAndGet()
            }
        }
    }

    /**
     * 等待所有排队的写入落盘完成（最长等待 [timeoutMs] 毫秒）。
     * 用于 restart 等必须确保"写入已持久化"的场景。
     */
    suspend fun awaitPendingWrites(timeoutMs: Long = 3000) {
        withTimeoutOrNull(timeoutMs) {
            while (pendingCount.get() > 0) {
                delay(50)
            }
        }
    }

    suspend fun putString(key: String, value: String?) {
        appCtx.dataStore.edit { prefs ->
            val dsKey = stringPreferencesKey(key)
            if (value == null) prefs.remove(dsKey) else prefs[dsKey] = value
        }
    }

    suspend fun putInt(key: String, value: Int) {
        appCtx.dataStore.edit { it[intPreferencesKey(key)] = value }
    }

    suspend fun putBoolean(key: String, value: Boolean) {
        appCtx.dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun putLong(key: String, value: Long) {
        appCtx.dataStore.edit { it[longPreferencesKey(key)] = value }
    }

    suspend fun putFloat(key: String, value: Float) {
        appCtx.dataStore.edit { it[floatPreferencesKey(key)] = value }
    }
}
