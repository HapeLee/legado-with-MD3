package io.legado.app.core.platform

import java.io.File

class JvmFileSystemAndroidHostContractTest : FileSystemContractTest() {
    override fun createFileSystem(): FileSystem = JvmFileSystem
    override fun createTempDir(): String =
        File(System.getProperty("java.io.tmpdir"), "fscontract-android-${System.nanoTime()}")
            .apply { mkdirs() }.absolutePath
}
