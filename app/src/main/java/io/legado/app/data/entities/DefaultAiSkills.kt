package io.legado.app.data.entities

/**
 * 首次启动写入的内置 AI Skill。
 *
 * id 固定，重复写入由 AiProfileDao.insertSkill 的 REPLACE 策略保证幂等；
 * 是否导入由 PreferKey.aiSkillsSeeded 控制，用户删除后不会再被塞回来。
 */
object DefaultAiSkills {

    private val CHAPTER_BRIEF = """
        # 章节速览

        ## 何时使用
        用户说「讲讲这章」「这章讲了什么」「概括一下」时。

        ## 步骤
        1. 调用 get_chapter_content 取当前章正文。若返回为空或被截断，改用 get_chapter_window 取当前章及前后各一章。
        2. 若用户问的是「刚才那几章」，先用 list_book_chapters 定位章节，再逐章取正文。
        3. 严格按下面三段输出，除小标题外不加任何多余内容：

        剧情：一句话说清这一章发生了什么，不超过 40 字。
        关键动作：列出 2 到 3 个推动剧情的人物行为，用「谁做了什么」的句式。
        伏笔：这一章留下的悬念或异常，没有就写「无」。

        ## 约束
        - 总字数不超过 200 字。
        - 不复述原文句子，不评价文笔，不剧透后面的章节。
        - 用户说「存下来」时，调用 save_ai_artifact，type 用 note，标题用「第N章 速览」。
    """.trimIndent()

    private val RECAP = """
        # 前情提要

        ## 何时使用
        用户追更前想看「前面讲到哪了」「回顾一下」「我上次看到哪」。

        ## 步骤
        1. 调用 get_reading_stats 确认当前阅读进度，拿到当前章节序号和标题。
        2. 调用 get_book_outline 取已有大纲。若大纲为空，用 list_book_chapters 取目录，再用 get_chapter_window 回溯最近 5 章。
        3. 按下面三块输出：

        主线进展：3 条以内，按时间顺序，每条一句。
        未解决的冲突：当前还悬着的问题，最多 3 条。
        主要人物现状：谁在哪、在做什么，只写最近几章有出场的。

        ## 约束
        - 只回溯最近 5 到 8 章，不要通读全书，也不要凭印象编。
        - 已有 save_book_outline_node 记录时优先复用，不重复读正文。
        - 结尾不要问「需要我继续吗」，直接给出内容。
    """.trimIndent()

    private val CHARACTER_MAP = """
        # 人物图谱

        ## 何时使用
        用户问「这书有哪些人」「谁和谁什么关系」「某某是谁」「这个人之前出现过吗」。

        ## 步骤
        1. 先调用 search_book_characters 查已有画像，命中就直接采用。
        2. 不足时用 search_chapter_content 搜人名，或 get_chapter_window 精读相关段落补充。
        3. 输出四列清单，按出场重要性排序：

        人物 | 身份或立场 | 与主角的关系 | 最近一次出场

        4. 用户要求「存下来」「记到档案里」时，逐个调用 save_book_character_profile；两人之间关系明确时，再调用 save_book_character_relation。

        ## 约束
        - 只写文本中有依据的内容。推测必须显式标注「推测」。
        - 同一人物有多个称呼时合并成一条，在身份栏注明别名。
        - 没有出过场、只被提到名字的，单独列在末尾并标注「仅提及」。
    """.trimIndent()

    private val FORESHADOW = """
        # 伏笔追踪

        ## 何时使用
        用户问「有什么伏笔」「这个坑填了吗」「前面那个东西后来怎么样了」。

        ## 步骤
        1. 调用 get_book_outline 看已有结构，再用 search_book_knowledge 查「伏笔」「悬念」类条目。
        2. 用 search_chapter_content 按关键词检索，常见关键词：信物、约定、身世、遗言、旧伤、失踪、信件。
        3. 按两段输出：

        已埋未收：伏笔内容 + 首次出现章节 + 目前状态。
        已回收：伏笔内容 + 埋设章节 + 回收章节。

        4. 用户要求记录时，调用 save_book_knowledge_entry，type 用 foreshadow，把章节号写进内容里。

        ## 约束
        - 严格区分「文本已明确回收」和「尚未回收」。无法判断就写「待确认」。
        - 不把普通情节当伏笔。只记录被反复提及、或明显带暗示的细节。
        - 章节号拿不准时不要编，写「约第N章前后」或省略。
    """.trimIndent()

