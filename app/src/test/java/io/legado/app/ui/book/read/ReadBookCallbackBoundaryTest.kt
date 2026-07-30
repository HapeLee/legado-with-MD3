package io.legado.app.ui.book.read

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R2.3（Track B 端游）—— `ReadBook` 的回调不再穿过 `ReadBookViewModel`。
 *
 * 两条会悄悄失效的边界：
 *
 * 1. VM 重新实现 `ReadBook.CallBack`——`ReadBook.callBack` 这个全局槽位又指回 VM，
 *    god object 通过回调面重新长回来。状态子集现在归 `LegacyReaderSession`，
 *    VM 只订阅 `ReaderSession.events`。
 * 2. 只在 `ReadBookController` 内部自产自销的渲染回调重新变成 `ReadBookEffect`——
 *    `ReadBookEffect` 是 VM 的对外协议，controller 自己 post 给自己的东西不该占位。
 */
class ReadBookCallbackBoundaryTest {

    @Test
    fun `ReadBookViewModel 不再实现 ReadBook CallBack`() {
        // 注释里会照常提到这些名字（说明它们搬去了哪儿），先剥掉注释再断言。
        val source = stripComments(
            mainSourceFile("io/legado/app/ui/book/read/ReadBookViewModel.kt").readText()
        )
        // 只查超类型和注册调用，不查 `override fun upMenuView` 之类的方法名：
        // Kotlin 里没有超类型就写不出 override，方法名检查是多余的；而各 delegate 的
        // Host 完全可以有同名方法（VM 确实实现了 ReadBookLoadDelegate.Host.sureNewProgress），
        // 查方法名会把这种正当写法误报成回归。
        val violations = buildList {
            if (CALLBACK_SUPERTYPE.containsMatchIn(source)) add("声明了 ReadBook.CallBack 超类型")
            if (Regex("""\bReadBook\.(?:register|unregister)\s*\(\s*this\s*\)""")
                    .containsMatchIn(source)
            ) {
                add("ReadBook.register/unregister(this)")
            }
        }
        assertTrue(
            "ReadBookViewModel 又接回了 ReadBook.CallBack：${violations.joinToString()}。\n" +
                "状态子集（upMenuView/loadChapterList/notifyBookChanged/sureNewProgress）" +
                "归 LegacyReaderSession，VM 用 readerSession.attach()/detach() 挂载、" +
                "订阅 ReaderSession.events 消费。",
            violations.isEmpty(),
        )
    }

    @Test
    fun `controller 内部自用的渲染回调不占用 ReadBookEffect`() {
        val source = stripComments(
            mainSourceFile("io/legado/app/ui/book/read/ReadBookContract.kt").readText()
        )
        val leaked = CONTROLLER_LOCAL_EFFECTS.filter { effect ->
            Regex("""\b$effect\b[^\n]*:\s*ReadBookEffect\b""").containsMatchIn(source)
        }
        assertTrue(
            "${leaked.joinToString()} 又回到了 ReadBookEffect。\n" +
                "这几个只由 ReadBookController 的 postRender 产生、又只由它自己的 " +
                "handleEffect 消费，从不经过 ViewModel 的 _effects——" +
                "请直接内联进 controller 的渲染方法。",
            leaked.isEmpty(),
        )
    }

    private companion object {
        val CONTROLLER_LOCAL_EFFECTS = listOf(
            "PageChanged",
            "ContentLoadFinish",
            "LayoutPageCompleted",
        )

        val CALLBACK_SUPERTYPE = Regex("""\bReadBook\.CallBack\b""")

        fun stripComments(text: String): String = text
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

        fun mainSourceFile(relativePath: String): File {
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                for (prefix in listOf("src/main/java", "app/src/main/java")) {
                    val candidate = File(directory, "$prefix/$relativePath")
                    if (candidate.isFile) return candidate
                }
                directory = directory.parentFile
            }
            error("从 ${File("").absolutePath} 向上找不到 $relativePath")
        }
    }
}
