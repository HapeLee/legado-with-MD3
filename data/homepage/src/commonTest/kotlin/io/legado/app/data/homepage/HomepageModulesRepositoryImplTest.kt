package io.legado.app.data.homepage

import io.legado.app.data.dao.HomepageCustomSetDao
import io.legado.app.data.dao.HomepageModuleDao
import io.legado.app.data.entities.HomepageCustomSet as HomepageCustomSetEntity
import io.legado.app.data.entities.HomepageModule as HomepageModuleEntity
import io.legado.app.domain.model.CustomSetItem
import io.legado.app.domain.model.ModuleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * [io.legado.app.data.homepage.HomepageModulesRepositoryImpl] 的行为测试（M4-7）。
 *
 * 为什么不能只靠两个 mapper 测试：本域端口有**四个 `Flow` 方法**，映射在流内每次发射都跑。
 * 把 `.map { list -> list.map { it.toDomain() } }` 换成 unchecked cast **能编译**（一条告警），
 * 而且全部 mapper 用例照样绿 —— 真机每次发射才 `ClassCastException`（M4-3 的判据，
 * M4-6 第二次、本片第三次应用）。
 *
 * 另外两条是 mapper 测试完全够不着的**行为**：
 *  - `createCustomSet` 的 id 形状（`cs_<millis>`，落库后原样返回）；
 *  - `deleteCustomSet` 的**顺序**（先摘模块再删集合）——反过来会留下孤儿模块。
 */
class HomepageModulesRepositoryImplTest {

    /**
     * 跨 DAO 的**调用顺序**记录（M4-7 变异第 4 轮抓出来的）：
     * 两个 fake 各自维护的 `calls` 只能证明「各自被调了」，**表达不了先后**——
     * 把 `deleteCustomSet` 的两条语句对调，两个列表照样各只有一条，断言全绿。
     * 共享一条序列才能钉住「先摘模块、再删集合」。
     */
    private class CallOrder {
        val sequence = mutableListOf<String>()
    }

    private class FakeModuleDao(
        private val order: CallOrder = CallOrder(),
    ) : HomepageModuleDao {
        val calls = mutableListOf<String>()
        var emissions: List<List<HomepageModuleEntity>> = emptyList()
        var byIdResult: HomepageModuleEntity? = null
        var lastUpserted: List<HomepageModuleEntity>? = null

        override fun flowEnabled(): Flow<List<HomepageModuleEntity>> {
            calls.add("flowEnabled")
            return flow { emissions.forEach { emit(it) } }
        }

        override fun flowAll(): Flow<List<HomepageModuleEntity>> {
            calls.add("flowAll")
            return flow { emissions.forEach { emit(it) } }
        }

        override fun flowBySource(sourceUrl: String): Flow<List<HomepageModuleEntity>> {
            calls.add("flowBySource:$sourceUrl")
            return flow { emissions.forEach { emit(it) } }
        }

        override suspend fun getById(id: String): HomepageModuleEntity? {
            calls.add("getById:$id")
            return byIdResult?.takeIf { it.id == id }
        }

        override suspend fun upsertAll(modules: List<HomepageModuleEntity>) {
            lastUpserted = modules
        }

        override suspend fun setEnabled(id: String, enabled: Boolean) {
            calls.add("setEnabled:$id:$enabled")
        }

        override suspend fun setSortOrder(id: String, order: Int) {
            calls.add("setSortOrder:$id:$order")
        }

        override suspend fun batchSetSortOrders(orders: Map<String, Int>) {
            calls.add("batchSetSortOrders:${orders.size}")
        }

        override suspend fun setCustomSetTitle(id: String, title: String?) {
            calls.add("setCustomSetTitle:$id:$title")
        }

        override suspend fun setCustomSetId(id: String, setId: String?) {
            calls.add("setCustomSetId:$id:$setId")
        }

        override suspend fun delete(id: String) {
            calls.add("delete:$id")
        }

        override suspend fun deleteByCustomSetId(setId: String) {
            calls.add("deleteByCustomSetId:$setId")
            order.sequence.add("moduleDao.deleteByCustomSetId:$setId")
        }

        override suspend fun deleteStale(sourceUrl: String, currentIds: List<String>) {
            calls.add("deleteStale:$sourceUrl:${currentIds.size}")
        }

        override suspend fun getAll(): List<HomepageModuleEntity> = emptyList()

        override suspend fun deleteAll() = Unit

        override suspend fun replaceAll(modules: List<HomepageModuleEntity>) = Unit
    }

