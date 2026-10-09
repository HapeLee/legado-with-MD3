package io.legado.app.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 字体文件夹的两条来源：
 * - SAF 系统选择器：`content://` tree URI；
 * - 应用私有字体目录（「导入字体」写入）：`file://` 路径。
 *
 * 这里只覆盖不依赖 Android 运行时的文件目录扫描，即 `file://` 这条路径的核心：
 * 必须和 SAF 一样按 `.ttf/.otf`（不分大小写）过滤，并且在目录不可读时返回 null，
 * 让 UI 能提示“无法读取”而不是静默显示空列表。
 */
class FontLoaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun isFontFileName_matchesTtfAndOtfIgnoringCase() {
        assertTrue(isFontFileName("regular.ttf"))
        assertTrue(isFontFileName("serif.OTF"))
        assertTrue(isFontFileName("字体.Ttf"))
        assertFalse(isFontFileName("readme.txt"))
        assertFalse(isFontFileName("font.ttf.zip"))
        assertFalse(isFontFileName("ttf"))
    }

    @Test
    fun listFontFilesInDir_keepsFontsOnly() {
        val dir = tempFolder.newFolder("fonts").apply {
            File(this, "regular.ttf").writeBytes(byteArrayOf(0, 1))
            File(this, "serif.OTF").writeBytes(byteArrayOf(0, 1))
            File(this, "readme.txt").writeBytes(byteArrayOf(0, 1))
            File(this, "sub").mkdirs()
        }

        val names = listFontFilesInDir(dir)!!.map { it.name }.sorted()

        assertEquals(listOf("regular.ttf", "serif.OTF"), names)
    }

    @Test
    fun listFontFilesInDir_returnsNullWhenNotDirectory() {
        val missing = File(tempFolder.root, "font_not_exists")

        assertNull("不存在的路径必须返回 null，宿主据此提示不可访问", listFontFilesInDir(missing))
        assertNull("普通文件也必须返回 null", listFontFilesInDir(File(tempFolder.root, "a.ttf").apply { writeBytes(byteArrayOf(0)) }))
    }
}
