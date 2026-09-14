package io.legado.app.core.platform

/**
 * 九宫格背景图解析契约（M1-3p）。
 *
 * 为什么需要它：`widget/components/AppContainerBackground.kt` 的 `Modifier.appContainerBackground`
 * 把用户配置的容器背景图交给图像加载器（Coil）画在内容后面。其中 `.9.png` **必须先按九宫格规则
 * 解成可拉伸的 drawable**——直接按普通位图加载会把四周那 1px 的拉伸标记一起画出来。这一步在
 * Android 上是 `BitmapFactory.decodeFile` + `NinePatch.isNinePatchChunk` + `NinePatchDrawable`
 * 三件套，属平台 SDK；desktop / iOS 没有九宫格概念。原先正因为这段代码，整个
 * `AppContainerBackground.kt` 被钉死在 `:core:ui`，连带 `card/GlassCard.kt`、
 * `checkBox/CheckboxItem.kt` 都进不了共享层。
 *
 * ### 契约面：不透明 payload，而不是某个具体平台类型
 *
 * [load] 的返回值**就是图像加载器 `data` 参数的类型**（仓库里是 `coil3.ImageRequest.Builder.data`，
 * 形参即 `Any?`）——调用方把它原样交给加载器，不解释、不转型。返回类型因此不能收窄成任何
 * 平台类或 Compose 类，否则契约模块会被反向钉住（对比 M1-3o：`ClipEntry` 是 Compose 的
 * `expect class`，契约只能住在 `:core:designsystem` 而不是本模块）。
 *
 * 三种 `null` 是**同一条路径**，调用方一律回落成「按原路径加载」：
 *   1. 文件不是九宫格（`.9.png` 之外）；
 *   2. 是九宫格但解析失败（截断文件、chunk 非法）；
 *   3. 该平台没有九宫格能力（desktop / iOS，见下面的未注入语义）。
 *
 * ### 调用方负责切调度器
 *
 * 实现会做**磁盘 I/O**（`BitmapFactory.decodeFile`）。调用方在 `produceState` +
 * `withContext(Dispatchers.IO)` 里调它——搬动前后这个位置没变，实现里不要自作主张再包一层。
 *
 * ### 未注入时的语义：一律 `null`
 *
 * 与 [MimeTypeResolver] 同类，而**不是** [ClipboardProvider] 那种抛异常：「这个平台没有九宫格」
 * 是**正常状态**，不是配置错误。要求所有非 Android 宿主都注入一个「永不命中」的实现才肯编译，
 * 是仪式而不是约束。Android 侧由
 * `io.legado.app.platform.AndroidPlatformCapabilities.ninePatchLoader()` 委托
 * `BitmapFactory`/`NinePatch`/`NinePatchDrawable`，**不需要 `Context`**。
 */
fun interface NinePatchLoader {

    /**
     * @param path 图片文件路径。是否只看 `.9.png` 后缀由**实现**决定（那是 Android 的命名约定，
     *   不属于契约语义）——Android 实现逐字保留了搬动前的 `endsWith(".9.png", ignoreCase = true)`
     *   前置判断。
     * @return 可直接作为图像加载器 `data` 的本地图片源；无法给出九宫格时返回 `null`，
     *   由调用方回落成按原路径加载。
     */
    fun load(path: String): Any?
}

/**
 * [NinePatchLoader] 的注入点。模式同 [MimeTypeResolverProvider] / [ClipboardProvider]。
 *
 * 安装点在 `io.legado.app.help.PlatformServices.install()`（由 `App.onCreate` 调用）。
 */
object NinePatchLoaderProvider {

    /** 未注入时的兜底：一律「没有九宫格」。 */
    private val NO_NINE_PATCH = NinePatchLoader { null }

    @Volatile
    private var delegate: NinePatchLoader? = null

    fun install(loader: NinePatchLoader) {
        delegate = loader
    }

    fun uninstall() {
        delegate = null
    }

    val isInstalled: Boolean get() = delegate != null

    /**
     * 永不返回 `null`：未注入时是「一律 `null` 结果」的加载器，见接口注释。
     * 调用方因此不需要写 `?.`，也不会在非 Android 宿主上崩。
     */
    val current: NinePatchLoader get() = delegate ?: NO_NINE_PATCH
}
