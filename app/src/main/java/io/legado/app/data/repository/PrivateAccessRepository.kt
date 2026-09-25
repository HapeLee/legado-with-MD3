package io.legado.app.data.repository

import android.content.Context
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.domain.gateway.PrivateAccessGateway
import io.legado.app.domain.model.PrivateAccessState
import io.legado.app.domain.model.PrivateBiometricStatus
import io.legado.app.domain.model.PrivateUnlockScope
import io.legado.app.domain.model.PrivateUnlockTarget
import io.legado.app.domain.model.settings.PrivateAccessSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.security.BiometricUnlockLauncher
import io.legado.app.help.security.PrivatePasswordCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 私密内容解锁状态宿主。
 *
 * 解锁状态只放在进程内存里（[unlockedFlow] 与按目标的授权集合）：进程重启即回到锁定态，
 * 符合"一次启动验证一次"的约定，不做超时、不做切后台重锁。
 *
 * 两种读写路径并存是有意的：设置项走 [SettingsRepository] 的 flow（需要把变化推给界面），
 * 凭据（salt / 校验值 / 信封）走 [AppConfigStore] 直读写——它们是"校验时读一次"的短值，
 * 不需要被观察，也不该在 flow 里反复解码。两条路径落在同一份 SharedPreferences 上，
 * 所以不存在"界面看到的和读到的不一致"。
 */
