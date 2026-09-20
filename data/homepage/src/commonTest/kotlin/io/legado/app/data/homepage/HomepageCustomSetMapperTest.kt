package io.legado.app.data.homepage

import io.legado.app.data.entities.HomepageCustomSet as HomepageCustomSetEntity
import io.legado.app.domain.model.CustomSetItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * `HomepageCustomSet` 实体 ↔ `CustomSetItem` 的映射契约（M4-7）。
 *
 * 只有三个字段，但 `sortOrder` 是 DAO 的排序键（`ORDER BY sortOrder ASC`），
 * `name` 是用户可见文本，两个都不能被"顺手"改动。
 */
class HomepageCustomSetMapperTest {

    @Test
    fun toDomainCopiesEveryField() {
        val source = HomepageCustomSetEntity(id = "cs_1", name = "常用", sortOrder = 2)
        val actual = source.toDomain()
        assertEquals(source.id, actual.id)
        assertEquals(source.name, actual.name)
        assertEquals(source.sortOrder, actual.sortOrder)
    }

    @Test
    fun toEntityCopiesEveryField() {
        val source = CustomSetItem(id = "cs_1", name = "常用", sortOrder = 2)
        val actual = source.toEntity()
        assertEquals(source.id, actual.id)
        assertEquals(source.name, actual.name)
        assertEquals(source.sortOrder, actual.sortOrder)
    }

    @Test
    fun roundTripIsLossless() {
        val source = HomepageCustomSetEntity(id = "cs_9", name = "书源分组", sortOrder = 7)
        assertEquals(source, source.toDomain().toEntity())
    }

    /** 空名字是合法值（新建集合后未改名），映射不得 trim / 归一。 */
    @Test
    fun blankNameIsNotNormalized() {
        val domain = HomepageCustomSetEntity(id = "cs_2", name = "  ", sortOrder = 0).toDomain()
        assertEquals("  ", domain.name)
    }

    /** 反向用例：`sortOrder` 不同必须判不等（它是展示顺序，不是主键装饰）。 */
    @Test
    fun copyWithDifferentSortOrderIsNotEqual() {
        val a = CustomSetItem(id = "cs_1", name = "n", sortOrder = 1)
        val b = CustomSetItem(id = "cs_1", name = "n", sortOrder = 2)
        assertNotEquals(a, b)
    }

    @Test
    fun toDomainListPreservesOrder() {
        val list = listOf(
            HomepageCustomSetEntity(id = "cs_2", name = "b", sortOrder = 0),
            HomepageCustomSetEntity(id = "cs_1", name = "a", sortOrder = 1),
        )
        assertEquals(listOf("cs_2", "cs_1"), list.toDomainList().map { it.id })
    }
}