    private class FakeCustomSetDao(
        private val order: CallOrder = CallOrder(),
    ) : HomepageCustomSetDao {
        val calls = mutableListOf<String>()
        var emissions: List<List<HomepageCustomSetEntity>> = emptyList()
        var byIdResult: HomepageCustomSetEntity? = null
        var lastUpserted: HomepageCustomSetEntity? = null

        override fun flowAll(): Flow<List<HomepageCustomSetEntity>> {
            calls.add("flowAll")
            return flow { emissions.forEach { emit(it) } }
        }

        override suspend fun getById(id: String): HomepageCustomSetEntity? {
            calls.add("getById:$id")
            return byIdResult?.takeIf { it.id == id }
        }

        override suspend fun upsert(customSet: HomepageCustomSetEntity) {
            lastUpserted = customSet
        }

        override suspend fun rename(id: String, name: String) {
            calls.add("rename:$id:$name")
        }

        override suspend fun setSortOrder(id: String, order: Int) {
            calls.add("setSortOrder:$id:$order")
        }

        override suspend fun batchSetSortOrders(orders: Map<String, Int>) {
            calls.add("batchSetSortOrders:${orders.size}")
        }

        override suspend fun delete(id: String) {
            calls.add("delete:$id")
            order.sequence.add("customSetDao.delete:$id")
        }

        override suspend fun getAll(): List<HomepageCustomSetEntity> = emptyList()

        override suspend fun deleteAll() = Unit

        override suspend fun insertAll(sets: List<HomepageCustomSetEntity>) = Unit

        override suspend fun replaceAll(sets: List<HomepageCustomSetEntity>) = Unit
    }

    private fun moduleEntity(id: String, title: String = "t") = HomepageModuleEntity(
        id = id,
        sourceUrl = "https://example.com/src",
        moduleKey = id,
        type = "ranking",
        title = title,
        sortOrder = 1,
    )

    /**
     * 四个 Flow 方法共用一条判据：**每次发射都要映射**。给两条不同的发射，收集全部，
     * 再取一条非主键字段——若流内没映射，这里访问的就是实体的字段 ⇒ `ClassCastException`。
     */
    @Test
    fun allFlowMethodsMapEveryEmission() = runBlocking {
        val modules = FakeModuleDao().apply {
            emissions = listOf(listOf(moduleEntity("a", "一")), listOf(moduleEntity("b", "二")))
        }
        val sets = FakeCustomSetDao().apply {
            emissions = listOf(
                listOf(HomepageCustomSetEntity(id = "cs_1", name = "常用")),
                emptyList(),
            )
        }
        val impl = HomepageModulesRepositoryImpl(modules, sets)

        val enabled = impl.flowEnabled().toList()
        val all = impl.flowAll().toList()
        val bySource = impl.flowBySource("https://example.com/src").toList()
        val customSets = impl.flowCustomSets().toList()

        assertEquals(listOf("a", "b"), enabled.map { it.first().id })
        assertEquals(listOf("a", "b"), all.map { it.first().id })
        assertEquals(listOf("a", "b"), bySource.map { it.first().id })
        // 取第二条发射的第一个元素的**非主键**字段：未映射的话这里就炸了
        assertEquals("二", all[1].first().title)

        assertEquals(2, customSets.size)
        assertEquals("常用", customSets[0].first().name)
        assertTrue(customSets[1].isEmpty())
    }

    @Test
    fun getByIdMapsHitAndReturnsNullOnMiss() = runBlocking {
        val modules = FakeModuleDao().apply { byIdResult = moduleEntity("m1") }
        val sets = FakeCustomSetDao().apply {
            byIdResult = HomepageCustomSetEntity(id = "cs_1", name = "常用")
        }
        val impl = HomepageModulesRepositoryImpl(modules, sets)

        assertEquals("m1", impl.getById("m1")?.id)
        assertNull(impl.getById("missing"))
        assertEquals("cs_1", impl.getCustomSetById("cs_1")?.id)
        assertNull(impl.getCustomSetById("missing"))
    }

