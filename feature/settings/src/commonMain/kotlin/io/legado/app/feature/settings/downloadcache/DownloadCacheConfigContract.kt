package io.legado.app.feature.settings.downloadcache

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.DownloadCacheSettings

// M5-7：从 `:app` 的 `io.legado.app.ui.config.downloadCacheConfig` 迁来。
//
// 一处**结构变更**（不是纯改名）：UI state 新增 [DownloadCacheConfigUiState.maxDownloadConcurrency]。
//
// 迁移前 Screen **自己**读 `CacheBook.maxDownloadConcurrency`（3 处：滑块的当前值夹取、
// `defaultValue`、`valueRange` 上界），而 `CacheBook` 在 `:app` ⇒ 共享层的 composable 拿不到。
// 它不是「状态」而是环境常数，但让 VM 从 `DownloadCachePlatform` 填进 state 是**唯一不引入
// 反向依赖**的走法：另一条路是宿主把值当参数传给 Screen，那会让 `MainNavGraph` 也依赖平台契约，
// 把 `:app` 的接线面从「取 VM + 收 state」扩大成「还要装配平台值」。
//
// ⚠️ 该字段**刻意不给默认值**。给个 `= 8` 就等于在共享层写死一份上限，与引擎的
// `CacheBook.maxDownloadConcurrency` 形成第二真源 —— 日后引擎放宽到 16 时，共享层会静默漂移，
// 表现为「滑块拉不到底」这种不会有人报的 bug。要求调用方显式提供，则编译器会拦住。
// 见 `DownloadCachePlatform` KDoc 第 1 条。

@Stable
data class DownloadCacheConfigUiState(
    val settings: DownloadCacheSettings = DownloadCacheSettings(),
    val coverCacheSizeMb: Double = 0.0,
    val mangaCacheSizeMb: Double = 0.0,
    val dialog: DownloadCacheConfigDialog? = null,
    /** 见文件头注释：由 VM 从平台契约填入，**不给默认值**。 */
    val maxDownloadConcurrency: Int,
)

enum class DownloadCacheConfigDialog {
    ClearCoverCache,
    ClearMangaCache,
    ClearBookCache,
    ShrinkDatabase,
}

sealed interface DownloadCacheConfigIntent {
    data class SetThreadCount(val value: Int) : DownloadCacheConfigIntent
    data class SetCacheBookThreadCount(val value: Int) : DownloadCacheConfigIntent
    data class SetPreDownloadNum(val value: Int) : DownloadCacheConfigIntent
    data class SetBitmapCacheSize(val value: Int) : DownloadCacheConfigIntent
    data class SetImageRetainNum(val value: Int) : DownloadCacheConfigIntent
    data class SetUserAgent(val value: String) : DownloadCacheConfigIntent
    data class SetCronetEnabled(val value: Boolean) : DownloadCacheConfigIntent
    data class ShowDialog(val dialog: DownloadCacheConfigDialog) : DownloadCacheConfigIntent
    data object DismissDialog : DownloadCacheConfigIntent
    data object ConfirmDialog : DownloadCacheConfigIntent
}

/**
 * 本页目前**没有**一次性事件 —— 与 `TranslationConfigEffect` 一样是个空 sealed interface。
 *
 * 全仓零引用（只在声明处出现），曾考虑顺手删掉，但**保留**：本模块每个页面的契约都带一个
 * Effect 类型（`lab` / `translation` / `ai` 各有），空的那个表达「这一页没有一次性事件」，
 * 删除会让模块的词汇表不一致。删除属于独立决策（先例 M5-6a 把它做成了单独的提交），
 * 不在迁移片里顺带做。
 */
sealed interface DownloadCacheConfigEffect
