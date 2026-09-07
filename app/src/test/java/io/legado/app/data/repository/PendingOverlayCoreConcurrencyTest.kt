package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.LabSettings
import io.legado.app.domain.model.settings.MangaSettings
import io.legado.app.help.config.PendingOverlayCore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.setPrefValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * [PendingOverlayCore]（AppConfigStore 内部状态机）的并发原子性测试。
 *
 * 该核心是 app 侧 DataStore 落盘层的实现，依赖 [Preferences] 类型，不随 settings
 * repository 下沉到 core:data。此处仅验证「并发 transform 串行、不丢更新」不变式。
 */
class PendingOverlayCoreConcurrencyTest {

    @Test
    fun `基于同一快照并发更新不同字段不丢更新`() {
        val current = LabSettings()
        val transforms: List<(LabSettings) -> LabSettings> = listOf(
            { it.copy(enabled = true) },
            { it.copy(eyeProtection = true) },
        )
        val core = PendingOverlayCore(
            initial = labPrefMap(current),
            launchWrite = {},
            persist = { _, _ -> error("不会执行落盘") },
            persistAll = { error("不会执行落盘") },
        )
        val start = CountDownLatch(1)
        val writers = transforms.map { transform ->
            thread(start = true) {
                start.await()
                core.atomicUpdate(
                    read = ::toLabSettings,
                    toPrefMap = ::labToPrefMap,
                    transform = transform,
                )
            }
        }

        start.countDown()
        writers.forEach(Thread::join)

        assertEquals(
            current.copy(enabled = true, eyeProtection = true),
            toLabSettings(core.preferencesFlow.value),
        )
    }

    @Test
    fun `并发启用墨水屏与灰度时完整 transform 串行且保持互斥`() {
        val initial = MangaSettings()
        val core = PendingOverlayCore(
            initial = mangaPrefMap(initial),
            launchWrite = {},
            persist = { _, _ -> error("不会执行落盘") },
            persistAll = { error("不会执行落盘") },
        )
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val first = thread {
            core.atomicUpdate(
                read = ::toMangaSettings,
                toPrefMap = ::mangaToPrefMap,
            ) {
                firstEntered.countDown()
                releaseFirst.await()
                it.copy(enableEInk = true, enableGray = false)
            }
        }
        firstEntered.await()

        val secondStarted = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val second = thread {
            secondStarted.countDown()
            core.atomicUpdate(
                read = ::toMangaSettings,
                toPrefMap = ::mangaToPrefMap,
            ) {
                secondEntered.countDown()
                it.copy(enableEInk = false, enableGray = true)
            }
        }
        secondStarted.await()
        val secondRacedWithFirst = secondEntered.await(200, TimeUnit.MILLISECONDS)
        releaseFirst.countDown()
        first.join()
        second.join()

        assertFalse(secondRacedWithFirst)
        assertEquals(
            MangaSettings(enableEInk = false, enableGray = true),
            toMangaSettings(core.preferencesFlow.value),
        )
    }
}

// —— 以下为 app 本地、Preferences 上的 read/toPrefMap 映射（仅覆盖本测试用到的字段）——
// core:data 的 `toLabSettings`/`toPrefMap` 为 internal，跨模块不可见；此处用 app 侧
// `compatDsBoolean`/`setPrefValue` 复刻最小映射，仅验证 PendingOverlayCore 并发不变式。

private fun toLabSettings(p: Preferences): LabSettings = LabSettings(
    enabled = p.compatDsBoolean(PreferKey.labEnabled) ?: false,
    eInkDisplay = p.compatDsBoolean(PreferKey.labEInkDisplay) ?: false,
    eyeProtection = p.compatDsBoolean(PreferKey.labEyeProtection) ?: false,
)

private fun labToPrefMap(s: LabSettings): Map<String, Any?> = mapOf(
    PreferKey.labEnabled to s.enabled,
    PreferKey.labEInkDisplay to s.eInkDisplay,
    PreferKey.labEyeProtection to s.eyeProtection,
)

private fun toMangaSettings(p: Preferences): MangaSettings = MangaSettings(
    enableEInk = p.compatDsBoolean(PreferKey.enableMangaEInk) ?: false,
    enableGray = p.compatDsBoolean(PreferKey.enableMangaGray) ?: false,
)

private fun mangaToPrefMap(s: MangaSettings): Map<String, Any?> = mapOf(
    PreferKey.enableMangaEInk to s.enableEInk,
    PreferKey.enableMangaGray to s.enableGray,
)

private fun labPrefMap(s: LabSettings): Preferences = mutablePreferencesOf().apply {
    setPrefValue(PreferKey.labEnabled, s.enabled)
    setPrefValue(PreferKey.labEInkDisplay, s.eInkDisplay)
    setPrefValue(PreferKey.labEyeProtection, s.eyeProtection)
}

private fun mangaPrefMap(s: MangaSettings): Preferences = mutablePreferencesOf().apply {
    setPrefValue(PreferKey.enableMangaEInk, s.enableEInk)
    setPrefValue(PreferKey.enableMangaGray, s.enableGray)
}
