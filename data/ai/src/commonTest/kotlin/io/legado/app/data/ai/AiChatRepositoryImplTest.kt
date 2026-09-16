package io.legado.app.data.ai

import io.legado.app.core.platform.systemTimeMillis
import io.legado.app.data.dao.AiChatDao
import io.legado.app.data.dao.BranchCount
import io.legado.app.data.entities.AiChatConversation as AiChatConversationEntity
import io.legado.app.data.entities.AiChatMessage as AiChatMessageEntity
import io.legado.app.domain.ai.AiChatConversation
import io.legado.app.domain.ai.AiChatMessage
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessagePartJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

/**
 * `AiChatRepositoryImpl` 的**行为**护栏（M4-4）。
 *
 * ⚠️ **为什么本片必须有它**（判据来自 M4-2，M4-3 补了「Flow 端口」这一条触发条件）：
 * 本实现**不是**纯 DAO 委派——13 个端口方法里有 6 个带真实逻辑，且**全是 mapper 测试碰不到的**：
 * - `saveRegeneratedMessage`：先取 `countBranches` 定 `branchIndex`，再把现有兄弟**逐个**
 *   `copy(isSelected = false)` 落库（旧分支不删），最后写新消息并推会话时间戳；
 * - `selectBranch`：两处提前返回（消息不存在 / 无 `parentMessageId`）后，取消兄弟选中再选中自己；
 * - `createConversation` / `saveMessage`：生成 id 与时间戳 → 落库 → 推会话 `updatedAt`；
 * - `getBranchCounts`：把 DAO 的 `List<BranchCount>` **聚合成 `Map`**；
 * - `deleteConversation`：**两条** DAO 调用（先删消息、再删会话）。
 * 这些都是「多条 DAO 调用的组合 / 条件分支」，删掉任何一条 mapper 用例都不会变红。
 *
 * ⚠️ 另有两条**只有本形态能钉住**的：
 * ① 两个 `Flow` 方法必须在**流内**做映射（M4-3 立的规矩）——用假 DAO 的 `flow { }` 发射两次来验；
 * ② `partsJson` 的**编码**发生在实现侧（用 `AiMessagePartJson.encode`），不是映射器侧。
 *
 * ⚠️ 用 `runBlocking` 而不是 `kotlinx.coroutines.test` 的 `runTest`：本模块只依赖
 * `kotlinx.coroutines.core`，不为一个测试文件引入新的测试依赖（同 M4-2 / M4-3）。
 */
class AiChatRepositoryImplTest {

