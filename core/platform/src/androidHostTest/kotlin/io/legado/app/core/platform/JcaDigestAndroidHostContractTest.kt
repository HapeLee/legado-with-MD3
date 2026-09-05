package io.legado.app.core.platform

class JcaDigestAndroidHostContractTest : DigestContractTest() {
    override fun createDigest(): Digest = JcaDigest
}
