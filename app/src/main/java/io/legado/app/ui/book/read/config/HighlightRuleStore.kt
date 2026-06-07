package io.legado.app.ui.book.read.config

import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.HighlightRule
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import splitties.init.appCtx
import java.io.File

object HighlightRuleStore {

    const val backupFileName = "highlightRule.json"

    data class BackupData(
        val rules: List<HighlightRule> = emptyList(),
        val dialogEnabled: Boolean = true,
        val bookTitleEnabled: Boolean = true,
        val bracketNoteEnabled: Boolean = true,
    )

    private val dao get() = appDb.highlightRuleDao

    fun load(): List<HighlightRule> {
        migrateFromPrefsIfNeeded()
        return dao.getAll()
    }

    fun loadEnabled(): List<HighlightRule> {
        migrateFromPrefsIfNeeded()
        return dao.getEnabled()
    }

    fun save(rules: List<HighlightRule>) {
        val sanitized = rules.mapIndexed { index, rule ->
            sanitizeRule(rule).copy(position = index)
        }
        dao.replaceAll(sanitized)
        cleanupUnusedBgImages(sanitized)
    }

    fun update(rule: HighlightRule) {
        dao.update(sanitizeRule(rule))
    }

    fun delete(rule: HighlightRule) {
        dao.delete(rule)
    }

    fun reset(): List<HighlightRule> {
        val defaults = createDefaultRules()
        dao.replaceAll(defaults)
        return defaults
    }

    fun createBackupData(): BackupData {
        return BackupData(
            rules = load(),
            dialogEnabled = appCtx.getPrefBoolean(PreferKey.highlightRuleDialog, true),
            bookTitleEnabled = appCtx.getPrefBoolean(PreferKey.highlightRuleBookTitle, true),
            bracketNoteEnabled = appCtx.getPrefBoolean(PreferKey.highlightRuleBracketNote, true),
        )
    }

    fun restoreBackupData(backupData: BackupData, backupRootPath: String? = null) {
        val rules = backupData.rules.map { rule ->
            val safeRule = sanitizeRule(rule)
            val restoredBgImage = restoreRuleBgImage(backupRootPath, safeRule.bgImage)
            safeRule.copy(bgImage = restoredBgImage)
        }
        save(rules)
        appCtx.putPrefBoolean(PreferKey.highlightRuleDialog, backupData.dialogEnabled)
        appCtx.putPrefBoolean(PreferKey.highlightRuleBookTitle, backupData.bookTitleEnabled)
        appCtx.putPrefBoolean(PreferKey.highlightRuleBracketNote, backupData.bracketNoteEnabled)
    }

    /**
     * 从旧版 SharedPreferences 迁移数据（一次性）
     */
    private fun migrateFromPrefsIfNeeded() {
        if (dao.count() > 0) return
        // 尝试从 SharedPreferences 读取旧数据
        val stored = appCtx.getPrefString(PreferKey.highlightRuleItems)
        if (!stored.isNullOrBlank()) {
            val oldRules = GSON.fromJsonArray<LegacyHighlightRule>(stored).getOrNull()
            if (!oldRules.isNullOrEmpty()) {
                val migrated = oldRules.mapIndexed { index, old ->
                    sanitizeRule(
                        HighlightRule(
                            id = old.id,
                            name = old.name,
                            pattern = old.pattern,
                            sampleText = old.sampleText,
                            targetScope = old.targetScope,
                            enabled = old.enabled,
                            position = index,
                            textColor = old.textColor,
                            underlineMode = old.underlineMode,
                            underlineColor = old.underlineColor,
                            underlineWidth = old.underlineWidth,
                            underlineOffset = old.underlineOffset,
                            underlineSvgPath = old.underlineSvgPath,
                            bgImage = old.bgImage,
                            bgImageFit = old.bgImageFit,
                            bgImageScale = old.bgImageScale,
                        )
                    ).copy(position = index)
                }
                dao.insertAll(migrated)
                // 清除旧 SharedPreferences 数据
                appCtx.putPrefString(PreferKey.highlightRuleItems, null)
                return
            }
        }
        // 尝试从旧版 RegexColorRule 迁移
        migrateFromRegexColorRules()
    }

    private fun migrateFromRegexColorRules() {
        val oldRules = ReadBookConfig.regexColorRules
        if (oldRules.isEmpty()) return
        val migrated = oldRules.mapIndexed { index, old ->
            HighlightRule(
                name = old.name,
                pattern = old.pattern,
                position = index,
                textColor = old.color,
            )
        }
        dao.insertAll(migrated)
        oldRules.clear()
        ReadBookConfig.save()
    }

