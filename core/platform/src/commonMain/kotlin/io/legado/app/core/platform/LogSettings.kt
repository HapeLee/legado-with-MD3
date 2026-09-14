package io.legado.app.core.platform

/**
 * 共享层日志门控（M2-4a）。
 *
 * 迁移前 [AppLogStore.putDebug] 读的是 `:app` 的 `OtherSettingsGateway.currentSettings.recordLog`；
 * 环形缓冲下沉到共享层后，共享层读不到设置网关，于是按本仓既有做法（[DebugFlags]、
 * [RuleDataStorage.rootDir]）把「设置里的一个布尔」变成**由 host 写入的配置项**。
 *
 * 宿主契约（`:app`）：
 *  1. `App.onCreate` 里同步写一次初值；
 *  2. 订阅 `OtherSettingsGateway.settings` 的 `recordLog` 并持续同步——这样设置界面开关、
 *     恢复备份等任何写入路径都能生效，而不是只在启动时读一次。
 *
 * 未写入时保持 `false`，等价于设置项默认关闭（`PreferKey.recordLog` 默认 false）。
 */
object LogSettings {

    @Volatile
    var recordLog: Boolean = false
}
