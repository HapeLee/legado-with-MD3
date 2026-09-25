package io.legado.app.help.storage

import io.legado.app.data.local.preferences.LocalPreferencesKeys
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupConfigSecurityTest {

    @Test
    fun `local password and migration marker are never exported`() {
        assertTrue(LocalPreferencesKeys.PASSWORD.name in alwaysIgnoredPreferenceKeys)
        assertTrue(LocalPreferencesKeys.MIGRATED_TO_SETTINGS.name in alwaysIgnoredPreferenceKeys)
    }

    /**
     * 私密解锁的派生凭据一律不进备份：
     * - salt + verifier 是可离线爆破的产物，密码通常很短，PBKDF2 挡不住 GPU；
     * - 生物信封与开关绑在本机 Keystore 上，导出只会得到"开关亮着但解不开"的假状态。
     */
    @Test
    fun `private access credentials are never exported`() {
        assertTrue(LocalPreferencesKeys.PRIVATE_PASSWORD_SALT.name in alwaysIgnoredPreferenceKeys)
        assertTrue(LocalPreferencesKeys.PRIVATE_PASSWORD_VERIFIER.name in alwaysIgnoredPreferenceKeys)
        assertTrue(LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENABLED.name in alwaysIgnoredPreferenceKeys)
        assertTrue(LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENVELOPE.name in alwaysIgnoredPreferenceKeys)
        assertTrue(LocalPreferencesKeys.PRIVATE_BIOMETRIC_IV.name in alwaysIgnoredPreferenceKeys)
    }
}