    fun sanitizeRule(rule: HighlightRule): HighlightRule {
        val name = runCatching { rule.name }.getOrNull().orEmpty()
        val pattern = runCatching { rule.pattern }.getOrNull().orEmpty()
        val sampleText = runCatching { rule.sampleText }.getOrNull().orEmpty()
        val id = runCatching { rule.id }.getOrNull().orEmpty().ifBlank {
            "${System.currentTimeMillis()}_${listOf(name, pattern).joinToString("|").hashCode().toUInt().toString(16)}"
        }
        return HighlightRule(
            id = id,
            name = name,
            pattern = pattern,
            sampleText = sampleText,
            targetScope = normalizeTargetScope(runCatching { rule.targetScope }.getOrDefault(HighlightRule.TARGET_ALL)),
            enabled = runCatching { rule.enabled }.getOrDefault(true),
            position = runCatching { rule.position }.getOrDefault(0),
            textColor = runCatching { rule.textColor }.getOrNull(),
            bgColor = runCatching { rule.bgColor }.getOrNull(),
            underlineMode = runCatching { rule.underlineMode }.getOrDefault(0).coerceIn(0, 5),
            underlineColor = runCatching { rule.underlineColor }.getOrNull(),
            underlineWidth = runCatching { rule.underlineWidth }.getOrDefault(1f).coerceIn(0.1f, 10f),
            underlineOffset = runCatching { rule.underlineOffset }.getOrDefault(2f).coerceIn(0f, 20f),
            underlineSvgPath = runCatching { rule.underlineSvgPath }.getOrNull(),
            bgImage = runCatching { rule.bgImage }.getOrNull()?.takeIf { it.isNotBlank() },
            bgImageFit = runCatching { rule.bgImageFit }.getOrDefault(0).coerceIn(0, 2),
            bgImageScale = runCatching { rule.bgImageScale }.getOrDefault(1f).coerceIn(0.1f, 5f),
        )
    }

    private fun normalizeTargetScope(value: Int, fallback: Int = HighlightRule.TARGET_ALL): Int {
        return when (value) {
            HighlightRule.TARGET_ALL,
            HighlightRule.TARGET_TITLE,
            HighlightRule.TARGET_BODY -> value
            else -> fallback
        }
    }

