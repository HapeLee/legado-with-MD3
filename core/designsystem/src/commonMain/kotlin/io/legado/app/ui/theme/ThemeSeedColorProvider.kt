package io.legado.app.ui.theme

/**
 * 自定义配色未指定 seed 时的回退主色来源。
 *
 * 这个值在 app 侧来自主题偏好存储（`lib/theme/ThemeStore`：先读 `primary_color` 偏好，
 * 缺失时回落到 `?attr/colorPrimary`）。它是**应用层状态**，不是共享 UI 概念，所以共享层
 * 不能直接依赖 `ThemeStore`——那会把一套遗留主题存储拖进来。
 *
 * 因此沿用本仓库既有的「接口 + provider 注入」手法（同 `BigDataStore`、`SourceRuntime`）：
 * 由宿主在 `Application.onCreate` 里 [install]，取用时未注入则**直接抛异常**，不做静默兜底，
 * 避免共享层悄悄换了一套主色语义。
 *
 * M1-3l：本契约原先带 `context: Context` 参数，这既把 `android.*` 钉死在签名上、也让
 * [ThemeEngine] 无法进 `commonMain`。现在改为**无参**——Android 实现自己持有 `appCtx`
 * （`ThemeStore.primaryColor` 的默认参数本来就是 `appCtx`）。
 *
 * ⚠️ 语义边界：该回退**只**在「`Custom` 模式且调用方明确传 `customSeedColor = null`」时求值。
 * 现网三个调用点（`AppTheme`、`ThemeConfigScreen` ×2）传的都是非空 `Int`（`ThemeSettings.customPrimary`
 * 声明为 `Int = 0`），`OpaqueColorScheme` 则因 `forceOpaque = true` 把 `Transparent` 归一到 `WH`、
 * 根本走不到 `Custom` 分支 ⇒ **本回退当前是死代码**。保留它是为了语义完整，不是因为它在跑。
 */
fun interface ThemeSeedColorProvider {
    fun primaryColor(): Int
}

object ThemeSeedColors {

    private var provider: ThemeSeedColorProvider? = null

    fun install(provider: ThemeSeedColorProvider) {
        this.provider = provider
    }

    fun primaryColor(): Int =
        requireNotNull(provider) {
            "ThemeSeedColorProvider 未注入：宿主必须在 Application.onCreate 中调用 " +
                "ThemeSeedColors.install(...)，否则自定义配色的回退主色无从确定"
        }.primaryColor()
}
