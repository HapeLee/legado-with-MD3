package io.legado.app.ui.main

/**
 * 主界面返回栈内 picker 的回传结果类型，配合 `androidx.navigation3.runtime.result` 使用。
 *
 * nav3 1.1.7 没有 result API，1.2.0 起官方提供 `ResultEffect` + `sendResult`；本文件只定义
 * 载荷类型与 key 生成，通道本身用官方的 `ResultEventBus`（由
 * `rememberResultEventBusNavEntryDecorator` 经 `LocalResultEventBus` 提供给每个 entry）。
 *
 * **必须用自定义 resultKey 而不是官方的 reified 类型 key**：官方 `sendResult(result)` 默认用
 * `T::class.toString()` 作 key，而 `Channel.receiveAsFlow()` 是多消费者竞争消费（fan-out）——
 * 阅读页与书籍详情可能同时在栈内并都监听目录结果，用类型 key 会让结果被不确定的一方截走。
 * 每个发起方 entry 各自持有一个随机 key，结果才定向。
 *
 * 官方明确：结果不跨配置变更与进程死亡保存。
 */

/** 目录页（章节/书签）picker 的结果。 */
sealed interface TocPickResult {

    /**
     * 选中章节或书签。
     *
     * @param chapterIndex 章节序号
     * @param chapterPos 章节内位置（书签跳转时非 0）
     * @param chapterChanged 是否确需切章（对齐原 `TocActivityResult` 的第三个字段）
     */
    data class Picked(
        val chapterIndex: Int,
        val chapterPos: Int,
        val chapterChanged: Boolean,
    ) : TocPickResult

    /**
     * 以 picker 身份被打开、但用户没选就返回。
     *
     * 必须显式回传而不是「什么都不发」：书籍详情靠这个信号判断「用户放弃了本次选章」，
     * 不在书架的书会据此回滚（`BookInfoViewModel.onTocResult(null)`）。
     */
    data object Cancelled : TocPickResult
}

/** TXT 目录规则页以 picker 身份被打开后选中的规则。 */
data class TxtTocRulePickResult(val rule: String)

/**
 * 书籍详情页的「书被删了」信号。
 *
 * 原来由 `BookInfoActivity.onFinish` 的 `setResult(RESULT_OK)` 承担：阅读页据此
 * `ReadBookIntent.BookInfoResult(bookDeleted = true)` 退出，漫画页据此向上转发
 * `READER_RESULT_DELETED`。nav3 下没有 Activity result，改走同栈结果通道。
 *
 * 只有删除成功才发；书籍加载失败之类的 `Finish(resultCode = null)` 不发（那是详情页自己出栈）。
 */
data object BookInfoDeleted

/**
 * 生成一次性结果 key。命名成方法而不是让调用方各写一遍，是为了保证「一次性」这个约定只有一处实现。
 */
fun newNavResultKey(): String = java.util.UUID.randomUUID().toString()
