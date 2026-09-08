package io.legado.app.ui.util

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

/**
 * 构造一个包含纯文本的 [ClipEntry] 平台工厂。
 *
 * Compose 的 `LocalClipboard`（新 API）把「读/写剪贴板」抽成了跨平台接口，但 `ClipEntry`
 * 仍是平台类型——Android 上构造它必须 `ClipEntry(ClipData.newPlainText(label, text))`，
 * 也就是要直接触碰 `android.content.ClipData`。所有调用 `clipboard.setClipEntry(...)`
 * 的 Screen 因此都散落着 `ClipData` 直连。
 *
 * 这里把这「构造平台 ClipEntry」一件事收口成纯函数：Screen 侧
 * `clipboard.setClipEntry(plainTextClipEntry(label, text))`，不再 import `android.content.ClipData`。
 *
 * [label] 是系统剪贴板管理器看到的元数据标签（Android 12+ 部分应用可能据此提示来源），
 * 对用户无感知；语义与迁移前的 `ClipData.newPlainText(label, text)` 完全等价（纯写入、不弹提示）。
 */
fun plainTextClipEntry(label: String, text: String): ClipEntry =
    ClipEntry(ClipData.newPlainText(label, text))
