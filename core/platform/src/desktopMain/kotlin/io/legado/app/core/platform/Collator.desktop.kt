package io.legado.app.core.platform

import java.text.Collator
import java.util.Locale

actual fun cnCompare(a: String, b: String): Int {
    return Collator.getInstance(Locale.CHINA).compare(a, b)
}
