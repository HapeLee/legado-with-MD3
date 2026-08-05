package io.legado.app.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import splitties.systemservices.connectivityManager

/**
 * 监测网络变化
 */
@SuppressLint("ObsoleteSdkInt")
class NetworkChangedListener(private val context: Context) {

    var onNetworkChanged: (() -> Unit)? = null
    private var registered = false

    private val receiver: NetworkChangedReceiver? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) NetworkChangedReceiver() else null
    }

    private val networkCallback: ConnectivityManager.NetworkCallback? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return@lazy object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    onNetworkChanged?.invoke()
                }
            }
        }
        return@lazy null
    }

    @SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag")
    fun register() {
        if (registered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback?.let {
                connectivityManager.registerDefaultNetworkCallback(it)
                registered = true
            }
        } else {
            receiver?.let {
                context.registerReceiver(it, it.filter)
                registered = true
            }
        }
    }

    fun unRegister() {
        if (!registered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
                registered = false
            }
        } else {
            receiver?.let {
                context.unregisterReceiver(it)
                registered = false
            }
        }
    }

    inner class NetworkChangedReceiver : BroadcastReceiver() {

        val filter = IntentFilter().apply {
            @Suppress("DEPRECATION")
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }

        override fun onReceive(context: Context, intent: Intent) {
            onNetworkChanged?.invoke()
        }

    }

}
