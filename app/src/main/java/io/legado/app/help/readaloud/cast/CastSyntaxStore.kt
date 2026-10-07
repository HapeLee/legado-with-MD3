package io.legado.app.help.readaloud.cast

import android.content.Context
import android.content.SharedPreferences
import io.legado.app.feature.reader.core.cast.CastSyntax
import io.legado.app.feature.reader.core.cast.CastSyntaxHolder
import org.koin.core.context.GlobalContext

/**
 * 「多角色识别」符号配置：读写 prefs 并把生效语法推给 [CastSyntaxHolder]。
 *
 * 阅读器测量/注入链路是热路径（每段都要跑正则），所以这里带内存缓存：
 * [current] 只在首次或 [save] 之后重读 prefs 并重建 [CastSyntax]。
 * UI/ViewModel 一律经本 Store，不直接碰 SharedPreferences。
 */
object CastSyntaxStore {

    private const val PREFS = "cast_prefs"
    private const val KEY_MARK_START = "castMarkStart"
    private const val KEY_MARK_END = "castMarkEnd"
    private const val KEY_POOL_START = "castPoolStart"
    private const val KEY_POOL_END = "castPoolEnd"

    /** 单个符号的最长字符数；再长只会让标记吃掉正文。 */
    const val MAX_SYMBOL_CHARS = 4

    /** 校验失败的原因，UI 据此提示。 */
    enum class ValidationError { NONE, EMPTY, TOO_LONG, DUPLICATED }

    @Volatile
    private var cached: CastSyntax? = null

    data class Config(
        val markStart: String = CastSyntax.DEFAULT_MARK_START,
        val markEnd: String = CastSyntax.DEFAULT_MARK_END,
        val poolStart: String = CastSyntax.DEFAULT_POOL_START,
        val poolEnd: String = CastSyntax.DEFAULT_POOL_END,
    )

    private fun prefs(): SharedPreferences =
        GlobalContext.get().get<Context>().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 装载并推送当前语法；已缓存时零成本。所有读取标记的入口先调它。 */
    fun current(): CastSyntax {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: run {
                val p = prefs()
                CastSyntax(
                    markStart = p.getString(KEY_MARK_START, CastSyntax.DEFAULT_MARK_START)
                        ?: CastSyntax.DEFAULT_MARK_START,
                    markEnd = p.getString(KEY_MARK_END, CastSyntax.DEFAULT_MARK_END)
                        ?: CastSyntax.DEFAULT_MARK_END,
                    poolStart = p.getString(KEY_POOL_START, CastSyntax.DEFAULT_POOL_START)
                        ?: CastSyntax.DEFAULT_POOL_START,
                    poolEnd = p.getString(KEY_POOL_END, CastSyntax.DEFAULT_POOL_END)
                        ?: CastSyntax.DEFAULT_POOL_END,
                ).also {
                    cached = it
                    CastSyntaxHolder.current = it
                }
            }
        }
    }

    /** 页面展示用的当前配置。 */
    fun config(): Config = current().let {
        Config(it.markStart, it.markEnd, it.poolStart, it.poolEnd)
    }

    /** 预览：按给定符号生成一个示例标记（不落库）。 */
    fun preview(config: Config): String =
        CastSyntax(config.markStart, config.markEnd, config.poolStart, config.poolEnd)
            .markerText("张三", "男青年")
            .orEmpty()

    fun validate(config: Config): ValidationError {
        val values = listOf(config.markStart, config.markEnd, config.poolStart, config.poolEnd)
        if (values.any { it.isBlank() }) return ValidationError.EMPTY
        if (values.any { it.length > MAX_SYMBOL_CHARS }) return ValidationError.TOO_LONG
        // 池分隔不能与包裹符号相同：`>>` 作分隔会让 markerRegex 的第二分支先吃掉收尾符号
        if (config.poolStart == config.markStart || config.poolStart == config.markEnd ||
            config.poolEnd == config.markStart || config.poolEnd == config.markEnd
        ) {
            return ValidationError.DUPLICATED
        }
        if (values.distinct().size < values.size) return ValidationError.DUPLICATED
        return ValidationError.NONE
    }

    /** 校验通过才落库并让缓存失效；非法返回错误码。 */
    fun save(config: Config): ValidationError {
        val error = validate(config)
        if (error != ValidationError.NONE) return error
        prefs().edit()
            .putString(KEY_MARK_START, config.markStart)
            .putString(KEY_MARK_END, config.markEnd)
            .putString(KEY_POOL_START, config.poolStart)
            .putString(KEY_POOL_END, config.poolEnd)
            .apply()
        cached = null
        current()
        return ValidationError.NONE
    }
}
