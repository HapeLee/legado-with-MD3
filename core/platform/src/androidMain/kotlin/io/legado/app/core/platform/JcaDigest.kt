package io.legado.app.core.platform

import java.security.MessageDigest

/**
 * JCA 实现：委托 `java.security.MessageDigest`。
 * androidMain 与 desktopMain 都是 JVM，JCA 可用，故实现相同。
 * 样本（shutiao/legado）用 mbedTLS cinterop；本仓库 native 需求未定，先 JCA。
 */
object JcaDigest : Digest {
    override fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}
