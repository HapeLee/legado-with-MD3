package io.legado.app.core.platform

/**
 * 应用日志后端使用的 logger 名称。
 *
 * `:app` 的 `LogUtils.init(context)` 会在**这个名字**对应的 `java.util.logging.Logger`
 * 上挂 `AsyncFileHandler`（落盘），`:app` 的 `LogUtils.d/e` 也写同一个 logger。
 * 因此共享层往同名 logger 写日志就能复用宿主已经配置好的输出通道，
 * 两端必须共用这一份常量而不是各自硬编码字符串——见 `LogUtils.logger`。
 */
const val APP_LOGGER_NAME = "Legado"

/**
 * 应用日志后端原语（M2-4a）。
 *
 * 背景：`BaseSource`（`:core:data` 的 commonMain）要从共享层记录日志，而日志框架的宿主是
 * `:app`（`LogUtils` + `java.util.logging` 的 `FileHandler`）。共享层不能反向依赖 `:app`，
 * 于是只把「往宿主日志后端写一条」这件平台无能为力的事做成原语；环形缓冲、`recordLog`
 * 门控、日志条目结构等语义都留在共享层的 [AppLogStore]。
 *
 * **为什么是 expect/actual 而不是 interface + DI**：消费方是 Room 构造的 entity 实例方法
 * （`BaseSource` 的 `getLoginInfo`/`putLoginInfo`/请求头规则求值），进不了 DI 图（M2 纪律）；
 * 而实现只需要「一个 logger 名字 + 一次 `Logger.log`」，是纯配置而非活对象服务。
 * androidMain / desktopMain 都是 JVM，两份 actual 相同（先例 [RuleDataStorage]、[SymmetricCrypto]）。
 */
expect object PlatformLog {

    /**
     * 写一条 INFO 级日志。语义与 `:app` 的 `LogUtils.d(tag, msg)` 逐字节一致
     * （输出 `"$tag $msg"`），因此与 `LogUtils` 写出的行在同一个日志文件里无法区分——
     * 这正是迁移前的行为。
     */
    fun info(tag: String, msg: String)
}