    @Test
    fun upsertAllMapsBackToEntities() = runBlocking {
        val modules = FakeModuleDao()
        val impl = HomepageModulesRepositoryImpl(modules, FakeCustomSetDao())

        impl.upsertAll(listOf(ModuleItem(id = "m1", title = "榜单", sortOrder = 5)))

        val saved = modules.lastUpserted
        assertEquals(1, saved?.size)
        assertEquals("m1", saved?.first()?.id)
        assertEquals("榜单", saved?.first()?.title)
        assertEquals(5, saved?.first()?.sortOrder)
    }

    /** id 形状是 `cs_<millis>`，且**落库的那个**与返回的是同一个（不是先查一次再返回）。 */
    @Test
    fun createCustomSetUsesTimestampIdAndReturnsTheUpsertedOne() = runBlocking {
        val sets = FakeCustomSetDao()
        val impl = HomepageModulesRepositoryImpl(FakeModuleDao(), sets)

        val created = impl.createCustomSet("我的分组")

        assertTrue(created.id.startsWith("cs_"))
        assertTrue(created.id.removePrefix("cs_").toLongOrNull() != null)
        assertEquals("我的分组", created.name)
        assertEquals(created.id, sets.lastUpserted?.id)
        assertEquals("我的分组", sets.lastUpserted?.name)
    }

    /**
     * `deleteCustomSet` 的**顺序**是行为：先摘掉挂在该集合下的模块，再删集合。
     * 反过来会留下 `customSetId` 指向已删集合的孤儿模块。
     */
    @Test
    fun deleteCustomSetRemovesModulesBeforeTheSet() = runBlocking {
        val order = CallOrder()
        val modules = FakeModuleDao(order)
        val sets = FakeCustomSetDao(order)
        val impl = HomepageModulesRepositoryImpl(modules, sets)

        impl.deleteCustomSet("cs_1")

        // 断言**跨 DAO 的相对顺序**（各自单独的列表无法表达先后，见 CallOrder 的说明）。
        assertEquals(
            listOf("moduleDao.deleteByCustomSetId:cs_1", "customSetDao.delete:cs_1"),
            order.sequence,
        )
    }

    @Test
    fun scalarMutationsPassThrough() = runBlocking {
        val modules = FakeModuleDao()
        val sets = FakeCustomSetDao()
        val impl = HomepageModulesRepositoryImpl(modules, sets)

        impl.setEnabled("m1", false)
        impl.setCustomSetId("m1", "cs_1")
        impl.setCustomSetTitle("m1", "常用")
        impl.delete("m1")
        impl.deleteStale("https://example.com/src", listOf("a", "b"))
        impl.batchSetSortOrders(mapOf("m1" to 1, "m2" to 2))
        impl.renameCustomSet("cs_1", "新名字")
        impl.batchSetCustomSetSortOrders(mapOf("cs_1" to 0))

        assertEquals(
            listOf(
                "setEnabled:m1:false",
                "setCustomSetId:m1:cs_1",
                "setCustomSetTitle:m1:常用",
                "delete:m1",
                "deleteStale:https://example.com/src:2",
                "batchSetSortOrders:2",
            ),
            modules.calls,
        )
        assertEquals(
            listOf("rename:cs_1:新名字", "batchSetSortOrders:1"),
            sets.calls,
        )
    }

    @Test
    fun upsertCustomSetMapsBackToEntity() = runBlocking {
        val sets = FakeCustomSetDao()
        val impl = HomepageModulesRepositoryImpl(FakeModuleDao(), sets)

        impl.upsertCustomSet(CustomSetItem(id = "cs_2", name = "分组", sortOrder = 4))

        assertEquals("cs_2", sets.lastUpserted?.id)
        assertEquals("分组", sets.lastUpserted?.name)
        assertEquals(4, sets.lastUpserted?.sortOrder)
    }
}