    fun createDefaultRules(): List<HighlightRule> {
        val ctx = appCtx
        return listOf(
            HighlightRule(
                id = "dialog_default",
                name = "对话高亮",
                pattern = "“[^\\u201d\\n]{1,120}\\u201d|\"[^\"\\n]{1,120}\"|「[^」\\n]{1,120}」|『[^』\\n]{1,120}』",
                sampleText = "她轻声说：“今晚就出发。”",
                position = 0,
                enabled = ctx.getPrefBoolean(PreferKey.highlightRuleDialog, true),
                textColor = 0xFFFF8C00.toInt()
            ),
            HighlightRule(
                id = "book_title_default",
                name = "书名号高亮",
                pattern = "《[^》\\n]{1,80}》",
                sampleText = "最近在重读《百年孤独》，节奏依然很稳。",
                position = 1,
                enabled = ctx.getPrefBoolean(PreferKey.highlightRuleBookTitle, true),
                underlineMode = 3,
                underlineWidth = 0.5f,
                underlineColor = 0xFF63C37D.toInt()
            ),
            HighlightRule(
                id = "bracket_note_default",
                name = "括号标注高亮",
                pattern = "（[^（）\\n]{1,80}）|\\([^()\\n]{1,80}\\)|【[^】\\n]{1,80}】|\\[[^\\]\\n]{1,80}]",
                sampleText = "他停了一下（像是忽然想起了什么）。",
                position = 2,
                enabled = ctx.getPrefBoolean(PreferKey.highlightRuleBracketNote, true),
                textColor = 0xFF8F959E.toInt(),
                underlineMode = 2,
                underlineWidth = 0.5f,
                underlineColor = 0xFF5A8DEE.toInt()
            ),
            HighlightRule(
                id = "title_emphasis_default",
                name = "标题强调",
                pattern = "(?m)^\\s{0,2}(?:第[0-9零〇一二两三四五六七八九十百千万IVXLCDMivxlcdm]{1,12}[章节卷回部篇集幕]|序章|楔子|引子|终章|尾声|后记|番外)[^\\n]{0,40}$",
                sampleText = "第一章 雨夜来客",
                targetScope = HighlightRule.TARGET_TITLE,
                position = 3,
                enabled = true,
                textColor = 0xFF333333.toInt(),
                underlineMode = 4,
                underlineColor = 0xFF7C5634.toInt()
            ),
            HighlightRule(
                id = "thought_default",
                name = "心理活动",
                pattern = "（[^）\\n]{0,40}(?:心想|暗道|心道|想到|寻思着|琢磨|嘀咕)[^）\\n]{0,40}）",
                sampleText = "她心中一紧（暗道不对，这里一定有问题）。",
                position = 4,
                enabled = false,
                textColor = 0xFF9370DB.toInt(),
                underlineMode = 1,
                underlineWidth = 0.5f,
                underlineColor = 0xFF9370DB.toInt()
            ),
            HighlightRule(
                id = "narrator_default",
                name = "旁白说明",
                pattern = "(?:未完待续|待续|下文再表|按：?|注：?)[^\\n]{0,40}|（(?:注|旁白|作者有话说)[:：][^）\\n]{0,40}）",
                sampleText = "（注：此处时间线与前文同步）",
                position = 5,
                enabled = false,
                textColor = 0xFF708090.toInt()
            ),
            HighlightRule(
                id = "emphasis_default",
                name = "重点强调",
                pattern = "(?:\\*\\*|__)[^\\n*_]{1,40}(?:\\*\\*|__)|(?:!!!|！？|\\?!)[^\\n]{0,20}",
                sampleText = "**这是重点内容**，需要特别注意。",
                position = 6,
                enabled = false,
                textColor = 0xFFDC143C.toInt(),
                underlineMode = 1,
                underlineColor = 0xFFDC143C.toInt()
            ),
            HighlightRule(
                id = "poetry_default",
                name = "诗词引用",
                pattern = "(?m)^[\\p{IsHan}，。！？；：、]{5,24}$",
                sampleText = "床前明月光，\n疑是地上霜。",
                position = 7,
                enabled = false,
                textColor = 0xFF2F4F4F.toInt(),
                underlineMode = 3,
                underlineWidth = 0.5f,
                underlineColor = 0xFF2F4F4F.toInt()
            ),
            HighlightRule(
                id = "ellipsis_default",
                name = "省略停顿",
                pattern = "…{2,}|\\.{3,}|—{2,}|-{3,}",
                sampleText = "他沉默了很久……最后还是点了头。",
                position = 8,
                enabled = false,
                textColor = 0xFF8B8B8B.toInt()
            ),
            HighlightRule(
                id = "number_default",
                name = "数字金额",
                pattern = "(?:¥|￥)?\\d+(?:\\.\\d+)?(?:元|块|万|千|百|亿|%|％)|[零〇一二两三四五六七八九十百千万亿]+(?:元|块|万|千|百|亿)",
                sampleText = "原价100元，现在只要50元。",
                position = 9,
                enabled = false,
                textColor = 0xFF4169E1.toInt()
            ),
            HighlightRule(
                id = "english_default",
                name = "英文单词",
                pattern = "\\b[A-Za-z]{2,}[A-Za-z0-9'-]*\\b",
                sampleText = "Hello World，你好世界。",
                position = 10,
                enabled = false,
                textColor = 0xFF4169E1.toInt()
            ),
            HighlightRule(
                id = "date_time_default",
                name = "时间日期",
                pattern = "(?:\\d{2,4}|[零〇一二两三四五六七八九十]{2,4})年(?:\\d{1,2}|[正一二三四五六七八九十冬腊])月(?:\\d{1,2}|[一二三四五六七八九十廿三])?[日号]?|\\b\\d{1,2}:\\d{2}\\b|(?:[0-1]?\\d|2[0-3])点(?:[0-5]?\\d分?)?",
                sampleText = "2024年8月12日，上午10:30出发。",
                position = 11,
                enabled = false,
                textColor = 0xFF20B2AA.toInt()
            )
        )
    }

    private fun cleanupUnusedBgImages(rules: List<HighlightRule>) {
        val usedPaths = rules.mapNotNull { it.bgImage }
            .filter { it.isNotBlank() && !it.startsWith("assets://") }
            .toSet()
        val dir = File(appCtx.filesDir, "bg_images")
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.absolutePath !in usedPaths) {
                runCatching { file.delete() }
            }
        }
    }

    private fun restoreRuleBgImage(backupRootPath: String?, bgImage: String?): String? {
        val path = bgImage ?: return null
        if (path.isBlank() || path.startsWith("assets://")) return path
        val rootPath = backupRootPath ?: return path
        val backupFile = File(rootPath, "highlightRuleBg${File.separator}${File(path).name}")
            .takeIf { it.exists() && it.isFile }
            ?: return path
        val dir = File(appCtx.filesDir, "bg_images")
        if (!dir.exists()) dir.mkdirs()
        val targetFile = File(dir, backupFile.name)
        if (!targetFile.exists() || targetFile.length() != backupFile.length()) {
            backupFile.copyTo(targetFile, overwrite = true)
        }
        return targetFile.absolutePath
    }

    /**
     * 旧版 SharedPreferences 数据结构（用于迁移）
     */
    private data class LegacyHighlightRule(
        val id: String = "",
        val name: String = "",
        val pattern: String = "",
        val sampleText: String = "",
        val targetScope: Int = 0,
        val enabled: Boolean = true,
        val textColor: Int? = null,
        val underlineMode: Int = 0,
        val underlineColor: Int? = null,
        val underlineWidth: Float = 1f,
        val underlineOffset: Float = 2f,
        val underlineSvgPath: String? = null,
        val bgImage: String? = null,
        val bgImageFit: Int = 0,
        val bgImageScale: Float = 1f,
    )
}
