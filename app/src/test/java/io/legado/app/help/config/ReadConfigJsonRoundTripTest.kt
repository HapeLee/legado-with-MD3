package io.legado.app.help.config

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R4.7 把 `ReadBookConfig.Config` 的值字段全改成了 `val`。这是本次改造里
 * **唯一编译器覆盖不到的风险**：配置是 GSON 反序列化出来的，而 GSON 不走构造函数，
 * 靠 `Field.setAccessible(true)` 往字段里塞值。`val` 编译成 `final` 字段，
 * 万一哪天换了序列化实现或加了 `@JvmField` 之类的改动让反射写入失效，
 * 失败模式是**用户所有排版配置读出来全是默认值**——静默、且编译和类型都拦不住。
 *
 * 所以这里钉死三条：写得进（非默认值能反序列化回来）、认得全（三种模式的颜色/背景都在）、
 * 缺字段时回落到默认值（老版本配置文件仍能读）。
 *
 * 用裸 `Gson()` 而不是项目的 `GSON`：后者带 appCtx 相关的注册，JVM 单测里起不来；
 * 本测试要验的是 Kotlin `val` + 反射写入这件事本身，与自定义适配器无关。
 */
class ReadConfigJsonRoundTripTest {

    private val gson = Gson()

    @Test
    fun `非默认值能完整往返`() {
        val original = ReadBookConfig.Config(
            name = "夜读",
            textSize = 33,
            letterSpacing = 0.42f,
            lineSpacingExtra = 27,
            textItalic = true,
            underline = true,
            underlineHeight = 3,
            paragraphIndent = "  ",
            textFont = "/sdcard/fonts/x.ttf",
            tipHeaderLeft = 9,
            tipFooterRight = 14,
            bgType = 2,
            bgStr = "/sdcard/bg/a.jpg",
            bgAlpha = 66,
        )

        val restored = gson.fromJson(gson.toJson(original), ReadBookConfig.Config::class.java)

        assertEquals("data class 相等性只看构造参数，正好是要持久化的那批", original, restored)
        assertEquals(33, restored.textSize)
        assertEquals(0.42f, restored.letterSpacing, 0f)
        assertTrue(restored.textItalic)
        assertEquals("/sdcard/bg/a.jpg", restored.bgStr)
    }

    @Test
    fun `私有的模式相关字段也能往返`() {
        // textColor / textColorNight / pageAnim / darkStatusIcon 是 private 构造参数，
        // 只能经由 withCurXxx 写、getXxx 读——但它们同样要落盘。
        val json = """
            {"name":"x","textColor":"#112233","textColorNight":"#445566",
             "textColorEInk":"#778899","pageAnim":3,"pageAnimEInk":1,
             "darkStatusIcon":false,"darkStatusIconNight":true}
        """.trimIndent()

        val restored = gson.fromJson(json, ReadBookConfig.Config::class.java)

        assertEquals("#112233", restored.getTextColor())
        assertEquals("#445566", restored.getTextColorNight())
        assertEquals("#778899", restored.getTextColorEInk())
        assertEquals(3, restored.getPageAnim())
        assertEquals(1, restored.getPageAnimEInk())
        assertEquals(false, restored.getDarkStatusIcon())
        assertEquals(true, restored.getDarkStatusIconNight())
    }

    @Test
    fun `缺字段的老配置回落到默认值`() {
        val restored = gson.fromJson("""{"name":"旧"}""", ReadBookConfig.Config::class.java)

        assertEquals("旧", restored.name)
        assertEquals("没有走构造函数的话，默认值不会生效，这里会读到 0", 20, restored.textSize)
        assertEquals(100, restored.bgAlpha)
        assertEquals("　　", restored.paragraphIndent)
        assertEquals(12, restored.lineSpacingExtra)
    }

    @Test
    fun `copy 会重置颜色记忆化缓存`() {
        // curTextColor() 把解析结果记在 @Transient 字段里，并用 initColorInt 挡住重复解析。
        // copy 只复制构造参数，两个字段都应回到未初始化——否则改了颜色的那份会沿用旧解析值。
        // curTextColor() 本身要经 Koin 取主题网关，JVM 单测起不来，所以用反射模拟
        // 「ensureColorInts 已经跑过」的状态，直接断言 copy 出来的实例缓存是空的。
        val config = ReadBookConfig.Config(name = "x")
        val cacheField = ReadBookConfig.Config::class.java.getDeclaredField("textColorInt")
            .apply { isAccessible = true }
        val initFlagField = ReadBookConfig.Config::class.java.getDeclaredField("initColorInt")
            .apply { isAccessible = true }
        cacheField.setInt(config, 0x123456)
        initFlagField.setBoolean(config, true)

        val changed = config.copy(name = "y")

        assertEquals("y", changed.name)
        assertEquals(
            "copy 若带走了 initColorInt，改色后 ensureColorInts 不会重算，永远沿用旧解析值",
            false,
            initFlagField.getBoolean(changed),
        )
        assertEquals(
            "copy 若带走了记忆化缓存，改色后会沿用旧解析值",
            -1,
            cacheField.getInt(changed),
        )
    }
}
