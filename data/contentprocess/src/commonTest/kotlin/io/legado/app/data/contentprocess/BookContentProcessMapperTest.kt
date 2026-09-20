package io.legado.app.data.contentprocess

import io.legado.app.data.entities.BookContentProcess as BookContentProcessEntity
import io.legado.app.domain.contentprocess.BookContentProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * `BookContentProcess` 实体 ↔ 领域模型的映射契约（M4-8）。
 *
 * 本片在**全字段判等**一侧（两侧都无 `equals` 覆写）⇒ 整对象断言成立，另配反向用例。
 *
 * ⚠️ 最要紧的一条是 [constantsMatchEntityAndLiterals]：领域模型上的 15 个常量是实体那份的
 * **第二副本**，而实体那份被 DAO 的 `@Query` 字符串插值成 SQL 字面量。两处取值漂移
 * **不会有任何编译错误**，只会让「已删除的记录仍被查出来」这类现象出现。所以逐个比对
 * **实体常量**与**字面量**，两个方向都要。
 */
class BookContentProcessMapperTest {

    private fun entity(
        id: String = "p1",
        bookUrl: String = "https://example.com/book",
        chapterIndex: Int? = 3,
        kind: String = "ai_clean",
        stage: String = "content",
        target: String = "selection",
        anchorJson: String = """{"chapterIndex":3}""",
        actionJson: String = """{"type":"replace"}""",
        styleJson: String? = null,
        source: String = "ai",
        aiArtifactId: String? = "art-1",
        sourceContentHash: String? = null,
        enabled: Boolean = true,
        sortOrder: Int = 2,
        status: Int = 1,
        schemaVersion: Int = 1,
        createdAt: Long = 1_700_000_000_000L,
        updatedAt: Long = 1_700_000_000_999L,
    ) = BookContentProcessEntity(
        id = id,
        bookUrl = bookUrl,
        chapterIndex = chapterIndex,
        kind = kind,
        stage = stage,
        target = target,
        anchorJson = anchorJson,
        actionJson = actionJson,
        styleJson = styleJson,
        source = source,
        aiArtifactId = aiArtifactId,
        sourceContentHash = sourceContentHash,
        enabled = enabled,
        sortOrder = sortOrder,
        status = status,
        schemaVersion = schemaVersion,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun toDomainCopiesEveryField() {
        val source = entity()
        val actual = source.toDomain()
        assertEquals(source.id, actual.id)
        assertEquals(source.bookUrl, actual.bookUrl)
        assertEquals(source.chapterIndex, actual.chapterIndex)
        assertEquals(source.kind, actual.kind)
        assertEquals(source.stage, actual.stage)
        assertEquals(source.target, actual.target)
        assertEquals(source.anchorJson, actual.anchorJson)
        assertEquals(source.actionJson, actual.actionJson)
        assertEquals(source.styleJson, actual.styleJson)
        assertEquals(source.source, actual.source)
        assertEquals(source.aiArtifactId, actual.aiArtifactId)
        assertEquals(source.sourceContentHash, actual.sourceContentHash)
        assertEquals(source.enabled, actual.enabled)
        assertEquals(source.sortOrder, actual.sortOrder)
        assertEquals(source.status, actual.status)
        assertEquals(source.schemaVersion, actual.schemaVersion)
        assertEquals(source.createdAt, actual.createdAt)
        assertEquals(source.updatedAt, actual.updatedAt)
    }

    @Test
    fun toEntityCopiesEveryField() {
        val source = entity().toDomain()
        val actual = source.toEntity()
        assertEquals(source.id, actual.id)
        assertEquals(source.bookUrl, actual.bookUrl)
        assertEquals(source.chapterIndex, actual.chapterIndex)
        assertEquals(source.kind, actual.kind)
        assertEquals(source.stage, actual.stage)
        assertEquals(source.target, actual.target)
        assertEquals(source.anchorJson, actual.anchorJson)
        assertEquals(source.actionJson, actual.actionJson)
        assertEquals(source.styleJson, actual.styleJson)
        assertEquals(source.source, actual.source)
        assertEquals(source.aiArtifactId, actual.aiArtifactId)
        assertEquals(source.sourceContentHash, actual.sourceContentHash)
        assertEquals(source.enabled, actual.enabled)
        assertEquals(source.sortOrder, actual.sortOrder)
        assertEquals(source.status, actual.status)
        assertEquals(source.schemaVersion, actual.schemaVersion)
        assertEquals(source.createdAt, actual.createdAt)
        assertEquals(source.updatedAt, actual.updatedAt)
    }

    /** 四个可空字段的 `null` 原样搬运（`chapterIndex` / `styleJson` / `aiArtifactId` / `sourceContentHash`）。 */
    @Test
    fun nullablesPassThroughUnchanged() {
        val source = entity(
            chapterIndex = null,
            styleJson = null,
            aiArtifactId = null,
            sourceContentHash = null,
        )
        val domain = source.toDomain()
        assertNull(domain.chapterIndex)
        assertNull(domain.styleJson)
        assertNull(domain.aiArtifactId)
        assertNull(domain.sourceContentHash)
        val back = domain.toEntity()
        assertNull(back.chapterIndex)
        assertNull(back.aiArtifactId)
    }

    /** 三个 JSON 串是不透明的：首尾空白与原样形状都要过去（M4-4 的教训）。 */
    @Test
    fun jsonFieldsAreCopiedVerbatim() {
        val raw = "  not-a-json-at-all { unbalanced  "
        val domain = entity(anchorJson = raw, actionJson = raw, styleJson = raw).toDomain()
        assertEquals(raw, domain.anchorJson)
        assertEquals(raw, domain.actionJson)
        assertEquals(raw, domain.styleJson)
        val back = domain.toEntity()
        assertEquals(raw, back.anchorJson)
        assertEquals(raw, back.styleJson)
    }

    @Test
    fun roundTripIsLossless() {
        val source = entity()
        assertEquals(source, source.toDomain().toEntity())
    }

    /**
     * ⚠️ **本片最关键的一条**：领域模型的 15 个常量必须**同时**等于实体常量与字面量。
     *
     * 两个方向都断言的理由不同：
     *  - 对**字面量**：防"两边一起改"（若只比对实体常量，把两侧同时改成 `"deleted2"` 仍然绿，
     *    而 DAO 的 SQL 与既有库里的数据对不上）；
     *  - 对**实体常量**：防"只改模型一侧"（那样模型与 SQL 分叉，最典型的是删除语义失配）。
     */
    @Test
    fun constantsMatchEntityAndLiterals() {
        assertEquals(BookContentProcessEntity.KIND_AI_CLEAN, BookContentProcess.KIND_AI_CLEAN)
        assertEquals(BookContentProcessEntity.KIND_AI_REWRITE, BookContentProcess.KIND_AI_REWRITE)
        assertEquals(BookContentProcessEntity.KIND_USER_UNDERLINE, BookContentProcess.KIND_USER_UNDERLINE)
        assertEquals(BookContentProcessEntity.KIND_USER_HIGHLIGHT, BookContentProcess.KIND_USER_HIGHLIGHT)
        assertEquals(BookContentProcessEntity.STAGE_CONTENT, BookContentProcess.STAGE_CONTENT)
        assertEquals(BookContentProcessEntity.STAGE_STYLE, BookContentProcess.STAGE_STYLE)
        assertEquals(BookContentProcessEntity.TARGET_SELECTION, BookContentProcess.TARGET_SELECTION)
        assertEquals(BookContentProcessEntity.TARGET_PARAGRAPH, BookContentProcess.TARGET_PARAGRAPH)
        assertEquals(BookContentProcessEntity.TARGET_CHAPTER, BookContentProcess.TARGET_CHAPTER)
        assertEquals(BookContentProcessEntity.SOURCE_USER, BookContentProcess.SOURCE_USER)
        assertEquals(BookContentProcessEntity.SOURCE_AI, BookContentProcess.SOURCE_AI)
        assertEquals(BookContentProcessEntity.STATUS_DRAFT, BookContentProcess.STATUS_DRAFT)
        assertEquals(BookContentProcessEntity.STATUS_ACTIVE, BookContentProcess.STATUS_ACTIVE)
        assertEquals(BookContentProcessEntity.STATUS_DISABLED, BookContentProcess.STATUS_DISABLED)
        assertEquals(BookContentProcessEntity.STATUS_DELETED, BookContentProcess.STATUS_DELETED)

        assertEquals("ai_clean", BookContentProcess.KIND_AI_CLEAN)
        assertEquals("ai_rewrite", BookContentProcess.KIND_AI_REWRITE)
        assertEquals("user_underline", BookContentProcess.KIND_USER_UNDERLINE)
        assertEquals("user_highlight", BookContentProcess.KIND_USER_HIGHLIGHT)
        assertEquals("content", BookContentProcess.STAGE_CONTENT)
        assertEquals("style", BookContentProcess.STAGE_STYLE)
        assertEquals("selection", BookContentProcess.TARGET_SELECTION)
        assertEquals("paragraph", BookContentProcess.TARGET_PARAGRAPH)
        assertEquals("chapter", BookContentProcess.TARGET_CHAPTER)
        assertEquals("user", BookContentProcess.SOURCE_USER)
        assertEquals("ai", BookContentProcess.SOURCE_AI)
        assertEquals(0, BookContentProcess.STATUS_DRAFT)
        assertEquals(1, BookContentProcess.STATUS_ACTIVE)
        assertEquals(2, BookContentProcess.STATUS_DISABLED)
        assertEquals(3, BookContentProcess.STATUS_DELETED)
    }

    /** 反向用例：全字段判等要对非主键字段敏感（`status` 参与引擎过滤，改名会漏处理项）。 */
    @Test
    fun copyWithDifferentStatusIsNotEqual() {
        val a = entity().toDomain()
        val b = entity().toDomain().copy(status = BookContentProcess.STATUS_DELETED)
        assertNotEquals(a, b)
    }

    @Test
    fun toDomainListPreservesOrder() {
        val list = listOf(entity(id = "c"), entity(id = "a"), entity(id = "b"))
        assertEquals(listOf("c", "a", "b"), list.toDomainList().map { it.id })
    }
}
