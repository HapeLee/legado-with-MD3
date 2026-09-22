package io.legado.app.domain.gateway

import io.legado.app.domain.model.PrivateAccessState
import io.legado.app.domain.model.PrivateUnlockTarget
import io.legado.app.domain.model.settings.PrivateAccessSettings
import kotlinx.coroutines.flow.Flow

/**
 * 私密内容解锁凭据。
 *
 * 本地密码是唯一权威凭据；生物识别只是"免手输密码"的快捷方式，生物验证成功后
 * 仍然是用本地密码完成解锁（见 help/security/PrivatePasswordCipher）。
 *
 * 契约里不出现 Keystore / Cipher / Activity 等平台类型：平台侧的弹框与解密由
 * help/security 下的组件完成，成功后仍回到 [verifyPassword] 这条唯一入口。
 */
interface PrivateAccessGateway {

    val state: Flow<PrivateAccessState>

    /** 验证时机设置 */
    val settings: Flow<PrivateAccessSettings>

    /** 与普通设置网关一致：一次 copy 原子提交相关字段 */
    suspend fun updateSettings(transform: (PrivateAccessSettings) -> PrivateAccessSettings)

    /**
     * 用本地密码解锁；成功返回 true，失败返回 false（调用方负责提示）。
     *
     * [target] 指明这次解锁是为了看什么：在 [PrivateUnlockScope.EveryTime] 下只授予该目标；
     * 传 null（如启动应用时验证）或频率为 [PrivateUnlockScope.AppSession] 时授予本次进程。
     */
    suspend fun verifyPassword(
        password: String,
        target: PrivateUnlockTarget? = null,
    ): Boolean

    /**
     * 只校验密码，**不授予任何访问**。
     *
     * 给"验证后才能进入"的场景用（隐私设置页）：这类场景要的是"证明你是本人"，
     * 而不是"解锁私密内容"。用 [verifyPassword] 会顺带把内容解锁——在 AppSession 频率下
     * 尤其明显：只是进设置看一眼，整个保险箱就打开了。
     */
    suspend fun verifyPasswordOnly(password: String): Boolean

    /** 离开目标时撤销其授权；仅在"每次验证"频率下有效 */
    fun revoke(target: PrivateUnlockTarget)

    /**
     * 同步判定"这个目标此刻是否已获准"。
     *
     * 给必须同步返回的路径用（AI 工具同步拼字符串、等不了挂起调用）。只读内存里的授权集合，
     * 不查盘、不做 IO；判定复用 [PrivateAccessState.isTargetGranted]，不另立一套规则。
     */
    fun isGrantedNow(bookUrl: String, group: Long): Boolean

    /**
     * 关闭生物快捷：清空信封并把开关落成关闭。
     *
     * 开启**不**在这里做——信封必须由一次真实的生物认证产出
     * （见 help/security/BiometricEnvelopeWriter），认证成功后经 [storeBiometricEnvelope] 落盘。
     * 把"开启"拆成两步，是为了让"信封究竟有没有写成"有唯一的事实来源。
     */
    suspend fun disableBiometricShortcut()

    /**
     * 保存生物认证后产出的信封。
     *
     * 只有真正写入成功才置位开关并返回 true；传入空信封或设备能力已不可用时返回 false。
     * 绝不留下"开关亮着、信封却是空的"这种假支持——那会让生物路径每次都在启动时静默回退。
     */
    suspend fun storeBiometricEnvelope(ciphertext: String, iv: String): Boolean

    /**
     * 重新探测设备生物识别能力（从设置页返回、录入新指纹后调用）。
     */
    fun refreshBiometricStatus()

    /** 主动回到锁定态 */
    fun lock()

    /**
     * 应用离开前台。
     *
     * 只在 [PrivateUnlockScope.UntilBackground] 下真正回到锁定态，其它频率下是空操作——
     * 调用方不需要自己判断频率，免得每个宿主各写一遍。
     */
    fun onAppBackground()
}
