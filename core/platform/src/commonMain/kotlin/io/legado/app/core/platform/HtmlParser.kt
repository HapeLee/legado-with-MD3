package io.legado.app.core.platform

/**
 * HTML 解析契约（D2 双轨）。
 *
 * jsoup 1.16.2 被 AGENTS.md 锁定版本，且是纯 JVM 库、没有 KMP 产物，
 * 因此 [commonMain] 不能 import `org.jsoup.*`（`checkSharedPurity` 已将其列为禁止 import）。
 * 双轨做法：共享层只面向本文件的三个窄接口，各 target 的 actual 委托本机 jsoup 1.16.2，
 * 从而保证现有解析行为（含 jsoup 的实体解码、标签补齐、属性序列化）零变化。
 *
 * **接缝边界（务必先读）**：本契约只覆盖「解析 / 选择 / 序列化」这一侧。
 * `help/JsExtensions` 的 `get/head/post` 会把 `org.jsoup.Connection.Response` 原样返回给
 * **书源 JS**，JS 桥靠反射调 `body()/code()/header()`，所以 jsoup 类型在 JS 边界
 * 必须保留原样——那一侧是**平台岛，不迁移、不替换**（R1 的对策，与参照样本被迫写
 * 9 个 `org.jsoup.*` 兼容文件同源）。
 *
 * **接口面按真实消费方收敛**：当前唯一消费方是 `:app` 的 `HtmlFormatter`
 * （`formatDisplayText` 用到 5 个 jsoup 调用）。`AnalyzeByJSoup` / `JsoupExtensions.textArray`
 * 需要的宽遍历面（`NodeTraversor` / `NodeVisitor` / `org.jsoup.internal.StringUtil` /
 * `CDataNode` / `tag().preserveWhitespace()`）**不在本契约内**，等有真实消费方时再扩，
 * 不为对称提前扩接口（AGENTS.md「无调用方抽象」）。
 */
interface HtmlParser {
    /**
     * 解析 HTML 片段，等价 `Jsoup.parseBodyFragment(html)`。
     *
     * 注意语义：会把入参当作 `<body>` 内容补齐成完整文档，空/空白输入同样产生带空 body 的文档，
     * 不会返回 null。
     */
    fun parseBodyFragment(html: String): HtmlDocument
}

/** 一份已解析的 HTML 文档。 */
interface HtmlDocument {
    /** 文档 `<body>` 元素；`parseBodyFragment` 的结果一定非空。 */
    fun body(): HtmlElement

    /**
     * 是否输出格式化缩进，等价 `document.outputSettings().prettyPrint(enabled)`。
     *
     * 这是**行为相关**开关而非样式偏好：`prettyPrint(true)` 会给 [HtmlElement.html] 的序列化
     * 结果插入换行与缩进，直接改变 `HtmlFormatter` 清洗后的段落切分。消费方必须显式设置为
     * `false`，不要依赖默认值。
     */
    fun setPrettyPrint(enabled: Boolean)
}

/** 一个 HTML 元素。 */
interface HtmlElement {
    /** 按 CSS 选择器选后代，等价 `element.select(cssQuery)`；无匹配时返回空列表。 */
    fun select(cssQuery: String): List<HtmlElement>

    /** 从父节点摘除自身，等价 `element.remove()`；已是根节点时无操作。 */
    fun remove()

    /** 序列化**内部** HTML（不含自身标签），等价 `element.html()`。 */
    fun html(): String
}
