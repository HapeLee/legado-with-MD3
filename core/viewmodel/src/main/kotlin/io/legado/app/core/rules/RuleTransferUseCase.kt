package io.legado.app.core.rules

import io.legado.app.data.repository.UploadRepository
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.ImportItemWrapper
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 一个规则实体类型在导入/导出流程里需要的最小语义。
 *
 * 每个规则 Feature（标签分组、高亮标签、替换规则、TOC 规则……）的「怎么序列化、怎么判断
 * 变化、怎么落库」各不相同，但**流程本身是同一套**：读源文本 → 解析 → 与库中旧值比对分类
 * → 勾选 → 落库。所以把变化的部分收进本接口，把不变的流程收进 [RuleTransferUseCase]。
 *
 * 与 `BaseRuleViewModel` 的区别：这里只描述**实体语义**，不携带任何 UI 状态、列表、选择、
 * 搜索或 Android 类型，因此可以脱离 `AndroidViewModel` 被组合进任意 ViewModel。
 */
interface RuleEntitySpec<Entity> {

    /** 导出/上传时把实体列表序列化为 JSON 文本。 */
    suspend fun generateJson(entities: List<Entity>): String

    /** 解析导入文本为实体列表；解析不了必须抛异常（调用方会转成 `Error` 状态）。 */
    fun parseImportRules(text: String): List<Entity>

    /** 判断导入的同 id 实体相对库中旧值是否发生变化（决定分类是 `Update` 还是 `Existing`）。 */
    fun hasChanged(newRule: Entity, oldRule: Entity): Boolean

    /** 按 id 取库中旧实体；不存在返回 null。 */
    suspend fun findOldRule(newRule: Entity): Entity?

    /**
     * 把用户勾选的实体落库。
     *
     * 实现自行决定后续动作（例如标签分组规则落库后要重新应用到所有书）——这些动作必须在
     * 本方法返回前完成，因为调用方紧接着会把导入状态置回 `Idle`。
     */
    suspend fun persist(entities: List<Entity>)
}

/**
 * 规则导入/导出/上传的**无 UI 编排**。
 *
 * 从 `BaseRuleViewModel` 里抽出来（M1-3b）：原基类同时承担三件事——Android `ViewModel` 生命周期、
 * 列表/搜索/选择状态、导入导出流程。前两件是 UI 关注点，只有第三件是真正可共享的编排。抽成本类后：
 * - 编排可以脱离 Android 与 Compose 单独测试（`RuleTransferUseCaseTest`）；
 * - 需要它的 ViewModel 不再必须继承一个吃 `Application` 的基类（`BaseRuleViewModel` 因此可以
 *   逐 Feature 退役，而不是被复制成第二套基类）。
 *
 * 包位置：原计划与 `BaseRuleViewModel` 同住 `io.legado.app.base.rules`，但那会让引用它的
 * Feature 新增 `import io.legado.app.base.**` 计数——`checkLegacyArchitecture` 的 `legacyBase`
 * 棘轮只降不升、新区域必须为零。搬进 `io.legado.app.core.rules` 后，`tagrules` 的 base 依赖为 0。
 *
 * **并发与线程语义与迁移前逐字一致**（换实现即改行为）：
 * - 导出：`Dispatchers.IO`，空选择只发提示不写；
 * - 上传：默认调度器上序列化，`uploadRepository` 为空时**静默返回**；
 * - 导入：`Dispatchers.IO` 读源 + 解析 + 查库分类，失败进 `Error(localizedMessage)`；
 * - 落库：`Dispatchers.IO` 落库，回主线程置 `Idle`；
 * - 事件走会合 [Channel]（不是 `SharedFlow`）：没有收集者时发送方会挂起，破坏「先起 collector」
 *   这一 UI 契约——原基类同样如此。
 */
