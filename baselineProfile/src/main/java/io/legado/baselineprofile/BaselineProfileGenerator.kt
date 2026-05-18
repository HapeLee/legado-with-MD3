package io.legado.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class generates a basic startup baseline profile for the target package.
 **/
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

            val bookshelfSelector = By.desc("bookshelf_list")
            device.wait(Until.hasObject(bookshelfSelector), 10000)

            device.waitForIdle()
            Thread.sleep(2000)

            val screenWidth = device.displayWidth
            val screenHeight = device.displayHeight
            val startX = screenWidth / 2
            val startY = (screenHeight * 0.75).toInt()
            val endY = (screenHeight * 0.25).toInt()

            repeat(3) {
                device.swipe(startX, startY, startX, endY, 25)
                Thread.sleep(1000)
            }

            repeat(2) {
                device.swipe(startX, endY, startX, startY, 10)
                Thread.sleep(1000)
            }

            // "nav_my"
            /* val tabs = listOf("nav_explore", "nav_rss", "nav_bookshelf")
            for (tabDesc in tabs) {
                val tab = device.wait(Until.findObject(By.desc(tabDesc)), 5000)
                if (tab != null) {
                    val bounds = tab.visibleBounds
                    device.click(bounds.centerX(), bounds.centerY())
                    Thread.sleep(3000)
                }
            }*/
        }
    }
}