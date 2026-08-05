package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R4·R4.1a —— 绘制路径只读 `ChapterProvider.renderStyle` 快照，不直读 `ReadBookConfig`。
 *
 * 直读的代价不只是分层：`ReadBookConfig.underline` 这类属性会走
 * `config → durConfig → getConfig(styleSelect)`，而 `getConfig` 是 `@Synchronized`
 * ——放在 `draw()` 里就是**每行每列每帧**抢一次全局配置的监视器锁，还可能在一帧之内
 * 读到跨越两份配置的取值组合。
 *
 * 两条会悄悄失效的边界：
 *
 * 1. 有人在渲染实体里写回 `ReadBookConfig.xxx`——锁和撕裂一起长回来，而且看不出问题，
 *    因为功能是对的、只有性能和一致性退化。
 * 2. 给 [ChapterProvider.RenderStyle] 加了字段却忘了在 `upRenderStyle()` 里赋值——
 *    该项永远保持默认值，表现为「这个设置改了不生效」。
 */
class RenderStyleSnapshotBoundaryTest {

    @Test
    fun `渲染实体不直读 ReadBookConfig 或 ReadConfig`() {
        // ReadConfig 门面同罪：它的每个属性都要全量构造一份 Settings 快照
        // （几十个 preferences 查找），放在 draw() 里比 getConfig 的监视器锁还贵。
        val violations = RENDER_ENTITIES.filter { path ->
            Regex("""\b(?:ReadBookConfig|ReadConfig)\b""")
                .containsMatchIn(stripComments(mainSource(path)))
        }
        assertTrue(
            "以下渲染实体又直接读 ReadBookConfig / ReadConfig 了：\n" +
                violations.joinToString("\n") { "  - $it" } + "\n" +
                "绘制期要用的排版项请加进 ChapterProvider.RenderStyle，" +
                "由 upRenderStyle() 统一重建后读快照。",
            violations.isEmpty(),
        )
    }

    @Test
    fun `PageView 不直读 ReadBookConfig`() {
        assertTrue(
            "PageView 又直接读 ReadBookConfig 了。页眉/页脚与页面外框要用的配置请加进 " +
                "TipStyleProvider.TipStyle，由 upTipStyle() 统一重建后读快照——" +
                "三个 PageView 实例各读一遍全局，等于把同一份配置解析三次。",
            !Regex("""\bReadBookConfig\b""").containsMatchIn(stripComments(mainSource(PAGE_VIEW))),
        )
    }

    @Test
    fun `绘制画笔不得用 by lazy 定型`() {
        val lazyPaints = Regex("""\bval\s+(\w*[Pp]aint)\s*:\s*\w*Paint\s+by\s+lazy""")
            .findAll(stripComments(mainSource(CHAPTER_PROVIDER)))
            .map { it.groupValues[1] }
            .toList()

        assertTrue(
            "以下画笔用 by lazy 定型：${lazyPaints.joinToString()}。\n" +
                "`by lazy` 只在首次取用时算一次，此后改字体/字号/下划线粗细都不会反映到它上面，" +
                "而 upThemeColors() 又会就地改它的 color——于是它「颜色跟得上、其余永远是首帧那份」，" +
                "看起来像是生效了，最难归因。画笔请和 titlePaint/contentPaint 一样由 upStyle() 重建。",
            lazyPaints.isEmpty(),
        )
    }

    @Test
    fun `upRenderStyle 覆盖 RenderStyle 的每个字段`() {
        assertAllFieldsAssigned(
            source = mainSource(CHAPTER_PROVIDER),
            declaration = "internal data class RenderStyle(",
            builder = "fun upRenderStyle()",
            constructorCall = "RenderStyle(",
        )
    }

    @Test
    fun `upTipStyle 覆盖 TipStyle 的每个字段`() {
        assertAllFieldsAssigned(
            source = mainSource(TIP_STYLE_PROVIDER),
            declaration = "data class TipStyle(",
            builder = "fun upTipStyle()",
            constructorCall = "TipStyle(",
        )
    }

    /**
     * 断言 [builder] 里的 [constructorCall] 给 [declaration] 的每个 `val` 都传了值。
     * 漏掉的字段会静默保持默认值——「改了设置不生效」，且没有任何编译期信号。
     */
    private fun assertAllFieldsAssigned(
        source: String,
        declaration: String,
        builder: String,
        constructorCall: String,
    ) {
        val fields = Regex("""\bval\s+(\w+)\s*:""")
            .findAll(balancedParens(source, source.requireIndex(declaration) + declaration.length - 1))
            .map { it.groupValues[1] }
            .toList()

        // 从函数签名之后开始找构造调用：`upRenderStyle()` 本身就含有子串 `RenderStyle(`
        val builderStart = source.requireIndex(builder) + builder.length
        val callStart = source.indexOf(constructorCall, builderStart)
        require(callStart >= 0) { "在 $builder 里找不到 $constructorCall——请同步本测试" }
        val assigned = Regex("""(\w+)\s*=""")
            .findAll(balancedParens(source, callStart + constructorCall.length - 1))
            .map { it.groupValues[1] }
            .toSet()

        val missing = fields.filterNot { it in assigned }
        assertTrue(
            "${declaration.substringAfter("class ").substringBefore("(")} 的以下字段没有在 " +
                "$builder 里被赋值，它们会永远保持默认值，表现为「改了设置不生效」：\n" +
                missing.joinToString("\n") { "  - $it" },
            missing.isEmpty(),
        )
    }

    private companion object {
        const val CHAPTER_PROVIDER =
            "io/legado/app/ui/book/read/page/provider/ChapterProvider.kt"
        const val TIP_STYLE_PROVIDER =
            "io/legado/app/ui/book/read/page/provider/TipStyleProvider.kt"
        const val PAGE_VIEW = "io/legado/app/ui/book/read/page/PageView.kt"

        val RENDER_ENTITIES = listOf(
            "io/legado/app/ui/book/read/page/entities/TextLine.kt",
            "io/legado/app/ui/book/read/page/entities/TextPage.kt",
            "io/legado/app/ui/book/read/page/entities/column/TextColumn.kt",
            "io/legado/app/ui/book/read/page/entities/column/TextHtmlColumn.kt",
        )

        fun String.requireIndex(token: String): Int = indexOf(token).also {
            require(it >= 0) { "找不到 `$token`——被改名或改结构了，请同步本测试" }
        }

        /** 取 [openIndex] 处 `(` 起、括号配平为止的文本（不含两端括号）。 */
        fun balancedParens(source: String, openIndex: Int): String {
            var depth = 0
            for (i in openIndex until source.length) {
                when (source[i]) {
                    '(' -> depth++
                    ')' -> if (--depth == 0) return source.substring(openIndex + 1, i)
                }
            }
            error("括号没有配平，起点 $openIndex")
        }

        fun stripComments(text: String): String = text
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

        fun mainSource(relativePath: String): String {
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                for (prefix in listOf("src/main/java", "app/src/main/java")) {
                    val candidate = File(directory, "$prefix/$relativePath")
                    if (candidate.isFile) return candidate.readText()
                }
                directory = directory.parentFile
            }
            error("从 ${File("").absolutePath} 向上找不到 $relativePath")
        }
    }
}