    /**
     * Room `@Dao` 的手写假实现：只记录调用、返回预置数据，不碰 Room 运行时。
     *
     * `conversationEmissions` / `messageEmissions` 预置成**多次发射**的流，这样「映射只做第一次」
     * 这类错误会被抓住（`flow { }` 顺序发射，收集方能看到每一次）。
     */
    private class FakeAiChatDao(
        private val conversationEmissions: List<List<AiChatConversationEntity>> = emptyList(),
        private val messageEmissions: List<List<AiChatMessageEntity>> = emptyList(),
        private val branches: List<AiChatMessageEntity> = emptyList(),
        private val branchCountValue: Int = 0,
        private val branchCountRows: List<BranchCount> = emptyList(),
        private val conversation: AiChatConversationEntity? = null,
        private val message: AiChatMessageEntity? = null,
    ) : AiChatDao {

        val insertedConversations = mutableListOf<AiChatConversationEntity>()
        val insertedMessages = mutableListOf<AiChatMessageEntity>()
        val touched = mutableListOf<Pair<String, Long>>()
        val titleUpdates = mutableListOf<Triple<String, String, Long>>()
        val reasoningUpdates = mutableListOf<Triple<String, String, Long>>()
        val selectedBranches = mutableListOf<String>()
        val deletedConversations = mutableListOf<String>()
        val deletedMessagesFor = mutableListOf<String>()
        val branchQueries = mutableListOf<String>()
        val branchCountQueries = mutableListOf<String>()

        /**
         * 单调递增的调用序列：只记「有顺序语义」的写操作。
         * 用来钉 `deleteConversation` 的「先删消息再删会话」——两个独立列表看不出先后。
         */
        val callLog = mutableListOf<String>()

        override fun observeConversations(): Flow<List<AiChatConversationEntity>> =
            flow { conversationEmissions.forEach { emit(it) } }

        override fun observeMessages(conversationId: String): Flow<List<AiChatMessageEntity>> =
            flow { messageEmissions.forEach { emit(it) } }

        override fun observeSelectedMessages(conversationId: String): Flow<List<AiChatMessageEntity>> =
            flow { messageEmissions.forEach { emit(it) } }

        override suspend fun countBranches(parentMessageId: String): Int = branchCountValue

        override suspend fun getBranches(parentMessageId: String): List<AiChatMessageEntity> {
            branchQueries += parentMessageId
            return branches
        }

        override suspend fun getBranchCounts(conversationId: String): List<BranchCount> {
            branchCountQueries += conversationId
            return branchCountRows
        }

        override suspend fun deselectAssistantAfter(conversationId: String, afterTimestamp: Long) = Unit

        override suspend fun selectBranch(messageId: String) {
            selectedBranches += messageId
        }

        override suspend fun deselectAll(conversationId: String) = Unit

        override suspend fun getConversation(id: String): AiChatConversationEntity? = conversation

        override suspend fun getMessage(id: String): AiChatMessageEntity? = message

        override suspend fun insertConversation(conversation: AiChatConversationEntity) {
            insertedConversations += conversation
        }

        override suspend fun insertMessage(message: AiChatMessageEntity) {
            insertedMessages += message
        }

        override suspend fun updateConversationTitle(conversationId: String, title: String, updatedAt: Long) {
            titleUpdates += Triple(conversationId, title, updatedAt)
        }

        override suspend fun updateConversationReasoningLevel(
            conversationId: String,
            reasoningLevel: String,
            updatedAt: Long,
        ) {
            reasoningUpdates += Triple(conversationId, reasoningLevel, updatedAt)
        }

        override suspend fun touchConversation(conversationId: String, updatedAt: Long) {
            touched += conversationId to updatedAt
        }

        override suspend fun deleteConversation(conversationId: String) {
            callLog += "deleteConversation"
            deletedConversations += conversationId
        }

        override suspend fun deleteMessagesByConversation(conversationId: String) {
            callLog += "deleteMessagesByConversation"
            deletedMessagesFor += conversationId
        }
    }

    private fun conversationEntity(id: String = "c1", title: String = "t") = AiChatConversationEntity(
        id = id,
        title = title,
        reasoningLevel = "auto",
        modelProfileId = null,
        createdAt = 10L,
        updatedAt = 20L,
    )

    private fun messageEntity(
        id: String = "m1",
        parentMessageId: String? = null,
        isSelected: Boolean = true,
        branchIndex: Int = 0,
    ) = AiChatMessageEntity(
        id = id,
        conversationId = "c1",
        role = "assistant",
        partsJson = """[{"type":"text","text":"$id"}]""",
        createdAt = 30L,
        branchIndex = branchIndex,
        isSelected = isSelected,
        parentMessageId = parentMessageId,
        thinkingDuration = 2,
    )

    // ---------- Flow 端口：映射必须在流内、每次发射都做 ----------

    /**
     * ⚠️ 让假 DAO 发射**两次**，两次都必须拿到**领域模型**。
     * 变异验证：把实现里的 `.map { it.toDomainList() }` 换成 `as List<AiChatConversation>` ⇒ 立刻
     * `ClassCastException`。用 `collect` 收全部发射而不是只看第一次：后者证明不了「每次都映射」。
     */
    @Test
    fun `observeConversations 每次发射都映射为领域模型`() = runBlocking {
        val dao = FakeAiChatDao(
            conversationEmissions = listOf(
                listOf(conversationEntity("c1", "第一条")),
                listOf(conversationEntity("c1", "第一条"), conversationEntity("c2", "第二条")),
            )
        )

        val collected = mutableListOf<List<AiChatConversation>>()
        AiChatRepositoryImpl(dao).observeConversations().collect { collected += it }

        assertEquals(2, collected.size, "假 DAO 发射两次，端口应透传两次")
        assertEquals(listOf("c1"), collected[0].map { it.id })
        assertEquals(listOf("c1", "c2"), collected[1].map { it.id })
        assertEquals(listOf("第一条", "第二条"), collected[1].map { it.title })
    }

