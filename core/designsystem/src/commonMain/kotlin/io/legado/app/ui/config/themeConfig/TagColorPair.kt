package io.legado.app.ui.config.themeConfig

/**
 * 标签配色对（文字色 + 背景色，均为 ARGB）。
 *
 * M5-19d：从 `:app` 的 `ui/config/themeConfig/ThemeConfig.kt` **搬进**
 * `:core:designsystem/commonMain`（**包名不变** ⇒ 5 处消费者
 * `LabelColorManageSheet` / `BookItem` / `BookshelfConfigSheet` / `BookshelfUiState` /
 * `BookshelfViewModel` 的 import **零改动**）。
 *
 * 触发条件同 `MainDestination`(M5-19c-pre)：**共享层出现消费者** —— `themeConfig` 的
 * `LabelColorManageSheet` 要迁进共享层，而它的参数面就是 `List<TagColorPair>`。
 *
 * ⚠️ 只搬了**这个 data class**：同文件的 `ThemeConfig` 对象（已被 `@Deprecated`，且带
 * `androidx.appcompat.app.AppCompatDelegate`）**留在 `:app`** —— 它进不了 `commonMain`，
 * 也不是本片的目标。搬走的这个类**逐字未改**（纯数据，无平台依赖）。
 */
data class TagColorPair(
    val textColor: Int = 0,
    val bgColor: Int = 0,
)
