package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ReadStyleState
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream

interface ReadStyleGateway {
    val currentState: ReadStyleState
    val state: StateFlow<ReadStyleState>

    fun refresh()
    fun save()

    /**
     * 日夜 / 墨水屏模式切换后调用。
     *
     * 排版值本身没变，但**解析后的生效值**变了（`curTextColor()`/`curUnderlineColor()`/
     * `curTextShadowColor()` 等都按当前模式取值），订阅方必须重新派生快照。
     */
    fun notifyModeChanged()
    fun updateCurrentStyle(mutation: ReadStyleMutation)

    /**
     * 排版时发现当前字体文件已不存在——清空字体设置并落盘，下次排版回落到系统字体。
     */
    fun clearMissingTextFont()
    fun applyPreset(index: Int): Boolean
    fun addStyle(): Int
    fun deleteCurrentStyle(): Boolean
    fun importCurrentStyle(bytes: ByteArray)
    fun importOrReplaceStyle(bytes: ByteArray): String
    fun exportCurrentStyle(): ByteArray
    fun saveBackgroundImage(inputStream: InputStream, displayName: String?): String
    fun setCurrentBackgroundImage(path: String)
    fun setCurrentBackgroundImageForMode(path: String, isNight: Boolean)
    fun exportConfigsJson(): String
    fun exportShareConfigJson(): String

    /** 备份要把所有排版引用到的背景图一起带走。 */
    fun allBackgroundImagePaths(): List<String>

    /** 清掉没有任何排版引用的背景图，以及导入导出留下的缓存。 */
    fun clearUnusedBackgrounds()
}
