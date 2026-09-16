package io.legado.app.domain.ai

import io.legado.app.domain.model.AiMessagePart
import kotlinx.coroutines.flow.Flow

/**
 * AI 会话的**端口**（M4-4）。收发的是领域模型 [AiChatConversation] / [AiChatMessage]。
 *
 * 由 `:data:ai` 的 `AiChatRepositoryImpl` 实现：过渡期它直接持有 `:core:data` 的 `AiChatDao`
 * （Room 的 DAO/实体仍归 `:core:data`），把方法逐条委派给 DAO 并在边界上做实体 ↔ 领域模型 的映射。
 * 等 `data:database`（Room 唯一 owner）拆出来，实现只需换 DAO 来源，本接口与调用方不动。
 *
 * ⚠️ **本接口是搬迁，不是新造**：迁移前叫 `:core:data` 的
 * `io.legado.app.domain.gateway.AiChatGateway`（同名，只是收发的是 Room 实体）。名字沿用而不改名，
 * 理由与 M4-1 / M4-2 / M4-3 一致：本仓 `domain/gateway` 下有 60+ 个同形态的既有契约，改名会把
 * 「既有契约下沉」变成一次无收益的 rename churn。旧文件随本片删除，**不留门面、不留 typealias**。
 *
 * ⚠️ **本域一个端口装两个实体**（会话 + 消息），这与前几片「一实体一域」不同——因为
 * 迁移前的 `AiChatGateway` 本来就是**一个**契约、对应**一个** DAO（`AiChatDao`）和**一个**仓储。
 * 这里不按实体拆成两个端口：`saveMessage` / `saveRegeneratedMessage` 在写消息之后还要
 * `touchConversation` 更新会话的 `updatedAt`，`deleteConversation` 要级联删消息——拆开会立刻需要
 * 互相调用，是人为耦合。判据仍是「既有契约怎么切就怎么搬」。
 *
 * ⚠️ **方法数 13 → 11**：迁移前的 `observeMessages` 与 `getBranches` 全仓**零调用方**，
 * 随片不进端口（同 M4-1 删 `savePreset`、M4-2 一次删 5 个）。
 * - `observeMessages`：UI 只用 [observeSelectedMessages]（只显示选中分支），未选中的整棵分支树
 *   从不直接观察 ⇒ DAO 方法 `AiChatDao.observeMessages` 留在 DAO 上不动（`data:database` 债务）。
 * - `getBranches`：作为**端口方法**零调用方，但**实现内部要用**——`saveRegeneratedMessage` 与
 *   `selectBranch` 都要先取出同一 `parentMessageId` 下的兄弟消息、把它们逐个改成未选中。
 *   所以只删端口方法，`AiChatDao.getBranches` 照旧被实现调用。
 *
 * ⚠️ **映射发生在流内**（[observeConversations] / [observeSelectedMessages] 都是 `Flow`）：
 * 实现必须 `dao.observeX().map { it.toDomainList() }`，每次发射都转一次，不能透传 DAO 的实体流
 * （类型系统会拒绝，但更该记住的是「映射要在流内」）。这是 M4-3 立的规矩。
 *
 * ⚠️ [saveMessage] / [saveRegeneratedMessage] 收的是 `List<AiMessagePart>`（**入参**）而不是
 * 已编码的 JSON：编码是**实现**的职责（`AiMessagePartJson.encode`），调用方不该关心存储形态。
 * 落库后 [AiChatMessage.partsJson] 存的是编码结果——这是既有的存储契约，本片不改。
 */
interface AiChatGateway {

    /**
     * 观察全部会话，按 `updatedAt` 倒序（最近活跃的在前）。DAO 的 `ORDER BY updatedAt DESC`
     * 就是这个语义。
     */
    fun observeConversations(): Flow<List<AiChatConversation>>

    /**
     * 观察某会话**选中分支**的消息，按 `createdAt` 正序。
     *
     * ⚠️ 只返回 `isSelected = 1` 的消息：界面上展示的是一条分支路径，而不是整棵分支树。
     * 未选中的兄弟消息只有 [getBranchCounts] 体现出来的「第几条 / 共几条」。
     */
    fun observeSelectedMessages(conversationId: String): Flow<List<AiChatMessage>>

    /** 取单条会话；不存在返回 `null`（调用方据此决定是否 [createConversation]）。 */
    suspend fun getConversation(id: String): AiChatConversation?

    /**
     * 新建会话并落库：`id` 由实现生成，`createdAt` / `updatedAt` 取当前时间。
     * [title] 的默认值 `"New Chat"` 沿用迁移前的签名默认值。
     */
    suspend fun createConversation(title: String = "New Chat"): AiChatConversation

    /**
     * 追加一条消息并落库，同时把所属会话的 `updatedAt` 推到当前时间（会话列表因此重新排序）。
     *
     * [parentMessageId] 为 `null` 表示这是用户发的**根消息**；非空表示这是对某条消息的**回复**
     * （可被 [saveRegeneratedMessage] 重新生成）。
     */
    suspend fun saveMessage(
        conversationId: String,
        role: String,
        parts: List<AiMessagePart>,
        parentMessageId: String? = null,
        thinkingDuration: Int = 0,
    ): AiChatMessage

    /**
     * 重新生成：在同一个 [parentMessageId] 下**新增一条分支**，并把原有的兄弟分支全部置为非选中。
     *
     * ⚠️ 与 [saveMessage] 的两点差异，都是既有行为、必须照抄：
     * ① 新消息的 `branchIndex` = `countBranches(parentMessageId)`（从 0 起的下一条）；
     * ② 写入前先把现有兄弟消息逐个 `copy(isSelected = false)` 落库——所以「新增一条」旧分支**不删**，
     * 只是不再被选中，界面上的「1/3」计数正是靠这些留下的行算出来的。
     */
    suspend fun saveRegeneratedMessage(
        conversationId: String,
        role: String,
        parts: List<AiMessagePart>,
        parentMessageId: String,
        thinkingDuration: Int = 0,
    ): AiChatMessage

    /**
     * 切换到指定分支：把它的兄弟全部置为非选中，再把它置为选中。
     *
     * ⚠️ 两处提前返回都是既有行为：消息不存在 ⇒ 什么都不做；消息没有 `parentMessageId`
     * （即根消息）⇒ 也什么都不做（根消息没有兄弟可切）。
     */
    suspend fun selectBranch(messageId: String)

    /**
     * 某会话里「每个父消息下有几条分支」，键是 `parentMessageId`。
     *
     * ⚠️ 只包含**有父消息**的消息（DAO 里 `parentMessageId IS NOT NULL`），所以调用方取不到键时
     * 要按 1 处理（只有一条，就没有分支切换可言）。
     */
    suspend fun getBranchCounts(conversationId: String): Map<String, Int>

    /** 重命名会话；同时把 `updatedAt` 推到当前时间。 */
    suspend fun updateConversationTitle(conversationId: String, title: String)

    /** 更新会话的推理等级（存的是小写枚举名，见 [AiChatConversation.reasoningLevel]）；同时推 `updatedAt`。 */
    suspend fun updateReasoningLevel(conversationId: String, reasoningLevel: String)

    /** 删除会话**并级联删除它的全部消息**（两条 DAO 调用，顺序照抄迁移前）。 */
    suspend fun deleteConversation(conversationId: String)
}
