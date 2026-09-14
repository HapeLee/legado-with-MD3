package io.legado.app.core.platform

/**
 * 扩展名 → MIME 类型的推断契约（M1-3n）。
 *
 * 为什么需要它：`widget/components/filePicker/FilePickerSheet` 要把调用方给的扩展名列表
 * （`arrayOf("json")` / `arrayOf("json", "txt")`）翻译成系统文件选择器要的 MIME 数组，
 * 原先直接调 `android.webkit.MimeTypeMap`——那是 Android 独有的类，把这个组件钉死在
 * `:core:ui`（与它一起进 `:core:designsystem/commonMain` 的是 R 资源，见 M1-3j 配方）。
 *
 * 契约只暴露一个**纯查询**：不需要 `Context`，实现也不应做 I/O 或缓存。
 * Android 侧由 `io.legado.app.platform.AndroidPlatformCapabilities.mimeTypeResolver()`
 * 委托 `MimeTypeMap.getSingleton().getMimeTypeFromExtension(...)`。
 *
 * ⚠️ **未注入时的语义**与 `DynamicColorSchemes` 同类，而**不是** [ClipboardProvider] 那种抛异常：
 * 「这个平台没有 MIME 表」是**正常状态**——desktop / iOS 没有系统文件选择器，也就没有这张表。
 * [MimeTypeResolverProvider.current] 会回落到「一律返回 `null`」的实现，而调用方本就把 `null`
 * 翻译成 `application/octet-stream`，这正是 Android 上「未知扩展名」走的同一条路径
 * （`MimeTypeMap` 查不到时也返回 `null`）。要求所有非 Android 宿主都注入一个「永不命中」的
 * 实现才肯编译，是仪式而不是约束。
 */
fun interface MimeTypeResolver {

    /**
     * @param extension 不带点的扩展名（如 `json`、`txt`）。调用方目前传的是小写字面量，
     *   但契约不保证规范化——实现应像 `MimeTypeMap` 一样大小写不敏感。
     * @return 对应 MIME 类型；无法判定时返回 `null`，由调用方回落 `application/octet-stream`。
     */
    fun mimeTypeOf(extension: String): String?
}

/**
 * [MimeTypeResolver] 的注入点。模式同 [ClipboardProvider] / [KeyValueStoreProvider]。
 *
 * 安装点在 `io.legado.app.help.PlatformServices.install()`（由 `App.onCreate` 调用）。
 */
object MimeTypeResolverProvider {

    /** 未注入时的兜底：一律「无法判定」。 */
    private val UNKNOWN_ONLY = MimeTypeResolver { null }

    @Volatile
    private var delegate: MimeTypeResolver? = null

    fun install(resolver: MimeTypeResolver) {
        delegate = resolver
    }

    fun uninstall() {
        delegate = null
    }

    val isInstalled: Boolean get() = delegate != null

    /**
     * 永不返回 `null`：未注入时是「一律 `null` 结果」的解析器，见接口注释。
     * 调用方因此不需要写 `?.`，也不会在非 Android 宿主上崩。
     */
    val current: MimeTypeResolver get() = delegate ?: UNKNOWN_ONLY
}
