package io.legado.app.data.repository

import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.repository.HighlightRulePresets.PRESET_BG_COLOR

/**
 * 阅读页「高亮规则 → 预设规则」入口的可选清单。
 *
 * 只做一件事：给一份开箱可用的正则。样式统一为同一个半透明底纹
 * （[PRESET_BG_COLOR]）——预设是「加进来再自己调」的起点，不在这里预设
 * 一堆颜色/下划线组合，避免和用户已有的样式审美打架。
 *
 * `enabled` 沿用示例里「常用 4 条开、其余关」的分布：一次加十几条全开会糊满正文，
 * 关着加进来、用户按需打开更可控。
 *
 * id 与 [HighlightRuleRepository.createDefaultRules] 保持一致：已经存在同 id 规则时，
 * 预设入口把它标为「已有」并默认不勾选，只补缺失的规则，不覆盖用户改过的样式。
 */
object HighlightRulePresets {

    /** 统一的预设底色：20% 透明度的琥珀，日间/夜间压在正文上都不糊字。 */
    private const val PRESET_BG_COLOR = 0x33FFC107.toInt()

    val rules: List<HighlightRule> = listOf(
        HighlightRule(
            id = "dialog_default",
            name = "对话高亮",
            pattern = "“[^“”]{1,500}”|\"[^\"\\n]{1,500}\"|「[^「」]{1,500}」|『[^『』]{1,500}』",
            sampleText = "她轻声说：“今晚就出发。”",
            enabled = true,
            bgColor = PRESET_BG_COLOR,
        ),
        HighlightRule(
            id = "book_title_default",
            name = "书名号高亮",
            pattern = "《[^》\\n]{1,120}》",
            sampleText = "最近在重读《百年孤独》，节奏依然很稳。",
            enabled = true,
            bgColor = PRESET_BG_COLOR,
        ),
        HighlightRule(
            id = "bracket_note_default",
            name = "括号标注高亮",
            pattern = "（[^（）\\n]{1,120}）|\\([^()\\n]{1,120}\\)|【[^】\\n]{1,120}】|\\[[^\\]\\n]{1,120}]",
            sampleText = "他停了一下（像是忽然想起了什么）。",
            enabled = true,
            bgColor = PRESET_BG_COLOR,
        ),
        HighlightRule(
            id = "title_emphasis_default",
            name = "标题强调",
            pattern = "(?m)^[ \\t　]{0,2}(?:第[0-9零〇一二两三四五六七八九十百千万IVXLCDMivxlcdm]{1,12}[章节卷回部篇集幕]|序章|楔子|引子|终章|尾声|后记|番外)[^\\n]{0,200}\$",
            sampleText = "第一章 雨夜来客",
            targetScope = HighlightRule.TARGET_TITLE,
            enabled = true,
            bgColor = PRESET_BG_COLOR,
        ),
        HighlightRule(
            id = "thought_default",
            name = "心理活动",
            pattern = "（[^）\\n]{0,40}(?:心想|暗道|心道|想到|寻思着|琢磨|嘀咕)[^）\\n]{0,40}）",
            sampleText = "她心中一紧（暗道不对，这里一定有问题）。",
            enabled = false,
            bgColor = PRESET_BG_COLOR,
        ),
        HighlightRule(
            id = "narrator_default",
            name = "旁白说明",
            pattern = "(?m)^[ \\t　]{0,2}(?:(?:未完待续|待续|下文再表)[。…！!]{0,2}|(?:按|注)[:：][^\\n]{0,40})[ \\t　]*\$|（(?:注|旁白|作者有话说)[:：][^）\\n]{0,40}）",
            sampleText = "（注：此处时间线与前文同步）",
            enabled = false,
            bgColor = PRESET_BG_COLOR,
        ),
        HighlightRule(
            id = "emphasis_default",
            name = "重点强调",
            // 示例里那段裸 `[^\n*_]{1,40}` 会匹配任意一行正文，这里换成成对标记 + 连续感叹/问号。
            pattern = "\\*\\*[^\\n*_]{1,40}\\*\\*|__[^\\n*_]{1,40}__|(?:!{3,}|！{2,}|！？|？！|\\?!|!\\?)[^\\n]{0,20}",
            sampleText = "**这是重点内容**，需要特别注意。",
            enabled = false,
            bgColor = PRESET_BG_COLOR,
        ),
        HighlightRule(
            id = "poetry_default",
            name = "诗词引用",
            pattern = "(?m)^[ \\t　]*(?!(?:第[零〇一二两三四五六七八九十百千万0-9]{1,12}[章节卷回部篇集幕]|序章|楔子|引子|终章|尾声|后记|番外))(?:[\\p{IsHan}]{5}|[\\p{IsHan}]{7})[，。！？；]?[ \\t　]*\$",
            sampleText = "床前明月光，\n疑是地上霜。",
            enabled = false,
            bgColor = PRESET_BG_COLOR,
        ),
        HighlightRule(
            id = "ellipsis_default",
            name = "省略停顿",
            pattern = "…{2,}|\\.{3,}|—{2,}|-{3,}",
            sampleText = "他沉默了很久……最后还是点了头。",
            enabled = false,
            bgColor = PRESET_BG_COLOR,
        ),
        HighlightRule(
            id = "number_default",
            name = "数字金额",
            pattern = "[¥￥][ \\t]?\\d+(?:\\.\\d+)?(?:元|块|万|千|百|亿)?|\\d+(?:\\.\\d+)?(?:元|块|万|千|百|亿|%|％)|[零〇一二两三四五六七八九十百千万亿]+(?:元|块|万|千|百|亿)",
            sampleText = "原价100元，现在只要50元。",
            enabled = false,
            bgColor = PRESET_BG_COLOR,
        ),
        HighlightRule(
            id = "english_default",
            name = "英文单词",
            pattern = "\\b[A-Za-z]{2,}[A-Za-z0-9'-]*\\b",
            sampleText = "Hello World，你好世界。",
            enabled = false,
            bgColor = PRESET_BG_COLOR,
        ),
        HighlightRule(
            id = "date_time_default",
            name = "时间日期",
            pattern = "(?:\\d{2,4}年(?:0?[1-9]|1[0-2])月(?:(?:0?[1-9]|[12]\\d|3[01])[日号]?)?|[零〇一二三四五六七八九十]{2,4}年(?:十一|十二|正|[一二三四五六七八九十冬腊])月(?:(?:初[一二三四五六七八九十]|十[一二三四五六七八九]?|二十[一二三四五六七八九]?|三十|廿[一二三四五六七八九]?|卅|[一二三四五六七八九])[日号]?)?)(?![0-9零〇一二三四五六七八九十初廿卅日号])|(?<!\\d)(?:[01]?\\d|2[0-3]):[0-5]\\d(?!\\d)|(?<!\\d)(?:[01]?\\d|2[0-3])点(?:[0-5]?\\d分?)?(?!\\d)",
            sampleText = "2024年8月12日，上午10:30出发。",
            enabled = false,
            bgColor = PRESET_BG_COLOR,
        ),
    )
}
