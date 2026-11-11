package io.legado.app.ui.book.toc

import androidx.annotation.StringRes
import io.legado.app.R

/**
 * 目录页顶栏溢出菜单的动作注册表：稳定 ID 与 `@StringRes` 文案，供 `menuGroups` 生成
 * `AppAction` 时复用。ID 顺序即旧 `dropDownMenuContent` 在章节列表页的条目顺序。
 */
internal enum class TocMenuAction(
    val id: String,
    @param:StringRes val labelRes: Int,
) {
    UseReplace("use_replace", R.string.use_replace_rule),
    ShowWordCount("show_word_count", R.string.show_word_count),
    ReverseToc("reverse_toc", R.string.reverse_toc),
    UpdateToc("update_toc", R.string.update_toc),
    ReplaceRule("replace_rule", R.string.replace_rule_title),
    AddReplaceRule("add_replace_rule", R.string.add_replace_rule),
    LocalTocRule("local_toc_rule", R.string.local_book_toc_rule),
    SplitLongChapters("split_long_chapters", R.string.split_long_chapters),
    ExportBookmarksJson("export_bookmarks_json", R.string.export_bookmarks_json),
    ExportBookmarksMarkdown("export_bookmarks_markdown", R.string.export_bookmarks_markdown),
}

/**
 * 菜单 ID 对应的行为。动态参数（书名 / 书源 / 导出文件名）刻意不放在这里：
 * 它们由调用方在执行时按当前 state 计算，这样 [tocMenuBehaviorFor] 保持纯函数，
 * 可以在 JVM 单测里逐项比对旧菜单的点击行为。
 */
internal sealed interface TocMenuBehavior {
    data class Dispatch(val intent: TocIntent) : TocMenuBehavior
    data object OpenReplaceRuleList : TocMenuBehavior
    data object OpenNewReplaceRule : TocMenuBehavior
    data object EditLocalTocRule : TocMenuBehavior
    data class ExportBookmarks(val isMarkdown: Boolean) : TocMenuBehavior
}

/** 未知 ID 不产生动作，避免入口回调拼错时误触发其它 Intent。 */
internal fun tocMenuBehaviorFor(id: String): TocMenuBehavior? = when (id) {
    TocMenuAction.UseReplace.id -> TocMenuBehavior.Dispatch(TocIntent.ToggleUseReplace)
    TocMenuAction.ShowWordCount.id -> TocMenuBehavior.Dispatch(TocIntent.ToggleShowWordCount)
    TocMenuAction.ReverseToc.id -> TocMenuBehavior.Dispatch(TocIntent.ReverseToc)
    TocMenuAction.UpdateToc.id -> TocMenuBehavior.Dispatch(TocIntent.UpdateToc)
    TocMenuAction.SplitLongChapters.id ->
        TocMenuBehavior.Dispatch(TocIntent.ToggleSplitLongChapter)
    TocMenuAction.ReplaceRule.id -> TocMenuBehavior.OpenReplaceRuleList
    TocMenuAction.AddReplaceRule.id -> TocMenuBehavior.OpenNewReplaceRule
    TocMenuAction.LocalTocRule.id -> TocMenuBehavior.EditLocalTocRule
    TocMenuAction.ExportBookmarksJson.id ->
        TocMenuBehavior.ExportBookmarks(isMarkdown = false)
    TocMenuAction.ExportBookmarksMarkdown.id ->
        TocMenuBehavior.ExportBookmarks(isMarkdown = true)
    else -> null
}
