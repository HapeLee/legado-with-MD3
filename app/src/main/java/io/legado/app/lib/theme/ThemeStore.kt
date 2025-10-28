package io.legado.app.lib.theme

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.AttrRes
import androidx.annotation.CheckResult
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import io.legado.app.utils.ColorUtils
import splitties.init.appCtx
import androidx.core.content.edit
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors

/**
 * @author Aidan Follestad (afollestad), Karim Abou Zeid (kabouzeid)
 */

@Suppress("unused")
class ThemeStore @SuppressLint("CommitPrefEdits")
private constructor(private val mContext: Context) : ThemeStoreInterface {

    private val mEditor = prefs(mContext).edit()

    override fun addColorScheme(@ColorInt color: Int): ThemeStore {
        val options = DynamicColorsOptions.Builder()
            .setContentBasedSource(color)
            .build()

        val wrapped = DynamicColors.wrapContextIfAvailable(
            mContext,
            options).apply {
            resources.configuration.uiMode = mContext.resources.configuration.uiMode
            }

        val fallback = color

//        val secondary = MaterialColors.getColor(
//            wrapped,
//            com.google.android.material.R.attr.colorSecondary,
//            fallback
//        )
//
//        val primaryContainer = MaterialColors.getColor(
//            wrapped,
//            com.google.android.material.R.attr.colorPrimaryContainer,
//            fallback
//        )

        val primary = MaterialColors.getColor(
            wrapped,
            androidx.appcompat.R.attr.colorPrimary,
            fallback
        )

        //mEditor.putInt(ThemeStorePrefKeys.KEY_SECONDARY_COLOR, secondary)
        //mEditor.putInt(ThemeStorePrefKeys.KEY_PRIMARY_CONTAINER_COLOR, primaryContainer)
        mEditor.putInt(ThemeStorePrefKeys.KEY_PRIMARY_COLOR, primary)

        return this
    }

    override fun primaryColor(@ColorInt color: Int): ThemeStore {
        if (autoGeneratePrimaryDark(mContext))
            primaryColorDark(ColorUtils.darkenColor(color))
        mEditor.putInt(ThemeStorePrefKeys.KEY_PRIMARY_COLOR, color)
        //addColorScheme(color)
        return this
    }

    override fun primaryColorRes(@ColorRes colorRes: Int): ThemeStore {
        return primaryColor(ContextCompat.getColor(mContext, colorRes))
    }

    override fun primaryColorAttr(@AttrRes colorAttr: Int): ThemeStore {
        return primaryColor(ThemeUtils.resolveColor(mContext, colorAttr))
    }

    override fun primaryColorDark(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_PRIMARY_COLOR_DARK, color)
        return this
    }

    override fun primaryColorDarkRes(@ColorRes colorRes: Int): ThemeStore {
        return primaryColorDark(ContextCompat.getColor(mContext, colorRes))
    }

    override fun primaryColorDarkAttr(@AttrRes colorAttr: Int): ThemeStore {
        return primaryColorDark(ThemeUtils.resolveColor(mContext, colorAttr))
    }

    override fun backgroundColor(color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_BACKGROUND_COLOR, color)
        return this
    }

    override fun bottomBackground(color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_BOTTOM_BACKGROUND, color)
        return this
    }

    override fun autoGeneratePrimaryDark(autoGenerate: Boolean): ThemeStore {
        mEditor.putBoolean(ThemeStorePrefKeys.KEY_AUTO_GENERATE_PRIMARYDARK, autoGenerate)
        return this
    }

    // Commit method

    override fun apply() {
        mEditor.putLong(ThemeStorePrefKeys.VALUES_CHANGED, System.currentTimeMillis())
            .putBoolean(ThemeStorePrefKeys.IS_CONFIGURED_KEY, true)
            .apply()
    }

    companion object {

        fun editTheme(context: Context): ThemeStore {
            return ThemeStore(context)
        }

        fun isCustomThemeEnabled(context: Context): Boolean = true
           // AppConfig.customTheme

        // Static getters

        @CheckResult
        internal fun prefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(
                ThemeStorePrefKeys.CONFIG_PREFS_KEY_DEFAULT,
                Context.MODE_PRIVATE
            )
        }

        fun markChanged(context: Context) {
            ThemeStore(context).apply()
        }

        @CheckResult
        @ColorInt
        fun primaryColor(context: Context = appCtx): Int {
            return if (isCustomThemeEnabled(context)) {
                prefs(context).getInt(
                    ThemeStorePrefKeys.KEY_PRIMARY_COLOR,
                    ThemeUtils.resolveColor(
                        context,
                        androidx.appcompat.R.attr.colorPrimary,
                        "#455A64".toColorInt()
                    )
                )
            } else {
                ThemeUtils.resolveColor(context, androidx.appcompat.R.attr.colorPrimary)
            }
        }

