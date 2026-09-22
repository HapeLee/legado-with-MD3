package io.legado.app.domain.model

/**
 * 生物识别能力的显式建模。
 *
 * 平台能力不可用时必须如实上报，不得用"可用"伪造支持：书架与设置页据此决定
 * 是弹出系统验证框、还是直接回退到应用内密码输入。
 */
enum class PrivateBiometricStatus {
    /** 设备有可用生物识别且已录入凭据 */
    Available,

    /** 有硬件但尚未录入指纹/人脸 */
    NotEnrolled,

    /** 设备没有生物识别硬件 */
    NoHardware,

    /** 硬件暂时不可用（系统策略、维修模式等） */
    Unavailable,

    /** 系统版本过低，无法做加密绑定的生物解锁 */
    UnsupportedSystem
}

/** 验证通过后保持多久 */
enum class PrivateUnlockScope {
    /** 每次进入私密分组/打开私密书籍都要重新验证 */
    EveryTime,

    /** 进入应用验证一次，本次进程内不再重复 */
    AppSession,

    /**
     * 验证一次，直到应用离开前台为止。
     *
     * 介于上面两者之间：不像 [EveryTime] 那样每本书都要问一次，也不像 [AppSession]
     * 那样进程活着就一直免验证——切到后台再回来就要重新验证。
     */
    UntilBackground,
}

/**
 * 私密内容访问状态。
 *
 * 解锁状态只存活于当前进程：进程重启后回到锁定态。
 * [grantedBookUrls] / [grantedGroupIds] 只在 [PrivateUnlockScope.EveryTime] 下使用，
 * 记录"刚刚验证过的那一个目标"，离开它即失效。
 */
data class PrivateAccessState(
    val isUnlocked: Boolean = false,
    val hasPassword: Boolean = false,
    /**
     * 是否已通过"启动应用时验证"这道门槛。
     *
     * 与 [isUnlocked] 是两件事：启动验证只证明"进得来"，不代表对任何分组/书籍的授权。
     * "每次都要验证"频率下过一次启动验证就把全部私密内容解锁，会让分组与单书验证形同虚设。
     */
    val isAppStartVerified: Boolean = false,
    val biometricStatus: PrivateBiometricStatus = PrivateBiometricStatus.Unavailable,
    val biometricEnabled: Boolean = false,
    val grantedBookUrls: Set<String> = emptySet(),
    val grantedGroupIds: Set<Long> = emptySet(),
    /**
     * 私密功能总开关。关掉时 [isTargetGranted] 恒真——不验证、不脱敏、不守卫；
     * 密码与标记都留着，重新打开即刻恢复。判定集中在这里，所以各处不用各判一次。
     */
    val isEnabled: Boolean = true,
) {
    /** 是否可以直接弹系统生物验证框（而不是密码输入） */
    val canUseBiometricShortcut: Boolean
        get() = biometricEnabled && biometricStatus == PrivateBiometricStatus.Available

    /** 私密功能是否可用：没有本地密码时一律不可用 */
    val isAvailable: Boolean get() = hasPassword

    /**
     * 某个目标当前是否已获准查看。
     *
     * [group] 是书籍的分组位掩码：所属分组已获准时，组内书籍一并视为已获准，
     * 否则"进了私密分组还要逐本再验证"会非常吵。
     */
    fun isTargetGranted(bookUrl: String?, group: Long): Boolean =
        !isEnabled ||
                isUnlocked ||
                (bookUrl != null && bookUrl in grantedBookUrls) ||
                (group != 0L && grantedGroupIds.any { (group and it) != 0L })
}

/**
 * 私密判定的唯一实现：单本标记 ∪ 所属私密分组。
 *
 * 纯函数，便于单测；数据层与 UI 都只复用它，避免长出第二套判定。
 */
fun isPrivateBook(
    bookUrl: String,
    group: Long,
    privateBookUrls: Set<String>,
    privateGroupMask: Long,
): Boolean = bookUrl in privateBookUrls ||
        (privateGroupMask != 0L && (group and privateGroupMask) != 0L)

/**
 * 一本书的私密事实：是否私密 + 所属分组位掩码。
 *
 * 中心 gate（阅读器路由入口）只拿得到一个 bookUrl，需要这两项才能复用
 * [PrivateAccessState.isTargetGranted] 的分组授权语义——否则"刚在书架解锁了私密分组"
 * 的用户，从组内点开一本书会被再问一次。
 */
data class PrivateBookFacts(
    val isPrivate: Boolean,
    val groupMask: Long,
)

sealed interface PrivateUnlockTarget {
    /** 打开某本私密书籍（书架点击 / 详情页进入） */
    data class Book(val bookUrl: String) : PrivateUnlockTarget

    /** 查看某个私密分组内容 */
    data class Group(val groupId: Long) : PrivateUnlockTarget
}
