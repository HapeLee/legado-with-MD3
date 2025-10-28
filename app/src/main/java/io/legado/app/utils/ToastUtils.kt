@file:Suppress("unused")

package io.legado.app.utils

import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import io.legado.app.BuildConfig
import io.legado.app.databinding.ViewToastBinding
import io.legado.app.help.config.AppConfig
import splitties.systemservices.layoutInflater

private var toastForJs: Toast? = null
private var toast: Toast? = null
private var toastLegacy: Toast? = null

fun Context.toastOnUi(message: Int, duration: Int = Toast.LENGTH_SHORT) {
    toastOnUi(getString(message), duration)
}

fun Context.toastOnUi(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
    runOnUI {
        kotlin.runCatching {
            toast?.cancel()
            toast = Toast.makeText(this, message, duration)
            toast?.show()
        }
    }
}

fun Context.toastOnUiLegacy(message: CharSequence) {
    runOnUI {
        kotlin.runCatching {
            if (toastLegacy == null || BuildConfig.DEBUG || AppConfig.recordLog) {
                toastLegacy = Toast.makeText(this, message, Toast.LENGTH_SHORT)
            } else {
                toastLegacy?.setText(message)
                toastLegacy?.duration = Toast.LENGTH_SHORT
            }
            toastLegacy?.show()
        }
    }
}

fun Context.longToastOnUi(message: Int) {
    toastOnUi(message, Toast.LENGTH_LONG)
}

fun Context.longToastOnUi(message: CharSequence?) {
    toastOnUi(message, Toast.LENGTH_LONG)
}

fun Context.longToastOnUiLegacy(message: CharSequence) {
    runOnUI {
        kotlin.runCatching {
            if (toastLegacy == null || BuildConfig.DEBUG || AppConfig.recordLog) {
                toastLegacy = Toast.makeText(this, message, Toast.LENGTH_LONG)
            } else {
                toastLegacy?.setText(message)
                toastLegacy?.duration = Toast.LENGTH_LONG
            }
            toastLegacy?.show()
        }
    }
}

/**
 * JS 专用弹窗，短时间显示
 */
fun Context.toastForJs(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
    runOnUI {
        kotlin.runCatching {
            toastForJs?.cancel()
            toastForJs = Toast(this)
            ViewToastBinding.inflate(layoutInflater).run {
                toastForJs?.view = root
                tvText.text = message
            }
            toastForJs?.duration = duration
            toastForJs?.show()
        }
    }
}

fun Context.toastForJs(message: Int, duration: Int = Toast.LENGTH_SHORT) {
    toastForJs(getString(message), duration)
}

fun Context.longToastForJs(message: CharSequence?) {
    toastForJs(message, Toast.LENGTH_LONG)
}

fun Context.longToastForJs(message: Int) {
    toastForJs(message, Toast.LENGTH_LONG)
}

// Fragment 调用代理
fun Fragment.toastForJs(message: CharSequence?) = requireContext().toastForJs(message)
fun Fragment.toastForJs(message: Int) = requireContext().toastForJs(message)
fun Fragment.longToastForJs(message: CharSequence?) = requireContext().longToastForJs(message)
fun Fragment.longToastForJs(message: Int) = requireContext().longToastForJs(message)

fun Fragment.toastOnUi(message: Int) = requireActivity().toastOnUi(message)

fun Fragment.toastOnUi(message: CharSequence) = requireActivity().toastOnUi(message)

fun Fragment.longToast(message: Int) = requireContext().longToastOnUi(message)

fun Fragment.longToast(message: CharSequence) = requireContext().longToastOnUi(message)

