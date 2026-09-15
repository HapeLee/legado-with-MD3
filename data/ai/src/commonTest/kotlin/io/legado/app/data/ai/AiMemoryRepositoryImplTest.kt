package io.legado.app.data.ai

import io.legado.app.core.platform.systemTimeMillis
import io.legado.app.data.dao.AiMemoryDao
import io.legado.app.data.entities.AiMemory as AiMemoryEntity
import io.legado.app.domain.ai.AiMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

/**
 * `AiMemoryRepositoryImpl` 的**行为**护栏（M4-2）。
 *
 * ⚠️ **本片与 M4-1 不同，不能只做映射测试**：`AiPromptPresetRepositoryImpl` 是纯 DAO 委派，
 * 映射以外没有逻辑；而 `AiMemoryRepositoryImpl` 有两条真实逻辑，光靠 `AiMemoryMapperTest`
 * 一条都护不住——
 * 1. `upsert` 写前用当前时间**覆盖** `updatedAt`（迁移前是 `System.currentTimeMillis()`）；
 * 2. `getForPrompt` 的「全局 + 本会话」拼接、顺序、以及空白会话 id 的短路。
 *
 * 因此这里用一个手写的 DAO 假实现（纯 Kotlin 实现 Room 的 `@Dao` interface，不碰 Room
 * 运行时）把这两条钉住，并顺带记录调用参数。
 *
 * ⚠️ 用 `runBlocking` 而不是 `kotlinx.coroutines.test` 的 `runTest`：本模块只依赖
 * `kotlinx.coroutines.core`（`withContext(Dispatchers.IO)` 要用），不为一个测试文件引入
 * 新的测试依赖。
 */
class AiMemoryRepositoryImplTest {

    /** Room `@Dao` 的手写假实现：只记录调用、返回预置数据，不做任何真实持久化。 */
    private class FakeAiMemoryDao : AiMemoryDao {

        val global = mutableListOf<AiMemoryEntity>()
        val scoped = mutableMapOf<String, MutableList<AiMemoryEntity>>()
        val upserted = mutableListOf<AiMemoryEntity>()
        val deleted = mutableListOf<Pair<String, String>>()
        val queriedConversations = mutableListOf<String>()

        override fun observeByConversation(conversationId: String): Flow<List<AiMemoryEntity>> =
            flowOf(scoped[conversationId].orEmpty())

        override fun observeGlobal(): Flow<List<AiMemoryEntity>> = flowOf(global.toList())

        override suspend fun getByConversation(conversationId: String): List<AiMemoryEntity> {
            queriedConversations += conversationId
            return scoped[conversationId].orEmpty()
        }

        override suspend fun getGlobal(): List<AiMemoryEntity> = global.toList()

        override suspend fun upsert(memory: AiMemoryEntity) {
            upserted += memory
        }

        override suspend fun delete(conversationId: String, key: String) {
            deleted += conversationId to key
        }

        override suspend fun deleteAllForConversation(conversationId: String) {
            scoped.remove(conversationId)
        }
    }

    private fun globalMemory(key: String, value: String, updatedAt: Long = 1L) = AiMemoryEntity(
        conversationId = "",
        key = key,
        value = value,
        updatedAt = updatedAt,
    )

    private fun scopedMemory(key: String, value: String, updatedAt: Long = 1L) = AiMemoryEntity(
        conversationId = "conv_1",
        key = key,
        value = value,
        updatedAt = updatedAt,
    )

    /**
     * ⚠️ 空白会话 id ⇒ **不查**会话记忆（不是"查一个 id 为空白的会话"）。
     *
     * 这条钉的是 `isNotBlank()` 的短路：改成"总是查 `getByConversation`"，在 DAO 的真实现里会
     * 退化成 `WHERE conversationId = ''`，把全局记忆**重复返回一遍**。
     */
    @Test
    fun `空白会话 id 的 getForPrompt 只取全局记忆`() = runBlocking {
        val dao = FakeAiMemoryDao().apply {
            global += globalMemory("tone", "简洁")
            scoped["conv_1"] = mutableListOf(scopedMemory("genre", "科幻"))
        }

        val memories = AiMemoryRepositoryImpl(dao).getForPrompt("")

        assertEquals(listOf("tone"), memories.map { it.key })
        assertEquals(listOf(""), memories.map { it.conversationId })
        assertTrue(dao.queriedConversations.isEmpty(), "空白会话 id 不应触发会话查询")
    }

    /** 顺序是「全局在前、会话在后」——提示词里全局设定的优先级靠这个顺序表达。 */
    @Test
    fun `getForPrompt 先返回全局记忆再返回会话记忆`() = runBlocking {
        val dao = FakeAiMemoryDao().apply {
            global += globalMemory("tone", "简洁")
            global += globalMemory("language", "中文")
            scoped["conv_1"] = mutableListOf(scopedMemory("genre", "科幻"))
        }

        val memories = AiMemoryRepositoryImpl(dao).getForPrompt("conv_1")

        assertEquals(listOf("tone", "language", "genre"), memories.map { it.key })
        assertEquals(listOf("", "", "conv_1"), memories.map { it.conversationId })
        assertEquals(listOf("conv_1"), dao.queriedConversations)
    }

    /**
     * ⚠️ 钉住「写前覆盖 `updatedAt` 为当前时间」：调用方（`AiToolRepository`）构造记忆时从不传
     * `updatedAt`，落库时间全靠这一行。删掉 `copy(updatedAt = ...)` 会让所有记忆的
     * `updatedAt` 停在默认值（构造那一刻），界面上的"最后更新"与排序
     * （`ORDER BY updatedAt DESC`）随之失真。
     */
    @Test
    fun `upsert 用当前时间覆盖 updatedAt 且保留其余字段`() = runBlocking {
        val dao = FakeAiMemoryDao()
        val before = systemTimeMillis()

        AiMemoryRepositoryImpl(dao).upsert(
            AiMemory(
                conversationId = "conv_9",
                key = "favorite_genre",
                value = "科幻",
                updatedAt = 0L,
            )
        )

        val after = systemTimeMillis()
        val written = dao.upserted.single()

        assertEquals("conv_9", written.conversationId)
        assertEquals("favorite_genre", written.key)
        assertEquals("科幻", written.value)
        assertNotEquals(0L, written.updatedAt, "upsert 必须覆盖调用方传入的 updatedAt")
        assertTrue(
            written.updatedAt in before..after,
            "覆盖后的 updatedAt 应落在本次调用的时间窗内，实际 ${written.updatedAt}（窗口 $before..$after）",
        )
    }

    /** `delete` 是纯透传，两个参数都不能串位（复合主键顺序错了会删掉别的会话的同名记忆）。 */
    @Test
    fun `delete 透传会话 id 与 key`() = runBlocking {
        val dao = FakeAiMemoryDao()

        AiMemoryRepositoryImpl(dao).delete("conv_3", "tone")

        assertEquals(listOf("conv_3" to "tone"), dao.deleted)
    }
}
