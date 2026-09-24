package io.legado.app.utils

/**
 * M5-19c-pre：从 `:app` 的 `utils/CollectionExtensions.kt` 搬进
 * `:core:designsystem/commonMain`（**包名不变** ⇒ 8 处 `:app` 消费方 import 零改动）。
 *
 * 触发条件是共享层出现消费者：`themeConfig` 的 `MainNavigationSettingsSheet` 用它做导航项拖拽
 * 重排。函数**逐字保留** —— 纯泛型、无平台依赖。
 *
 * 为什么落在 designsystem 而不是 `:core:model`：8 处消费方全在 UI 侧
 * （`BookshelfManageScreen` / `BookshelfManageScreenViewModel` / `BookshelfViewModel` /
 * `GroupManageSheet` / `SetDetailPage` / `SetListPage` / `SourceBrowseDetailPage` +
 * `MainNavigationSettingsSheet`）⇒ 放 UI 模块更贴合实际使用面。
 */
fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return
    val element = removeAt(fromIndex)
    add(toIndex, element)
}
