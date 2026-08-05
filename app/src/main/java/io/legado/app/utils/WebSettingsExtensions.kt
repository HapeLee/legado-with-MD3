package io.legado.app.utils

import android.content.res.Configuration
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * 设置是否夜间模式。
 * 能否算法反色取决于 WebView 内核版本而非系统版本，用特性检测而不是 SDK_INT 判断；
 * 内核不支持时回落到旧的 forceDark 组合。
 */
fun WebSettings.setDarkeningAllowed(allow: Boolean) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, allow)
        return
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
        @Suppress("DEPRECATION")
        WebSettingsCompat.setForceDarkStrategy(
            this,
            WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
        )
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
        @Suppress("DEPRECATION")
        WebSettingsCompat.setForceDark(
            this,
            if (allow) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
        )
    }
}

/**
 * 把当前深浅色下发给 WebView。
 *
 * 页面侧的 prefers-color-scheme 取自 WebView 上下文主题的 android:isLightTheme，
 * 也就是 Activity 资源的 uiMode，由 AppCompat 的夜间模式决定，不归这里管；
 * 这里只负责重绘 WebView 自身、开关兜底的算法反色，并通知页面脚本。
 *
 * 不要在这里写 documentElement 的 color-scheme：页面一旦显式声明配色方案，
 * Chromium 就认为它自带深浅色适配，从此不再对它做算法反色，切回深色时页面会一直停在浅色。
 */
fun WebView.applyDayNight(isDark: Boolean) {
    val configuration = Configuration(resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (isDark) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
    }
    dispatchConfigurationChanged(configuration)
    settings.setDarkeningAllowed(isDark)
    evaluateJavascript(
        "window.dispatchEvent(new CustomEvent('legado-theme-change'," +
            " { detail: { dark: $isDark } }));",
        null,
    )
    postInvalidate()
}
