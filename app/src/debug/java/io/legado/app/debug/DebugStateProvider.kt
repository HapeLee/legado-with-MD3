package io.legado.app.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfigStore
import org.json.JSONObject

class DebugStateProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = Bundle().apply {
        val json = when (method) {
            METHOD_TOGGLE_THEME -> toggleTheme()
            else -> error("unsupported method: $method")
        }
        putString(
            RESULT_BASE64,
            Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP),
        )
    }

    private fun toggleTheme(): JSONObject {
        val current = AppConfigStore.getString(PreferKey.themeMode) ?: "1"
        AppConfigStore.putString(PreferKey.themeMode, if (current == "2") "1" else "2")
        return JSONObject().put("themeMode", AppConfigStore.getString(PreferKey.themeMode).orEmpty())
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        const val METHOD_TOGGLE_THEME = "toggleTheme"
        const val RESULT_BASE64 = "resultB64"
    }

}
