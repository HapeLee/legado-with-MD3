package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * [Clipboard] / [Toaster] 注入点契约测试。
 *
 * 只测 provider 语义（未安装抛错、安装后可取、可卸载）；平台实现行为由 `:app` 侧
 * `PlatformServices` 委托既有 `Context` 扩展，不在此重复覆盖。
 */
class ClipboardToasterContractTest {

    @Test
    fun clipboardUninstalledThrows() {
        ClipboardProvider.uninstall()
        assertFailsWith<IllegalStateException> { ClipboardProvider.current }
    }

    @Test
    fun clipboardInstalledReturnsDelegate() {
        val clipboard = FakeClipboard()
        ClipboardProvider.install(clipboard)
        try {
            clipboard.content = "粘贴内容"
            assertEquals("粘贴内容", ClipboardProvider.current.getText())
            ClipboardProvider.current.setText("新内容")
            assertEquals("新内容", clipboard.content)
        } finally {
            ClipboardProvider.uninstall()
        }
    }

    @Test
    fun clipboardUninstallClearsDelegate() {
        ClipboardProvider.install(FakeClipboard())
        ClipboardProvider.uninstall()
        assertFalse(ClipboardProvider.isInstalled)
    }

    @Test
    fun toasterUninstalledThrows() {
        ToasterProvider.uninstall()
        assertFailsWith<IllegalStateException> { ToasterProvider.current }
    }

    @Test
    fun toasterInstalledRecordsCalls() {
        val toaster = FakeToaster()
        ToasterProvider.install(toaster)
        try {
            ToasterProvider.current.toast("短")
            ToasterProvider.current.longToast("长")
            assertEquals(listOf("toast:短", "longToast:长"), toaster.calls)
        } finally {
            ToasterProvider.uninstall()
        }
    }

    private class FakeClipboard : Clipboard {
        var content: String? = null

        override fun getText(): String? = content

        override fun setText(text: String) {
            content = text
        }
    }

    private class FakeToaster : Toaster {
        val calls = mutableListOf<String>()

        override fun toast(message: String) {
            calls += "toast:$message"
        }

        override fun longToast(message: String) {
            calls += "longToast:$message"
        }
    }
}
