package io.legado.app.core.platform

/**
 * 时间源：[commonMain] 不能直接用 `java.lang.System`（JVM-only），故用 expect/actual
 * 提供系统时间原语（见 [systemTimeMillis]/[systemTimeNanos]），本接口是其可注入抽象。
 *
 * 这是 P1 第一个契约，用来跑通「接口 + expect/actual + contract test」流水线；
 * 首个真实消费方是 `:feature:reader:core` 的 `WholeBookPageCoordinator`
 * （原直接调 `System.currentTimeMillis()`/`System.nanoTime()`，属 JVM 泄漏）。
 */
interface Clock {
    /** Epoch 毫秒，等价 `System.currentTimeMillis()`。 */
    fun nowMillis(): Long

    /** 单调纳秒，等价 `System.nanoTime()`；仅用于区间测量，不是绝对时间。 */
    fun nowNanos(): Long
}

/** 系统时钟实现：委托 [systemTimeMillis]/[systemTimeNanos] 的平台 actual。 */
object SystemClock : Clock {
    override fun nowMillis(): Long = systemTimeMillis()
    override fun nowNanos(): Long = systemTimeNanos()
}
