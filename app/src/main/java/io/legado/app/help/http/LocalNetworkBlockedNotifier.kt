package io.legado.app.help.http

import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.LifecycleHelp
import io.legado.app.help.LocalNetworkAccess
import io.legado.app.help.isLocalNetworkHost
import io.legado.app.utils.eventBus.FlowEventBus
import io.legado.app.utils.toastOnUi
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import splitties.init.appCtx

/**
 * Android 17+ 未授予本地网络权限时，系统会在网络栈层阻断发往局域网地址的包，
 * 书源、图源、WebDAV 这类请求只会莫名其妙地超时或连接被拒。这里在网络层兜底：
 * 识别这种情况，提示一次，并留一个标记让宿主 Activity 回来补授权
 * （宿主通过 [consumePermissionRequest] 领取）。
 */
object LocalNetworkBlockedNotifier {

    /** 同一原因连续失败时不刷屏，只提示一次。 */
    private const val REPEAT_INTERVAL_MS = 60_000L

    private val lastNotifyAt = AtomicLong(0L)
    private val permissionRequested = AtomicBoolean(false)

    /**
     * 宿主 Activity 领取"需要补授权"标记；取到 true 时应申请本地网络权限。
     */
    fun consumePermissionRequest(): Boolean = permissionRequested.getAndSet(false)

    /** 请求因缺少本地网络权限失败时的兜底处理，由网络层在抛出 IOException 前调用。 */
    fun onRequestFailed(host: String, error: IOException) {
        if (!shouldReport(host, error, LocalNetworkAccess.isGranted(appCtx))) return
        permissionRequested.set(true)
        // 宿主在前台时立刻补授权；不在前台则由 onResume 再消费一次标记。
        FlowEventBus.post(EventBus.LOCAL_NETWORK_PERMISSION_REQUIRED, Unit)
        // 后台失败不打扰用户（也无法保证 Toast 可见），留给下次前台失败或 onResume 补授权时再提示，
        // 因此只有真正弹出提示才占用节流窗口。
        if (!LifecycleHelp.appVisible.value) return
        val now = System.currentTimeMillis()
        val last = lastNotifyAt.get()
        if (now - last < REPEAT_INTERVAL_MS || !lastNotifyAt.compareAndSet(last, now)) return
        appCtx.toastOnUi(R.string.local_network_permission_required)
    }

    /**
     * 是否像是被本地网络保护拦下的请求：目标在局域网内、没有权限，且失败是连接类错误。
     * 系统丢包与"服务本身没开"在网络层无法区分，所以提示文案不把话说死。
     */
    internal fun shouldReport(host: String, error: IOException, permissionGranted: Boolean): Boolean =
        !permissionGranted &&
            host.isLocalNetworkHost() &&
            (error is SocketTimeoutException || error is ConnectException)
}
