package io.legado.app.help.config

import io.legado.app.domain.gateway.ReadSettingsGateway
import org.koin.core.context.GlobalContext

/**
 * 段评气泡的全局开关与当前气泡包。
 *
 * 真正落盘由 [ReadSettingsGateway]（DataStore）负责；气泡包本身仍由
 * [BubblePackageManager] 以目录形式管理。
 *
 * 读取侧必须容错：图片尺寸解析可能在 Koin 尚未就绪的早期路径上被调用，
 * 那时退化成「内置气泡 + 不强制软件气泡」，而不是让整章排版崩掉。
 */
object BubbleSettings {

    private val gateway: ReadSettingsGateway? by lazy {
        runCatching { GlobalContext.get().get<ReadSettingsGateway>() }.getOrNull()
    }

    /** 当前生效的气泡包目录名。 */
    val activeDirName: String
        get() = gateway?.currentSettings?.paragraphBubblePackage
            ?.ifBlank { BubblePackageManager.BUILTIN_DIR_NAME }
            ?: BubblePackageManager.BUILTIN_DIR_NAME

    /** 正文里图片形式的段评入口是否强制改用软件气泡渲染。 */
    val forceSoftwareParagraphBubble: Boolean
        get() = gateway?.currentSettings?.forceSoftwareParagraphBubble ?: false

    suspend fun setActiveDirName(dirName: String) {
        requireNotNull(gateway) { "settings gateway is not ready" }
            .update { it.copy(paragraphBubblePackage = dirName) }
    }

    suspend fun setForceSoftwareParagraphBubble(enabled: Boolean) {
        requireNotNull(gateway) { "settings gateway is not ready" }
            .update { it.copy(forceSoftwareParagraphBubble = enabled) }
    }
}
