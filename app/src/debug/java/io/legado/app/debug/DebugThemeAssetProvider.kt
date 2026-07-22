package io.legado.app.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileNotFoundException

class DebugThemeAssetProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (failureArmed) {
            val error = FileNotFoundException("Injected theme asset failure: $uri")
            Log.e(LOG_TAG, "INJECTED_THEME_ASSET_OPEN uri=$uri mode=$mode", error)
            throw error
        }
        val context = requireNotNull(context)
        val fixture = File(context.cacheDir, "debug-theme-provider-background.png")
        if (!fixture.exists()) {
            context.assets.open("web/uploadBook/img/close.png").use { input ->
                fixture.outputStream().use(input::copyTo)
            }
        }
        return ParcelFileDescriptor.open(fixture, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun call(method: String, arg: String?, extras: android.os.Bundle?): android.os.Bundle? {
        if (method == "armFailure") {
            failureArmed = true
            Log.i(LOG_TAG, "INJECTED_THEME_ASSET_FAILURE_ARMED")
        }
        return null
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        const val LOG_TAG = "LegadoDebug"
        @Volatile
        var failureArmed = false
    }
}
