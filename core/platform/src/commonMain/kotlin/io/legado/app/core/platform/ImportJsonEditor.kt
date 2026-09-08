package io.legado.app.core.platform

/**
 * 导入对象的「按字段编辑」能力契约（P4 Stage B 最后一刀）。
 *
 * 背景：`:core:ui` 的 `importComponents/ImportComponents.kt` 需要把任意导入对象拆成可编辑字段
 * （布尔→开关、对象/数组→多行 JSON、其余→单行文本），原先直接使用 `com.google.gson` 的
 * **JSON 树模型**（`JsonObject.entrySet()` / `JsonPrimitive` / `JsonParser`）。那会让通用 UI 组件库
 * 依赖 Gson，或者依赖 `:core:data` 才能拿到 app 配置好的 `GSON`——两条路都把 UI 层往数据层拽。
 *
 * 这里把「怎么拆字段、怎么回写」收敛成三个方法，JSON 树细节留在实现侧
 * （`:core:data/androidMain` 的 `GsonImportJsonEditor`，与 `GSON` 同模块），UI 只见值模型。
 *
 * **不是**通用 JSON 库抽象：只覆盖导入对话框「编辑」页的真实需求
 * （AGENTS.md「无调用方抽象」）。
 */
interface ImportJsonEditor {

    /**
     * 把对象拆成字段列表；**非对象**（数组/原始值/null）返回 null，UI 显示「不支持编辑」。
     *
     * 字段顺序即对象自身顺序（对齐 `JsonObject.entrySet()` 的迭代顺序）。
     */
    fun fieldsOf(data: Any?): List<ImportJsonField>?

    /**
     * 用编辑后的文本更新 [fieldName] 字段，返回新对象；**解析失败返回 null**，
     * 调用方应忽略本次编辑（与迁移前 `newText.toImportJsonElement(value)?.let { ... }` 一致）。
     */
    fun <T : Any> withEditedText(data: T?, fieldName: String, text: String): T?

    /** 用开关值更新 [fieldName] 字段，返回新对象；失败返回 null。 */
    fun <T : Any> withBoolean(data: T?, fieldName: String, value: Boolean): T?
}

/** 一个可编辑字段。 */
data class ImportJsonField(val name: String, val value: ImportFieldValue)

/**
 * 字段值模型——只表达「UI 该怎么渲染」，不暴露 JSON 树类型。
 *
 * 迁移前的渲染分支：布尔原始值 → 开关；对象/数组 → 多行 JSON 文本；JSON null → 空单行；
 * 其余原始值（字符串/数字）→ 单行文本（数字取 `asString`）。
 */
sealed interface ImportFieldValue {

    /** 布尔原始值：渲染为开关。 */
    data class Bool(val value: Boolean) : ImportFieldValue

    /** 对象或数组：渲染为多行 JSON 文本，[text] 为序列化结果。 */
    data class Json(val text: String) : ImportFieldValue

    /** 字符串/数字等原始值：渲染为单行文本。 */
    data class Text(val text: String) : ImportFieldValue

    /** JSON null：渲染为空单行。 */
    data object Null : ImportFieldValue
}

/**
 * [ImportJsonEditor] 的注入点。模式同 [ClipboardProvider]。
 */
object ImportJsonEditorProvider {

    @Volatile
    private var delegate: ImportJsonEditor? = null

    fun install(editor: ImportJsonEditor) {
        delegate = editor
    }

    fun uninstall() {
        delegate = null
    }

    val isInstalled: Boolean get() = delegate != null

    val current: ImportJsonEditor
        get() = delegate ?: error(
            "ImportJsonEditor 未安装：请在应用 composition root 调用 " +
                "ImportJsonEditorProvider.install(...) 注入平台实现。"
        )
}
