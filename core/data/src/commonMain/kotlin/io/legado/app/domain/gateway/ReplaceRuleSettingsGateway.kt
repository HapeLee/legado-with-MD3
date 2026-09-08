package io.legado.app.domain.gateway

/**
 * 替换规则页的持久化显示偏好。
 *
 * 目前只承载列表排序模式。形态参照 [LocalPasswordGateway] /
 * [OtherConfigSystemGateway] 这类窄设置网关：读同步（构造期即可初始化）、写 suspend，
 * **不套** `currentSettings + settings: Flow + update {}` 三件套——本契约没有订阅方，
 * 加 Flow 会产生无调用方抽象。
 */
interface ReplaceRuleSettingsGateway {

    /**
     * 当前排序模式：`"asc"` / `"desc"`。读取失败或未设置时回落到 `"desc"`。
     */
    fun getSortMode(): String

    suspend fun setSortMode(mode: String)
}
