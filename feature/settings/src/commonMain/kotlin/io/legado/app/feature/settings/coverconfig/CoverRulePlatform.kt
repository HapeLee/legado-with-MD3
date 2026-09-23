package io.legado.app.feature.settings.coverconfig

/**
 * 封面规则（自定义封面搜索规则）的读写，收成的窄契约。
 *
 * M5-12a：迁移前 `CoverConfigViewModel` 直接调两个 `:app` 符号：
 * `model.BookCover`（178 行，依赖 `android.graphics.Bitmap` / `Drawable` / `appCtx`）与
 * `help.DefaultData`（`appCtx.assets` 读 `defaultData/coverRule.json`）。两者都进不了共享层。
 *
 * ⚠️ **刻意只传原始值**（[CoverRuleSpec]），不搬 `BookCover.CoverRule` 那个嵌套类型 ——
 * 它在 `BookCover` 里，而 `BookCover` 深绑 Android 的图形类型。本契约只需三个字段，
 * 用一个共享 data class 表达即可，宿主那侧负责与 `CoverRule` 互转。
 *
 * 与 M5-7 的 `DownloadCachePlatform` 同一形态：**feature 模块自己定义窄契约，Android 实现
 * 留在 `:app`，Koin 注入**；方法是 `suspend`，调度器下沉到实现侧（迁移前 VM 里是显式
 * `Dispatchers.IO`，现在由 `AndroidCoverRulePlatform` 内部 `withContext(Dispatchers.IO)` 承担）。
 */
data class CoverRuleSpec(
    val enabled: Boolean,
    val searchUrl: String,
    val expression: String,
)

interface CoverRulePlatform {
    /** 当前生效的规则（未配置时即为默认规则）。 */
    suspend fun current(): CoverRuleSpec

    /** 内置默认规则。 */
    suspend fun default(): CoverRuleSpec

    suspend fun save(spec: CoverRuleSpec)

    suspend fun delete()
}
