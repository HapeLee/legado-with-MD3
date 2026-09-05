package io.legado.app.constant

/**
 * 页眉/页脚每个位置可以显示的信息类型。
 *
 * 这批值是编译期常量、不是配置状态——它们过去挂在 `ReadBookConfig` 上，导致
 * 「渲染层还直读多少配置」这类统计和护栏没法写：`PageView` 里 70 处 `ReadBookConfig.`
 * 有 41 处其实只是引用这些常量。
 *
 * 真正的配置项是「哪个位置放哪个类型」，即 `ReadBookConfig.tipHeaderLeft` 等六项，
 * 它们的取值来自这里。
 */
@Suppress("ConstPropertyName")
object ReadTipType {
    const val tipNone = 0
    const val tipChapterTitle = 1
    const val tipTime = 2
    const val tipBattery = 3
    const val tipPage = 4
    const val tipTotalProgress = 5
    const val tipPageAndTotal = 6
    const val tipBookName = 7
    const val tipTimeBattery = 8
    const val tipTimeBatteryPercentage = 9
    const val tipBatteryPercentage = 10
    const val tipTotalProgress1 = 11
    const val tipChapterTitleArrow = 12
    const val tipBatteryInside = 13
    const val tipBatteryIcon = 14
    const val tipBatteryClassic = 15
    const val tipTimeBatteryClassic = 16
    const val tipChapterTitleArrowClassic = 17
    const val tipCustom = 18
    const val tipWholeBookPage = 19
    const val tipWholeBookPageAndProgress = 20
}