//        fun primaryContainerColor(context: Context = appCtx): Int {
//            return if (isCustomThemeEnabled(context)) {
//                prefs(context).getInt(
//                    ThemeStorePrefKeys.KEY_PRIMARY_CONTAINER_COLOR,
//                    "#FFFFFF".toColorInt()
//                )
//            } else {
//                ThemeUtils.resolveColor(context, com.google.android.material.R.attr.colorPrimaryContainer)
//            }
//        }
//
//        fun secondaryColor(context: Context = appCtx): Int {
//            return if (isCustomThemeEnabled(context)) {
//                prefs(context).getInt(
//                    ThemeStorePrefKeys.KEY_SECONDARY_COLOR,
//                    primaryColor(context)
//                )
//            } else {
//                primaryColor(context)
//            }
//        }

        @CheckResult
        @ColorInt
        fun primaryColorDark(context: Context): Int {
            return if (isCustomThemeEnabled(context)) {
                prefs(context).getInt(
                    ThemeStorePrefKeys.KEY_PRIMARY_COLOR_DARK,
                    ThemeUtils.resolveColor(
                        context,
                        androidx.appcompat.R.attr.colorPrimaryDark,
                        "#37474F".toColorInt()
                    )
                )
            } else {
                ThemeUtils.resolveColor(context, androidx.appcompat.R.attr.colorPrimaryDark)
            }
        }

        @CheckResult
        @ColorInt
        fun backgroundColor(context: Context = appCtx): Int {
            return if (isCustomThemeEnabled(context)) {
                prefs(context).getInt(
                    ThemeStorePrefKeys.KEY_BACKGROUND_COLOR,
                    ThemeUtils.resolveColor(context, android.R.attr.colorBackground)
                )
            } else {
                ThemeUtils.resolveColor(context, android.R.attr.colorBackground)
            }
        }

        @CheckResult
        @ColorInt
        fun bottomBackground(context: Context = appCtx): Int {
            return prefs(context).getInt(
                ThemeStorePrefKeys.KEY_BOTTOM_BACKGROUND,
                ThemeUtils.resolveColor(context, android.R.attr.colorBackground)
            )
        }

        @CheckResult
        fun coloredStatusBar(context: Context): Boolean {
            return prefs(context).getBoolean(
                ThemeStorePrefKeys.KEY_APPLY_PRIMARYDARK_STATUSBAR,
                true
            )
        }

        @CheckResult
        fun coloredNavigationBar(context: Context): Boolean {
            return prefs(context).getBoolean(ThemeStorePrefKeys.KEY_APPLY_PRIMARY_NAVBAR, false)
        }

        @CheckResult
        fun autoGeneratePrimaryDark(context: Context): Boolean {
            return prefs(context).getBoolean(ThemeStorePrefKeys.KEY_AUTO_GENERATE_PRIMARYDARK, true)
        }

        @CheckResult
        fun isConfigured(context: Context): Boolean {
            return prefs(context).getBoolean(ThemeStorePrefKeys.IS_CONFIGURED_KEY, false)
        }

        @SuppressLint("CommitPrefEdits")
        fun isConfigured(context: Context, version: Int): Boolean {
            val prefs = prefs(context)
            val lastVersion = prefs.getInt(ThemeStorePrefKeys.IS_CONFIGURED_VERSION_KEY, -1)
            if (version > lastVersion) {
                prefs.edit { putInt(ThemeStorePrefKeys.IS_CONFIGURED_VERSION_KEY, version) }
                return false
            }
            return true
        }
    }
}