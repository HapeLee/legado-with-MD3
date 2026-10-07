package io.legado.app.feature.reader.legacy

import android.app.Application
import io.legado.app.data.entities.HighlightRule
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.core.style.ReaderCharacterStyle
import io.legado.app.feature.reader.core.style.ReaderStyleTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 命中排版的分段口径：字距属于「命中段与相邻字之间」，所以只有段首那一个字让出 before、
 * 段末那一个字让出 after，段内一个字都不加；行距是行级属性，三段都带上，由分页按整行取较大值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LegacyReaderStyleRangeMapperTest {
    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    /** 「张三：“我是李四。”他惊了！」里 我=4 是=5 李=6 四=7。 */
    private fun body(text: String): ReaderChapterSource = ReaderChapterSourceParser.parse(
        chapterIndex = 0,
        title = "",
        paragraphs = listOf(text),
        includeTitle = false,
        adaptSpecialStyle = false,
    )

    private fun rangesFor(rule: HighlightRule) = LegacyReaderStyleRangeMapper.map(
        body("张三：“我是李四。”他惊了！"),
        listOf(rule),
        emptyList(),
    )

    @Test
    fun hitLetterSpacingSplitsIntoHeadBodyAndTail() {
        val ranges = rangesFor(
            HighlightRule(
                pattern = "我是李四",
                targetScope = HighlightRule.TARGET_BODY,
                letterSpacingBefore = 6f,
                letterSpacingAfter = 8f,
                lineSpacingTop = 2f,
                lineSpacingBottom = 3f,
            )
        )

        // 三段互不重叠：首字 4、段内 5..6、末字 7。
        assertEquals(listOf(4, 5, 7), ranges.map { it.start })
        assertEquals(listOf(5, 7, 8), ranges.map { it.endExclusive })
        assertEquals(listOf(6f, 0f, 0f), ranges.map { it.style.matchSpacingBeforePx })
        assertEquals(listOf(0f, 0f, 8f), ranges.map { it.style.matchSpacingAfterPx })
        // 行距三段都带：同一行里两条命中也只抬一次，谁大用谁。
        assertEquals(listOf(2f, 2f, 2f), ranges.map { it.style.linePadTopPx })
        assertEquals(listOf(3f, 3f, 3f), ranges.map { it.style.linePadBottomPx })
    }

    @Test
    fun aSingleCharacterHitCarriesBothSides() {
        val ranges = rangesFor(
            HighlightRule(
                pattern = "李",
                targetScope = HighlightRule.TARGET_BODY,
                letterSpacingBefore = 6f,
                letterSpacingAfter = 8f,
            )
        )

        // 它既是段首也是段尾，两边都要让，所以合成一段而不是三段。
        assertEquals(1, ranges.size)
        assertEquals(6, ranges.first().start)
        assertEquals(7, ranges.first().endExclusive)
        assertEquals(6f, ranges.first().style.matchSpacingBeforePx)
        assertEquals(8f, ranges.first().style.matchSpacingAfterPx)
    }

    @Test
    fun unsetSpacingLeavesTheStyleAtZero() {
        val ranges = rangesFor(
            HighlightRule(pattern = "我是李四", targetScope = HighlightRule.TARGET_BODY)
        )

        assertEquals(listOf(4, 5, 7), ranges.map { it.start })
        ranges.forEach {
            assertEquals(0f, it.style.matchSpacingBeforePx)
            assertEquals(0f, it.style.matchSpacingAfterPx)
            assertEquals(0f, it.style.linePadTopPx)
            assertEquals(0f, it.style.linePadBottomPx)
        }
    }

    /**
     * 预览与角色气泡走字面区间：样式照样拆成首字/段内/末字三段，但完全不问正则命没命中、
     * 规则有没有停用、原本作用于标题还是正文——那三条都不该让一块预览变成死的。
     */
    @Test
    fun literalRangeAppliesTheStyleWithoutConsultingThePattern() {
        val rule = HighlightRule(
            pattern = "这个词不会出现在示例句里",
            enabled = false,
            targetScope = HighlightRule.TARGET_TITLE,
            letterSpacingBefore = 6f,
            letterSpacingAfter = 8f,
        )

        val ranges = LegacyReaderStyleRangeMapper.rangesForLiteralRange(
            rule = rule,
            start = 3,
            endExclusive = 8,
            target = ReaderStyleTarget.BODY,
            priority = 0,
        )

        assertEquals(listOf(3, 4, 7), ranges.map { it.start })
        assertEquals(listOf(4, 7, 8), ranges.map { it.endExclusive })
        assertEquals(listOf(6f, 0f, 0f), ranges.map { it.style.matchSpacingBeforePx })
        assertEquals(listOf(0f, 0f, 8f), ranges.map { it.style.matchSpacingAfterPx })
        assertTrue(ranges.all { it.target == ReaderStyleTarget.BODY })
        // 空区间什么都不产出，调用方可以据此退回按正则命中
        assertTrue(
            LegacyReaderStyleRangeMapper
                .rangesForLiteralRange(rule, 5, 5, ReaderStyleTarget.BODY, 0)
                .isEmpty(),
        )
    }

    /**
     * 角色气泡：区间是「整句台词连同引号」，压在句首的角色胶囊因此落在同一个气泡里。
     *
     * 同一句上高亮规则也命中时，气泡那两栏归角色，字色仍归规则——
     * 「优先用角色的」说的是气泡，不是把整条高亮规则吃掉。
     */
    @Test
    fun characterBubbleWrapsTheQuoteAndKeepsTheRuleTextColor() {
        // 张0 三1 ：2 “3  标记4..13  我14 是15 李16 四17 。18  ”19 他20 惊21 了22 ！23
        val source = ReaderChapterSource(
            chapterIndex = 0,
            title = "",
            blocks = listOf(
                ReaderChapterSourceBlock.Text(
                    value = "张三：“<<张三（男青）>>我是李四。”他惊了！",
                    chapterPosition = 0,
                ),
            ),
            characterCount = 24,
            semanticContent = "张三：“<<张三（男青）>>我是李四。”他惊了！",
        )
        val bubble = ReaderCharacterStyle(backgroundImage = bubbleImage())

        val ranges = LegacyReaderStyleRangeMapper.map(
            source = source,
            rules = listOf(
                HighlightRule(
                    pattern = "我是李四",
                    targetScope = HighlightRule.TARGET_BODY,
                    textColor = 0xFF112233.toInt(),
                ),
            ),
            processes = emptyList(),
            castBubbles = mapOf("张三" to bubble),
        ).filter { it.priority == LegacyReaderStyleRangeMapper.CAST_BUBBLE_PRIORITY }

        // 引号段（连同引号与句首的角色胶囊）整段被气泡盖住
        assertEquals(3, ranges.minOf { it.start })
        assertEquals(20, ranges.maxOf { it.endExclusive })
        assertTrue(ranges.all { it.style.backgroundImage != null })
        // 规则命中的那五个字（我14 是15 李16 四17 → 14..18）仍然带着规则的字色：
        // 气泡只换它自己那两栏，没有把整条高亮规则吃掉
        val colored = ranges.filter { it.style.colorArgb == 0xFF112233.toInt() }
        assertEquals(14, colored.minOf { it.start })
        assertEquals(18, colored.maxOf { it.endExclusive })
    }

    /** 没人设气泡时一个区间都不该多出来：正文的区间逐字节不变。 */
    @Test
    fun noBubbleLeavesTheRangesUntouched() {
        val ranges = rangesFor(HighlightRule(pattern = "我是李四", targetScope = HighlightRule.TARGET_BODY))

        assertTrue(ranges.none { it.priority == LegacyReaderStyleRangeMapper.CAST_BUBBLE_PRIORITY })
    }

    /**
     * 「气泡的任何设置都是优先的」：角色设过的每一栏都要盖过同一条高亮规则，
     * 包括**数值更小**的间距。若按较大者取，规则把字距调大后角色的设置就永远不生效，
     * 只有把高亮规则关掉才看得到——所以这里以角色的值优先。
     */
    @Test
    fun characterStyleOverridesTheRuleWhereItSetItsOwnValue() {
        val source = ReaderChapterSource(
            chapterIndex = 0,
            title = "",
            blocks = listOf(
                ReaderChapterSourceBlock.Text(
                    value = "张三：“<<张三（男青）>>我是李四。”他惊了！",
                    chapterPosition = 0,
                ),
            ),
            characterCount = 24,
            semanticContent = "张三：“<<张三（男青）>>我是李四。”他惊了！",
        )
        val cast = ReaderCharacterStyle(
            colorArgb = 0xFFEEDDCC.toInt(),
            fontPath = "/sdjs/cast.ttf",
            fontWeight = 700,
            italic = true,
            backgroundImage = bubbleImage(),
            matchSpacingBeforePx = 3f,
            matchSpacingAfterPx = 4f,
            linePadTopPx = 5f,
            linePadBottomPx = 6f,
        )

        val ranges = LegacyReaderStyleRangeMapper.map(
            source = source,
            rules = listOf(
                HighlightRule(
                    pattern = "我是李四",
                    targetScope = HighlightRule.TARGET_BODY,
                    textColor = 0xFF112233.toInt(),
                    fontPath = "/sdjs/rule.ttf",
                    letterSpacingBefore = 20f,
                    letterSpacingAfter = 20f,
                    lineSpacingTop = 20f,
                    lineSpacingBottom = 20f,
                ),
            ),
            processes = emptyList(),
            castBubbles = mapOf("张三" to cast),
        ).filter { it.priority == LegacyReaderStyleRangeMapper.CAST_BUBBLE_PRIORITY }

        // 设过的栏全部换成角色的那一份，与规则的数值大小无关
        assertTrue(ranges.all { it.style.colorArgb == 0xFFEEDDCC.toInt() })
        assertTrue(ranges.all { it.style.fontPath == "/sdjs/cast.ttf" })
        assertTrue(ranges.all { it.style.fontWeight == 700 && it.style.italic == true })
        assertTrue(ranges.all { it.style.backgroundImage != null })
        assertTrue(ranges.all { it.style.linePadTopPx == 5f && it.style.linePadBottomPx == 6f })
        // 命中字距只让在整句的首字（开引号 3）与末字（闭引号 19）上，段内一个字都不加
        val head = ranges.first { it.start == 3 }
        val tail = ranges.first { it.endExclusive == 20 }
        assertEquals(3f, head.style.matchSpacingBeforePx)
        assertEquals(0f, head.style.matchSpacingAfterPx)
        assertEquals(4f, tail.style.matchSpacingAfterPx)
        assertEquals(0f, tail.style.matchSpacingBeforePx)
        assertTrue(
            ranges.filter { it != head && it != tail }.all {
                it.style.matchSpacingBeforePx == 0f && it.style.matchSpacingAfterPx == 0f
            },
        )
    }

    /** 引号跟踪器是章级共享的：第二段里的气泡要用第二段自己的坐标，不能带上前一段的长度。 */
    @Test
    fun bubbleInALaterParagraphUsesThatParagraphsOwnCoordinates() {
        val first = "第一段没有对话。"
        val second = "她说：“<<王五（女青）>>你好。”"
        val source = ReaderChapterSource(
            chapterIndex = 0,
            title = "",
            blocks = listOf(
                ReaderChapterSourceBlock.Text(value = first, chapterPosition = 0),
                ReaderChapterSourceBlock.Text(value = second, chapterPosition = first.length),
            ),
            characterCount = first.length + second.length,
            semanticContent = first + second,
        )

        val ranges = LegacyReaderStyleRangeMapper.map(
            source = source,
            rules = emptyList(),
            processes = emptyList(),
            castBubbles = mapOf("王五" to ReaderCharacterStyle(backgroundImage = bubbleImage())),
        ).filter { it.priority == LegacyReaderStyleRangeMapper.CAST_BUBBLE_PRIORITY }

        // 气泡落在第二段自己的坐标上：从本段的开引号起、到本段闭引号后一位
        // （少了 origin 换算的话这里会多出一整段前一段的长度）
        assertEquals(first.length + second.indexOf('“'), ranges.minOf { it.start })
        assertEquals(first.length + second.length, ranges.maxOf { it.endExclusive })
    }

    private fun bubbleImage(): ReaderTextBackgroundImage = ReaderTextBackgroundImage(
        source = "/tmp/bubble.png",
        fit = 3,
        scale = 1f,
        ninePatchLeft = 0.1f,
        ninePatchRight = 0.1f,
        ninePatchTop = 0.1f,
        ninePatchBottom = 0.1f,
    )
}
