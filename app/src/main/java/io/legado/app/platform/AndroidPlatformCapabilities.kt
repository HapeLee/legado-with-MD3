package io.legado.app.platform

import android.content.ClipData
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.NinePatch
import android.graphics.drawable.NinePatchDrawable
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry
import io.legado.app.ui.widget.components.topbar.StatusBarInsets
import io.legado.app.core.platform.Clipboard
import io.legado.app.core.platform.MimeTypeResolver
import io.legado.app.core.platform.NinePatchLoader
import io.legado.app.core.platform.Toaster
import io.legado.app.ui.util.PlainTextClipEntryFactory
import io.legado.app.utils.getClipText
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import java.io.File

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

    /**
     * MIME 推断适配（M1-3n）。对齐 `android.webkit.MimeTypeMap.getMimeTypeFromExtension`：
     * 查不到返回 `null`（调用方回落 `application/octet-stream`）。
     *
     * **不需要 `Context`**——`MimeTypeMap` 是进程级单例表。因此这里没有参数，`:core:designsystem`
     * 的 `FilePickerSheet` 得以只依赖 `MimeTypeResolverProvider`，不接触任何 Android 类型。
     */
    fun mimeTypeResolver(): MimeTypeResolver = MimeTypeResolver { extension ->
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    /**
     * 纯文本 ClipEntry 工厂（M1-3o）。对齐迁移前 `PlainTextClipEntry.kt` 的那一行
     * `ClipEntry(ClipData.newPlainText(label, text))`——**逐字等价，纯写入、不弹提示**。
     *
     * **不需要 `Context`**（`ClipData` 是纯数据对象），所以这里没有参数——与本文件的
     * [mimeTypeResolver] 同理，`:core:designsystem` 的 `plainTextClipEntry` 因此只依赖
     * `PlainTextClipEntryFactory`，不接触任何 Android 类型。
     *
     * 返回类型属于 `:core:designsystem`：`ClipEntry` 是 Compose 的 `expect class`，
     * `:core:platform`（零 Compose）装不下这个契约，契约因此住在 designsystem。
     */
    fun plainTextClipEntryFactory(): PlainTextClipEntryFactory =
        PlainTextClipEntryFactory { label, text ->
            ClipEntry(ClipData.newPlainText(label, text))
        }

    /**
     * 九宫格背景图解析适配（M1-3p）。**逐字搬运**迁移前 `AppContainerBackground.kt` 里的私有
     * `loadNinePatch(path)`：先按 `.9.png` 后缀预筛（不是九宫格就直接返回 `null`，省掉一次解码），
     * 再 `BitmapFactory.decodeFile` → 校验 `bitmap.ninePatchChunk` 是不是合法 chunk →
     * `NinePatchDrawable(null, bitmap, chunk, null, null)`。任何一步失败或抛异常都返回 `null`
     * （保持原来的 `runCatching { }.getOrNull()` 语义）。
     *
     * 返回的 `NinePatchDrawable` 会被调用方原样交给 Coil 的 `ImageRequest.Builder.data(...)`，
     * 所以返回类型写成 `Any?`——契约在 `:core:platform`，不能也不必知道这个平台类型。
     *
     * **不需要 `Context`**（`decodeFile` 收的是绝对路径）。注意本实现做磁盘 I/O，
     * 调用方（`AppContainerBackground` 的 `produceState`）已经在 `Dispatchers.IO` 上调用它。
     */
    fun ninePatchLoader(): NinePatchLoader = NinePatchLoader { path ->
        loadNinePatch(path)
    }

    private fun loadNinePatch(path: String): NinePatchDrawable? {
        if (!path.endsWith(".9.png", ignoreCase = true)) return null
        return runCatching {
            val bitmap = BitmapFactory.decodeFile(File(path).absolutePath) ?: return null
            val chunk = bitmap.ninePatchChunk
            if (!NinePatch.isNinePatchChunk(chunk)) return null
            NinePatchDrawable(null, bitmap, chunk, null, null)
        }.getOrNull()
    }

    /**
     * 顶栏状态栏 inset 适配（M1-3r）。用 `statusBarsIgnoringVisibility` 而不是 `statusBars`：
     * 前者**不管状态栏当前是否可见都返回真实高度**，因此从隐藏了状态栏的界面（阅读器）返回时
     * 顶栏高度不变、内容不重排——`GlassTopAppBar` 里原本就是这么写的；后者在隐藏期间返回 0，
     * 会让顶栏跳动。
     *
     * 未注入时共享层回落到 `WindowInsets.statusBars`（desktop 恒为零，语义正确），
     * 所以 Android 侧**必须注入**，否则会引入真实的行为差异。
     *
     * 契约类型 `StatusBarInsets` 住 `:core:designsystem`：签名里出现的是 Compose foundation
     * 的 `WindowInsets`，零 Compose 的 `:core:platform` 装不下。
     */
    @OptIn(ExperimentalLayoutApi::class)
    fun topBarStatusBarInsets(): StatusBarInsets = object : StatusBarInsets {
        @Composable
        override fun get(): WindowInsets = WindowInsets.statusBarsIgnoringVisibility
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
