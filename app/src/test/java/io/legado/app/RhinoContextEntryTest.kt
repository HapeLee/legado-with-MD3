package io.legado.app

import com.script.rhino.RhinoContext
import com.script.rhino.enterRhinoContext
import com.script.rhino.runScriptWithContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * Rhino 入口必须保证先安装自定义全局 ContextFactory 再 `Context.enter()`。
 *
 * 回归对象：Rhino 的 `Context.enter()` 取的是全局工厂，而生产 [RhinoContext] 的工厂只在
 * RhinoScriptEngine 的 object init 中安装。安装前进入上下文会拿到普通
 * `org.mozilla.javascript.Context`，直接强转既抛 ClassCastException，又因为没有
 * `Context.exit()` 把该线程永久留在普通 Context 上。
 */
class RhinoContextEntryTest {

    @Test
    fun enterRhinoContextYieldsRhinoContextAndLeavesNoContext() {
        inFreshThread {
            val cx = enterRhinoContext()
            assertEquals(RhinoContext::class.java.name, cx.javaClass.name)
            assertSame(cx, Context.getCurrentContext())
            Context.exit()
            assertNull(Context.getCurrentContext())
        }
    }

    /**
     * 线程已被非 RhinoContext 占用时（旧实现崩溃后留下的线程）：
     * 必须抛出可诊断的 IllegalStateException，而不是 ClassCastException；
     * 且不能多退一次 Context，清理干净后线程还能正常进入 Rhino 上下文。
     */
    @Test
    fun foreignContextOnThreadFailsLoudlyWithoutBreakingThreadState() {
        inFreshThread {
            val foreignFactory = ContextFactory()
            val foreign = foreignFactory.enterContext()
            try {
                enterRhinoContext()
                fail("应抛出 IllegalStateException，而不是 ClassCastException")
            } catch (e: IllegalStateException) {
                assertTrue(
                    "错误信息应带上实际 Context 类型，实际为: ${e.message}",
                    e.message.orEmpty().contains(foreign.javaClass.name)
                )
            }
            // 失败路径不能多退：外来 Context 的 enter 计数必须原样保留
            assertSame(foreign, Context.getCurrentContext())
            // 调用方自己退出后线程应彻底干净
            Context.exit()
            assertNull(Context.getCurrentContext())

            val cx = enterRhinoContext()
            assertEquals(RhinoContext::class.java.name, cx.javaClass.name)
            Context.exit()
            assertNull(Context.getCurrentContext())
        }
    }

    /** 书源登录页计算 headers 用的就是这个 suspend 重载。 */
    @Test
    fun suspendRunScriptWithContextEnterAndExitCleanly() = runBlocking {
        val result = runScriptWithContext { "ok" }
        assertEquals("ok", result)
        assertNull(Context.getCurrentContext())
    }

    private fun inFreshThread(block: () -> Unit) {
        val failure = AtomicReference<Throwable?>(null)
        val thread = Thread {
            try {
                block()
            } catch (t: Throwable) {
                failure.set(t)
            }
        }
        thread.start()
        thread.join(30_000)
        if (thread.isAlive) {
            thread.interrupt()
            fail("测试线程未在超时时间内结束")
        }
        failure.get()?.let { throw it }
    }
}
