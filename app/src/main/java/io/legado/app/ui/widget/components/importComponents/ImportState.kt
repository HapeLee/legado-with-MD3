package io.legado.app.ui.widget.components.importComponents

// 单个条目的状态包装
data class ImportItemWrapper<T>(
    val data: T,// 具体的数据对象 (ReplaceRule, BookSource, etc.)
    val oldData: T? = null,
    val isSelected: Boolean = true,
    val status: ImportStatus = ImportStatus.New // 用于UI显示颜色
)

// 导入状态枚举
enum class ImportStatus {
    New,      // 新增 绿
    Update,   // 更新 黄
    Existing, // 已有 灰
    Error     // 错误 红
}

// 整个导入流程的 UI State
sealed interface BaseImportUiState<out T> {
    data object Idle : BaseImportUiState<Nothing>
    data object Loading : BaseImportUiState<Nothing>
    data class Error(val msg: String) : BaseImportUiState<Nothing>
    data class Success<T>(
        val source: String,
        val items: List<ImportItemWrapper<T>>,
        // 导入配置项
        val keepOriginalName: Boolean = false,
        val customGroup: String? = null,
        val isAddGroup: Boolean = false
    ) : BaseImportUiState<T>
}