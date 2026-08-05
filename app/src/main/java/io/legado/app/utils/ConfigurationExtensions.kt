@file:Suppress("unused")

package io.legado.app.utils

import android.content.res.Configuration
import splitties.init.appCtx

val sysConfiguration: Configuration get() = appCtx.resources.configuration

val Configuration.isNightMode: Boolean
    get() {
        val mode = uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }