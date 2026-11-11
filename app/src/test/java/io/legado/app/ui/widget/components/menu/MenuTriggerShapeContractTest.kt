package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 触发内容的形状裁剪契约。
 *
 * 入口包的那层唯一可点节点是矩形；触发内容自己画圆角按钮时，不按形状裁剪就会出现
 * 「按钮没有圆角」——M3 的按压底色/水波纹从四个角溢出去。形状只能由调用方给出，
 * 所以两个菜单入口必须共用同一份裁剪实现，且每个引擎的 Row 分支都要应用。
 */
class MenuTriggerShapeContractTest {

    @Test
    fun everyRowActionBranchClipsToTriggerShape() {
        val source = sourceOf(ACTION_MENU)
        assertTrue(
            "Row 锚点默认没按圆形/胶囊裁剪",
            source.contains("triggerShape: Shape = CircleShape"),
        )
        val clips = Regex("clipTriggerShape\\(triggerShape\\)").findAll(source).count()
        assertTrue("四个引擎分支都应裁剪（实际 $clips 处）", clips >= 4)
    }

    @Test
    fun bothEntryPointsShareOneClipDefinition() {
        val helper = sourceOf(TRIGGER_SHAPE)
        assertTrue(
            "缺共用的裁剪扩展",
            helper.contains("internal fun Modifier.clipTriggerShape(shape: Shape?)"),
        )
        listOf(ACTION_MENU, CHOICE).forEach { path ->
            assertTrue("$path 未使用共用裁剪扩展", sourceOf(path).contains(".clipTriggerShape("))
        }
        assertFalse(
            "AppChoiceDropdown 里仍留着私有副本",
            sourceOf(CHOICE).contains("private fun Modifier.clipTriggerShape"),
        )
    }

    /**
     * 几何固定的 compact 触发必须与 trigger 自己画的容器同源裁剪。
     * 受 `BaseCard` 主题圆角动态覆盖的卡片不在此列：那些不能硬编码一个固定 dp，
     * 需要另做「卡片把 resolvedShape 暴露给入口」的设计。
     */
    @Test
    fun fixedGeometryCompactTriggersDeclareMatchingShape() {
        val seriesShape = "IconButtonDefaults.extraSmallRoundShape"
        assertTrue(
            "SeriesButton 默认形状已变，MediumTonalButton 的裁剪值需同步",
            sourceOf(SERIES_BUTTON).contains("shape: Shape = $seriesShape"),
        )
        listOf(HOMEPAGE_MANAGE, CLOUD_TTS).forEach { path ->
            val source = sourceOf(path)
            val compactCount = Regex("compact = true").findAll(source).count()
            val shapeCount = Regex("triggerShape = $seriesShape").findAll(source).count()
            assertTrue(
                "$path 有 $compactCount 处 compact，只有 $shapeCount 处按同源形状裁剪",
                compactCount > 0 && shapeCount == compactCount,
            )
        }
        val textProcessing = sourceOf(TEXT_PROCESSING)
        assertTrue(
            "CompactTextProcessTool 是 10dp 方角，入口必须按同形状裁剪",
            textProcessing.contains("clip(RoundedCornerShape(10.dp))") &&
                textProcessing.contains("triggerShape = RoundedCornerShape(10.dp)"),
        )
    }

    /**
     * 走 BaseCard 的方卡圆角由主题动态解析，调用点写死 dp 会在「覆盖卡片圆角」
     * 或 Mochi 引擎下裁错。这些 compact 触发必须与卡片取同一份解析结果；
     * 纯图标触发没有可见容器，保持 null。
     */
    @Test
    fun baseCardCompactTriggersResolveShapeFromCardLayer() {
        val card = sourceOf("io/legado/app/ui/widget/components/card/GlassCard.kt")
        assertTrue(
            "卡片层须有公开的形状解析入口",
            card.contains("fun resolveBaseCardShape(cornerRadius: Dp = MiuixCardDefaults.CornerRadius): Shape"),
        )
        assertTrue(
            "解析须同时覆盖主题圆角覆盖与 Mochi 连续曲率轮廓",
            card.contains("overrideBaseCardCornerRadius") &&
                card.contains("composeEngine == ComposeEngine.Mochi"),
        )
        assertTrue(
            "BaseCard 必须复用该解析结果",
            card.contains("val resolvedShape = resolveBaseCardShape(cornerRadius)"),
        )
        assertFalse("卡片层不得留第二份形状定义", card.contains("val resolvedShape = if ("))
        assertTrue(
            "紧凑方卡的 12dp 须由共享常量表达",
            card.contains("val CompactCardCornerRadius: Dp = 12.dp") &&
                sourceOf("io/legado/app/ui/widget/components/settingItem/TinySettingItems.kt")
                    .contains("cornerRadius = CompactCardCornerRadius"),
        )

        listOf(
            "io/legado/app/ui/rss/source/edit/RssSourceEditScreen.kt",
            "io/legado/app/ui/book/source/edit/BookSourceEditScreen.kt",
            "io/legado/app/ui/book/read/sheet/ClickActionConfigSheet.kt",
            "io/legado/app/ui/book/knowledge/BookKnowledgeDetailScreen.kt",
            "io/legado/app/ui/book/knowledge/BookCharacterDetailScreen.kt",
            "io/legado/app/ui/book/manga/MangaReaderSheets.kt",
        ).forEach { path ->
            val source = sourceOf(path)
            val compactCount = Regex("compact = true").findAll(source).count()
            val shapeCount = Regex("triggerShape = resolveBaseCardShape\\(\\)").findAll(source).count()
            assertTrue(
                "$path 有 $compactCount 处 compact，只有 $shapeCount 处按解析形状裁剪",
                compactCount > 0 && shapeCount == compactCount,
            )
        }

        val globalTheme = sourceOf("io/legado/app/ui/book/read/sheet/GlobalThemePage.kt")
        assertTrue(
            "阅读菜单翻页动画两张卡须改吃解析结果",
            globalTheme.contains("resolveBaseCardShape(CompactCardCornerRadius)"),
        )
        assertFalse(
            "不应再硬编码 12dp 的 RoundedCornerShape",
            globalTheme.contains("RoundedCornerShape(12.dp)"),
        )
        listOf(
            "io/legado/app/ui/widget/components/FontSelectGrid.kt",
            "io/legado/app/ui/book/read/sheet/CharsetConfigSheet.kt",
        ).forEach { path ->
            assertFalse("$path 是纯图标触发，保持矩形即可", sourceOf(path).contains("triggerShape"))
        }
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
        const val ACTION_MENU = "io/legado/app/ui/widget/components/menu/AppActionMenu.kt"
        const val CHOICE = "io/legado/app/ui/widget/components/menu/AppChoiceDropdown.kt"
        const val TRIGGER_SHAPE = "io/legado/app/ui/widget/components/menu/TriggerShape.kt"
        const val SERIES_BUTTON = "io/legado/app/ui/widget/components/button/series/SeriesIconButton.kt"
        const val HOMEPAGE_MANAGE = "io/legado/app/ui/main/homepage/HomepageModuleManageSheet.kt"
        const val CLOUD_TTS = "io/legado/app/ui/book/readaloud/cloudtts/CloudTtsScreen.kt"
        const val TEXT_PROCESSING = "io/legado/app/ui/book/read/sheet/TextProcessingSheet.kt"
    }
}