    /** 同上，消息流也必须每次发射都完成映射（且字段一致）。 */
    @Test
    fun `observeSelectedMessages 每次发射都映射为领域模型`() = runBlocking {
        val dao = FakeAiChatDao(
            messageEmissions = listOf(
                listOf(messageEntity("m1")),
                listOf(messageEntity("m1"), messageEntity("m2", parentMessageId = "m1", isSelected = false)),
            )
        )

        val collected = mutableListOf<List<AiChatMessage>>()
        AiChatRepositoryImpl(dao).observeSelectedMessages("c1").collect { collected += it }

        assertEquals(2, collected.size)
        assertEquals(listOf("m1"), collected[0].map { it.id })
        assertEquals(listOf("m1", "m2"), collected[1].map { it.id })
        assertEquals(listOf(null, "m1"), collected[1].map { it.parentMessageId })
        assertEquals(listOf(true, false), collected[1].map { it.isSelected })
    }

    // ---------- 会话 ----------

    /** 未命中时透传 `null`（调用方据此决定是否新建会话），不得拿空对象兜底。 */
    @Test
    fun `getConversation 未命中透传 null`() = runBlocking {
        val dao = FakeAiChatDao(conversation = null)

        assertEquals(null, AiChatRepositoryImpl(dao).getConversation("nope"))
    }

    /** 命中时映射为领域模型（而不是把实体透出去）。 */
    @Test
    fun `getConversation 命中时映射为领域模型`() = runBlocking {
        val dao = FakeAiChatDao(conversation = conversationEntity("c9", "命中"))

        val conversation = AiChatRepositoryImpl(dao).getConversation("c9")

        assertEquals("c9", conversation?.id)
        assertEquals("命中", conversation?.title)
        assertEquals("auto", conversation?.reasoningLevel)
    }

    /**
     * ⚠️ 新建会话：`id` 必须带 `chat_` 前缀、时间戳取当前时间、**落库的是实体**、返回的是领域模型。
     * 变异验证：把 `newId("chat")` 改成 `newId("message")` ⇒ 前缀断言变红。
     */
    @Test
    fun `createConversation 生成 chat 前缀 id 并落库后返回领域模型`() = runBlocking {
        val dao = FakeAiChatDao()
        val before = systemTimeMillis()

        val conversation = AiChatRepositoryImpl(dao).createConversation("新会话")

        val after = systemTimeMillis()
        val written = dao.insertedConversations.single()

        assertTrue(written.id.startsWith("chat_"), "id 前缀应为 chat_，实际 ${written.id}")
        assertEquals("新会话", written.title)
        assertTrue(
            written.createdAt in before..after,
            "createdAt 应落在本次调用的时间窗内，实际 ${written.createdAt}（窗口 $before..$after）",
        )
        assertEquals(written.createdAt, written.updatedAt, "新建时 created / updated 取同一个 now")
        // 返回的是领域模型，字段与落库实体一致。
        assertEquals(written.id, conversation.id)
        assertEquals("新会话", conversation.title)
    }

    /**
     * ⚠️ `createConversation` 的默认标题 `"New Chat"` 是**签名默认值**（迁移前就有），
     * 调用方常不传；被改成空串会让新会话在列表里显示为无名条目。
     */
    @Test
    fun `createConversation 的默认标题沿用 New Chat`() = runBlocking {
        val dao = FakeAiChatDao()

        AiChatRepositoryImpl(dao).createConversation()

        assertEquals("New Chat", dao.insertedConversations.single().title)
    }

    /** 重命名：三个参数都要到位，且 `updatedAt` 取当前时间（会话因此重新排序到最前）。 */
    @Test
    fun `updateConversationTitle 透传参数并推当前时间戳`() = runBlocking {
        val dao = FakeAiChatDao()
        val before = systemTimeMillis()

        AiChatRepositoryImpl(dao).updateConversationTitle("c1", "改个名")

        val after = systemTimeMillis()
        val (conversationId, title, updatedAt) = dao.titleUpdates.single()

        assertEquals("c1", conversationId)
        assertEquals("改个名", title)
        assertTrue(updatedAt in before..after, "updatedAt 应取当前时间，实际 $updatedAt")
    }

