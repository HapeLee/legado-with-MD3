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

    /** 在自建样式区（内置预设那几份之外）内重排；返回是否真的移动了。 */
    fun moveStyle(from: Int, to: Int): Boolean

    /** 批量删除自建样式（下标按删除前算）；返回是否真的删了。 */
    fun deleteStylesAt(indices: List<Int>): Boolean

    /** 把选中样式整块搬到边缘：置顶 = 紧跟预设之后，置底 = 列表末尾；块内保持原相对次序。 */
    fun moveStylesToEdge(indices: List<Int>, toTop: Boolean): Boolean
    fun importCurrentStyle(bytes: ByteArray)
    fun importOrReplaceStyle(bytes: ByteArray): String

    /**
     * 导入为**新增**样式：全部追加到列表末尾，绝不碰任何已有主题（也不改当前选中）。
     *
     * @return 单份导入时返回新样式的下标（调用方据此"立刻应用"）；
     *         批量包返回 null —— 批量必须保持静默，不切换正在使用的主题。
     */
    fun importStylesAsNew(bytes: ByteArray): Int?
    fun exportCurrentStyle(): ByteArray

    /**
     * 批量导出：外层 zip 套若干「单样式包」。按各样式本来的样子导
     * （不混入共享排版的字体项——那套合并语义只属于单份 [exportCurrentStyle]）。
     */
    fun exportStyles(indices: List<Int>): ByteArray

    /**
     * 单份导出指定下标的那份样式（产物 = 批量包里对应那一个小包，格式完全一致）。
     * 下标无效返回 null。用于"只选中 1 个时直接分享单份"。
     */
    fun exportStyleAt(index: Int): ByteArray?
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
