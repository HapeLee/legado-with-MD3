package io.legado.app.ui.widget.components.privacy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.model.PrivateAccessState
import io.legado.app.domain.model.PrivateBookFacts
import io.legado.app.domain.model.PrivateUnlockTarget
import io.legado.app.domain.model.settings.PrivateAccessSettings
import io.legado.app.help.security.BiometricUnlockLauncher
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.utils.toastOnUi
import org.koin.compose.viewmodel.koinViewModel

/**
 * 中心 gate 的状态。
 *
 * [isLocked] 是**派生**的而不是一次判定的结果：解锁成功后 [access] 会变化，状态自然翻到
 * 未锁定，"验证成功后自动进阅读器"因此不需要额外的回调管道。
 */
@Stable
data class PrivateReadGateUiState(
    val isChecking: Boolean = true,
    val bookUrl: String? = null,
    val facts: PrivateBookFacts = PrivateBookFacts(isPrivate = false, groupMask = 0L),
    val access: PrivateAccessState = PrivateAccessState(),
    val settings: PrivateAccessSettings = PrivateAccessSettings(),
) {
    val target: PrivateUnlockTarget? get() = bookUrl?.let(PrivateUnlockTarget::Book)

    val isLocked: Boolean
        get() = !isChecking && bookUrl != null && facts.isPrivate &&
                settings.verifyOnOpenBook &&
                !access.isTargetGranted(bookUrl, facts.groupMask)
}

sealed interface PrivateReadGateEffect {
    data class ShowMessage(val message: String) : PrivateReadGateEffect
    data object NavigateToLocalPasswordSettings : PrivateReadGateEffect
}

/**
 * 阅读器路由的私密闸门。
 *
 * 未授权时用**全屏锁定页**取代阅读器内容——不是"先打开再遮住"，阅读器在这个分支里
 * 根本不会被组合，也就没有加载正文的机会。验证通过后内容分支自然接上，就是"自动进阅读器"。
 *
 * @param onExit 取消时退出的动作：调用方负责"退回当前页，没有当前页就退回书架"
 */
@Composable
fun PrivateReadGate(
    bookUrl: String?,
    onExit: () -> Unit,
    onOpenLocalPasswordSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    val viewModel = koinViewModel<PrivateReadGateViewModel>(
        key = "PrivateReadGate:${bookUrl ?: "last-read"}"
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }

    val unlockTitle = stringResource(R.string.private_unlock_title)
    val unlockSubtitle = stringResource(R.string.private_unlock_subtitle)
    val unlockUsePassword = stringResource(R.string.private_unlock_use_password)
    val passwordTitle = stringResource(R.string.private_unlock_password_title)

    LaunchedEffect(bookUrl) { viewModel.resolve(bookUrl) }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PrivateReadGateEffect.ShowMessage -> context.toastOnUi(effect.message)
                PrivateReadGateEffect.NavigateToLocalPasswordSettings -> {
                    showPasswordDialog = false
                    onOpenLocalPasswordSettings()
                }
            }
        }
    }

    // 离开阅读器即撤销本次授权：回到书架时卡片要重新脱敏。
    // 放在这里而不是"打开前撤销"，是因为打开前撤销会让紧接着的这道 gate 再次拦人。
    DisposableEffect(state.bookUrl) {
        val url = state.bookUrl
        if (url == null) {
            onDispose { }
        } else {
            onDispose { viewModel.revokeGrant(url) }
        }
    }

    fun requestUnlock() {
        if (!state.access.hasPassword) {
            viewModel.openLocalPasswordSettings()
            return
        }
        val activity = context.findFragmentActivity()
        if (activity != null && state.access.canUseBiometricShortcut) {
            BiometricUnlockLauncher.launch(
                activity = activity,
                title = unlockTitle,
                subtitle = unlockSubtitle,
                negativeButtonText = unlockUsePassword,
                onPassword = { viewModel.submitPassword(it) },
                onFallback = { showPasswordDialog = true },
                // 取消 = 放弃打开：按约定退回当前页 / 书架
                onCancel = onExit,
                onError = { message -> context.toastOnUi(message) },
            )
        } else {
            showPasswordDialog = true
        }
    }

    // 锁定态**直接弹验证**（与详情页同一套做法）：从书签 / 阅读记录点一条私密书进来，
    // 这一次点击本身就该是解锁动作，而不是先看锁定页、再点一次"验证"。
    // 只会自动弹一次；用户取消后停在锁定页，那里仍有验证入口。
    var autoUnlockLaunched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.isLocked) {
        if (state.isLocked && !autoUnlockLaunched) {
            autoUnlockLaunched = true
            requestUnlock()
        }
    }

    when {
        // 判定期间不渲染任何内容：这一帧本来就处在路由淡入里，看不到空白，
        // 但绝不能先渲染阅读器再遮住
        state.isChecking -> Box(modifier = Modifier.fillMaxSize())

        state.isLocked -> PrivateLockedPage(
            title = stringResource(R.string.private_locked_book_title),
            description = stringResource(
                if (state.access.hasPassword) {
                    R.string.private_locked_book_desc
                } else {
                    R.string.private_content_no_password
                }
            ),
            actionText = stringResource(
                if (state.access.hasPassword) {
                    R.string.private_verify_and_open
                } else {
                    R.string.set_local_password
                }
            ),
            onAction = { requestUnlock() },
        )

        else -> content()
    }

    if (showPasswordDialog) {
        var password by remember { mutableStateOf("") }
        AppAlertDialog(
            show = true,
            onDismissRequest = { onExit() },
            title = passwordTitle,
            content = {
                AppTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = passwordTitle,
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmText = stringResource(R.string.ok),
            onConfirm = {
                showPasswordDialog = false
                viewModel.submitPassword(password)
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onExit() },
        )
    }
}
