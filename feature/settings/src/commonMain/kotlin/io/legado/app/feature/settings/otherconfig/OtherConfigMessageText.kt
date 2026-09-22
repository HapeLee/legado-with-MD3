package io.legado.app.feature.settings.otherconfig

import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.clear_webview_data_failed
import io.legado.app.feature.settings.res.clear_webview_data_success
import io.legado.app.feature.settings.res.complete_required_information
import org.jetbrains.compose.resources.getString

/**
 * [OtherConfigMessageRes] → 本地化文案（M5-8a）。
 *
 * 与 `feature/about` 的 `AboutMessage.localizedText()` 是同一个模式、同一批理由：
 * 迁移前 VM 直接持有 `R.string.*` 的 `Int`，共享层的 VM 没有 `Context`（而 `@StringRes` /
 * 资源 id 本身就是 Android 概念），于是契约改成**枚举**，由 UI 侧查表。
 * **只有本文件 import `Res`**，模块的公开契约因此零资源依赖。
 *
 * 返回 `String` 而不是 `StringResource`（同 about）：后者会把 `org.jetbrains.compose.resources`
 * 摆进公开签名，逼着 `implementation` 升成 `api`。`getString` 本来就是 `suspend`，
 * 调用点是宿主收集消息的 `LaunchedEffect`，天然在协程里。
 *
 * ⚠️ 本片（M5-8a）只迁了逻辑层，**调用点暂时在 `:app` 的 `OtherConfigRouteScreen`**；
 * 下一片把 Screen 也迁进来时，调用点会随之移入共享层，本文件不用改。
 */
suspend fun OtherConfigMessageRes.localizedText(): String = getString(
    when (this) {
        OtherConfigMessageRes.ClearWebViewDataSuccess -> Res.string.clear_webview_data_success
        OtherConfigMessageRes.ClearWebViewDataFailed -> Res.string.clear_webview_data_failed
        OtherConfigMessageRes.CompleteRequiredInformation ->
            Res.string.complete_required_information
    }
)
