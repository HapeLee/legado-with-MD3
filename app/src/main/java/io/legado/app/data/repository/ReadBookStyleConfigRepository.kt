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
class ReadBookStyleConfigRepository {

    fun save() {
        ReadBookConfig.save()
    }

    fun addStyle(): Int {
        ReadBookConfig.configList.add(ReadBookConfig.Config())
        save()
        return ReadBookConfig.configList.lastIndex
    }

    fun deleteCurrentStyle(): Boolean {
        val deleted = ReadBookConfig.deleteDur()
        if (deleted) {
            save()
        }
        return deleted
    }

    fun importCurrentStyle(bytes: ByteArray) {
        ReadBookConfig.durConfig = ReadBookConfig.import(bytes)
        save()
    }

    fun exportCurrentStyle(): ByteArray {
        return ReadBookConfig.export()
    }

    fun saveBackgroundImage(inputStream: InputStream, displayName: String?): String {
        return ReadBookConfig.saveBackgroundImage(inputStream, displayName)
    }

    fun setCurrentBackgroundImage(path: String) {
        ReadBookConfig.durConfig.setCurBg(2, path)
        save()
    }
}