    /** 推理等级同理（存的是小写枚举名，实现只做透传，不转换大小写）。 */
    @Test
    fun `updateReasoningLevel 透传参数并推当前时间戳`() = runBlocking {
        val dao = FakeAiChatDao()
        val before = systemTimeMillis()

        AiChatRepositoryImpl(dao).updateReasoningLevel("c1", "high")

        val after = systemTimeMillis()
        val (conversationId, level, updatedAt) = dao.reasoningUpdates.single()

        assertEquals("c1", conversationId)
        assertEquals("high", level, "实现不得转换大小写——存的就是调用方给的值")
        assertTrue(updatedAt in before..after)
    }

    /**
     * ⚠️ 删除会话是**两条** DAO 调用且**先删消息再删会话**：顺序反了在真库上会留下孤儿消息行
     * （会话没了、消息还在，下次同名 id 复用时旧消息会突然「复活」）。
     * 用假 DAO 的 `callLog` 钉真实调用序列——两个独立列表只能证明「都调了」，证明不了先后。
     */
    @Test
    fun `deleteConversation 先删消息再删会话`() = runBlocking {
        val dao = FakeAiChatDao()

        AiChatRepositoryImpl(dao).deleteConversation("c1")

        assertEquals(
            listOf("deleteMessagesByConversation", "deleteConversation"),
            dao.callLog,
            "必须先删消息、再删会话",
        )
        assertEquals(listOf("c1"), dao.deletedMessagesFor)
        assertEquals(listOf("c1"), dao.deletedConversations)
    }

    // ---------- 消息：分支逻辑（本片最重的部分） ----------

    /**
     * ⚠️ **`saveMessage` 做四件事**：生成 `message_` 前缀 id、把 `parts` **编码** 成 `partsJson`、
     * 以 `branchIndex = 0` / `isSelected = true` 落库、再推会话 `updatedAt`。
     *
     * 变异验证：① 去掉 `touchConversation` ⇒ 会话列表不再因新消息重排，断言变红；
     * ② 把 `AiMessagePartJson.encode(parts)` 换成 `""` ⇒ partsJson 断言变红（编码是实现的职责）。
     */
    @Test
    fun `saveMessage 编码 parts 落库并推会话时间戳`() = runBlocking {
        val dao = FakeAiChatDao()
        val before = systemTimeMillis()
        val parts = listOf(AiMessagePart.Text("你好"), AiMessagePart.Reasoning("想了想"))

        val message = AiChatRepositoryImpl(dao).saveMessage(
            conversationId = "c1",
            role = "assistant",
            parts = parts,
            parentMessageId = "m_parent",
            thinkingDuration = 9,
        )

        val after = systemTimeMillis()
        val written = dao.insertedMessages.single()

        assertTrue(written.id.startsWith("message_"), "id 前缀应为 message_，实际 ${written.id}")
        assertEquals("c1", written.conversationId)
        assertEquals("assistant", written.role)
        assertEquals(
            AiMessagePartJson.encode(parts),
            written.partsJson,
            "partsJson 必须是实现侧 encode 的结果（不是映射器、也不是调用方传的字符串）",
        )
        // 新消息永远从「第一条分支 / 选中」开始。
        assertEquals(0, written.branchIndex)
        assertEquals(true, written.isSelected)
        assertEquals("m_parent", written.parentMessageId)
        assertEquals(9, written.thinkingDuration)
        assertTrue(written.createdAt in before..after)

        // 会话被推时间戳（新消息让会话跳到列表最前）。
        assertEquals(listOf("c1" to written.createdAt), dao.touched)
        // 返回的是领域模型。
        assertEquals(written.id, message.id)
        assertEquals(written.partsJson, message.partsJson)
    }

