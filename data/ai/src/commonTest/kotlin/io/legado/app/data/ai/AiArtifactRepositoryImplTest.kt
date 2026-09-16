package io.legado.app.data.ai

import io.legado.app.data.dao.AiArtifactDao
import io.legado.app.data.entities.AiArtifact as AiArtifactEntity
import io.legado.app.domain.ai.AiArtifact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

/**
 * `AiArtifactRepositoryImpl` 的行为护栏（M4-3）。
 *
 * ⚠️ **为什么本片需要它**（判据来自 M4-2）：实现体本身是纯 DAO 委派（无 `copy(`、无分支），
 * 按判据"光 mapper 测试够用"。但本片有一处 **mapper 测试碰不到**的东西：
 * `observeBookArtifacts` 是本域下沉后**唯一的 `Flow` 端口方法**，而 `AiArtifactMapperTest`
 * 只测 `toDomain` / `toEntity` / `toDomainList`——它**不驱动那条流**。于是流水线上的映射整个
 * 不设防：把 `map { it.toDomainList() }` 换成 `map { it as List<AiArtifact> }` 能编译通过
 * （只有 unchecked cast 告警），九条 mapper 用例全绿，而真机上每次发射都 `ClassCastException`。
 *
 * 这里用手写的 DAO 假实现驱动 `Flow`，钉住三点：
 * 1. **映射发生在流内、每次发射都做一次**；
 * 2. `queryArtifacts` 的**三个可空筛选参数原样透传**（本片新"扩"出来的端口方法，
 *    传 `null` = 不筛该维度，串位会让 UI 筛选静默查错维度）；
 * 3. 未命中缓存时透传 `null`，不拿默认值兜底。
 *
 * ⚠️ 用 `runBlocking` 而不是 `runTest`：本模块只依赖 `kotlinx.coroutines.core`，
 * 不为一个测试文件引入 `kotlinx.coroutines.test`（同 M4-2）。
 */
class AiArtifactRepositoryImplTest {

    /**
     * Room `@Dao` 的手写假实现：只记录调用、返回预置数据，不碰 Room 运行时。
     *
     * `observeSource` 预置成一个**多次发射**的流，这样"映射只做第一次"这类错误会被抓住。
     */
    private class FakeAiArtifactDao(
        private val emissions: List<List<AiArtifactEntity>> = emptyList(),
        private val rows: List<AiArtifactEntity> = emptyList(),
        private val cached: AiArtifactEntity? = null,
    ) : AiArtifactDao {

        val upserted = mutableListOf<AiArtifactEntity>()
        val queryCalls = mutableListOf<QueryCall>()
        val contentHashCalls = mutableListOf<ContentHashCall>()

        data class QueryCall(val bookUrl: String?, val taskType: String?, val chapterIndex: Int?, val limit: Int)
        data class ContentHashCall(
            val bookUrl: String,
            val chapterIndex: Int,
            val taskType: String,
            val contentHash: String,
            val limit: Int,
        )

        override fun observeBookArtifacts(bookUrl: String, taskType: String): Flow<List<AiArtifactEntity>> =
            flow { emissions.forEach { emit(it) } }

        override suspend fun getCachedArtifact(
            bookUrl: String,
            chapterIndex: Int?,
            taskType: String,
            contentHash: String,
            promptHash: String,
            modelProfileId: String,
        ): AiArtifactEntity? = cached

        override suspend fun queryArtifacts(
            bookUrl: String?,
            taskType: String?,
            chapterIndex: Int?,
            limit: Int,
        ): List<AiArtifactEntity> {
            queryCalls += QueryCall(bookUrl, taskType, chapterIndex, limit)
            return rows
        }

        override suspend fun queryArtifactsByContentHash(
            bookUrl: String,
            taskType: String,
            chapterIndex: Int,
            contentHash: String,
            limit: Int,
        ): List<AiArtifactEntity> {
            contentHashCalls += ContentHashCall(bookUrl, chapterIndex, taskType, contentHash, limit)
            return rows
        }

        override suspend fun upsert(artifact: AiArtifactEntity) {
            upserted += artifact
        }

        override suspend fun deleteBookArtifacts(bookUrl: String, taskType: String) = Unit
    }

    private fun entity(id: String, status: Int = AiArtifactEntity.STATUS_SUCCESS) = AiArtifactEntity(
        id = id,
        taskType = "chapter_summary",
        bookUrl = "https://example.com/book/1",
        chapterIndex = 1,
        contentHash = "c-$id",
        promptHash = "p-$id",
        modelProfileId = "m",
        status = status,
        output = "o-$id",
        createdAt = 1L,
        updatedAt = 2L,
    )

    /**
     * ⚠️ **流内映射**：让假 DAO 发射**两次**（先一条、再两条），两次都必须拿到**领域模型**。
     *
     * 变异验证（第 5 轮）：把实现里的 `.map { entities -> entities.toDomainList() }` 换成
     * `map { entities -> entities as List<AiArtifact> }` ⇒ 本用例立刻 `ClassCastException`。
     * 之所以收集**全部**发射而不是只看第一次：后者只能证明"头一次对了"，证明不了映射发生在
     * **每一次**发射上。
     */
    @Test
    fun `observeBookArtifacts 每次发射都映射为领域模型`() = runBlocking {
        val dao = FakeAiArtifactDao(
            emissions = listOf(
                listOf(entity("a1")),
                listOf(entity("a1"), entity("a2")),
            )
        )
        val gateway = AiArtifactRepositoryImpl(dao)

        // 逐次收集，验证「每一次」发射都完成了映射。
        val collected = mutableListOf<List<AiArtifact>>()
        gateway.observeBookArtifacts("https://example.com/book/1", "chapter_summary")
            .collect { collected += it }

        assertEquals(2, collected.size, "假 DAO 发射两次，端口应透传两次")
        assertEquals(listOf("a1"), collected[0].map { it.id })
        assertEquals(listOf("a1", "a2"), collected[1].map { it.id })
        // 真正的判据：拿到的是领域模型（若为 unchecked cast，这里已经 ClassCastException）。
        assertEquals(listOf(AiArtifact.STATUS_SUCCESS, AiArtifact.STATUS_SUCCESS), collected[1].map { it.status })
        assertEquals(listOf("o-a1", "o-a2"), collected[1].map { it.output })
    }

