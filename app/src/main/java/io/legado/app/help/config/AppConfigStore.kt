package io.legado.app.help.config

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.legado.app.data.repository.dataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

import io.legado.app.utils.LogUtils
import io.legado.app.utils.stackTraceStr

/**
 * "settings" DataStore 的进程内内存快照层，设置读取的唯一同步入口。
 *
 * 三个职责（不做其他事，防止长成 AppConfig 2.0）：
 * 1. snapshot：App.onCreate 首行同步预加载一次（同时触发 SharedPreferencesMigration），
 *    之后由常驻 collector 跟随 DataStore 变化回灌，保证外部写入（Restore 批量写等）可见；
 * 2. pending overlay：写入先进内存立即对读侧生效，异步串行落盘、回灌确认后移除，
 *    保证"写后立即读"永远读到新值，collector 携带旧状态回灌也不会闪烁；
 * 3. observe：按 key 订阅变化，替代 SP OnSharedPreferenceChangeListener。
 *
 * 约束：读 API 是纯内存查找（主线程零 IO），但 onCreate 的预加载耗时与 settings 文件
 * 大小成正比——大 value（长 JSON、图片路径列表等）不得写进 "settings" DataStore，
 * 应走各自的文件级配置。
 */
object AppConfigStore {

    @Volatile
    private var core: PendingOverlayCore? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 在 App.onCreate 首行调用，先于一切设置读取（主题初始化等）。
     * runBlocking 首读会触发 DataStore 迁移，与旧版首个 PrefDelegate 构造时的行为一致，
     * 只是显式提前且全程只做一次。
     */
    fun init(context: Context) {
        if (core != null) return
        val dataStore = context.applicationContext.dataStore
        val initial = runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .first()
        }
        val newCore = PendingOverlayCore(
            initial = initial,
            launchWrite = { SettingsWriter.launchWrite(it) },
            persist = { key, value -> dataStore.edit { it.setPrefValue(key, value) } },
            persistAll = { values ->
                dataStore.edit { prefs ->
                    values.forEach { (key, value) -> prefs.setPrefValue(key, value) }
                }
            },
        )
        core = newCore
        scope.launch {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .collect { newCore.onEmission(it) }
        }
    }

    private fun requireCore(): PendingOverlayCore =
        checkNotNull(core) { "AppConfigStore 未初始化，应在 App.onCreate 首行调用 init()" }

    /** 当前生效的设置快照（DataStore 落盘状态 + 未落盘写入叠加） */
    val preferences: Preferences get() = requireCore().preferencesFlow.value

    val preferencesFlow: StateFlow<Preferences> get() = requireCore().preferencesFlow

    // 读取：key 不存在时返回 null，由调用方（过渡期的门面）决定回退行为
    fun getString(key: String): String? = preferences.compatDsString(key)
    fun getInt(key: String): Int? = preferences.compatDsInt(key)
    fun getBoolean(key: String): Boolean? = preferences.compatDsBoolean(key)
    fun getLong(key: String): Long? = preferences.compatDsLong(key)
    fun getFloat(key: String): Float? = preferences.compatDsFloat(key)
    fun getStringSet(key: String): Set<String>? = preferences.compatDsStringSet(key)

    // 写入：立即对读侧生效，异步串行落盘；value 为 null 表示移除
    fun putString(key: String, value: String?) = requireCore().put(key, value)
    fun putInt(key: String, value: Int) = requireCore().put(key, value)
    fun putBoolean(key: String, value: Boolean) = requireCore().put(key, value)
    fun putLong(key: String, value: Long) = requireCore().put(key, value)
    fun putFloat(key: String, value: Float) = requireCore().put(key, value)
    fun putStringSet(key: String, value: Set<String>) = requireCore().put(key, value)
    fun remove(key: String) = requireCore().put(key, null)

    /** 批量写入（Restore 恢复备份等）：整批立即对读侧生效，单次 edit 落盘 */
    fun putAll(values: Map<String, Any?>) = requireCore().putAll(values)

    /** 批量写入并等待 DataStore edit 完成；失败会回滚 overlay 并向调用方抛出。 */
    suspend fun putAllAndAwait(values: Map<String, Any?>) =
        requireCore().putAllAndAwait(values)

    /**
     * 基于当前生效快照原子更新设置。
     * [transform] 必须是纯计算、快速且不挂起，不得在其中执行 IO 或回调外部状态。
     */
    fun <T> atomicUpdate(
        read: (Preferences) -> T,
        toPrefMap: (T) -> Map<String, Any?>,
        transform: (T) -> T,
    ) = requireCore().atomicUpdate(read, toPrefMap, transform)

    /**
     * 原子更新设置并等待 DataStore edit 完成。
     * [transform] 必须是纯计算、快速且不挂起。
     */
    suspend fun <T> atomicUpdateAndAwait(
        read: (Preferences) -> T,
        toPrefMap: (T) -> Map<String, Any?>,
        transform: (T) -> T,
    ) = requireCore().atomicUpdateAndAwait(read, toPrefMap, transform)

    // 订阅：替代 SP 监听器的统一入口，值不存在时发 null
    fun observeString(key: String): Flow<String?> = observe { it.compatDsString(key) }
    fun observeInt(key: String): Flow<Int?> = observe { it.compatDsInt(key) }
    fun observeBoolean(key: String): Flow<Boolean?> = observe { it.compatDsBoolean(key) }
    fun observeLong(key: String): Flow<Long?> = observe { it.compatDsLong(key) }
    fun observeFloat(key: String): Flow<Float?> = observe { it.compatDsFloat(key) }

    private inline fun <T> observe(crossinline read: (Preferences) -> T?): Flow<T?> =
        preferencesFlow.map { read(it) }.distinctUntilChanged()
}