    /**
     * ⚠️ **`saveRegeneratedMessage` 是本片最容易写错的一条**，它有三步且顺序有意义：
     * ① 用 `countBranches(parentMessageId)` 定新消息的 `branchIndex`（假 DAO 返回 3 ⇒ 期望 3）；
     * ② 把现有兄弟**逐个**用 `insertMessage(copy(isSelected = false))` 落库（**不是删除**）；
     * ③ 再写新消息（`isSelected = true`）并推会话时间戳。
     *
     * 变异验证：① 把 `branchIndex = branchCount` 改成 `0` ⇒ 变红；
     * ② 删掉「取消兄弟选中」的循环 ⇒ 兄弟行断言变红（真机上表现为新旧分支同时被选中，
     * 消息在界面上重复出现）。
     */
    @Test
    fun `saveRegeneratedMessage 递增分支号并取消旧分支选中`() = runBlocking {
        val dao = FakeAiChatDao(
            branches = listOf(
                messageEntity("sibling1", parentMessageId = "m_parent", isSelected = true, branchIndex = 0),
                messageEntity("sibling2", parentMessageId = "m_parent", isSelected = false, branchIndex = 1),
            ),
            branchCountValue = 3,
        )

        val message = AiChatRepositoryImpl(dao).saveRegeneratedMessage(
            conversationId = "c1",
            role = "assistant",
            parts = listOf(AiMessagePart.Text("换个说法")),
            parentMessageId = "m_parent",
            thinkingDuration = 4,
        )

        // 三次落库：两条「取消选中」的兄弟 + 一条新消息。顺序 = 兄弟在前、新消息最后。
        assertEquals(3, dao.insertedMessages.size, "应落库 2 条被取消选中的兄弟 + 1 条新消息")

        val siblingWrites = dao.insertedMessages.dropLast(1)
        assertEquals(listOf("sibling1", "sibling2"), siblingWrites.map { it.id })
        assertTrue(siblingWrites.all { !it.isSelected }, "现有兄弟必须被逐个置为非选中")
        // 取消选中是「原地改写」：其余字段不能丢（不是新建一条空壳）。
        val first = siblingWrites.first()
        assertEquals("c1", first.conversationId)
        assertEquals(0, first.branchIndex)
        assertEquals("""[{"type":"text","text":"sibling1"}]""", first.partsJson)

        val written = dao.insertedMessages.last()
        assertTrue(written.id.startsWith("message_"))
        assertEquals(3, written.branchIndex, "branchIndex 应取 countBranches 的结果（3）")
        assertEquals(true, written.isSelected, "新分支必须是选中的那条")
        assertEquals("m_parent", written.parentMessageId)
        assertEquals(listOf("m_parent"), dao.branchQueries, "应先按 parentMessageId 取兄弟")
        assertEquals(listOf("c1" to written.createdAt), dao.touched)

        assertEquals(written.id, message.id)
        assertEquals(3, message.branchIndex)
        assertEquals(true, message.isSelected)
    }

    /**
     * ⚠️ **`selectBranch` 的第一处提前返回**：消息不存在 ⇒ 什么都不做。
     * 变异验证：把 `?: return@withContext` 改成继续往下走，会让 `getBranches` / `selectBranch`
     * 被以 null 父 id 调用（真机上会去改一批无关行）。
     */
    @Test
    fun `selectBranch 消息不存在时什么都不做`() = runBlocking {
        val dao = FakeAiChatDao(message = null)

        AiChatRepositoryImpl(dao).selectBranch("missing")

        assertTrue(dao.branchQueries.isEmpty(), "消息不存在时不应查兄弟")
        assertTrue(dao.insertedMessages.isEmpty(), "消息不存在时不应写库")
        assertTrue(dao.selectedBranches.isEmpty(), "消息不存在时不应选中任何分支")
    }

    /**
     * ⚠️ **第二处提前返回**：消息没有 `parentMessageId`（用户发的根消息）⇒ 也没有兄弟可切，
     * 直接返回。变异验证同上：去掉这一处会拿 `null` 当 parentId 去查库。
     */
    @Test
    fun `selectBranch 根消息无父节点时什么都不做`() = runBlocking {
        val dao = FakeAiChatDao(message = messageEntity("root", parentMessageId = null))

        AiChatRepositoryImpl(dao).selectBranch("root")

        assertTrue(dao.branchQueries.isEmpty(), "根消息没有兄弟可切，不应查库")
        assertTrue(dao.insertedMessages.isEmpty())
        assertTrue(dao.selectedBranches.isEmpty())
    }

