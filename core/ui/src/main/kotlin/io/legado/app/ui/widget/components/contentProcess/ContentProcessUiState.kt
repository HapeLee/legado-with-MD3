package io.legado.app.ui.widget.components.contentProcess

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 正文处理（替换/净化规则产生的处理项）的共享 UI 状态契约。
 *
 * 原定义在 `:app` 的 `io.legado.app.ui.book.read.ReadBookContract.kt`（1643 行、依赖 app 侧
 * 17 个包，整体下沉不现实），但这两个类本身是**纯 `@Stable` data class**，只依赖
 * `ImmutableList`，因此单独抽出来，供两处消费者共用：
 * - 阅读器：`ReadContentProcessDelegate` / `ContentProcessesSheet` / `TextProcessingSheet`；
 * - 替换规则：`:feature:replacerules` 的 `ReplaceRuleContract` / `ReplaceRuleViewModel`
 *   （本类即为该 Feature 提升为 Gradle 模块清理的前置之一）。
 *
 * 为什么放 `:core:ui` 而不是 `:core:designsystem`：后者是 KMP 模块，commonMain 目前零依赖
 * （`ListUiState` / `ImportState` 只用 `List` 与 `Set`），为 `@Stable` 引入 Androidx
 * Compose BOM 会给 desktop target 带来不必要的兼容面；而这两个类的消费者（阅读器与
 * `:feature:replacerules`）都是 Android。等真有非 Android 消费者时再上提到共享层。
 *
 * 只有这几个字段是**渲染需要**的形状，不含 `BookContentProcess` 实体字段；
 * 实体 → UI 的映射留在各自消费侧（`toContentProcessItemUi()`）。
 *
 * 注意：边界测试 `ReadBookDomainSplitBoundaryTest` 用「`ReadBookViewModel.kt` 源码里
 * 是否出现类型名」来守「正文处理逻辑不回流 VM」，是纯文本匹配，与包名无关；
 * 因此本次改包名不影响该断言。
 */
@Stable
data class ContentProcessConfigUiState(
    val isLoading: Boolean = false,
    val items: ImmutableList<ContentProcessItemUi> = persistentListOf(),
    val deleteItem: ContentProcessItemUi? = null,
    val errorMessage: String? = null,
)

@Stable
data class ContentProcessItemUi(
    val id: String,
    val kind: String,
    val actionType: String,
    val enabled: Boolean,
    val chapterIndex: Int,
    val selectedText: String,
    val replacementText: String,
    val createdAt: Long,
)
