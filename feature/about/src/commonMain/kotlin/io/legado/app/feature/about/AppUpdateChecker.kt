package io.legado.app.feature.about

import androidx.compose.runtime.Stable

/**
 * 一次更新检查的结果（M5-1c）。
 *
 * 字段与顺序逐字照抄迁移前的 `io.legado.app.help.update.AppUpdate.UpdateInfo`——它是纯数据
 * （四个 `val String`），没有任何 Android/JVM 类型，因此可以直接在共享层重建一份同形定义；
 * Android 实现负责在边界上做**恒等**映射。之所以不把原类型搬下来：它的宿主
 * `AppUpdate`（`gitHubUpdate` / `AppUpdateInterface`）依赖 `Coroutine`、OkHttp、GSON 与
 * `AppConst.appInfo`，整包留在 `:app`。
 */
@Stable
data class UpdateInfo(
    val tagName: String,
    val updateLog: String,
    val downloadUrl: String,
    val fileName: String,
)

/**
 * 更新检查契约（M5-1c）。
 *
 * 迁移前 VM 里是 `AppUpdate.gitHubUpdate?.run { check(viewModelScope) ... }`——那条链路要
 * OkHttp + GSON 门面 + `AppConst.appInfo`（版本名/渠道），全部只在 `:app` 可解析，因此按
 * AGENTS.md「Android 专有能力通过窄接口进入共享层」抽成契约。
 *
 * 失败语义：**用 `Result` 表达，不要抛异常**。迁移前的 `onError { }` 分支会把
 * `e.localizedMessage` 拼进提示（`AboutMessage.CheckUpdateFailed` 的 `detail`），调用方需要
 * 拿到异常本身而不是被中断。
 *
 * 渠道不可用（迁移前 `gitHubUpdate == null`）不在契约里建模：`AppUpdate.gitHubUpdate` 是
 * `by lazy { AppUpdateGitHub }`，恒非 null，那条分支实际不可达。
 */
interface AppUpdateChecker {

    suspend fun check(): Result<UpdateInfo>
}
