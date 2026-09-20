package io.legado.app.data.homepage

import io.legado.app.data.entities.HomepageModule as HomepageModuleEntity
import io.legado.app.domain.model.ModuleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * `HomepageModule` 实体 ↔ `ModuleItem` 的映射契约（M4-7）。
 *
 * 本片在**全字段判等**一侧（实体与模型都没有 `equals` / `hashCode` 覆写）⇒ 整对象
 * `assertEquals` 成立；仍配一条反向用例挡住后来者补一个 id-only 判等。
 *
 * ⚠️ 16 个字段一一对应，但**实体与模型的构造参数顺序不同**（实体把 `customTitle` /
 * `customSetTitle` 放在末尾，模型放在 `title` 之后）。顺序不同正是「必须逐字段显式赋值、
 * 不能靠位置」的理由。
 */
class HomepageModuleMapperTest {

    private fun entity(
        id: String = "m1",
        sourceUrl: String = "https://example.com/src",
        moduleKey: String = "ranking",
        type: String = "ranking",
        title: String = "排行榜",
        args: String? = """{"limit":10}""",
        layoutConfig: String? = null,
        url: String? = "https://example.com/api",
        isEnabled: Boolean = true,
        sortOrder: Int = 3,
        customSetId: String? = null,
        isUserCreated: Boolean = false,
        customTitle: String? = "我的榜单",
        customSetTitle: String? = "常用",
        sourceJsonHash: String? = "abc123",
        syncedAt: Long = 1_700_000_000_000L,
    ) = HomepageModuleEntity(
        id = id,
        sourceUrl = sourceUrl,
        moduleKey = moduleKey,
        type = type,
        title = title,
        args = args,
        layoutConfig = layoutConfig,
        url = url,
        isEnabled = isEnabled,
        sortOrder = sortOrder,
        customSetId = customSetId,
        isUserCreated = isUserCreated,
        customTitle = customTitle,
        customSetTitle = customSetTitle,
        sourceJsonHash = sourceJsonHash,
        syncedAt = syncedAt,
    )

    @Test
    fun toDomainCopiesEveryField() {
        val source = entity()
        val actual = source.toDomain()
        assertEquals(source.id, actual.id)
        assertEquals(source.sourceUrl, actual.sourceUrl)
        assertEquals(source.moduleKey, actual.moduleKey)
        assertEquals(source.type, actual.type)
        assertEquals(source.title, actual.title)
        assertEquals(source.args, actual.args)
        assertEquals(source.layoutConfig, actual.layoutConfig)
        assertEquals(source.url, actual.url)
        assertEquals(source.isEnabled, actual.isEnabled)
        assertEquals(source.sortOrder, actual.sortOrder)
        assertEquals(source.customSetId, actual.customSetId)
        assertEquals(source.isUserCreated, actual.isUserCreated)
        assertEquals(source.customTitle, actual.customTitle)
        assertEquals(source.customSetTitle, actual.customSetTitle)
        assertEquals(source.sourceJsonHash, actual.sourceJsonHash)
        assertEquals(source.syncedAt, actual.syncedAt)
    }

    @Test
    fun toEntityCopiesEveryField() {
        val source = entity().toDomain()
        val actual = source.toEntity()
        assertEquals(source.id, actual.id)
        assertEquals(source.sourceUrl, actual.sourceUrl)
        assertEquals(source.moduleKey, actual.moduleKey)
        assertEquals(source.type, actual.type)
        assertEquals(source.title, actual.title)
        assertEquals(source.args, actual.args)
        assertEquals(source.layoutConfig, actual.layoutConfig)
        assertEquals(source.url, actual.url)
        assertEquals(source.isEnabled, actual.isEnabled)
        assertEquals(source.sortOrder, actual.sortOrder)
        assertEquals(source.customSetId, actual.customSetId)
        assertEquals(source.isUserCreated, actual.isUserCreated)
        assertEquals(source.customTitle, actual.customTitle)
        assertEquals(source.customSetTitle, actual.customSetTitle)
        assertEquals(source.sourceJsonHash, actual.sourceJsonHash)
        assertEquals(source.syncedAt, actual.syncedAt)
    }

    /** 六个可空字段的 `null` 必须原样搬运（`layoutConfig` 已经是 `null`，这里全设为 `null` 再验一遍）。 */
    @Test
    fun nullablesPassThroughUnchanged() {
        val source = entity(
            args = null,
            layoutConfig = null,
            url = null,
            customSetId = null,
            customTitle = null,
            customSetTitle = null,
            sourceJsonHash = null,
        )
        val domain = source.toDomain()
        assertNull(domain.args)
        assertNull(domain.layoutConfig)
        assertNull(domain.url)
        assertNull(domain.customSetId)
        assertNull(domain.customTitle)
        assertNull(domain.customSetTitle)
        assertNull(domain.sourceJsonHash)
        val back = domain.toEntity()
        assertNull(back.customTitle)
        assertNull(back.sourceJsonHash)
    }

    @Test
    fun roundTripIsLossless() {
        val source = entity()
        assertEquals(source, source.toDomain().toEntity())
    }

    /** `displayTitle` 是计算属性（`customTitle ?: title`），不参与持久化，也不在映射里。 */
    @Test
    fun displayTitleIsDerivedNotMapped() {
        val withCustom = entity(customTitle = "我的榜单", title = "排行榜")
        assertEquals("我的榜单", withCustom.toDomain().displayTitle)
        val withoutCustom = entity(customTitle = null, title = "排行榜")
        assertEquals("排行榜", withoutCustom.toDomain().displayTitle)
    }

    /** 反向用例：全字段判等必须对一个非主键字段敏感（`sortOrder` 是排序键，改了必须不相等）。 */
    @Test
    fun copyWithDifferentSortOrderIsNotEqual() {
        val a = entity().toDomain()
        val b = entity(sortOrder = 99).toDomain()
        assertNotEquals(a, b)
    }

    @Test
    fun toDomainListPreservesOrder() {
        val list = listOf(entity(id = "b"), entity(id = "a"))
        assertEquals(listOf("b", "a"), list.toDomainList().map { it.id })
    }
}
