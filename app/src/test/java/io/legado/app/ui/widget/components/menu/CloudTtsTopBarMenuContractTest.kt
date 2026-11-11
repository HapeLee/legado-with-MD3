package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `CloudTtsScreen` 顶栏「引擎」页命令菜单的迁移契约。
 *
 * 旧实现是 `Box { TopBarActionButton(onClick = { expanded = true }); RoundDropdownMenu }`，
 * 四项纯命令（导入 TTS / 导出文件 / 复制 URL / 清除缓存）。迁移后按钮与开合交给
 * [AppActionMenu] 的 `Icon` 分支，业务只保留「稳定 ID → 副作用」分派。
 *
 * **刻意保留（本文件仅剩 1 处下拉）**：
 * - 区域菜单是**混合菜单**：`profile.regionOptions` 选项 + 「自定义区域」命令项
 *   （`cloud_tts_custom_region_action` 会清空 region 并展开输入框），
 *   [AppChoiceDropdown] 只能选不能带命令项，塞进去会丢副作用。
 * - provider / model / style / role / format 5 处纯单选已在第十七轮迁到
 *   [AppChoiceDropdown]：trigger 定为 `MediumTonalButton(onClick = null)` 纯展示形态，
 *   契约见 [CloudTtsChoiceMenuContractTest]。
 *
 * 静态契约只保证结构与 ID 一致性，**不证明命令副作用或四引擎按钮外观**。
 */
class CloudTtsTopBarMenuContractTest {

    @Test
    fun topBarMenuUsesDeclarativeEntrypoint() {
        val source = sourceOf(CLOUD_TTS)
        val topBar = source.substringAfter("topBar = {").substringBefore("bottomContent = {")
        assertFalse("顶栏仍手画 RoundDropdownMenu", topBar.contains("RoundDropdownMenu("))
        assertFalse("顶栏仍自带 expanded 开合状态", topBar.contains("var expanded by remember"))
        assertTrue("顶栏缺少 AppActionMenu", topBar.contains("AppActionMenu("))
        assertTrue("顶栏缺少纯展示 trigger", topBar.contains("AppIcons.MoreVert"))
    }

    @Test
    fun everyTopBarActionHasDeclarationAndDispatchBranch() {
        val source = sourceOf(CLOUD_TTS)
        IDS.forEach { id ->
            assertTrue("未声明 $id", source.contains("\"$id\""))
            assertTrue("缺少 $id 的分派分支", source.contains("\"$id\" ->"))
        }
    }

    @Test
    fun remainingFormMenusKeepRatchet() {
        val source = sourceOf(CLOUD_TTS)
        assertEquals(
            "纯单选与区域混合菜单迁完后应归零",
            0,
            Regex("RoundDropdownMenu\\(").findAll(source).count(),
        )
        // 区域菜单的混合命令项必须留着：迁成纯单选会静默丢掉「自定义区域」分支。
        assertTrue(
            "区域菜单的「自定义区域」命令项被误删",
            source.contains("R.string.cloud_tts_custom_region_action"),
        )
    }

    private fun sourceOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val CLOUD_TTS = "io/legado/app/ui/book/readaloud/cloudtts/CloudTtsScreen.kt"
        val IDS = listOf("import_tts", "export_tts_file", "copy_tts_url", "clear_tts_cache")
    }
}
