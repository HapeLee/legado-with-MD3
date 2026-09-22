package io.legado.app.ui.config.ai

// M5-5b 的**临时副本**（`:app` 侧）。
//
// `formatTokenLimit` 原先定义在 `AiModelEditScreen.kt`（`internal`），但**同包的
// `AiProviderEditScreen.kt` 也在用它**。M5-5b 把 `AiModelEdit*` 迁进 `:feature:settings`
// 之后，`internal` 不再跨模块可见 ⇒ 这里保留一份，让尚未迁移的 `AiProviderEditScreen`
// 继续编译。
//
// ⚠️ **等 `AiProviderEdit` 也迁走（ai 域收官）后删除本文件**——那时 `:app` 里不会再有人用。
// 届时共享层的同名函数是它的唯一实现（两边逻辑逐字相同）。
internal fun formatTokenLimit(value: Int): String {
    return when {
        value <= 0 -> "0"
        value >= 1_000_000 && value % 1_000_000 == 0 -> "${value / 1_000_000}M"
        value >= 1_000 && value % 1_000 == 0 -> "${value / 1_000}K"
        else -> value.toString()
    }
}
