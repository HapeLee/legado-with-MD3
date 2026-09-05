package io.legado.app.core.platform

import java.io.File

class JvmFileSystemDesktopContractTest : FileSystemContractTest() {
    override fun createFileSystem(): FileSystem = JvmFileSystem
    override fun createTempDir(): String =
        File(System.getProperty("java.io.tmpdir"), "fscontract-desktop-${System.nanoTime()}")
            .apply { mkdirs() }.absolutePath
}