/**
 * 快照 + pending overlay 的核心状态机，与 Android/DataStore 解耦以便 JVM 单测。
 *
 * 不变式：[preferencesFlow] = snapshot 叠加所有 pending 写入，pending 中的值永远优先。
 * snapshot 只由 collector 回灌（[onEmission]）推进，回灌按落盘提交顺序到达，因此单调不回退；
 * pending 条目要等到回灌中该 key 的值已等于写入目标值时才移除——落盘完成后若先处理到
 * 一条落盘前的旧回灌，该 key 仍被 overlay 保护，读值不会短暂回滚（回滚会让 observe
 * 订阅方收到"新→旧→新"的幻影变更，如触发多余的 Activity recreate / WebDav refresh）。
 * 落盘失败时移除对应 pending 实现回滚，读取回退到已落盘状态。
 */
internal class PendingOverlayCore(
    initial: Preferences,
    private val launchWrite: (suspend () -> Unit) -> Unit,
    private val persist: suspend (key: String, value: Any?) -> Preferences,
    private val persistAll: suspend (values: Map<String, Any?>) -> Preferences,
) {

    private val lock = Any()
    private var snapshot: Preferences = initial
    private val pending = LinkedHashMap<String, PendingWrite>()
    private val _preferences = MutableStateFlow(initial)
    val preferencesFlow: StateFlow<Preferences> get() = _preferences

    private class PendingWrite(val value: Any?)

    fun put(key: String, value: Any?) {
        synchronized(lock) {
            val write = PendingWrite(value)
            pending[key] = write
            rebuild()
            // 在锁内提交写任务，保证落盘顺序与 pending 覆盖顺序一致
            launchWrite {
                // 成功时不动 snapshot/pending：等 collector 回灌到含新值的状态时（onEmission）
                // 再移除 overlay，避免"落盘完成后才处理到旧回灌"导致读值短暂回滚
                runCatching { persist(key, value) }.onFailure { e ->
                    synchronized(lock) {
                        if (pending[key] === write) {
                            // 失败回滚：移除 overlay，读取回退到已落盘状态
                            pending.remove(key)
                            rebuild()
                            LogUtils.e("AppConfigStore", "保存设置失败: key=$key, value=$value\n${e.stackTraceStr}")
                        } else {
                            // 同 key 已有更新的写入，继续由新条目覆盖
                            LogUtils.e("AppConfigStore", "保存设置失败且已被新写入覆盖: key=$key, value=$value\n${e.stackTraceStr}")
                        }
                    }
                }
            }
        }
    }

    fun putAll(values: Map<String, Any?>) = enqueueAll(values, completion = null)

    suspend fun putAllAndAwait(values: Map<String, Any?>) {
        if (values.isEmpty()) return
        val completion = CompletableDeferred<Result<Unit>>()
        enqueueAll(values, completion)
        completion.await().getOrThrow()
    }

    /**
     * 读取 overlay、计算 transform 与差量入队均在同一临界区内完成。
     * transform 必须是纯计算、快速且不挂起。
     */
    fun <T> atomicUpdate(
        read: (Preferences) -> T,
        toPrefMap: (T) -> Map<String, Any?>,
        transform: (T) -> T,
    ) {
        synchronized(lock) {
            val values = atomicDiffLocked(read, toPrefMap, transform)
            if (values.isNotEmpty()) enqueueAllLocked(values, completion = null)
        }
    }

    /** [transform] 必须是纯计算、快速且不挂起。 */
    suspend fun <T> atomicUpdateAndAwait(
        read: (Preferences) -> T,
        toPrefMap: (T) -> Map<String, Any?>,
        transform: (T) -> T,
    ) {
        val completion = synchronized(lock) {
            val values = atomicDiffLocked(read, toPrefMap, transform)
            if (values.isEmpty()) {
                null
            } else {
                CompletableDeferred<Result<Unit>>().also {
                    enqueueAllLocked(values, completion = it)
                }
            }
        }
        completion?.await()?.getOrThrow()
    }

    private fun <T> atomicDiffLocked(
        read: (Preferences) -> T,
        toPrefMap: (T) -> Map<String, Any?>,
        transform: (T) -> T,
    ): Map<String, Any?> {
        val current = read(_preferences.value)
        val previous = toPrefMap(current)
        return toPrefMap(transform(current)).filter { (key, value) -> previous[key] != value }
    }

    private fun enqueueAll(
        values: Map<String, Any?>,
        completion: CompletableDeferred<Result<Unit>>?,
    ) {
        if (values.isEmpty()) {
            completion?.complete(Result.success(Unit))
            return
        }
        synchronized(lock) {
            enqueueAllLocked(values, completion)
        }
    }

    /** 调用方必须已持有 [lock]。 */
    private fun enqueueAllLocked(
        values: Map<String, Any?>,
        completion: CompletableDeferred<Result<Unit>>?,
    ) {
        val writes = values.mapValues { PendingWrite(it.value) }
        pending.putAll(writes)
        rebuild()
        launchWrite {
            runCatching { persistAll(values) }
                .onSuccess { completion?.complete(Result.success(Unit)) }
                .onFailure { e ->
                    synchronized(lock) {
                        var removed = false
                        writes.forEach { (key, write) ->
                            if (pending[key] === write) {
                                pending.remove(key)
                                removed = true
                            }
                        }
                        if (removed) rebuild()
                        LogUtils.e("AppConfigStore", "批量保存设置失败: keys=${values.keys}\n${e.stackTraceStr}")
                    }
                    completion?.complete(Result.failure(e))
                }
        }
    }

    fun onEmission(prefs: Preferences) {
        synchronized(lock) {
            snapshot = prefs
            // 回灌已包含某个 pending 写入的目标值时，说明该写入已落盘且对 collector 可见，
            // 此时移除 overlay 才不会被更早的旧回灌回滚
            pending.entries.removeAll { (key, write) -> prefs.rawPrefValue(key) == write.value }
            rebuild()
        }
    }

    private fun rebuild() {
        if (pending.isEmpty()) {
            _preferences.value = snapshot
            return
        }
        val prefs = snapshot.toMutablePreferences()
        pending.forEach { (key, write) -> prefs.setPrefValue(key, write.value) }
        _preferences.value = prefs.toPreferences()
    }
}
