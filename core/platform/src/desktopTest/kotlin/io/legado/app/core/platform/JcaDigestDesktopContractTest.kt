package io.legado.app.core.platform

class JcaDigestDesktopContractTest : DigestContractTest() {
    override fun createDigest(): Digest = JcaDigest
}