    private val QUOTES = """
        # 金句摘录

        ## 何时使用
        用户说「摘几句好的」「这句话记一下」「有没有写得好的句子」。

        ## 步骤
        1. 取正文：当前章用 get_chapter_content；用户指定范围时用 get_chapter_window。
        2. 挑 3 到 5 句。标准是三条同时满足：脱离上下文仍能独立成立、包含明确观点或情绪、不是纯叙事交代。
        3. 输出格式：

        > 原句

        —— 第N章，后接一句 15 字以内的点评，可省略。

        4. 用户说「存下来」时，调用 save_ai_artifact，type 用 quote，把原文和章节号一起存进去。

        ## 约束
        - 必须逐字引用，不改写、不合并、不补标点。
        - 单句不超过 80 字，超了就截到最近的完整分句。
        - 一段里找不到符合标准的句子就直说，不要为了凑数硬挑。
    """.trimIndent()

    private val BOOK_SOURCE = """
        # 书源编写

        ## 何时使用
        用户说「给这个网站写个书源」「这个源搜索不准，帮我改」「能不能做个源」时。

        ## 流程
        1. 先 list_book_sources 查同站是否已有源。有就走 update_book_source，没有才 create_book_source。
        2. 用 fetch_web_page 抓真实页面源码，确认选择器真能命中。搜索页要抓带关键词的完整 URL。
        3. 按下面的模板组装 JSON，字段名不能改。
        4. create_book_source 传完整 JSON。
        5. 用 search_with_book_source 拿真实关键词验证。返回空列表就是 searchUrl 或 ruleSearch 错了。
        6. 还失败就 debug_book_source，target 用 search，看日志判断是请求失败还是解析为空。

        ## 最小模板
        {
          "bookSourceUrl": "https://example.com",
          "bookSourceName": "示例站",
          "bookSourceGroup": "自建",
          "bookSourceType": 0,
          "enabled": true,
          "searchUrl": "/search?q={{key}}&page={{page}}",
          "ruleSearch": {
            "bookList": ".result-item",
            "name": "h3@text",
            "author": ".author@text",
            "bookUrl": "h3 a@href",
            "intro": ".intro@text"
          },
          "ruleBookInfo": {
            "name": "h1@text",
            "author": ".author@text",
            "intro": ".intro@text",
            "coverUrl": ".cover img@src"
          },
          "ruleToc": {
            "chapterList": ".chapter-list li",
            "chapterName": "a@text",
            "chapterUrl": "a@href"
          },
          "ruleContent": {
            "content": "#content@html"
          }
        }

        ## 规则语法
        - {{key}} 是关键词，{{page}} 是页码，必须原样留在 URL 里。
        - 选择器在 @ 前，取值方式在 @ 后：@text 取文本，@href 取链接，@src 取图片地址，@html 取内层 HTML。
        - bookList、chapterList 先定位到每一条，其余字段都相对这一条解析。
        - 需要计算或拼接时用 @js: 前缀写 JS，返回值必须是字符串。

        ## 约束
        - bookSourceUrl 只填站点根地址，不带路径和参数。
        - 不要凭印象编选择器。没抓到页面就先抓，抓不到就直说站点可能反爬。
        - 改源只提交要变的字段，别整份覆盖，update_book_source 会深合并。
        - 每次改完都要跑一次 search_with_book_source，没验证过不算完成。
    """.trimIndent()

    /** 内置技能版本。新增技能时 +1，App 启动按版本补齐。 */
    const val VERSION = 2

    /** VERSION 1 发布的技能 id：迁移时视为已处理，避免把用户删掉的又塞回去。 */
    val legacyIds: Set<String> = setOf(
        "builtin.chapter_brief",
        "builtin.recap",
        "builtin.character_map",
        "builtin.foreshadow",
        "builtin.quotes",
    )

    fun all(): List<AiSkillProfile> = listOf(
        AiSkillProfile(
            id = "builtin.chapter_brief",
            name = "章节速览",
            description = "按固定结构概括当前章节：剧情、人物动作、伏笔，200 字以内",
            content = CHAPTER_BRIEF,
            sortNumber = 0,
        ),
        AiSkillProfile(
            id = "builtin.recap",
            name = "前情提要",
            description = "追更前回顾最近章节的主线、未解冲突和人物现状",
            content = RECAP,
            sortNumber = 1,
        ),
        AiSkillProfile(
            id = "builtin.character_map",
            name = "人物图谱",
            description = "梳理书中人物身份、立场与关系，可写入人物档案",
            content = CHARACTER_MAP,
            sortNumber = 2,
        ),
        AiSkillProfile(
            id = "builtin.foreshadow",
            name = "伏笔追踪",
            description = "找出已埋未收和已回收的伏笔，按章节定位",
            content = FORESHADOW,
            sortNumber = 3,
        ),
        AiSkillProfile(
            id = "builtin.quotes",
            name = "金句摘录",
            description = "逐字摘录可独立成立的句子，可选存入摘录库",
            content = QUOTES,
            sortNumber = 4,
        ),
        AiSkillProfile(
            id = "builtin.book_source",
            name = "书源编写",
            description = "照着目标站点写或改书源，写完用真实关键词验证规则是否生效",
            content = BOOK_SOURCE,
            sortNumber = 5,
        ),
    )
}
