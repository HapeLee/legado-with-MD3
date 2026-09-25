package io.legado.app.ui.widget.components.privacy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.gateway.PrivateAccessGateway
import io.legado.app.domain.model.PrivateAccessState
import io.legado.app.help.security.BiometricUnlockLauncher
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 「验证后才能进入」的门槛（隐私设置页）。
 *
 * 两点与启动门槛刻意不同：
 * 1. **只校验、不授予**：走 [PrivateAccessGateway.verifyPasswordOnly]。用 `verifyPassword`
 *    会顺带把私密内容解锁——AppSession 频率下尤其明显：只是进设置看一眼，保险箱就开了。
 * 2. **取消只返回，不退出应用**：这只是"这一页不让进"，不是"不许用应用"。
 *
 * 未设本地密码时不拦：那一页本身就是"设置密码"的入口。
 */
@Composable
fun PrivateVerifyGate(
    onCancel: () -> Unit,
    gateway: PrivateAccessGateway = koinInject(),
    content: @Composable () -> Unit,
) {
    val access by gateway.state.collectAsStateWithLifecycle(PrivateAccessState())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var verified by rememberSaveable { mutableStateOf(false) }
    var autoLaunched by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    val title = stringResource(R.string.private_unlock_title)
    val description = stringResource(R.string.private_unlock_subtitle)
    val usePassword = stringResource(R.string.private_unlock_use_password)
    val passwordTitle = stringResource(R.string.private_unlock_password_title)
    val passwordErrorText = stringResource(R.string.private_unlock_password_error)

    fun submit(input: String) {
        scope.launch {
            if (gateway.verifyPasswordOnly(input)) {
                verified = true
                password = ""
                showPasswordDialog = false
            } else {
                // 输错属于"还没通过"，不是"放弃"：留在页面上让用户重试
                passwordError = true
            }
        }
    }

    fun launchVerify() {
        val activity = context.findFragmentActivity()
        if (activity != null && access.canUseBiometricShortcut) {
            BiometricUnlockLauncher.launch(
                activity = activity,
                title = title,
                subtitle = description,
                negativeButtonText = usePassword,
                onPassword = { submit(it) },
                onFallback = { showPasswordDialog = true },
                onCancel = onCancel,
                onError = { showPasswordDialog = true },
            )
        } else {
            showPasswordDialog = true
        }
    }

    // 总开关关掉时这一页不拦——否则关掉之后就再也进不去把它打开了
    val requiresVerification = access.isEnabled && access.hasPassword && !verified

    if (!requiresVerification) {
        content()
    } else {
        LaunchedEffect(Unit) {
            if (autoLaunched) return@LaunchedEffect
            autoLaunched = true
            launchVerify()
        }

        BackHandler { onCancel() }

        PrivateLockedPage(
            title = title,
            description = description,
            actionText = stringResource(R.string.private_verify_and_open),
            onAction = { launchVerify() },
            modifier = Modifier.fillMaxSize(),
        )

        if (showPasswordDialog) {
            AppAlertDialog(
                show = true,
                onDismissRequest = { onCancel() },
                title = passwordTitle,
                content = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AppTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                passwordError = false
                            },
                            label = passwordTitle,
                            backgroundColor = LegadoTheme.colorScheme.surface,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (passwordError) {
                            AppText(
                                text = passwordErrorText,
                                color = LegadoTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                },
                confirmText = stringResource(R.string.ok),
                onConfirm = {
                    val input = password
                    submit(input)
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { onCancel() },
            )
        }
    }
}
