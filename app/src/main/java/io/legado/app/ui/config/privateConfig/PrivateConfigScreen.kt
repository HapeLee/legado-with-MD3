package io.legado.app.ui.config.privateConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.PrivateBiometricStatus
import io.legado.app.domain.model.PrivateUnlockScope
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateConfigScreen(
    state: PrivateConfigUiState,
    onIntent: (PrivateConfigIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.privacy),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.private_credential_title)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.private_feature_enabled),
                        description = stringResource(R.string.private_feature_enabled_summary),
                        checked = state.settings.enabled,
                        onCheckedChange = {
                            onIntent(PrivateConfigIntent.SetPrivateEnabled(it))
                        }
                    )

                    ClickableSettingItem(
                        title = stringResource(R.string.set_local_password),
                        description = stringResource(
                            if (state.hasLocalPassword) {
                                R.string.private_content_summary
                            } else {
                                R.string.private_content_no_password
                            }
                        ),
                        onClick = { onIntent(PrivateConfigIntent.ShowPasswordDialog) }
                    )

                    SwitchSettingItem(
                        title = stringResource(R.string.private_content_biometric),
                        description = stringResource(
                            when {
                                !state.hasLocalPassword -> R.string.private_content_no_password
                                !state.biometricSupported ->
                                    biometricStatusHint(state.biometricStatus)

                                else -> R.string.private_content_biometric_summary
                            }
                        ),
                        checked = state.biometricEnabled,
                        enabled = state.hasLocalPassword && state.biometricSupported,
                        onCheckedChange = {
                            onIntent(PrivateConfigIntent.SetBiometricShortcutEnabled(it))
                        }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.private_verify_timing_title)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.private_verify_on_enter_group),
                        description = stringResource(R.string.private_verify_on_enter_group_summary),
                        checked = state.settings.verifyOnEnterGroup,
                        enabled = state.hasLocalPassword,
                        onCheckedChange = {
                            onIntent(PrivateConfigIntent.SetVerifyOnEnterGroup(it))
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.private_verify_on_open_book),
                        description = stringResource(R.string.private_verify_on_open_book_summary),
                        checked = state.settings.verifyOnOpenBook,
                        enabled = state.hasLocalPassword,
                        onCheckedChange = {
                            onIntent(PrivateConfigIntent.SetVerifyOnOpenBook(it))
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.private_verify_on_app_start),
                        description = stringResource(R.string.private_verify_on_app_start_summary),
                        checked = state.settings.verifyOnAppStart,
                        enabled = state.hasLocalPassword,
                        onCheckedChange = {
                            onIntent(PrivateConfigIntent.SetVerifyOnAppStart(it))
                        }
                    )
                    DropdownListSettingItem(
                        title = stringResource(R.string.private_unlock_scope_title),
                        description = stringResource(R.string.private_unlock_scope_summary),
                        selectedValue = state.settings.unlockScope.name,
                        displayEntries = arrayOf(
                            stringResource(R.string.private_unlock_scope_every_time),
                            stringResource(R.string.private_unlock_scope_app_session),
                            stringResource(R.string.private_unlock_scope_until_background)
                        ),
                        entryValues = arrayOf(
                            PrivateUnlockScope.EveryTime.name,
                            PrivateUnlockScope.AppSession.name,
                            PrivateUnlockScope.UntilBackground.name
                        ),
                        onValueChange = { value ->
                            PrivateUnlockScope.entries.firstOrNull { it.name == value }
                                ?.let { onIntent(PrivateConfigIntent.SetUnlockScope(it)) }
                        }
                    )
                    // 只在"离开应用前有效"下才出现：其它频率下这个值没有意义
                    if (state.settings.unlockScope == PrivateUnlockScope.UntilBackground) {
                        DropdownListSettingItem(
                            title = stringResource(R.string.private_background_grace_title),
                            description = stringResource(R.string.private_background_grace_summary),
                            selectedValue = state.settings.backgroundGraceSeconds.toString(),
                            displayEntries = arrayOf(
                                stringResource(R.string.private_background_grace_immediately),
                                stringResource(R.string.private_background_grace_seconds, 30),
                                stringResource(R.string.private_background_grace_minutes, 2),
                                stringResource(R.string.private_background_grace_minutes, 3),
                                stringResource(R.string.private_background_grace_minutes, 5),
                            ),
                            // 值统一按秒存；秒/分钟只是给人看的
                            entryValues = arrayOf("0", "30", "120", "180", "300"),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let {
                                    onIntent(PrivateConfigIntent.SetBackgroundGraceSeconds(it))
                                }
                            }
                        )
                    }
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.private_permission_title)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.notification_permission),
                        description = stringResource(R.string.notification_permission_rationale),
                        onClick = { onIntent(PrivateConfigIntent.RequestNotificationPermission) }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.background_permission),
                        description = stringResource(R.string.ignore_battery_permission_rationale),
                        onClick = { onIntent(PrivateConfigIntent.RequestBatteryPermission) }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.firebase_enable_title),
                        description = stringResource(R.string.firebase_enable_summary),
                        checked = state.firebaseEnable,
                        onCheckedChange = {
                            onIntent(PrivateConfigIntent.SetFirebaseEnabled(it))
                        }
                    )
                }
            }
        }
    }

    if (state.showPasswordDialog) {
        var currentPassword by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        val passwordKeyboard = KeyboardOptions(keyboardType = KeyboardType.Password)
        AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(PrivateConfigIntent.DismissPasswordDialog) },
            title = stringResource(
                if (state.hasLocalPassword) {
                    R.string.private_change_password
                } else {
                    R.string.set_local_password
                }
            ),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 已有密码时先要旧密码：本地密码是私密内容的唯一凭据，
                    // 不验旧密码就等于"谁拿到这台手机，谁就能把锁换成自己的"
                    if (state.hasLocalPassword) {
                        AppTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = stringResource(R.string.private_current_password_title),
                            backgroundColor = LegadoTheme.colorScheme.surface,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = passwordKeyboard,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AppTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.private_unlock_password_title),
                        backgroundColor = LegadoTheme.colorScheme.surface,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = passwordKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmText = stringResource(R.string.ok),
            onConfirm = {
                onIntent(PrivateConfigIntent.SavePassword(password, currentPassword))
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(PrivateConfigIntent.DismissPasswordDialog) },
        )
    }
}

private fun biometricStatusHint(status: PrivateBiometricStatus): Int = when (status) {
    PrivateBiometricStatus.NotEnrolled -> R.string.private_biometric_not_enrolled
    PrivateBiometricStatus.NoHardware -> R.string.private_biometric_no_hardware
    PrivateBiometricStatus.UnsupportedSystem -> R.string.private_biometric_unsupported_system
    else -> R.string.private_biometric_unavailable
}
