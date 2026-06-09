package io.legado.app.data.repository

import io.legado.app.help.config.ReadBookConfig
import java.io.InputStream

/**
 * Repository boundary for the legacy read style config file.
 *
 * The underlying model is still [ReadBookConfig.Config] because each read style
 * keeps a full layout/background/text configuration. This wrapper centralizes
 * file mutations so UI and ViewModel code do not call persistence helpers directly.
 */
class ReadBookStyleConfigRepository(
    private val readStyleRepository: ReadStyleRepository,
    private val highlightRuleRepository: HighlightRuleRepository,
) {

    fun save() {
        ReadBookConfig.save()
    }

    fun addStyle(): Int {
        ReadBookConfig.configList.add(ReadBookConfig.Config())
        save()
        return ReadBookConfig.configList.lastIndex
    }

    fun deleteCurrentStyle(): Boolean {
        val deletedConfigName = ReadBookConfig.durConfig.name
        val deleted = ReadBookConfig.deleteDur()
        if (deleted) {
            highlightRuleRepository.removeConfigBinding(deletedConfigName)
            save()
        }
        return deleted
    }

    fun importCurrentStyle(bytes: ByteArray) {
        ReadBookConfig.durConfig = readStyleRepository.import(bytes)
        save()
    }

    fun exportCurrentStyle(): ByteArray {
        val config = ReadBookConfig.getExportConfig().copy(
            highlightRules = ArrayList(highlightRuleRepository.load(ReadBookConfig.durConfig.name))
        )
        return readStyleRepository.export(config)
    }

    fun saveBackgroundImage(inputStream: InputStream, displayName: String?): String {
        return ReadBookConfig.saveBackgroundImage(inputStream, displayName)
    }

    fun setCurrentBackgroundImage(path: String) {
        ReadBookConfig.durConfig.setCurBg(2, path)
        save()
    }

    fun setCurrentBackgroundImageForMode(path: String, isNight: Boolean) {
        if (isNight) {
            ReadBookConfig.durConfig.bgTypeNight = 2
            ReadBookConfig.durConfig.bgStrNight = path
        } else {
            ReadBookConfig.durConfig.bgType = 2
            ReadBookConfig.durConfig.bgStr = path
        }
        save()
    }

}
