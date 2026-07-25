package io.legado.app.domain.model.settings

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Track E · E2 —— `ReadStyleState.revision` 的存在理由。
 *
 * 排版底座 `ReadBookConfig.Config` 是可变全局、无 flow，[ReadStyleState] 只投影了
 * items/selectedIndex/shareLayout 三项。「只改了字号/行距/下划线」这类变更不会让这三项
 * 发生变化——而 `MutableStateFlow` 在新值与旧值相等时**不更新、不通知订阅方**。
 *
 * 所以只把 `ReadBookViewModel` 改成 collect gateway 的 state 是不够的：没有 [revision]，
 * 绝大多数排版编辑根本发不出去。本测试把这个前提固定住——若将来有人把 `revision` 从
 * 主构造函数里挪走（或标成不参与相等性），这里会红。
 */
class ReadStyleStateRevisionTest {

    @Test
    fun `三项投影字段不变时 StateFlow 会丢掉这次发射`() {
        val flow = MutableStateFlow(ReadStyleState(revision = 7L))
        val before = flow.value

        // 模拟「只改了字号」：items/selectedIndex/shareLayout 全都没变
        flow.value = ReadStyleState(revision = 7L)

        assertSame(
            "内容相等时 MutableStateFlow 保留旧实例、不通知订阅方——" +
                "这正是排版编辑需要 revision 的原因",
            before,
            flow.value,
        )
    }

    @Test
    fun `revision 递增后同样内容会被当作新值发射`() {
        val flow = MutableStateFlow(ReadStyleState(revision = 7L))
        val before = flow.value

        flow.value = ReadStyleState(revision = 8L)

        assertNotSame(
            "revision 递增后必须被 StateFlow 当作新值，否则 ReadBookViewModel " +
                "收不到排版变更通知，弹层会显示旧值",
            before,
            flow.value,
        )
    }
}