    /**
     * 正常路径：先按**父消息**取兄弟、逐个取消选中，最后把**自己**置为选中。
     * ⚠️ 顺序与「用哪个 id」都要对：`getBranches` 收的是 `parentMessageId`，
     * `selectBranch`（DAO）收的是**消息自己的 id**——两者互换会改错行。
     */
    @Test
    fun `selectBranch 取消兄弟选中并选中自身`() = runBlocking {
        val dao = FakeAiChatDao(
            branches = listOf(
                messageEntity("sibling", parentMessageId = "m_parent", isSelected = true),
                messageEntity("target", parentMessageId = "m_parent", isSelected = false),
            ),
            message = messageEntity("target", parentMessageId = "m_parent", isSelected = false),
        )

        AiChatRepositoryImpl(dao).selectBranch("target")

        assertEquals(listOf("m_parent"), dao.branchQueries, "取兄弟要用 parentMessageId")
        assertEquals(listOf("target"), dao.selectedBranches, "选中要用消息自己的 id")
        // 两条兄弟（sibling 原本选中、target 原本未选中）都被原地改写成未选中；
        // 随后才由 DAO 的 selectBranch 把 target 单独置为选中（顺序不能反）。
        assertEquals(listOf("sibling", "target"), dao.insertedMessages.map { it.id })
        assertTrue(dao.insertedMessages.all { !it.isSelected }, "取消选中阶段必须把所有兄弟都置为未选中")
    }

    /**
     * ⚠️ `getBranchCounts` 要把 DAO 的 `List<BranchCount>` **聚合成 Map**（键 = parentMessageId）。
     * 变异验证：把 `associate { it.parentMessageId to it.cnt }` 写成 `associate { it.parentMessageId to 1 }`
     * 或调换成 `it.cnt to it.parentMessageId` ⇒ 变红。**不要**返回 `List`（端口签名就是 Map）。
     */
    @Test
    fun `getBranchCounts 把行聚合成父消息到条数的映射`() = runBlocking {
        val dao = FakeAiChatDao(
            branchCountRows = listOf(
                BranchCount(parentMessageId = "m1", cnt = 3),
                BranchCount(parentMessageId = "m2", cnt = 2),
            )
        )

        val counts = AiChatRepositoryImpl(dao).getBranchCounts("c1")

        assertEquals(mapOf("m1" to 3, "m2" to 2), counts)
        assertEquals(listOf("c1"), dao.branchCountQueries)
    }

    /** 没有任何分支时返回空 Map（界面据此把条数按 1 处理），不是 `null` 也不是抛异常。 */
    @Test
    fun `getBranchCounts 无分支时返回空映射`() = runBlocking {
        val dao = FakeAiChatDao(branchCountRows = emptyList())

        assertEquals(emptyMap(), AiChatRepositoryImpl(dao).getBranchCounts("c1"))
    }

    /**
     * ⚠️ 主键（`id`）由实现生成 ⇒ **两次新建必须拿到不同的 id**。
     * 变异验证：把 `Uuid.random()` 换成常量 ⇒ 变红（真机上第二次 `insert` 会 REPLACE 掉第一条，
     * 表现为「发了新消息但旧的消失」）。
     */
    @Test
    fun `生成的 id 不重复`() = runBlocking {
        val dao = FakeAiChatDao()
        val gateway = AiChatRepositoryImpl(dao)

        gateway.createConversation("a")
        gateway.createConversation("b")
        gateway.saveMessage("c1", "user", listOf(AiMessagePart.Text("x")))
        gateway.saveMessage("c1", "user", listOf(AiMessagePart.Text("y")))

        val conversationIds = dao.insertedConversations.map { it.id }
        val messageIds = dao.insertedMessages.map { it.id }

        assertEquals(2, conversationIds.distinct().size, "两次新建会话的 id 不应相同：$conversationIds")
        assertEquals(2, messageIds.distinct().size, "两条新消息的 id 不应相同：$messageIds")
        assertNotEquals(conversationIds[0], conversationIds[1])
    }

    /**
     * ⚠️ `saveMessage` 的 `parentMessageId` 必须**原样透传 `null`**（用户发的根消息）。
     * 归一化成空串会让界面把根消息也当成「可重新生成」的回复。
     */
    @Test
    fun `saveMessage 透传 parentMessageId 的 null`() = runBlocking {
        val dao = FakeAiChatDao()

        val message = AiChatRepositoryImpl(dao).saveMessage(
            conversationId = "c1",
            role = "user",
            parts = listOf(AiMessagePart.Text("第一个问题")),
        )

        assertEquals(null, dao.insertedMessages.single().parentMessageId)
        assertEquals(null, message.parentMessageId)
    }
}
