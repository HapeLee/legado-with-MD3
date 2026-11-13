package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第四十三轮结构契约：`BookInfoEditScreen.BookTypeDropdown` 改为 `SettingItem` 单选行。
 *
 * 用户定案「直接用 SettingItem」：`SettingItem` 内部用受控 `AppChoiceDropdown` +
 * `matchParentSize()` 锚点，弹层拿到四引擎原生单选行（Material 分支仍是项目自绘行），
 * 因此不再需要 `ExposedDropdownMenuBox` / `menuAnchor` / `TextFieldState` 同步。
 *
 * 书类型只有四个固定枚举值，ID 用枚举名（跨四套主题库唯一且稳定），回解时按
 * `bookTypes` 校验而不是按列表下标 —— 避免列表顺序变化导致选错类型。
 * 静态断言不证明四引擎展开态外观与 TalkBack。
 */
class BookTypeSettingItemContractTest {

    /** 声明式形态：一处 SettingItem、零手画容器，函数签名与调用点保持不变。 */
    @Test
    fun bookTypeDropdownIsASingleSettingItem() {
        val source = codeOf(BOOK_INFO_EDIT)
        assertEquals(
            "应只有一个 SettingItem",
            1,
            Regex("\\bSettingItem\\(").findAll(source).count(),
        )
        assertEquals(
            "仍有手画 RoundDropdownMenu",
            0,
            Regex("RoundDropdownMenu\\(").findAll(source).count(),
        )
        assertFalse("仍保留 ExposedDropdownMenuBox", source.contains("ExposedDropdownMenuBox("))
        assertTrue("函数签名被改", source.contains("fun BookTypeDropdown("))
        assertEquals(
            "调用点应与定义各一处",
            2,
            Regex("BookTypeDropdown\\(").findAll(source).count(),
        )
        assertTrue("书类型标题丢失", source.contains("title = stringResource(R.string.book_type_label)"))
        assertFalse(
            "独立整行后不应保留封面行内的 8.dp 内边距",
            source.contains("modifier = Modifier.padding(horizontal = 8.dp)"),
        )
    }

    /** 稳定 ID：枚举名做 id / selectedId，回解按枚举值校验。 */
    @Test
    fun choiceIdsAreEnumNamesAndResolvedByValue() {
        val source = codeOf(BOOK_INFO_EDIT)
        assertTrue("选项未从 bookTypes 生成", source.contains("choiceOptions = bookTypes.map { (type, label) ->"))
        assertTrue("选项 ID 不是枚举名", source.contains("AppChoiceOption(id = type.name, label = label)"))
        assertTrue("选中态 ID 不是枚举名", source.contains("choiceSelectedId = selectedType.name"))
        assertTrue("未转成 ImmutableList", source.contains(".toImmutableList()"))
        assertTrue("缺少 ID 分派", source.contains("onChoiceSelect = { id ->"))
        assertTrue(
            "回解未按枚举值校验",
            source.contains("bookTypes.firstOrNull { it.first.name == id }?.first?.let(onTypeSelected)"),
        )
    }

    /** `map` 的 lambda 不是 @Composable，标签必须在外部先求值；显示值跟随 selectedType。 */
    @Test
    fun choicesAreBuiltOutsideComposableAndTrackSelectedType() {
        val source = codeOf(BOOK_INFO_EDIT)
        val choiceBlock = source
            .substringAfter("choiceOptions = bookTypes.map")
            .substringBefore(".toImmutableList()")
        assertFalse(
            "map lambda 里不能调 stringResource",
            choiceBlock.contains("stringResource"),
        )
        assertTrue("选项标签未用现成字符串", choiceBlock.contains("label = label"))
        assertTrue(
            "显示值未跟随 selectedType",
            source.contains("text = bookTypes.firstOrNull { it.first == selectedType }?.second.orEmpty()"),
        )
    }

    /** 原生单选行来自 SettingItem 内部的受控 AppChoiceDropdown，不是重新自绘。 */
    @Test
    fun settingItemOwnsTheFourEngineChoiceRows() {
        val source = codeOf(SETTING_ITEM)
        assertTrue("SettingItem 未接 AppChoiceDropdown", source.contains("AppChoiceDropdown("))
        assertTrue("不是受控展开", source.contains("embeddedExpanded = showMenu"))
        assertTrue("锚点未挂在取值区", source.contains("modifier = Modifier.matchParentSize()"))
        assertTrue("未传选项数据", source.contains("options = menuOptions"))
        assertTrue("未传选中 ID", source.contains("selectedId = choiceSelectedId"))
    }

    /**
     * 第四十五轮回归：`SettingItem` 把受控弹层用 `matchParentSize()` 挂在 `trailing` 的
     * `Box` 里；没有 `trailingContent` 时该 Box 没有任何撑尺寸的可见子节点（非 COUI 下
     * `trailingOption` 也为 null），锚点容器退化成 0 尺寸，弹层落到窗口原点（左上角）。
     * 另外 `SettingCard` 默认取 `MaterialTheme.colorScheme.surfaceContainerLow`，在编辑页里
     * 与 `AppTextField` 的 `LegadoTheme.colorScheme.surfaceInput` 不一致。
     */
    @Test
    fun choiceAnchorAndSurfaceFollowEditPage() {
        val source = codeOf(BOOK_INFO_EDIT)
        assertTrue(
            "trailing 槽缺少可见内容，受控弹层锚点会退化成 0 尺寸",
            source.contains("trailingContent = {"),
        )
        assertTrue(
            "卡片底色未跟随编辑页主题",
            source.contains("color = LegadoTheme.colorScheme.surfaceInput"),
        )
        assertTrue("下拉箭头丢失", source.contains("AppIcons.ExpandMore"))
        assertFalse(
            "取值文本不应再由 MaterialTheme.colorScheme.primary 的 option 渲染",
            source.contains("option = "),
        )
    }

    /** 第四十七轮：书类型独立成行，紧贴「固定书籍类型」上方，不再与封面同一行。 */
    @Test
    fun bookTypeDropdownSitsAboveFixedTypeSwitch() {
        val content = codeOf(BOOK_INFO_EDIT).substringAfter("fun BookInfoEditContent(")
        val coverRow = content.substringAfter("CoilBookCover(").substringBefore("BookKnowledgeEditCard(")
        assertFalse("书类型仍在封面行内", coverRow.contains("BookTypeDropdown("))

        val typeIndex = content.indexOf("BookTypeDropdown(")
        assertTrue("找不到书类型调用点", typeIndex >= 0)
        assertTrue(
            "书类型应移到知识卡片之后",
            typeIndex > content.indexOf("BookKnowledgeEditCard("),
        )
        assertTrue(
            "书类型应放在固定书籍类型上方",
            typeIndex < content.indexOf("R.string.fixed_book_type"),
        )
        val between = content.substring(typeIndex, content.indexOf("R.string.fixed_book_type"))
        assertTrue(
            "书类型与固定书籍类型之间应留 8.dp 间距",
            between.contains("Spacer(modifier = Modifier.height(8.dp))"),
        )
    }

    private fun codeOf(path: String): String = sourceOf(path)
        .lineSequence()
        .filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
        }
        .joinToString("\n")

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
        const val BOOK_INFO_EDIT = "io/legado/app/ui/book/info/edit/BookInfoEditScreen.kt"
        const val SETTING_ITEM = "io/legado/app/ui/widget/components/settingItem/SettingItem.kt"
    }
}
