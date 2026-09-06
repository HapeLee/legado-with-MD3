package io.legado.app.core.platform

import kotlin.reflect.KClass

/**
 * JSON 编解码契约（D7）。
 *
 * 取代 app 侧 `utils.GSON` 的核心用法，让 commonMain 的值对象（entity/analyzeRule 等）
 * 能序列化/反序列化而不依赖 JVM-only 的 Gson。
 *
 * 字节级对齐约束：`toJson` 的输出必须与 Gson(`setPrettyPrinting` + `disableHtmlEscaping`)
 * 一致。`CustomUrl.toString` / `Book.origin` 等结果会落库并参与字节等值查询，
 * 格式漂移会导致老库书籍查不中而重复建书。actual 委托 GSON 即可天然保证。
 *
 * 这是 expect/actual 而非 interface + DI：JSON 引擎是平台原语（JVM 有 Gson），
 * 符合 AGENTS.md「expect/actual 只用于平台原语」纪律。
 */
expect object JsonCodec {

    /** 序列化任意对象为 JSON（对齐 `GSON.toJson(obj)`）。 */
    fun toJson(obj: Any?): String

    /**
     * 反序列化为具体类（对齐 `GSON.fromJson(json, clazz)`）。
     *
     * 注意：泛型集合（`Map`/`List` 带类型参数）不能用 [KClass] 表达擦除后的完整类型，
     * 请改用 [decodeStringMap] / [decodeAnyMap]。
     */
    fun <T : Any> fromJsonObject(json: String?, clazz: KClass<T>): T?

    /** 反序列化 `Map<String, String>`（对齐 `GSON.fromJsonObject<HashMap<String, String>>`）。 */
    fun decodeStringMap(json: String?): Map<String, String>?

    /**
     * 反序列化 `Map<String, Any>`（对齐 `GSON.fromJsonObject<Map<String, Any>>`）。
     *
     * 保留 Gson `MapDeserializerDoubleAsIntFix` 语义：整数解析为 Long、非整数为 Double。
     */
    fun decodeAnyMap(json: String?): Map<String, Any>?
}
