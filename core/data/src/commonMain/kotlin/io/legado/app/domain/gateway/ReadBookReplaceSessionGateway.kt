package io.legado.app.domain.gateway

import io.legado.app.data.entities.ReplaceRule

/**
 * 当前阅读会话中与「替换规则」有关的只读快照。
 *
 * 字段全部是**已解析**的结果（例如 [useReplaceRule] 已经过默认值回退），
 * 调用方不需要再持有 `Book` 实体去二次判断。
 */
data class ReadBookReplaceSnapshot(
    val bookUrl: String,
    val chapterIndex: Int,
    val useReplaceRule: Boolean,
    val reSegment: Boolean,
    /** 当前章节内容实际生效的替换规则；无章节内容时为空。 */
    val effectiveReplaceRules: List<ReplaceRule>,
)

/**
 * 阅读会话替换相关能力的平台契约。
 *
 * 抽出来的原因：`ReplaceRuleViewModel` 原先直接依赖 `model.ReadBook`——那是 `:app` 侧的
 * 阅读器运行时单例（持有 `Book` 可变引用、朗读服务、`Coroutine.async` 等），既是
 * `:feature:replacerules` 无法独立成模块的最后几处硬阻碍之一，也让替换规则的开关逻辑
 * 无法在单测里替换。
 *
 * 这里只保留「读一次快照」+「四条语义化写命令」，与 `ReadBook` 自身的会话所有权约定
 * （外部只能通过语义化命令改写会话字段）保持一致：调用方拿不到 `Book` 实体，
 * 也就无法绕过命令直接改写会话。
 *
 * 契约不暴露 Android 类型；`ReplaceRule` 是 `:core:data` 的共享实体，故本文件放在
 * `:core:data` 的 commonMain。Android 实现留在 `:app`
 * （[AndroidReadBookReplaceSessionGateway]），由 Koin 注入。
 *
 * **语义必须与迁移前逐条一致**，任何一条改动都会改变用户可见行为：
 * - [setReSegment] 与 [loadContent] 是两次独立调用：迁移前即使当前无书，
 *   `loadContent` 仍会执行；
 * - [snapshot] 为 null 的判定等价于迁移前的 `ReadBook.book == null`。
 */
interface ReadBookReplaceSessionGateway {

    /** 当前阅读书籍的替换快照；无书时返回 null。 */
    fun snapshot(): ReadBookReplaceSnapshot?

    /** 改写当前书籍的替换开关（只改内存中的 `Book`，落库需再调 [saveRead]）。无书时无操作。 */
    fun setUseReplaceRule(enabled: Boolean)

    /** 改写当前书籍的重分段开关。无书时无操作。 */
    fun setReSegment(enabled: Boolean)

    /** 把当前进度与书籍配置落库。 */
    fun saveRead()

    /** 重新加载当前章节内容；[resetPageOffset] 为 true 时回到章首。 */
    fun loadContent(resetPageOffset: Boolean)
}
