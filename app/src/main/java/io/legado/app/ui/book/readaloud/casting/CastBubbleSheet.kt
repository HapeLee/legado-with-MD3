package io.legado.app.ui.book.readaloud.casting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.data.entities.HighlightRule
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ReadSheetConfigUiState
import io.legado.app.ui.book.read.sheet.HighlightRuleEditSheet
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

/**
 * 角色气泡设置：**就是高亮规则那一整套编辑器**，只是没有「规则信息」那一段
 * （名字 / 正则 / 作用域 / 停用）——哪一句归这个角色由分配表给定，不需要正则去匹配。
 *
 * 所以样式设置、命中排版、应用排版、字体替换、预览卡全都在，与规则那边同一份实现、
 * 同一份换算（`LegacyReaderStyleRangeMapper.styleOf`）。存进库的是这条规则本身的 JSON
 * （`CastCharacter.bubbleRuleJson`），渲染时按角色命中句整段覆盖到高亮规则之上。
 *
 * 清空走卡片菜单里的「不设气泡」（`CastRoleRowActions`），这里不放开关：
 * 弹层里已经没有「启用」这一栏了，再加一个伪开关只会和它长在同一处。
 */
@Composable
fun CastBubbleSheet(
    show: Boolean,
    characterName: String,
    /** [io.legado.app.data.entities.CastCharacter.bubbleRuleJson] 原样传进来；空串 = 没设过。 */
    initialJson: String,
    configNames: List<String>,
    onDismissRequest: () -> Unit,
    /** 回传要写进库的那段 JSON；空串 = 这一套什么都没设，等于不设气泡。 */
    onSave: (String) -> Unit,
) {
    var cached by remember(show, initialJson) {
        mutableStateOf(
            initialJson.takeIf { it.isNotBlank() }
                ?.let { GSON.fromJsonObject<HighlightRule>(it).getOrNull() }
        )
    }
    HighlightRuleEditSheet(
        show = show,
        rule = cached?.copy(enabled = true) ?: HighlightRule(enabled = true),
        allConfigNames = configNames,
        // 预览要沿用的是正文那一套排版。配音页不在阅读页里，拿不到弹层快照，就现读同一份全局配置
        // （字段口径与 `ReadBookViewModel.buildSheetConfig` 一致）。
        config = remember(show) { bodyTypography() },
        onDismissRequest = onDismissRequest,
        showRuleInfo = false,
        title = stringResource(R.string.cast_bubble_menu) + " · " + characterName,
        onSave = { rule -> onSave(if (rule.setsNothing()) "" else GSON.toJson(rule)) },
    )
}

/** 正文当前的字号/字距/行距/缩进/颜色/页边距：只取预览要用得到的那几栏。 */
private fun bodyTypography() = ReadSheetConfigUiState(
    textSize = ReadBookConfig.textSize,
    letterSpacing = ReadBookConfig.letterSpacing,
    lineSpacing = ReadBookConfig.lineSpacingExtra,
    paragraphIndentCount = ReadBookConfig.paragraphIndent.length,
    textItalic = ReadBookConfig.textItalic,
    textBold = ReadBookConfig.textBold,
    textColor = ReadBookConfig.durConfig.curTextColor(),
    textFullJustify = ReadBookConfig.textFullJustify,
    paddingTop = ReadBookConfig.paddingTop,
    paddingBottom = ReadBookConfig.paddingBottom,
    paddingLeft = ReadBookConfig.paddingLeft,
    paddingRight = ReadBookConfig.paddingRight,
)

/** 一个可见效果都没设：这时保存等于把气泡撤掉，别在库里留一条空规则。 */
private fun HighlightRule.setsNothing(): Boolean = bgImage.isNullOrBlank() &&
    bgColor == null && textColor == null && underlineMode == 0 && fontPath.isNullOrBlank() &&
    fontWeight == 400 && !isItalic && fontSizeOffset == 0 &&
    letterSpacingBefore == 0f && letterSpacingAfter == 0f &&
    lineSpacingTop == 0f && lineSpacingBottom == 0f
