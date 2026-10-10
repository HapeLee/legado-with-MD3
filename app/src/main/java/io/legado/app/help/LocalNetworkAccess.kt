package io.legado.app.help

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) 起本地网络保护强制生效：系统在 eBPF 层静默丢弃未授权应用的局域网流量，
 * 入站（Web 服务被其它设备访问）与出站（连接本地 AI、局域网书源）同样受限。
 * 漏掉权限检查时用户只会看到连接超时，因此访问局域网地址前必须先走这里。
 */
object LocalNetworkAccess {

    /**
     * Android 17 (API 37)。SDK 尚未提供对应的 Build.VERSION_CODES 常量，这里用字面量。
     */
    const val PERMISSION_SDK_INT = 37

    fun isRequired(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= PERMISSION_SDK_INT

    /** 低于 API 37 的设备隐式授予，调用方无需再自行判断系统版本。 */
    fun isGranted(context: Context): Boolean =
        !isRequired() || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_LOCAL_NETWORK
        ) == PackageManager.PERMISSION_GRANTED
}

/**
 * URL（允许不带 scheme）是否指向局域网或本机地址。只有这类地址受本地网络保护约束，
 * 访问公网 API 不需要该权限，不能无条件打扰用户。
 */
internal fun String.targetsLocalNetwork(): Boolean =
    extractHost()?.isLocalNetworkHost() == true

/**
 * 取出 URL 的 host。刻意不走 OkHttp 的 HttpUrl：用户填的 baseUrl 允许不带 scheme
 * （`192.168.50.1:17863`），这里只需要稳定的 host 提取，语义由本文件锁定。
 */
private fun String.extractHost(): String? {
    val authority = substringAfter("://", this)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
    // IPv6 字面量形如 [::1]:8080
    if (authority.startsWith('[')) {
        return authority.substringAfter('[').substringBefore(']').ifEmpty { null }
    }
    return authority.substringBefore(':').trim().lowercase().ifEmpty { null }
}

internal fun String.isLocalNetworkHost(): Boolean {
    val host = lowercase()
    return host == "localhost" ||
        host.endsWith(".localhost") ||
        host == "::1" ||
        host.isPrivateIpv4() ||
        host.isUniqueLocalIpv6() ||
        host.isLinkLocalIpv6()
}

/** 10/8、172.16/12、192.168/16、127/8、169.254/16 与 0.0.0.0。 */
private fun String.isPrivateIpv4(): Boolean {
    val octets = split('.')
    if (octets.size != 4) return false
    val nums = octets.map { it.toIntOrNull()?.takeIf { value -> value in 0..255 } ?: return false }
    return when {
        nums[0] == 10 -> true
        nums[0] == 127 -> true
        nums[0] == 0 -> true
        nums[0] == 192 && nums[1] == 168 -> true
        nums[0] == 172 && nums[1] in 16..31 -> true
        nums[0] == 169 && nums[1] == 254 -> true
        else -> false
    }
}

/** fc00::/7 唯一本地地址。 */
private fun String.isUniqueLocalIpv6(): Boolean {
    if (':' !in this) return false
    val first = substringBefore(':')
    return first.length >= 2 && (first.startsWith("fc") || first.startsWith("fd"))
}

/** fe80::/10 链路本地地址。 */
private fun String.isLinkLocalIpv6(): Boolean {
    if (':' !in this) return false
    val first = substringBefore(':')
    return first.length >= 3 && first.startsWith("fe") && first[2] in "89ab"
}
