package io.legado.app.constant

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [PageAnim] / [SourceType] / [BookSourceType] 的 characterization test。
 *
 * 这三者是**枚举式**常量（取值互斥，非位掩码），数值会被持久化到配置与数据库，
 * 因此下沉到 commonMain 后取值必须逐个锁死。
 */
class TypeConstantsTest {

    @Test
    fun `page animation ordinals are stable`() {
        assertEquals(0, PageAnim.coverPageAnim)
        assertEquals(1, PageAnim.slidePageAnim)
        assertEquals(2, PageAnim.simulationPageAnim)
        assertEquals(3, PageAnim.scrollPageAnim)
        assertEquals(4, PageAnim.fadePageAnim)
        assertEquals(5, PageAnim.noAnim)
    }

    @Test
    fun `source type ordinals are stable`() {
        assertEquals(0, SourceType.book)
        assertEquals(1, SourceType.rss)
    }

    @Test
    fun `book source type ordinals are stable`() {
        assertEquals(0, BookSourceType.default)
        assertEquals(1, BookSourceType.audio)
        assertEquals(2, BookSourceType.image)
        assertEquals(3, BookSourceType.file)
    }

    @Test
    fun `marker annotation classes survive the migration`() {
        // 9 个调用点以 @PageAnim.Anim / @SourceType.Type / @BookSourceType.Type 形式引用，
        // 移除 androidx.annotation.IntDef 后这些注解类必须仍然存在，否则调用点整体编译失败。
        assertEquals(PageAnim.Anim::class.simpleName, "Anim")
        assertEquals(SourceType.Type::class.simpleName, "Type")
        assertEquals(BookSourceType.Type::class.simpleName, "Type")
        assertEquals(BookType.Type::class.simpleName, "Type")
    }
}
