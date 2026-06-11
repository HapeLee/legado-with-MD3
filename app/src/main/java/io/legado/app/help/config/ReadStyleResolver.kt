package io.legado.app.help.config

import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import com.google.android.material.color.MaterialColors
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.resizeAndRecycle
import splitties.init.appCtx
import java.io.File

object ReadStyleResolver {

    enum class ReadStyleMode {
        Day,
        Night,
        EInk
    }

    data class ReadBackground(
        val type: Int,
        val value: String
    )

    fun currentMode(): ReadStyleMode {
        return when {
            ReadConfig.isEInkMode -> ReadStyleMode.EInk
            ReadConfig.isNightTheme -> ReadStyleMode.Night
            else -> ReadStyleMode.Day
        }
    }

    fun isNightTheme(): Boolean {
        return ReadConfig.isNightTheme
    }

    fun setCurrentBackground(
        config: ReadBookConfig.Config,
        bgType: Int,
        bg: String
    ) {
        when (currentMode()) {
            ReadStyleMode.EInk -> {
                config.bgTypeEInk = bgType
                config.bgStrEInk = bg
            }

            ReadStyleMode.Night -> {
                config.bgTypeNight = bgType
                config.bgStrNight = bg
            }

            ReadStyleMode.Day -> {
                config.bgType = bgType
                config.bgStr = bg
            }
        }
    }

    fun currentBackground(config: ReadBookConfig.Config): ReadBackground {
        return when (currentMode()) {
            ReadStyleMode.EInk -> ReadBackground(config.bgTypeEInk, config.bgStrEInk)
            ReadStyleMode.Night -> ReadBackground(config.bgTypeNight, config.bgStrNight)
            ReadStyleMode.Day -> ReadBackground(config.bgType, config.bgStr)
        }
    }

    fun backgroundPath(config: ReadBookConfig.Config, bgIndex: Int): String? {
        val bgType = when (bgIndex) {
            0 -> config.bgType
            1 -> config.bgTypeNight
            2 -> config.bgTypeEInk
            else -> error("unknown bgIndex: $bgIndex")
        }
        if (bgType != 2) {
            return null
        }
        val bgStr = when (bgIndex) {
            0 -> config.bgStr
            1 -> config.bgStrNight
            2 -> config.bgStrEInk
            else -> error("unknown bgIndex: $bgIndex")
        }
        return if (bgStr.contains(File.separator)) {
            bgStr
        } else {
            FileUtils.getPath(appCtx.externalFiles, "bg", bgStr)
        }
    }

    fun currentBackgroundDrawable(
        config: ReadBookConfig.Config,
        width: Int,
        height: Int
    ): Drawable {
        if (width == 0 || height == 0) {
            return fallbackBackground()
        }

        var bgDrawable: Drawable? = null
        val resources = appCtx.resources
        val background = currentBackground(config)
        try {
            bgDrawable = when (background.type) {
                0 -> background.value.toColorInt().toDrawable()
                1 -> {
                    val path = "bg" + File.separator + background.value
                    val bitmap = BitmapUtils.decodeAssetsBitmap(appCtx, path, width, height)
                    bitmap?.resizeAndRecycle(width, height)?.toDrawable(resources)
                }
                else -> {
                    val path = background.value.let {
                        if (it.contains(File.separator)) it
                        else FileUtils.getPath(appCtx.externalFiles, "bg", background.value)
                    }
                    val bitmap = BitmapUtils.decodeBitmap(path, width, height)
                    bitmap?.resizeAndRecycle(width, height)?.toDrawable(resources)
                }
            }
        } catch (e: OutOfMemoryError) {
            e.printOnDebug()
        } catch (e: Exception) {
            e.printOnDebug()
        }

        return bgDrawable ?: fallbackBackground()
    }

    private fun fallbackBackground(): Drawable {
        val fallbackColor = MaterialColors.getColor(
            appCtx,
            com.google.android.material.R.attr.colorSurface,
            Color.WHITE
        )
        return fallbackColor.toDrawable()
    }
}