class PrivateAccessRepository(
    context: Context,
    private val settingsRepository: SettingsRepository,
) : PrivateAccessGateway {

    private val appContext = context.applicationContext

    private val unlockedFlow = MutableStateFlow(false)

    /** 「启动应用时验证」这道门槛是否已过。与进程级解锁分开：它不授权任何内容 */
    private val appStartVerifiedFlow = MutableStateFlow(false)

    /** 仅"每次验证"频率下使用：刚验证过的那一个书籍 / 分组 */
    private val grantedBookUrlsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val grantedGroupIdsFlow = MutableStateFlow<Set<Long>>(emptySet())

    private val biometricStatusFlow = MutableStateFlow(
        BiometricUnlockLauncher.detectStatus(appContext)
    )

    private val hasPasswordFlow = settingsRepository
        .getString(LocalPreferencesKeys.PASSWORD.name, "")
        .map { it.isNotEmpty() }
        .distinctUntilChanged()

    private val biometricEnabledFlow = settingsRepository
        .getBoolean(LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENABLED.name, false)
        .distinctUntilChanged()

    private val enabledFlow = settingsRepository
        .getBoolean(LocalPreferencesKeys.PRIVATE_ENABLED.name, true)
        .distinctUntilChanged()

    private val backgroundGraceFlow = settingsRepository
        .getInt(LocalPreferencesKeys.PRIVATE_BACKGROUND_GRACE_SECONDS.name, 0)
        .distinctUntilChanged()

    private val verifyOnEnterGroupFlow = settingsRepository
        .getBoolean(LocalPreferencesKeys.PRIVATE_VERIFY_ON_ENTER_GROUP.name, true)
        .distinctUntilChanged()

    private val verifyOnOpenBookFlow = settingsRepository
        .getBoolean(LocalPreferencesKeys.PRIVATE_VERIFY_ON_OPEN_BOOK.name, true)
        .distinctUntilChanged()

    private val verifyOnAppStartFlow = settingsRepository
        .getBoolean(LocalPreferencesKeys.PRIVATE_VERIFY_ON_APP_START.name, false)
        .distinctUntilChanged()

    private val unlockScopeFlow = settingsRepository
        .getString(
            LocalPreferencesKeys.PRIVATE_UNLOCK_SCOPE.name,
            PrivateUnlockScope.AppSession.name
        )
        .map { stored ->
            PrivateUnlockScope.entries.firstOrNull { it.name == stored }
                ?: PrivateUnlockScope.AppSession
        }
        .distinctUntilChanged()

    override val settings: Flow<PrivateAccessSettings> = combine(
        verifyOnEnterGroupFlow,
        verifyOnOpenBookFlow,
        verifyOnAppStartFlow,
        unlockScopeFlow,
        enabledFlow
    ) { verifyOnEnterGroup, verifyOnOpenBook, verifyOnAppStart, unlockScope, enabled ->
        PrivateAccessSettings(
            verifyOnEnterGroup = verifyOnEnterGroup,
            verifyOnOpenBook = verifyOnOpenBook,
            verifyOnAppStart = verifyOnAppStart,
            unlockScope = unlockScope,
            enabled = enabled
        )
    }.combine(backgroundGraceFlow) { settings, graceSeconds ->
        settings.copy(backgroundGraceSeconds = graceSeconds)
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    override suspend fun updateSettings(transform: (PrivateAccessSettings) -> PrivateAccessSettings) {
        val current = readSettings()
        val next = transform(current)
        if (next == current) return
        AppConfigStore.putAllAndAwait(
            mapOf(
                LocalPreferencesKeys.PRIVATE_VERIFY_ON_ENTER_GROUP.name to next.verifyOnEnterGroup,
                LocalPreferencesKeys.PRIVATE_VERIFY_ON_OPEN_BOOK.name to next.verifyOnOpenBook,
                LocalPreferencesKeys.PRIVATE_VERIFY_ON_APP_START.name to next.verifyOnAppStart,
                LocalPreferencesKeys.PRIVATE_UNLOCK_SCOPE.name to next.unlockScope.name,
                LocalPreferencesKeys.PRIVATE_ENABLED.name to next.enabled,
                LocalPreferencesKeys.PRIVATE_BACKGROUND_GRACE_SECONDS.name to
                        next.backgroundGraceSeconds
            )
        )
    }

    override val state: Flow<PrivateAccessState> = combine(
        unlockedFlow,
        hasPasswordFlow,
        biometricStatusFlow,
        biometricEnabledFlow,
        grantedBookUrlsFlow
    ) { unlocked, hasPassword, status, biometricEnabled, grantedBookUrls ->
        AccessSnapshot(unlocked, hasPassword, status, biometricEnabled, grantedBookUrls)
    }.combine(grantedGroupIdsFlow) { snapshot, grantedGroupIds ->
        PrivateAccessState(
            isUnlocked = snapshot.isUnlocked,
            hasPassword = snapshot.hasPassword,
            biometricStatus = snapshot.biometricStatus,
            biometricEnabled = snapshot.biometricEnabled,
            grantedBookUrls = snapshot.grantedBookUrls,
            grantedGroupIds = grantedGroupIds
        )
    }.combine(appStartVerifiedFlow) { state, appStartVerified ->
        state.copy(isAppStartVerified = appStartVerified)
    }.combine(enabledFlow) { state, enabled ->
        // 总开关必须接进 state：所有"要不要验证/要不要脱敏"的判定都读 state.isTargetGranted，
        // 只把它放进 settings 的话，开关对验证与脱敏完全不起作用
        //（只有走同步入口 isGrantedNow 的 AI 守卫会生效——那正是这个 bug 之前的表现）。
        state.copy(isEnabled = enabled)
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private data class AccessSnapshot(
        val isUnlocked: Boolean,
        val hasPassword: Boolean,
        val biometricStatus: PrivateBiometricStatus,
        val biometricEnabled: Boolean,
        val grantedBookUrls: Set<String>,
    )

    /** 校验 + 授权必须串行：并发两次验证不能出现"其中一次白验" */
    private val unlockMutex = Mutex()

    override suspend fun verifyPasswordOnly(password: String): Boolean =
        withContext(Dispatchers.Default) { checkPassword(password) }

    /** 校验密码本身。不碰任何授权状态——授权由 [verifyPassword] 决定 */
    private suspend fun checkPassword(password: String): Boolean {
        if (password.isEmpty()) return false
        if (!ensurePasswordVerifier()) return false
        val salt =
            AppConfigStore.getString(LocalPreferencesKeys.PRIVATE_PASSWORD_SALT.name).orEmpty()
        val verifier =
            AppConfigStore.getString(LocalPreferencesKeys.PRIVATE_PASSWORD_VERIFIER.name).orEmpty()
        return PrivatePasswordCipher.verify(password, salt, verifier)
    }

    override suspend fun verifyPassword(
        password: String,
        target: PrivateUnlockTarget?,
    ): Boolean = withContext(Dispatchers.Default) {
        if (!checkPassword(password)) return@withContext false

        unlockMutex.withLock {
            when {
                // 进程级 / 到后台为止：任意一次验证通过即放行全部私密内容
                // （两者的差别只在"什么时候失效"，见 onAppBackground）
                readSettings().unlockScope != PrivateUnlockScope.EveryTime ->
                    unlockedFlow.value = true

                // 启动验证（target == null）只是"进入应用"的门槛，不是对内容的授权：
                // 记一笔"过了门槛"即可。否则"每次都要验证"的用户过一次启动验证
                // 就再也不被问，分组与单书验证会形同虚设。
                target == null -> appStartVerifiedFlow.value = true

                else -> when (target) {
                    is PrivateUnlockTarget.Book ->
                        grantedBookUrlsFlow.value = grantedBookUrlsFlow.value + target.bookUrl

                    is PrivateUnlockTarget.Group ->
                        grantedGroupIdsFlow.value = grantedGroupIdsFlow.value + target.groupId
                }
            }
        }
        true
    }

    override fun isGrantedNow(bookUrl: String, group: Long): Boolean = PrivateAccessState(
        isUnlocked = unlockedFlow.value,
        grantedBookUrls = grantedBookUrlsFlow.value,
        grantedGroupIds = grantedGroupIdsFlow.value,
        // 读内存里的当前值而不是 flow：这个函数是同步的（AI 工具等同步路径要用）
        isEnabled = AppConfigStore.getBoolean(LocalPreferencesKeys.PRIVATE_ENABLED.name) ?: true,
    ).isTargetGranted(bookUrl, group)

    override fun revoke(target: PrivateUnlockTarget) {
        // 只有"每次验证"才需要撤销；进程级解锁时撤销没有意义
        if (readSettings().unlockScope != PrivateUnlockScope.EveryTime) return
        when (target) {
            is PrivateUnlockTarget.Book ->
                grantedBookUrlsFlow.value = grantedBookUrlsFlow.value - target.bookUrl

            is PrivateUnlockTarget.Group ->
                grantedGroupIdsFlow.value = grantedGroupIdsFlow.value - target.groupId
        }
    }

    override suspend fun disableBiometricShortcut() = withContext(Dispatchers.Default) {
        AppConfigStore.putAllAndAwait(
            mapOf(
                LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENABLED.name to false,
                LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENVELOPE.name to "",
                LocalPreferencesKeys.PRIVATE_BIOMETRIC_IV.name to ""
            )
        )
    }

    override suspend fun storeBiometricEnvelope(ciphertext: String, iv: String): Boolean =
        withContext(Dispatchers.Default) {
            if (ciphertext.isBlank() || iv.isBlank()) return@withContext false
            val status = BiometricUnlockLauncher.detectStatus(appContext)
            biometricStatusFlow.value = status
            if (status != PrivateBiometricStatus.Available) return@withContext false
            AppConfigStore.putAllAndAwait(
                mapOf(
                    LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENVELOPE.name to ciphertext,
                    LocalPreferencesKeys.PRIVATE_BIOMETRIC_IV.name to iv,
                    LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENABLED.name to true
                )
            )
            true
        }

    override fun refreshBiometricStatus() {
        biometricStatusFlow.value = BiometricUnlockLauncher.detectStatus(appContext)
    }

    override fun onAppBackground() {
        // 只有"到后台为止"这种频率需要在这里失效；其它频率下切后台不该有任何影响
        if (readSettings().unlockScope != PrivateUnlockScope.UntilBackground) return
        lock()
    }

    override fun lock() {
        unlockedFlow.value = false
        // 主动回到锁定态同理要重新过启动门槛，否则"锁了却还留在应用里"
        appStartVerifiedFlow.value = false
        grantedBookUrlsFlow.value = emptySet()
        grantedGroupIdsFlow.value = emptySet()
    }

    /**
     * 老用户兜底：升级前设过本地密码的人只有 PASSWORD，没有 salt / 校验值
     * （这两个键是随私密功能一起引入的）。先补建一次，否则他们输入正确密码也永远校验失败，
     * 界面上还会一直挂着"验证"入口。
     *
     * PASSWORD 被刻意排除在备份之外，所以不能指望从恢复流程里带出这套值，只能就地生成。
     */
    private suspend fun ensurePasswordVerifier(): Boolean {
        val existingSalt =
            AppConfigStore.getString(LocalPreferencesKeys.PRIVATE_PASSWORD_SALT.name).orEmpty()
        val existingVerifier =
            AppConfigStore.getString(LocalPreferencesKeys.PRIVATE_PASSWORD_VERIFIER.name).orEmpty()
        if (existingSalt.isNotEmpty() && existingVerifier.isNotEmpty()) return true
        val legacyPassword = AppConfigStore.getString(LocalPreferencesKeys.PASSWORD.name).orEmpty()
        if (legacyPassword.isEmpty()) return false
        val salt = PrivatePasswordCipher.generateSalt()
        val verifier = PrivatePasswordCipher.deriveVerifier(legacyPassword, salt) ?: return false
        AppConfigStore.putAllAndAwait(
            mapOf(
                LocalPreferencesKeys.PRIVATE_PASSWORD_SALT.name to salt,
                LocalPreferencesKeys.PRIVATE_PASSWORD_VERIFIER.name to verifier
            )
        )
        return true
    }

    private fun readSettings(): PrivateAccessSettings = PrivateAccessSettings(
        verifyOnEnterGroup = AppConfigStore.getBoolean(
            LocalPreferencesKeys.PRIVATE_VERIFY_ON_ENTER_GROUP.name
        ) ?: true,
        verifyOnOpenBook = AppConfigStore.getBoolean(
            LocalPreferencesKeys.PRIVATE_VERIFY_ON_OPEN_BOOK.name
        ) ?: true,
        verifyOnAppStart = AppConfigStore.getBoolean(
            LocalPreferencesKeys.PRIVATE_VERIFY_ON_APP_START.name
        ) ?: false,
        unlockScope = AppConfigStore.getString(LocalPreferencesKeys.PRIVATE_UNLOCK_SCOPE.name)
            ?.let { stored -> PrivateUnlockScope.entries.firstOrNull { it.name == stored } }
            ?: PrivateUnlockScope.AppSession,
        enabled = AppConfigStore.getBoolean(LocalPreferencesKeys.PRIVATE_ENABLED.name) ?: true,
        backgroundGraceSeconds =
            AppConfigStore.getInt(LocalPreferencesKeys.PRIVATE_BACKGROUND_GRACE_SECONDS.name) ?: 0
    )

}
