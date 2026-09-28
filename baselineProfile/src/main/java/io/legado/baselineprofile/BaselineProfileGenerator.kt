package io.legado.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * 生成 baseline profile。
 *
 * 关键：journey 必须真正打开一本书进入阅读器，否则开书热路径（Compose 组合、正文排版、
 * Canvas 绘制）不会被采样进 profile，release 首次开书只能主线程内联 JIT，产生明显加载卡顿。
 *
 * ## 为什么这里只有一个完整 journey
 * 官方建议拆出「只有冷启动到书架可见」的用例（includeInStartupProfile = true 只给它），
 * 本工程试过，**失败**：androidx benchmark 报
 * `Generated Profile is empty, before filtering`（`BaselineProfiles.kt` 结尾的
 * `check(!lastProfile.isNullOrBlank())`）。原因（`androidx.benchmark:benchmark-macro:1.4.1`）：
 *
 * 1. `BaselineProfiles.collect` 每轮都是「先按 `Partial(Disable, warmupIterations=1)` 编译，
 *    再 `extractProfile()`（`pm dump-profiles`）」，见 `BaselineProfiles.kt` 的循环；
 * 2. `CompilationMode.Partial.compileImpl` 在「编译」这一步里**会跑一遍 journey 本身**
 *    （`repeat(warmupIterations) { warmupBlock() }`），然后 `killProcess()` 刷 profile，
 *    紧接着 `cmd package compile -m speed-profile` 把刚采集到的热点编译掉；
 * 3. 于是取 profile 时，短 journey 采集到的东西**已经被这一轮自己的 speed-profile 编译消耗**，
 *    剩下的为空。（所以传 `maxIterations = 1` 没有用——顺序就在第 1 轮内部，已实测。）
 *
 * 与此无关的因素（都已排除）：设备是否解锁、是否调用 `reportFullyDrawn`。
 *
 * ## 一次 collect 只写一个文件
 * `BaselineProfiles.kt#reportResults`：`includeInStartupProfile = true` 写
 * `<prefix>-startup-prof.txt`，false 写 `<prefix>-baseline-prof.txt`；而官方 javadoc 写明
 * 「startup profile 里的方法同样会用于 baseline profile」。所以像现在这样只用 `true` 跑完整
 * journey，AGP 合并后两个文件会字节相同——这就是当前两份产物一模一样的原因。
 *
 * 代价：startup profile 偏大（会被整体 AOT 编译，成本落在安装/首次启动）。若以后要再拆，
 * 请先在上面的前提下找到能让短 journey 留下非空 profile 的办法（例如明显加长启动段），
 * 并保证重跑后 `startup-prof.txt` 非空且明显小于 `baseline-prof.txt`。
 *
 * 验收：生成后必须先确认 baseline profile 里真有阅读器条目，否则等于只收集了启动态：
 * ```
 * (Select-String -Path app/src/appRelease/generated/baselineProfiles/baseline-prof.txt `
 *     -Pattern 'io/legado/app/ui/book/read/[A-Za-z0-9_$]+' -AllMatches).Matches.Value |
 *     Group-Object | Sort-Object Count -Descending | Select-Object -First 10
 * ```
 * 真正达标时 `ReadBookController` / `ReadBookViewModel` / `ReaderPaginator` 都应有条目；
 * 只有个位数条目就说明 journey 没进阅读器。（release 变体经过 R8，类名可能被混淆，
 * 未混淆构建上这段抽样最直观。）
 *
 * 运行前提：
 * - 目标设备书架里至少有一本书（本仓库用连接的真机生成，见 useConnectedDevices）；
 * - **解锁屏幕并保持常亮**：锁屏时 `startActivityAndWait` 之后 app 不会到前台，门禁会超时失败。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = packageName(),
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            waitForBookshelf()
            openFirstBook()

            // 某些入口先到详情页；以真实正文画布为门禁，不让空/占位阅读页冒充成功。
            if (!device.wait(Until.hasObject(READER_CONTENT), 5_000)) {
                val readButton =
                    checkNotNull(device.wait(Until.findObject(By.text("阅读")), 5_000)) {
                        "Neither reader content nor the book-detail read button appeared"
                    }
                readButton.click()
            }
            check(device.wait(Until.hasObject(READER_CONTENT), 15_000)) {
                "Reader content did not appear; refusing to generate an empty baseline profile"
            }

            // 等阅读器首屏排版完成。
            Thread.sleep(3500)

            // 翻几页，顺带采样相邻章排版与翻页绘制。
            val w = device.displayWidth
            val h = device.displayHeight
            repeat(4) {
                device.swipe((w * 0.85f).toInt(), h / 2, (w * 0.15f).toInt(), h / 2, 8)
                Thread.sleep(900)
            }

            device.pressBack()
            Thread.sleep(1500)
        }
    }

    private fun MacrobenchmarkScope.waitForBookshelf() {
        check(device.wait(Until.hasObject(BOOKSHELF_LIST), 10_000)) {
            "Bookshelf did not appear; cannot collect a baseline profile " +
                    "(设备是否已解锁并保持亮屏？锁屏下 app 不会到前台)"
        }
    }

    /**
     * 打开书架里的第一本书。
     *
     * 注意：开书前不要滑动书架——下滑会让顶栏展开、把书目挤下去，位置漂移点不中。
     * 书目是 Compose 语义节点，uiautomator 中 clickable=false，UiObject2.click() 不可靠，
     * 因此用「书特有的进度描述」（未读/已读/第N章）精确定位书节点，取其 bounds 中心，
     * 用原始坐标点击（等价 input tap，最稳）。找不到书时直接失败，避免生成空 profile。
     */
    private fun MacrobenchmarkScope.openFirstBook() {
        val bookMatcher = By.desc(Pattern.compile(".*(未读|已读|读到|第.{1,8}章).*"))
        val bookNode = checkNotNull(device.wait(Until.findObject(bookMatcher), 5_000)) {
            "No visible book with a progress description; cannot collect a reader profile"
        }
        val bounds = bookNode.visibleBounds
        device.click(bounds.centerX(), bounds.centerY())
    }

    private fun packageName(): String =
        InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "io.legato.kazusa"

    private companion object {
        val BOOKSHELF_LIST: BySelector = By.res("bookshelf_list")
        val READER_CONTENT: BySelector = By.res("reader_content")
    }
}
