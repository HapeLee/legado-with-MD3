package io.legado.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * 生成 baseline profile。
 *
 * 关键：journey 必须真正打开一本书进入阅读器，否则开书热路径（Compose 组合、
 * ChapterProvider/TextChapterLayout 排版、ReadView 绘制）不会被采样进 profile，
 * release 首次开书只能主线程内联 JIT，产生明显加载卡顿。
 *
 * 运行前提：目标设备书架里至少有一本书（本仓库用连接的真机生成，见 useConnectedDevices）。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "io.legado.app"

        rule.collect(
            packageName = packageName,
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()

            // 等书架加载完成。
            val bookshelfSelector = By.desc("bookshelf_list")
            device.wait(Until.hasObject(bookshelfSelector), 10000)
            device.waitForIdle()
            Thread.sleep(2000)

            val w = device.displayWidth
            val h = device.displayHeight

            // 打开第一本书进入阅读器。
            // 注意：开书前不要滑动书架——下滑会让顶栏展开、把书目挤下去，位置漂移点不中。
            // 书目是 Compose 语义节点，uiautomator 中 clickable=false，UiObject2.click() 不可靠，
            // 因此用「书特有的进度描述」（未读/已读/第N章）精确定位书节点，取其 bounds 中心，
            // 用原始坐标点击（等价 input tap，最稳）。找不到时退回已验证的左上角书位坐标。
            val bookMatcher = By.desc(Pattern.compile(".*(未读|已读|读到|第.{1,8}章).*"))
            val bookNode = device.wait(Until.findObject(bookMatcher), 5000)
            if (bookNode != null) {
                val b = bookNode.visibleBounds
                device.click(b.centerX(), b.centerY())
            } else {
                device.click((w * 0.18f).toInt(), (h * 0.37f).toInt())
            }
            device.waitForIdle()
            Thread.sleep(3000)

            // 若点书进的是书籍详情页，点“阅读”FAB 进入阅读器；已直接进阅读器则为 null，跳过。
            device.wait(Until.findObject(By.text("阅读")), 2000)?.click()
            device.waitForIdle()

            // 等阅读器首屏排版完成。
            Thread.sleep(3500)

            // 翻几页，顺带采样相邻章排版与翻页绘制。
            repeat(4) {
                device.swipe((w * 0.85f).toInt(), h / 2, (w * 0.15f).toInt(), h / 2, 8)
                Thread.sleep(900)
            }

            device.pressBack()
            Thread.sleep(1500)
        }
    }
}
