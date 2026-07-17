package io.legado.app.help.config

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.legado.app.data.repository.dataStore
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
 * 2. pending overlay：写入先进内存立即对读侧生效，异步串行落盘后移除，
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

    val isInitialized: Boolean get() = core != null

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
            launchWrite = { DsSync.launchWrite(it) },
            persist = { key, value -> dataStore.edit { it.setPrefValue(key, value) } },
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
 * pending 条目在对应落盘完成后移除；落盘返回的 Preferences 是该次 edit 后的完整状态，
 * 必然新于此前收到的任何回灌，因此落盘完成时直接采纳为 snapshot——即使 collector
 * 随后才处理一条落盘前的旧回灌，下一轮 collect 读到的也是最新状态，旧值不会驻留。
 */
internal class PendingOverlayCore(
    initial: Preferences,
    private val launchWrite: (suspend () -> Unit) -> Unit,
    private val persist: suspend (key: String, value: Any?) -> Preferences,
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
                val result = runCatching { persist(key, value) }
                synchronized(lock) {
                    result.onSuccess { 
                        snapshot = it 
                    }.onFailure { e ->
                        val isOverridden = pending[key] !== write
                        if (isOverridden) {
                            LogUtils.e("AppConfigStore", "保存设置失败且已被新写入覆盖: key=$key, value=$value\n${e.stackTraceStr}")
                        } else {
                            LogUtils.e("AppConfigStore", "保存设置失败: key=$key, value=$value\n${e.stackTraceStr}")
                        }
                    }
                    // 同 key 已有更新的写入时不移除，继续由新条目覆盖
                    if (pending[key] === write) pending.remove(key)
                    rebuild()
                }
            }
        }
    }

    fun onEmission(prefs: Preferences) {
        synchronized(lock) {
            snapshot = prefs
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
