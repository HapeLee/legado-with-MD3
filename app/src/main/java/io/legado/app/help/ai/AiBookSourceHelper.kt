package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书源助手：根据用户描述生成 / 校验书源
 */
object AiBookSourceHelper {

    /**
     * 基于给定的网站描述生成 Legado BookSource JSON
     */
    suspend fun generateFromWebsite(
        websiteUrl: String,
        siteName: String? = null,
        config: AiProviderConfig,
        model: String = config.chatModel,
        note: String? = null
    ): Result<BookSource> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = """你是一位 Legado 电子书阅读APP的资深书源工程师。请基于以下信息生成一个符合 Legado BookSource JSON 格式的书源配置。

目标信息:
- 网站URL: $websiteUrl${siteName?.let { "\n- 名称/主题: $it" } ?: ""}${note?.let { "\n- 备注: $it" } ?: ""}

【Legado 书源规范说明】：
- bookSourceUrl: 书源根域名（唯一标识，如 https://www.example.com）
- bookSourceName: 书源名称
- bookSourceType: 0(文本) / 1(音频) / 2(漫画/图片)
- bookSourceGroup: 书源分组（如"AI生成"、"小说"、"漫画"）
- bookUrlPattern: 书籍详情页URL正则，如^https://www\\.example\\.com/book/\\d+\\.html$
- searchUrl: 搜索接口，格式 "https://site.com/search?q={{key}},{{page}}|Json"
- exploreUrl: 发现页，多个分类可用 \n 分隔，如 "分类A::url\n分类B::url2"
- ruleSearch: 搜索结果解析规则，用JSON对象表示字段
- ruleBookInfo: 书籍信息解析规则
- ruleToc: 目录解析规则
- ruleContent: 正文解析规则
- header: 请求头（JSON，包含 User-Agent）
- enabled: true/false
- enabledExplore: true/false
- respondTime: 响应时间（ms，默认180000）

【规则写法说明】：
- CSS选择器: div.book-title@text（取文本）， img@src（取属性）
- XPath: //div[@class="book-title"]/text()
- 正则: @regex:<title>(.*?)</title>@@group:1
- 组合: 可用 ||| 分隔多个选择器（第一个匹配的生效）

【输出要求】
只输出一个合法的 JSON 对象，不要任何 Markdown 标记、说明文字或代码块。确保 JSON 语法正确、字段完整。"""

            val result = AiClient.chat(
                messages = listOf(AiMessage("user", prompt)),
                config = config,
                model = model
            )
            val jsonText = cleanJson(result.getOrThrow())
            GSON.fromJsonObject<BookSource>(jsonText).getOrElse {
                throw Exception("AI 返回的 JSON 解析失败，请手动检查")
            }
        }
    }

    /**
     * 使用 AI 分析校验一份书源配置，给出建议
     */
    suspend fun validate(
        sourceJson: String,
        config: AiProviderConfig,
        model: String = config.chatModel
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = """请作为一位资深的 Legado 书源审核工程师，仔细分析下面这份书源 JSON 配置。

书源 JSON:
```
${sourceJson.take(6000)}
```

请提供：
1. 整体评分（1-10分），并给出一句话评价
2. 格式与语法检查：JSON 是否合法、必填字段是否存在、类型是否正确
3. 规则质量评估：各 rule* 字段的选择器/XPath/正则写法是否合理、是否脆弱（过于依赖特定class名）
4. 常见问题诊断：
   - 反爬虫与 cookie
   - URL 格式是否正确（{{key}},{{page}} 是否使用）
   - 编码问题建议
   - 动态加载（SPA）提示
5. 改进建议：列出 3-5 条具体可改进项
6. 给出修正后的推荐 JSON（如果有明显问题）

输出使用 Markdown 格式，中文回答。"""
            AiClient.chat(listOf(AiMessage("user", prompt)), config, model).getOrThrow()
        }
    }

    /**
     * 将 AI 生成的 JSON 导入到数据库
     */
    suspend fun importToDatabase(sourceJson: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonText = cleanJson(sourceJson)
            val source = GSON.fromJsonObject<BookSource>(jsonText).getOrElse {
                throw Exception("书源 JSON 解析失败")
            }
            appDb.bookSourceDao.insert(source)
            "书源「${source.bookSourceName}」已导入"
        }
    }

    private fun cleanJson(text: String): String {
        var json = text.trim()
        // 去除 ```json ... ``` 包裹
        val codeBlock = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(json)
        if (codeBlock != null) {
            json = codeBlock.groupValues[1].trim()
        }
        // 找到第一个 { 和最后一个 }
        val start = json.indexOf('{').takeIf { it >= 0 } ?: 0
        val end = json.lastIndexOf('}').takeIf { it > start } ?: (json.length - 1)
        return json.substring(start, end + 1)
    }
}