    /**
     * ⚠️ **本片新"扩"出来的端口方法**：`queryArtifacts` 的三个筛选参数都可空，
     * **传 `null` = 不筛该维度**。`bookUrl` 与 `taskType` 都是 `String?`，串位编译期不报错，
     * UI 的筛选会静默查错维度 ⇒ 用「一个非空 + 两个 `null`」的混合输入来钉。
     */
    @Test
    fun `queryArtifacts 三个可空筛选参数原样透传`() = runBlocking {
        val dao = FakeAiArtifactDao()

        AiArtifactRepositoryImpl(dao).queryArtifacts(
            bookUrl = "https://example.com/book/9",
            taskType = null,
            chapterIndex = 7,
            limit = 15,
        )

        assertEquals(
            listOf(FakeAiArtifactDao.QueryCall("https://example.com/book/9", null, 7, 15)),
            dao.queryCalls,
        )
    }

    /** 三个维度全 `null` 表示"完全不筛"——不得归一化成 `""` / `0`。 */
    @Test
    fun `queryArtifacts 全 null 筛选透传为不筛`() = runBlocking {
        val dao = FakeAiArtifactDao()

        AiArtifactRepositoryImpl(dao).queryArtifacts(
            bookUrl = null,
            taskType = null,
            chapterIndex = null,
            limit = 1,
        )

        assertEquals(listOf(FakeAiArtifactDao.QueryCall(null, null, null, 1)), dao.queryCalls)
    }

    /**
     * ⚠️ `getArtifactsByContentHash` 的**参数串位陷阱**：端口与 DAO 的形参顺序不同
     * （端口 `chapterIndex` 在 `taskType` 前，DAO 反之），且 `contentHash` / `promptHash`
     * 是同一类型的相邻字符串。这里钉住五个参数都落在正确的形参位上。
     */
    @Test
    fun `getArtifactsByContentHash 五个参数不串位`() = runBlocking {
        val dao = FakeAiArtifactDao()

        AiArtifactRepositoryImpl(dao).getArtifactsByContentHash(
            bookUrl = "u",
            chapterIndex = 3,
            taskType = "chapter_summary",
            contentHash = "the-content-hash",
            limit = 5,
        )

        assertEquals(
            listOf(FakeAiArtifactDao.ContentHashCall("u", 3, "chapter_summary", "the-content-hash", 5)),
            dao.contentHashCalls,
        )
    }

    /** `upsertArtifact` 落库前必须过映射：写进去的是**实体**，字段与领域模型逐一相等。 */
    @Test
    fun `upsertArtifact 映射成实体后落库且 null 不归一化`() = runBlocking {
        val dao = FakeAiArtifactDao()

        AiArtifactRepositoryImpl(dao).upsertArtifact(
            AiArtifact(
                id = "a9",
                taskType = "clean_text",
                bookUrl = "u9",
                chapterIndex = null,
                contentHash = "c9",
                promptHash = "p9",
                modelProfileId = "m9",
                status = AiArtifact.STATUS_FAILED,
                output = null,
                errorMessage = "boom",
                schemaVersion = 2,
                createdAt = 10L,
                updatedAt = 11L,
            )
        )

        val written = dao.upserted.single()
        assertEquals("a9", written.id)
        assertEquals(AiArtifactEntity.STATUS_FAILED, written.status)
        assertNull(written.chapterIndex, "chapterIndex 的 null 不得归一化成 0")
        assertNull(written.output, "output 的 null 不得归一化成空串")
        assertEquals("boom", written.errorMessage)
        assertEquals(2, written.schemaVersion)
        assertEquals(10L, written.createdAt)
        assertEquals(11L, written.updatedAt)
    }

    /** 未命中缓存时透传 `null`，不得拿"空产物"兜底（那会让调用方以为命中了）。 */
    @Test
    fun `getCachedArtifact 未命中透传 null`() = runBlocking {
        val dao = FakeAiArtifactDao(cached = null)

        val artifact = AiArtifactRepositoryImpl(dao).getCachedArtifact(
            bookUrl = "u",
            chapterIndex = null,
            taskType = "t",
            contentHash = "c",
            promptHash = "p",
            modelProfileId = "m",
        )

        assertNull(artifact)
    }

    /** 命中时同样要过映射（返回领域模型而非实体）。 */
    @Test
    fun `getCachedArtifact 命中时映射为领域模型`() = runBlocking {
        val dao = FakeAiArtifactDao(cached = entity("hit"))

        val artifact = AiArtifactRepositoryImpl(dao).getCachedArtifact(
            bookUrl = "u",
            chapterIndex = null,
            taskType = "t",
            contentHash = "c",
            promptHash = "p",
            modelProfileId = "m",
        )

        assertEquals("hit", artifact?.id)
        assertEquals(AiArtifact.STATUS_SUCCESS, artifact?.status)
        assertEquals("o-hit", artifact?.output)
    }
}
