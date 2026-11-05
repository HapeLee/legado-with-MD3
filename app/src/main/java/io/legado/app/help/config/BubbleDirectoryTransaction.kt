package io.legado.app.help.config

import java.io.File
import java.io.IOException
import java.util.UUID

internal class BubbleDirectoryTransaction(
    private val exchange: FileExchange = NioFileExchange
) {
    fun <T> install(
        targetDir: File,
        stagingDir: File,
        backupDir: File,
        afterInstall: (File) -> T
    ): T {
        var backupCreated = false
        try {
            if (targetDir.exists()) {
                exchange.move(targetDir, backupDir)
                backupCreated = true
            }
            try {
                exchange.move(stagingDir, targetDir)
            } catch (installError: Exception) {
                if (backupCreated) restoreBackup(backupDir, targetDir, installError)
                throw installError
            }
            val result = try {
                afterInstall(targetDir)
            } catch (commitError: Exception) {
                rollbackInstalled(targetDir, backupDir.takeIf { backupCreated }, commitError)
                throw commitError
            }
            if (backupCreated) deleteBestEffort(backupDir)
            return result
        } finally {
            deleteBestEffort(stagingDir)
        }
    }

    private fun restoreBackup(backupDir: File, targetDir: File, originalError: Exception) {
        try {
            exchange.move(backupDir, targetDir)
        } catch (restoreError: Exception) {
            originalError.addSuppressed(restoreError)
            throw BubbleDirectoryRestoreException(backupDir, originalError)
        }
    }

    private fun rollbackInstalled(targetDir: File, backupDir: File?, originalError: Exception) {
        if (backupDir == null) {
            deleteRequired(targetDir, originalError)
            return
        }
        val failedDir = File(targetDir.parentFile, ".${targetDir.name}.failed-${UUID.randomUUID()}")
        try {
            if (targetDir.exists()) exchange.move(targetDir, failedDir)
            exchange.move(backupDir, targetDir)
            deleteBestEffort(failedDir)
        } catch (restoreError: Exception) {
            originalError.addSuppressed(restoreError)
            throw BubbleDirectoryRestoreException(backupDir, originalError)
        }
    }

    private fun deleteRequired(file: File, cause: Throwable) {
        try {
            deletePath(file)
        } catch (deleteError: Exception) {
            cause.addSuppressed(deleteError)
            throw IOException("failed to remove newly installed bubble package", cause)
        }
    }

    private fun deleteBestEffort(file: File) {
        runCatching { deletePath(file) }
    }

    private fun deletePath(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            if (!file.deleteRecursively() && file.exists()) throw IOException("failed to delete ${file.absolutePath}")
        } else {
            exchange.delete(file)
        }
    }
}

internal class BubbleDirectoryRestoreException(
    val backupDir: File,
    cause: Throwable
) : IOException("failed to restore bubble package; backup kept at ${backupDir.absolutePath}", cause)
