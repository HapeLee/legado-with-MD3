package io.legado.app.ui.widget.components.filePicker

import io.legado.app.core.platform.MimeTypeResolver
import io.legado.app.core.platform.MimeTypeResolverProvider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

/**
 * 锁住 `typesOfExtensions` 的映射表（M1-3n 搬件时新增）。
 *
 * 迁移前这个函数直接调 `android.webkit.MimeTypeMap`，零测试覆盖；M1-3n 把平台查表换成
 * [MimeTypeResolverProvider] 窄契约之后，这条路径第一次可以被测——所以把**全部三种真实入参**
 * （全仓 22 个调用点只传 `null` / `["json"]` / `["json", "txt"]`）的产出、`*` / `txt` / `xml`
 * 的特判、以及「查不到」的回落逐条钉死。
 *
 * ⚠️ 改了这里等于改了用户点「系统文件选择器」时看到的可选文件类型（`application/json`
 * 过滤不到文件、`*`/`*` 全都放行），所以**不是**可以顺手重构的地方。
 *
 * 断言一律按**集合**比较：实现用 `hashSetOf` + `toTypedArray()`，元素顺序本就无定义。
 */
class FilePickerSheetMimeTest {

    @AfterTest
    fun tearDown() {
        MimeTypeResolverProvider.uninstall()
    }

    /** 安装一张查表桩，并记录被问过的扩展名。 */
    private class StubResolver(vararg pairs: Pair<String, String?>) {
        private val table: Map<String, String?> = pairs.toMap()
        val asked = mutableListOf<String>()

        val resolver = MimeTypeResolver { extension ->
            asked.add(extension)
            table[extension]
        }
    }

    private fun assertTypes(expected: Set<String>, allowExtensions: Array<String>?) {
        assertEquals(expected, typesOfExtensions(allowExtensions).toSet())
    }

    // ---- 入参为空：不过滤 ----

    @Test
    fun nullExtensionsAllowEverything() {
        assertTypes(setOf("*/*"), null)
    }

    @Test
    fun emptyExtensionsAllowEverything() {
        assertTypes(setOf("*/*"), emptyArray())
    }

    @Test
    fun starIsNormalisedToWildcardMime() {
        val stub = StubResolver()
        MimeTypeResolverProvider.install(stub.resolver)

        assertTypes(setOf("*/*"), arrayOf("*"))
        assertEquals(emptyList(), stub.asked, "特判分支不应查平台表")
    }

    // ---- 文本族走特判，不查平台表 ----

    @Test
    fun txtAndXmlMapToTextWildcardWithoutAskingThePlatform() {
        val stub = StubResolver("txt" to "text/plain", "xml" to "application/xml")
        MimeTypeResolverProvider.install(stub.resolver)

        assertTypes(setOf("text/*"), arrayOf("txt"))
        assertTypes(setOf("text/*"), arrayOf("xml"))
        assertEquals(emptyList(), stub.asked, "txt/xml 是特判，不该落到 MimeTypeResolver")
    }

    @Test
    fun textTypesDeduplicateInOneCall() {
        assertTypes(setOf("text/*"), arrayOf("txt", "xml"))
    }

    // ---- 其余扩展名查平台表；查不到回落 ----

    @Test
    fun unknownToTableExtensionFallsBackToOctetStream() {
        val stub = StubResolver("json" to "application/json")
        MimeTypeResolverProvider.install(stub.resolver)

        assertTypes(setOf("application/octet-stream"), arrayOf("epub"))
        assertEquals(listOf("epub"), stub.asked)
    }

    // ---- 全仓真实调用：["json"] / ["json", "txt"] ----

    @Test
    fun jsonResolvesThroughPlatformTable() {
        val stub = StubResolver("json" to "application/json")
        MimeTypeResolverProvider.install(stub.resolver)

        assertTypes(setOf("application/json"), arrayOf("json"))
        assertEquals(listOf("json"), stub.asked)
    }

    @Test
    fun jsonMixedWithTxtUsesBothBranches() {
        val stub = StubResolver("json" to "application/json")
        MimeTypeResolverProvider.install(stub.resolver)

        assertTypes(setOf("application/json", "text/*"), arrayOf("json", "txt"))
        assertEquals(listOf("json"), stub.asked, "只有 json 查表，txt 走特判")
    }

    // ---- 未安装解析器（desktop / iOS）：不崩，行为等于「平台不认识这个扩展名」 ----

    @Test
    fun missingResolverDegradesToOctetStreamInsteadOfThrowing() {
        assertFalse(MimeTypeResolverProvider.isInstalled)

        assertTypes(setOf("application/octet-stream"), arrayOf("json"))
        assertTypes(setOf("*/*"), null)
    }

    // ---- 兜底：current 永不返回 null ----

    @Test
    fun currentIsNeverNullEvenWithoutInstall() {
        // 若 current 返回 null，下面这行会 NPE 而不是走到 fail。
        val resolver = MimeTypeResolverProvider.current
        if (resolver.mimeTypeOf("json") != null) fail("未安装时应一律返回 null")
    }
}
