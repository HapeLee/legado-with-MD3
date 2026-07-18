package io.legado.app.help.config

import androidx.annotation.StringRes
import io.legado.app.R

/**
 * 内置自定义页眉/页脚模板占位符。
 *
 * - [token] 是用户写在模板里的完整形式，例如 `{BookName}`，必须带花括号。
 * - [key] 是 [token] 去括号后的内部字符串，例如 `BookName`，用于校验。
 * - [labelRes] 是 chip / 提示中展示的本地化名，**不应**带花括号。
 *
 * 校验逻辑：先把模板中所有 `{Xxx}` 内部的 key 提取出来，
 * 再与 [validKeys] 集合对比 —— 任何未在枚举中的 key 都会被拒绝。
 */
enum class CustomTipPlaceholder(
    val token: String,
    val key: String,
    @StringRes val labelRes: Int,
) {
    BOOK_NAME("{BookName}", "BookName", R.string.placeholder_book_name),
    CHAPTER_TITLE("{ChapterTitle}", "ChapterTitle", R.string.placeholder_chapter_title),
    TIME("{Time}", "Time", R.string.placeholder_time),
    BATTERY_PERCENT("{BatteryPercent}", "BatteryPercent", R.string.placeholder_battery_percent),
    CHAPTER_INDEX("{ChapterIndex}", "ChapterIndex", R.string.placeholder_chapter_index),
    CHAPTER_SIZE("{ChapterSize}", "ChapterSize", R.string.placeholder_chapter_size),
    PAGE_INDEX("{PageIndex}", "PageIndex", R.string.placeholder_page_index),
    PAGE_SIZE("{PageSize}", "PageSize", R.string.placeholder_page_size),
    READ_PROGRESS("{ReadProgress}", "ReadProgress", R.string.placeholder_read_progress);

    companion object {
        /** 占位符 key（即花括号内的部分），用于校验。 */
        private val validKeys: Set<String> = values().mapTo(hashSetOf()) { it.key }

        /**
         * 从模板中提取所有 `{Xxx}` 内部的 key。
         * 解析规则：匹配 `{` 后面到下一个 `}` 之间的非空字符串。
         * 未闭合的 `{` 会被忽略（不会作为 key 提取）。
         */
        fun extractPlaceholders(template: String): List<String> {
            if (template.isEmpty()) return emptyList()
            val result = mutableListOf<String>()
            var i = 0
            while (i < template.length) {
                val open = template.indexOf('{', i)
                if (open < 0) break
                val close = template.indexOf('}', open + 1)
                if (close < 0) break
                val inside = template.substring(open + 1, close)
                if (inside.isNotEmpty()) {
                    result.add(inside)
                }
                i = close + 1
            }
            return result
        }

        /**
         * 校验模板中所有 `{Xxx}` 是否都是预定义占位符。
         *
         * @return `true` 表示模板中无占位符或所有占位符都是预定义的；`false` 表示至少有一个未知占位符。
         */
        fun isValid(template: String): Boolean {
            return extractPlaceholders(template).all { it in validKeys }
        }
    }
}
