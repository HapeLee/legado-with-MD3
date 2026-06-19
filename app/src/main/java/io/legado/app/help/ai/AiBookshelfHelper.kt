package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.constant.BookType
import io.legado.app.model.analyzeRule.QueryTTF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

object AiBookshelfHelper {

    data class ShelfStats(
        val bookCount: Int,
        val sourceStats: Map<String, Int>,
        val kindStats: Map<String, Int>,
        val sampleBooks: String
    )

    suspend fun getStats(): ShelfStats = withContext(Dispatchers.IO) {
        val allBooks: List<Book> = runCatching {
            appDb.bookDao.flowAll().firstOrNull() ?: emptyList()
        }.getOrElse { emptyList() }

        val books = allBooks.filter {
            it.type and BookType.local == 0 || it.type and BookType.text > 0
        }.take(500)

        val sources = books
            .groupBy { it.originName?.ifBlank { null } ?: "未分类" }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(15)
            .associate { it.key to it.value }

        val kinds = books
            .mapNotNull { it.kind?.trim()?.ifBlank { null } }
            .groupBy { it }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }

        val sample = buildString {
            appendLine("书架中的书籍（取最多20本预览）：")
            books.take(20).forEachIndexed { i, b ->
                append("${i + 1}. 《${b.name}》 - ${b.author}")
                b.kind?.takeIf { it.isNotBlank() }?.let { append(" [$it]") }
                appendLine()
                if (!b.originName.isNullOrEmpty()) {
                    appendLine("   书源: ${b.originName}")
                }
                b.intro?.take(120)?.let { appendLine("   简介: ${it.replace("\n", " ")}") }
            }
        }

        ShelfStats(
            bookCount = books.size,
            sourceStats = sources,
            kindStats = kinds,
            sampleBooks = sample
        )
    }

    suspend fun analyzeShelf(
        config: AiSimpleClientConfig,
        model: String = config.chatModel,
        customPrompt: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val stats = getStats()
            if (stats.bookCount == 0) {
                return@runCatching "你的书架目前是空的。先去添加一些书吧 📚"
            }

            val sourceSummary = stats.sourceStats.entries.joinToString("\n") { "- ${it.key}: ${it.value} 本" }
            val kindSummary = stats.kindStats.entries.joinToString("\n") { "- ${it.key}: ${it.value} 本" }

            val systemPrompt = customPrompt
                ?: """你是一位资深的阅读顾问和书籍推荐者。基于用户提供的书架信息：
1. 给出用户阅读画像（偏好类型、风格倾向）
2. 推荐相似主题的书籍（3-5本，附理由）
3. 给出阅读习惯的改进建议
4. 如果书源不均衡，提示可能想补充的书源
输出使用 Markdown，中文回答，结构清晰有数据支撑。"""

            val userPrompt = buildString {
                appendLine("我的书架统计：")
                appendLine("- 总数: ${stats.bookCount} 本")
                appendLine()
                appendLine("书源分布:")
                appendLine(sourceSummary)
                if (stats.kindStats.isNotEmpty()) {
                    appendLine()
                    appendLine("分类分布:")
                    appendLine(kindSummary)
                }
                appendLine()
                appendLine(stats.sampleBooks)
            }
            AiClient.chat(
                messages = listOf(AiSimpleMessage("user", userPrompt)),
                config = config,
                model = model,
                systemPrompt = systemPrompt
            ).getOrThrow()
        }
    }
}

