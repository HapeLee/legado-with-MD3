package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.help.book.characterBookKey
import io.legado.app.help.character.BookCharacterIdentityMigrator
import io.legado.app.model.ReadBook
import io.legado.app.ui.main.ai.AiChatMessage

object AiImagePromptRewriter {

    suspend fun rewrite(prompt: String, paragraphText: String): String {
        val book = ReadBook.book
        val chapter = ReadBook.curTextChapter?.chapter
        val characters = book?.let {
            val key = BookCharacterIdentityMigrator.migrate(it).ifBlank { it.characterBookKey() }
            appDb.bookCharacterDao.characters(key).take(12)
        }.orEmpty()
        val context = buildString {
            appendLine("当前书籍：${book?.name.orEmpty()}")
            appendLine("作者：${book?.author.orEmpty()}")
            appendLine("当前章节：${chapter?.title.orEmpty()}")
            appendLine("当前选中文字：")
            appendLine(paragraphText.ifBlank { prompt }.take(1200))
            if (characters.isNotEmpty()) {
                appendLine()
                appendLine("可参考的角色卡片：")
                characters.forEach { character ->
                    appendLine("- ${character.name.ifBlank { character.displayName() }}")
                    character.identity.takeIf { it.isNotBlank() }?.let { appendLine("  身份：$it") }
                    character.appearance.takeIf { it.isNotBlank() }?.let { appendLine("  形象：$it") }
                    character.personality.takeIf { it.isNotBlank() }?.let { appendLine("  性格：$it") }
                }
            }
            appendLine()
            appendLine("用户当前提示词：")
            appendLine(prompt)
        }
        val messages = listOf(
            AiChatMessage(
                role = AiChatMessage.Role.USER,
                content = """
                    你是小说插画提示词优化助手。
                    请根据下面的书籍上下文、选中文字和角色卡片，把用户当前提示词改写成更适合图像生成模型的提示词。
                    要求：
                    1. 只输出最终提示词，不要解释。
                    2. 不要把“章节名、章节序号、出处说明”当作画面内容写入提示词。
                    3. 保留人物、场景、氛围、服饰、构图、画风等对成图有帮助的信息。
                    4. 不确定的信息不要硬编。

                    $context
                """.trimIndent()
            )
        )
        return AiChatService.chatStream(
            messages = messages,
            onPartial = {},
            includeStructuredBlocks = false
        ).cleanPromptResult().ifBlank { prompt }
    }

    private fun String.cleanPromptResult(): String {
        return trim()
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("“", "”")
            .trim()
    }
}
