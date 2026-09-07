package io.legado.app.core.platform

import android.icu.text.Collator
import android.icu.util.ULocale
import android.os.Build
import java.util.Locale

@Suppress("ObsoleteSdkInt")
actual fun cnCompare(a: String, b: String): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Collator.getInstance(ULocale.SIMPLIFIED_CHINESE).compare(a, b)
    } else {
        java.text.Collator.getInstance(Locale.CHINA).compare(a, b)
    }
}
