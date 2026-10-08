package io.legado.app.model.localBook

import io.legado.app.data.entities.TxtTocRule
import java.util.regex.PatternSyntaxException

/**
 * TXT 目录识别默认要求的最小“章节样”匹配数。
 *
 * 单处命中不足以认定规则：像 `正文`、`尾声`、`楔子`、`番外` 这类词在简介、书名页里偶发出现
 * 一次，就会让这条规则被判为命中。误判比“识别不出规则”更糟——识别不出时会退回无规则按字数
 * 分章（正文照样能读），误判则可能把整本书压成一章，读起来就是空白/一直加载。
 */
internal const val MIN_TXT_TOC_CHAPTER_MATCHES = 2

/**
 * 从启用的 TXT 目录规则里挑一条最贴合样本文本的规则。
 *
 * @param rules 启用的规则，按 `serialNumber` 升序（与 [io.legado.app.data.dao.TxtTocRuleDao.enabled] 一致）
 * @param sample 目录样本文本（通常是文件开头一块）
 * @param minChapterMatches 至少要有多少处“章节样”匹配才算命中
 * @param onInvalidPattern 规则正则语法错误时回调，供调用方落日志
 */
internal fun selectTxtTocRule(
    rules: List<TxtTocRule>,
    sample: String,
    minChapterMatches: Int = MIN_TXT_TOC_CHAPTER_MATCHES,
    onInvalidPattern: (TxtTocRule, PatternSyntaxException) -> Unit = { _, _ -> },
): TxtTocRule? {
    var maxNum = minChapterMatches
    var bestRule: TxtTocRule? = null
    // 序号大的规则先参与，命中数相等时由后到的低序号规则覆盖（低序号优先）
    for (tocRule in rules.reversed()) {
        val pattern = try {
            Regex(tocRule.chapterRule, RegexOption.MULTILINE)
        } catch (e: PatternSyntaxException) {
            onInvalidPattern(tocRule, e)
            continue
        }
        val num = countChapterLikeMatches(pattern, sample)
        if (num >= maxNum) {
            maxNum = num
            bestRule = tocRule
        }
    }
    return bestRule
}

/**
 * 统计“章节样”的匹配数。
 *
 * 只有与上一处计数匹配间隔超过 [minGap] 字符的匹配才计数：章节之间总是隔着正文，
 * 而“匹配每一行/每一段”的错误规则会被间隔条件压到很低，不会被当成章节规则。
 * 空匹配（例如 `(?=…)`、空正则）不构成章节，直接跳过。
 */
internal fun countChapterLikeMatches(pattern: Regex, sample: String, minGap: Int = 1000): Int {
    var start = 0
    var num = 0
    for (m in pattern.findAll(sample)) {
        if (m.value.isEmpty()) continue
        if (start == 0 || m.range.first - start > minGap) {
            num++
            start = m.range.last + 1
        }
    }
    return num
}
