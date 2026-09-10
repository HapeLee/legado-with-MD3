package io.legado.app.platform

import android.content.Context
import io.legado.app.core.platform.Clipboard
import io.legado.app.core.platform.Toaster
import io.legado.app.utils.getClipText
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi

/**
 * `:core:platform` 里剪贴板/轻提示契约的 Android 适配。
 *
 * 为什么不在 `io.legado.app.help.PlatformServices`：这两个能力现在有两条消费路径——
 * 历史调用方走 `ClipboardProvider` / `ToasterProvider`，规则类 ViewModel 走**构造注入**
 * （M1-3 起）。构造注入要在 `di` 里引用实现，而 `di` 已被 legacy 棘轮禁止新增
 * `io.legado.app.help.**` import。把实现放到本包后，`di` 与 `PlatformServices` 共用同一组
 * 工厂，既满足棘轮，也不会出现两份会各自漂移的实现。
 *
 * 行为必须与既有扩展逐字一致：`setText` 内部调 `sendToClip`，**会顺带弹一次
 * 「复制完成」提示**；Toast 实现负责切主线程并吞掉平台异常。改这里等于改用户可见行为。
 */
object AndroidPlatformCapabilities {

    /** 剪贴板适配。对齐 `Context.getClipText()` / `Context.sendToClip(text)`。 */
    fun clipboard(context: Context): Clipboard = object : Clipboard {
        override fun getText(): String? = context.getClipText()

        override fun setText(text: String) {
            context.sendToClip(text)
        }
    }

    /** 轻提示适配。对齐 `Context.toastOnUi()` / `Context.longToastOnUi()`。 */
    fun toaster(context: Context): Toaster = object : Toaster {
        override fun toast(message: String) {
            context.toastOnUi(message)
        }

        override fun longToast(message: String) {
            context.longToastOnUi(message)
        }
    }
}
