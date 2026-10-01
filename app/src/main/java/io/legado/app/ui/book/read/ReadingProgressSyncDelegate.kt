package io.legado.app.ui.book.read

import io.legado.app.domain.usecase.GetReadingProgressUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 云端阅读进度同步域：让 [ReadBookUiState.isReadingProgressSyncConfigured] 反映**当前**
 * 的云端可用性，而不是开书那一瞬间的快照。
 *
 * `AppWebDav` 的 WebDAV 初始化走网络（校验授权 + 建目录），只由 App 启动时的设置流触发。
 * 开书早于初始化完成、或初始化失败一次，`syncFromReadBook` 投影出的 false 就没有任何路径
 * 会被纠正——阅读界面的「拉取云端进度 / 覆盖云端进度」永久消失。上游是 View 菜单，
 * `onPrepareOptionsMenu` 每次开菜单都重算 `AppWebDav.isOk`；Compose 侧没有这个时机，
 * 故在这里订阅状态流，并在开菜单时补一次初始化。
 *
 * 与 [ReadReplaceRuleDelegate] 同形，自己开订阅，不需要宿主显式 `start`。
 */
class ReadingProgressSyncDelegate(
    private val scope: CoroutineScope,
    private val getReadingProgressUseCase: GetReadingProgressUseCase,
    private val host: Host,
) {

    /** 把云端可用性写回 UiState（菜单可见性直接读它）。 */
    fun interface Host {
        fun setSyncConfigured(configured: Boolean)
    }

    /** 订阅云端可用性变更（StateFlow 会立刻回放当前值，晚订阅也不会漏掉初始态）。 */
    fun start() {
        scope.launch {
            getReadingProgressUseCase.isConfiguredFlow.collect(host::setSyncConfigured)
        }
    }

    /** 打开阅读菜单时补齐云端配置（上游 `onPrepareOptionsMenu` 的重算时机）。初始化走网络，故在 IO。 */
    fun ensureConfigured() {
        scope.launch(Dispatchers.IO) {
            getReadingProgressUseCase.ensureConfigured()
        }
    }

    /**
     * 菜单点击路径用：必须**等**初始化结束再落真正的读写，否则首次点击会撞上
     * `authorization` 仍为空而被静默丢弃（上传只返回 false，界面上没有任何反馈）。
     */
    suspend fun ensureConfiguredNow() {
        withContext(Dispatchers.IO) {
            getReadingProgressUseCase.ensureConfigured()
        }
    }
}
