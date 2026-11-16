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
        用户说「给这个网站写个书源」「这个源搜索不准 / 目录或正文抓不到，帮我改」「能不能做个源」时。
        目标不是写一份看起来对的 JSON，而是交付一个**用真实网址逐段验证过**的可用书源。

        ## 核心原则（务必遵守）
        - 选择器只能来自真实抓到的页面，绝不凭印象或模板编造。没抓到页面就先抓；抓不到就明确告诉用户站点可能反爬 / 需登录，不要硬写。
        - 写完必须逐段验证：搜索 → 详情 → 目录 → 正文，四段都通过才算完成。只验证搜索不算完成。
        - 不要臆造章节内容或书籍数据；验证结果是什么就如实汇报。

        ## 决策表：先看清楚要做什么
        | 用户诉求 | 动作 |
        |---|---|
        | 同站已有源、只是某段失效 | get_book_source 读出现有源 → 定位坏掉的 rule → update_book_source 只改那几个字段 |
        | 全新站点 | fetch_web_page 抓页面 → 推导规则 → create_book_source → 逐段验证 |
        | 源需要登录 / Cookie | 在源里配 cookie / 登录 UI，fetch_web_page 传 sourceUrl 复用该源的 header 与 Cookie |

        ## 标准流程
        1. 查存量：list_book_sources 按站点名 / 域名查。命中就走「改源」路径，没有才新建。
        2. 抓真实页面（关键，不能跳）：
           - 先抓搜索页。用站点真实的搜索 URL，把关键词替换进去，例如 `https://站点/search?q=斗破苍穹`。
           - 拿到一条结果的详情链接后再抓详情页；从详情页拿目录链接抓目录页；从目录拿章节链接抓正文页。
           - 页面是 JS 动态渲染、抓到的 HTML 里没有目标数据时，给 fetch_web_page 传 useWebView=true 再抓一次。
           - 站点需要登录 / 防盗链 / 反爬时，fetch_web_page 传 sourceUrl 复用该源的 header、Cookie 和限速。
           - 内容超长被截断时把 maxChars 调大（最大 60000）。抓到 body 后，按下面的语法从真实结构推导选择器。
        3. 按模板组装完整 JSON，字段名一个都不能改。
        4. 写入：新建用 create_book_source，改源用 update_book_source（只提交要变的字段，会深合并，bookSourceUrl 不可改）。
        5. 逐段验证（每段都必须跑）：
           - search_with_book_source 传真实关键词 → 返回书籍列表即搜索通过；空列表先查 searchUrl / ruleSearch。
           - debug_book_source 的 target 依次用 info（传详情 URL）、toc（传目录 URL）、content（传正文 URL）逐段验证；每段看日志区分「请求失败」还是「解析为空」。
           - 请求失败：查 URL、header、Cookie、限速；解析为空：查选择器、规则标志。
        6. 全部通过后再向用户汇报，附上验证用的关键词和结果数量。任一段失败就回到第 2 步修正。

        ## 规则语法（阅读 / Legado 规则）
        - URL 模板：{{key}} 是搜索关键词，{{page}} 是页码，必须原样保留；把站点真实参数填进 URL。
        - 取值写在 @ 后：@text 文本、@href 链接、@src 图片地址、@html 内层 HTML。
        - 规则标志（写在 {{...}} 或字段值里，区分选择器类型，缺标志会被当 JS 执行）：
          - @@ 默认规则，可省略
          - @XPath: XPath 规则；直接以 `//` 开头可省略标志
          - @Json: JSONPath 规则；直接以美元符加点开头可省略标志
          - : 开头（如 `:正则`）是正则规则，只用于书籍列表和目录列表
        - bookList / chapterList 先用 CSS、XPath 或 JSONPath 定位到「每一条」，其余字段都相对这一条解析。
        - 需要计算、拼接、拼接绝对地址时用 @js: 前缀写 JS，返回值必须是字符串；相对链接要拼成绝对 URL。
        - 站点返回 JSON 时整段用 @Json: 或 JSONPath（美元符加点开头），不要用 CSS。
        - 分页：searchUrl / 目录 URL 里用 {{page}}，并在规则里给出下一页判断（需要时用 @js:）。

        ## 最小模板（CSS 版；据真实页面改成 XPath / JSONPath）
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
        需要「发现 / 排行」时再补 ruleExplore（结构类似 ruleSearch）。

        ## 反模式（这些做法会导致源不可用）
        - 不抓页面就照模板编选择器 —— 十有八九命中不到，用户拿到就是坏源。
        - 只验证搜索就交付 —— 目录、正文常常另有反爬或不同结构。
        - 相对链接不拼绝对地址 —— 详情页 / 章节链接会 404。
        - JSON 接口用 CSS 选择器，或把 @js: / @Json: 标志写错 —— 解析必然为空。
        - 改源时整份覆盖 —— 会冲掉用户其它自定义字段，务必用 update_book_source 深合并。

        ## 约束
        - bookSourceUrl 只填站点根地址，不带路径和参数。
        - create_book_source / update_book_source 需要用户确认，属正常流程。
        - debug_book_source 用的是全局调试会话，可能挤掉用户正在进行的 UI 调试；验证完尽快结束，不要长时间占用。
        - 每次改完都要跑验证，没验证过不算完成。
    """.trimIndent()

    /** 内置技能版本。新增技能或升级内置技能正文时 +1，App 启动按版本补齐。 */
    const val VERSION = 3

    /**
     * 需要强制刷新正文的内置技能 id（版本升级时）。
     * 只对「当前确实存在」的技能刷新，绝不重新插入用户已删除的，避免复活。
     * 今后升级某个内置技能正文时，把它加进这里并升 VERSION。
     */
    val contentUpgradeIds: Set<String> = setOf("builtin.book_source")

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
