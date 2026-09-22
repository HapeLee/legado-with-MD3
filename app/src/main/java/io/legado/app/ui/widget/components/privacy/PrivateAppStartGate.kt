package io.legado.app.ui.widget.components.privacy

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.gateway.PrivateAccessGateway
import io.legado.app.domain.model.PrivateAccessState
import io.legado.app.domain.model.settings.PrivateAccessSettings
import io.legado.app.help.security.BiometricUnlockLauncher
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.system.exitProcess

/** 从 Compose 的 Context 链里找回承载系统生物验证框的 FragmentActivity */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

/**
 * 启动验证未通过：直接结束应用。
 *
 * 与"打开私密内容时验证失败"不同——那时可以退回锁定态继续使用应用；启动验证是进入应用的
 * 门槛，被拒绝就应该离开，而不是留在应用里。
 */
private fun exitApp(context: Context) {
    context.findFragmentActivity()?.finishAffinity()
    // 只走 exitProcess：killProcess 之后这行永远不可达，两者留一个就够，
    // 而 exitProcess 会正常走 JVM 退出，比直接信号杀进程更干净。
    exitProcess(0)
}

/**
 * 「启动应用时验证」的门槛。
 *
 * 它是**单独的 screen**而不是叠在应用界面上的对话框：门槛未过时 [content] 完全不组合，
 * 所以验证页背后既看不到书架/阅读界面，也没有任何可以点进去的入口。做成独立 Activity
 * 也能达到同样效果，但要多操心启动流程、任务栈与生物验证的宿主，收益不抵风险。
 *
 * 判定依据是 [PrivateAccessState.isAppStartVerified]，**不是** [PrivateAccessState.isUnlocked]：
 * 启动验证只证明"进得来"，不是对任何分组/书籍的授权（见 gateway 的 verifyPassword）。
 *
 * 这里是进入应用的门槛而不是内容开关，所以不通过就退出应用；密码输错不算"不通过"，
 * 留在页面里提示错误并允许重试。
 */
@Composable
fun PrivateAppStartGate(
    // content 放最后：调用点用尾随 lambda 表达"门槛内的应用界面"
    gateway: PrivateAccessGateway = koinInject(),
    content: @Composable () -> Unit,
) {
    val access by gateway.state.collectAsStateWithLifecycle(PrivateAccessState())
    val settings by gateway.settings.collectAsStateWithLifecycle(PrivateAccessSettings())

    // 应用离开前台：只有"离开应用前有效"这种频率会真正回到锁定态——频率判断在 gateway 里。
    // 宽限期（秒）在这里落地：离开时只记时间，回来时超过宽限期才失效；
    // 宽限期为 0 就是"离开即失效"，不必等回来再判断。
    val lifecycleOwner = LocalLifecycleOwner.current
    val graceSeconds = settings.backgroundGraceSeconds
    val leftAt = remember { mutableStateOf(0L) }
    DisposableEffect(lifecycleOwner, gateway, graceSeconds) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    leftAt.value = System.currentTimeMillis()
                    if (graceSeconds == 0) gateway.onAppBackground()
                }

                Lifecycle.Event.ON_START -> {
                    val since = leftAt.value
                    if (since != 0L &&
                        System.currentTimeMillis() - since > graceSeconds * 1000L
                    ) {
                        gateway.onAppBackground()
                    }
                    leftAt.value = 0L
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locked = settings.enabled && access.hasPassword && settings.verifyOnAppStart &&
            !access.isAppStartVerified && !access.isUnlocked

    if (locked) {
        PrivateAppStartVerifyScreen(gateway)
    } else {
        content()
    }
}

/** 门槛页：全屏，不透明，背后不组合任何应用内容 */
@Composable
private fun PrivateAppStartVerifyScreen(gateway: PrivateAccessGateway) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val access by gateway.state.collectAsStateWithLifecycle(PrivateAccessState())

    // 每次进程启动只主动打扰一次；旋转屏幕后不再重新弹系统验证框
    var autoLaunched by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    val title = stringResource(R.string.private_unlock_title)
    val subtitle = stringResource(R.string.private_unlock_subtitle)
    val usePassword = stringResource(R.string.private_unlock_use_password)
    val passwordTitle = stringResource(R.string.private_unlock_password_title)
    val passwordErrorText = stringResource(R.string.private_unlock_password_error)

    fun launchUnlock() {
        val activity = context.findFragmentActivity()
        if (activity != null && access.canUseBiometricShortcut) {
            BiometricUnlockLauncher.launch(
                activity = activity,
                title = title,
                subtitle = subtitle,
                negativeButtonText = usePassword,
                onPassword = { decrypted ->
                    scope.launch { gateway.verifyPassword(decrypted) }
                },
                onFallback = { showPasswordDialog = true },
                // 用户明确放弃：门槛没通过，离开应用
                onCancel = { exitApp(context) },
                onError = { showPasswordDialog = true },
            )
        } else {
            showPasswordDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (autoLaunched) return@LaunchedEffect
        autoLaunched = true
        launchUnlock()
    }

    BackHandler { exitApp(context) }

    PrivateLockedPage(
        title = title,
        description = subtitle,
        actionText = stringResource(R.string.private_verify_and_open),
        onAction = { launchUnlock() },
        modifier = Modifier.fillMaxSize(),
    )

    if (showPasswordDialog) {
        AppAlertDialog(
            show = true,
            // 点对话框外部或返回键都视为放弃验证
            onDismissRequest = { exitApp(context) },
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
                scope.launch {
                    if (gateway.verifyPassword(input)) {
                        password = ""
                        showPasswordDialog = false
                    } else {
                        // 输错属于"还没通过"，不是"放弃"：留在对话框里让用户重试
                        passwordError = true
                    }
                }
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { exitApp(context) },
        )
    }
}
