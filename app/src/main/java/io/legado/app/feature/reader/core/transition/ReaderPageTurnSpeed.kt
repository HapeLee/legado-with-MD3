package io.legado.app.feature.reader.core.transition

import io.legado.app.feature.reader.core.transition.ReaderPageTurnSpeed.Companion.BASE_DURATION_MILLIS
import io.legado.app.feature.reader.core.transition.ReaderPageTurnSpeed.Companion.Default


/**
 * 翻页动画速度挡位。
 *
 * 覆盖 / 滑动 / 渐变 / 仿真折页 / 滚动翻屏共用同一套「按位移折算」规则，挡位只决定
 * 折算的**基准时长**，也就是一整页 / 一整屏翻页从第 0 帧走完所需的时间；三条路径的
 * policy 都以 [BASE_DURATION_MILLIS] 为默认参数。
 *
 * 挡位给的是绝对时长而不是倍率：倍率乘完再取整会在 300 / 360 这类比例上被截断
 * （`360 * 0.8333f = 299.99…`），绝对时长没有这个问题。
 *
 * 落盘值是 [ordinal]，因此**不要重排枚举顺序**——重排会改变已有设置的含义。
 */
enum class ReaderPageTurnSpeed(val baseDurationMillis: Int) {
    /** 240ms：最快，几乎瞬时完成。 */
    FASTEST(240),

    /** 300ms：比默认略快，接近旧 View `defaultAnimationSpeed = 300` 的原生手感。 */
    FAST(300),

    // 枚举项初始化的早于 companion，这里不能写 BASE_DURATION_MILLIS；
    // 两者相等由 ReaderPageTurnSpeedTest 的断言守住。
    /** 360ms：统一基准，也是默认挡位。 */
    MODERATE(360),

    /** 540ms：明显放慢，突出折页/滑动的过程感。 */
    ELEGANT(540);

    companion object {
        /**
         * 全动画共用的原始基准时长（毫秒），等于 [MODERATE] 的时长。
         *
         * 覆盖 / 滑动 / 渐变、仿真折页、滚动翻屏一律以此为默认基准；不要再按旧 View 的
         * `ReadView.defaultAnimationSpeed = 300` 另开一份，否则同一次翻页会在不同动画下快慢不一。
         */
        const val BASE_DURATION_MILLIS = 360

        val Default: ReaderPageTurnSpeed = MODERATE

        /** 越界（老配置缺字段、脏数据）回落到 [Default]。 */
        fun fromValue(value: Int): ReaderPageTurnSpeed = entries.getOrNull(value) ?: Default
    }
}