class RuleTransferUseCase<Entity>(
    private val scope: CoroutineScope,
    private val spec: RuleEntitySpec<Entity>,
    private val transferPlatform: RuleTransferPlatform,
    private val uploadRepository: UploadRepository? = null,
) {

    private val _importState = MutableStateFlow<BaseImportUiState<Entity>>(BaseImportUiState.Idle)
    val importState: StateFlow<BaseImportUiState<Entity>> = _importState.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _events = Channel<RuleTransferEvent>()
    val events: Flow<RuleTransferEvent> = _events.receiveAsFlow()

    /**
     * 把 [entities] 序列化后写入 [targetUri]。
     *
     * [entities] 必须已经按用户选择过滤好——过滤属于列表状态，不是本类的职责。
     */
    fun export(targetUri: String, entities: List<Entity>) {
        scope.launch(Dispatchers.IO) {
            try {
                if (entities.isEmpty()) {
                    _events.send(RuleTransferEvent.ShowSnackbar("没有选中的规则可导出"))
                    return@launch
                }

                val json = spec.generateJson(entities)

                transferPlatform.writeExport(targetUri, json)
                _events.send(RuleTransferEvent.ShowSnackbar("导出成功"))
            } catch (e: Exception) {
                e.printStackTrace()
                _events.send(RuleTransferEvent.ShowSnackbar("导出失败: ${e.localizedMessage}"))
            }
        }
    }

    /**
     * 把 [entities] 上传为分享链接。未配置上传能力时静默返回（与迁移前一致）。
     */
    fun upload(entities: List<Entity>) {
        val repo = uploadRepository ?: return
        scope.launch {
            if (entities.isEmpty()) return@launch

            _isUploading.value = true
            try {
                val json = withContext(Dispatchers.Default) { spec.generateJson(entities) }

                val url = repo.upload(
                    fileName = "export_rules.json",
                    file = json,
                    contentType = "application/json"
                )

                _events.send(
                    RuleTransferEvent.ShowSnackbar(
                        message = "上传成功: $url",
                        actionLabel = "复制链接",
                        url = url
                    )
                )
            } catch (e: Exception) {
                _events.send(
                    RuleTransferEvent.ShowSnackbar(
                        message = "上传失败: ${e.localizedMessage}"
                    )
                )
            } finally {
                _isUploading.value = false
            }
        }
    }

    /**
     * 读入 [text]（本地文本 / URL / URI 由 [RuleTransferPlatform] 决定）并分类为导入条目。
     */
    fun importSource(text: String) {
        _importState.value = BaseImportUiState.Loading

        scope.launch(Dispatchers.IO) {
            runCatching {
                val jsonText = transferPlatform.readImportSource(text.trim())
                val rules = spec.parseImportRules(jsonText)
                val wrappers = rules.map { newRule ->
                    val oldRule = spec.findOldRule(newRule)

                    val status = when {
                        oldRule == null -> ImportStatus.New
                        spec.hasChanged(newRule, oldRule) -> ImportStatus.Update
                        else -> ImportStatus.Existing
                    }

                    ImportItemWrapper(
                        data = newRule,
                        oldData = oldRule,
                        status = status,
                        isSelected = status != ImportStatus.Existing
                    )
                }

                _importState.value = BaseImportUiState.Success(
                    source = text,
                    items = wrappers
                )
            }.onFailure {
                it.printStackTrace()
                _importState.value = BaseImportUiState.Error(it.localizedMessage ?: "Unknown Error")
            }
        }
    }

    fun cancelImport() {
        _importState.value = BaseImportUiState.Idle
    }

    fun toggleImportSelection(index: Int) {
        val currentState = _importState.value as? BaseImportUiState.Success<Entity> ?: return
        val newItems = currentState.items.toMutableList()
        val item = newItems[index]
        newItems[index] = item.copy(isSelected = !item.isSelected)
        _importState.value = currentState.copy(items = newItems)
    }

    fun toggleImportAll(isSelected: Boolean) {
        val currentState = _importState.value as? BaseImportUiState.Success<Entity> ?: return
        val newItems = currentState.items.map { it.copy(isSelected = isSelected) }
        _importState.value = currentState.copy(items = newItems)
    }

    fun updateImportItem(index: Int, data: Entity) {
        val currentState = _importState.value as? BaseImportUiState.Success<Entity> ?: return
        if (index !in currentState.items.indices) return
        val newItems = currentState.items.toMutableList()
        newItems[index] = newItems[index].copy(data = data)
        _importState.value = currentState.copy(
            items = newItems,
            version = currentState.version + 1
        )
    }

    /** 落库勾选条目，完成后把导入状态置回 `Idle`。 */
    fun saveImportedRules() {
        val state = _importState.value as? BaseImportUiState.Success<Entity> ?: return
        scope.launch(Dispatchers.IO) {
            val rulesToSave = state.items
                .filter { it.isSelected }
                .map { it.data }
            spec.persist(rulesToSave)
            withContext(Dispatchers.Main) {
                _importState.value = BaseImportUiState.Idle
            }
        }
    }
}
