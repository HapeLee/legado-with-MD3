package io.legado.app.domain.model.settings

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.PrivateUnlockScope

/**
 * 私密内容的验证时机与频率。
 *
 * 只描述"什么时候要求验证、验证后保持多久"；本地密码是否存在、生物识别是否可用属于
 * 能力状态，由 PrivateAccessState 承载，不混进设置项。
 */
@Stable
data class PrivateAccessSettings(
    /** 切到私密分组时验证 */
    val verifyOnEnterGroup: Boolean = true,
    /** 打开私密书籍时验证 */
    val verifyOnOpenBook: Boolean = true,
    /** 启动应用后主动验证一次（否则等到首次访问私密内容才验证） */
    val verifyOnAppStart: Boolean = false,
    /** 验证通过后保持多久：每次都要验证 / 本次进程验证一次 */
    val unlockScope: PrivateUnlockScope = PrivateUnlockScope.AppSession,
    /**
     * 总开关。关掉即整体休眠：不做任何验证、不脱敏、不放守卫；本地密码与书籍/分组标记
     * 全部留着，重新打开即刻恢复原样。
     *
     * 放在末尾是为了不破坏按位置构造的调用方。
     */
    val enabled: Boolean = true,
    /**
     * 离开前台后多久才要求重新验证（秒）。只在 [PrivateUnlockScope.UntilBackground] 下有意义：
     * 0 表示"离开即失效"，>0 表示这段时间内回来不重验。
     */
    val backgroundGraceSeconds: Int = 0,
)
