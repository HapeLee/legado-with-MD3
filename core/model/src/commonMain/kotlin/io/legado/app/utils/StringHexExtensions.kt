package io.legado.app.utils

/**
 * 十六进制字符串判定（P4 组件下沉配套）。
 *
 * 从 `:app` 的 `utils/StringExtensions.kt` 原样下沉：`:core:ui` 的颜色选择组件
 * （`dialog/ColorPickerSheet.kt`）用它校验用户输入，app 侧 `SymmetricCryptoAndroid` 也在用。
 *
 * 语义与原实现逐字一致：仅判断每个字符是否落在 `0-9a-fA-F`，**不校验长度或 `#` 前缀**。
 */
fun String.isHex(): Boolean {
    return all { c ->
        c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'
    }
}
