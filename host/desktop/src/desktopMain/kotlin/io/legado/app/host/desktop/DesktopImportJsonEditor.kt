package io.legado.app.host.desktop

import io.legado.app.core.platform.ImportJsonEditor
import io.legado.app.core.platform.ImportJsonField

/**
 * [ImportJsonEditor] 的 desktop 实现：**显式不可用**。
 *
 * Android 实现是 `:core:data` 的 `GsonImportJsonEditor`——它靠 Gson 的 JSON 树模型
 * （`JsonObject.entrySet()` / `JsonPrimitive` / `JsonParser`）拆字段与回写，而 Gson 门面
 * （`io.legado.app.utils.GSON`）只存在于 `:core:data/androidMain`。
 *
 * 用 kotlinx.serialization 复刻一份并不难，但**字段顺序、数值回写规则、`prettyPrinting` 文本**
 * 必须与 Gson 版逐分支等价才算「同一能力」——那是需要独立 characterization 证据的切片。
 * M2-1 只拆全局 Provider，不顺手换实现，所以这里按 AGENTS.md「平台能力不可用时必须显式建模
 * 为 capability/unsupported，不得用静默空实现伪造跨平台支持」抛异常。
 *
 * 可达性：desktop 的导入流程本身就走不通（`DesktopRuleTransferPlatform.readImportSource`
 * 对 URL/URI 分支抛异常、文件选择器不可用），`importState` 因此不会进入 `Success`，
 * 编辑面板在 desktop 上不可达；一旦将来打通导入，这里会是第一个需要补的实现。
 */
object DesktopImportJsonEditor : ImportJsonEditor {

    override fun fieldsOf(data: Any?): List<ImportJsonField>? = unsupported("fieldsOf")

    override fun <T : Any> withEditedText(data: T?, fieldName: String, text: String): T? =
        unsupported("withEditedText")

    override fun <T : Any> withBoolean(data: T?, fieldName: String, value: Boolean): T? =
        unsupported("withBoolean")

    private fun unsupported(method: String): Nothing = throw UnsupportedOperationException(
        "desktop host 未实现导入对象的按字段编辑（ImportJsonEditor.$method）：" +
            "Android 侧实现依赖 GSON 门面，desktop 复刻需要先建立字段顺序与回写的等价证据。"
    )
}
