package io.legado.app.help.config

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

internal class AtomicTextFileStore(
    private val target: File,
    private val exchange: FileExchange = NioFileExchange
) {

    private val staging = File(target.parentFile, ".${target.name}.staging")
    private val backup = File(target.parentFile, ".${target.name}.backup")
    private val pathLock = pathLocks.computeIfAbsent(target.canonicalPath) { Any() }

    fun writeVerified(text: String, verify: (String) -> Boolean) {
        synchronized(pathLock) {
            recoverInterruptedCommitUnlocked()
            target.parentFile?.let { parent ->
                check(parent.exists() || parent.mkdirs()) { "failed to create ${parent.absolutePath}" }
            }
            deleteRequired(staging)
            var backupCreated = false
            try {
                writeSynced(staging, text)
                check(verify(staging.readText())) { "failed to verify ${staging.absolutePath}" }
                if (target.exists()) {
                    deleteRequired(backup)
                    exchange.move(target, backup)
                    backupCreated = true
                }
                try {
                    exchange.move(staging, target)
                } catch (installError: Exception) {
                    if (backupCreated) {
                        try {
                            exchange.move(backup, target)
                            backupCreated = false
                        } catch (restoreError: Exception) {
                            installError.addSuppressed(restoreError)
                            throw AtomicFileRestoreException(backup, installError)
                        }
                    }
                    throw installError
                }
                if (backupCreated) {
                    deleteBestEffort(backup)
                }
            } finally {
                deleteBestEffort(staging)
            }
        }
    }

    fun recoverInterruptedCommit() {
        synchronized(pathLock) {
            recoverInterruptedCommitUnlocked()
        }
    }

    /**
     * Removes every file that can participate in this store's atomic state.
     *
     * The backup is deleted before the target so a partial failure cannot leave a backup that a
     * later recovery would resurrect after the user explicitly deleted the configuration.
     *
     * @return the best available size of the previously committed value, for external accounting.
     */
    fun delete(): Long {
        return synchronized(pathLock) {
            val committedSize = when {
                target.isFile -> target.length()
                backup.isFile -> backup.length()
                else -> 0L
            }
            deleteRequired(backup)
            deleteRequired(staging)
            deleteRequired(target)
            committedSize
        }
    }

    private fun recoverInterruptedCommitUnlocked() {
        if (target.exists()) {
            deleteBestEffort(backup)
        } else if (backup.exists()) {
            exchange.move(backup, target)
        }
        deleteBestEffort(staging)
    }

    private fun writeSynced(file: File, text: String) {
        FileOutputStream(file).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
    }

    private fun deleteRequired(file: File) {
        if (file.exists()) {
            exchange.delete(file)
        }
    }

    private fun deleteBestEffort(file: File) {
        runCatching {
            if (file.exists()) exchange.delete(file)
        }
    }

    private companion object {
        val pathLocks = ConcurrentHashMap<String, Any>()
    }
}

internal interface FileExchange {
    fun move(source: File, target: File)
    fun delete(file: File)
}

internal object NioFileExchange : FileExchange {
    override fun move(source: File, target: File) {
        if (target.exists()) {
            throw IOException("target already exists: ${target.absolutePath}")
        }
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath())
        } catch (_: UnsupportedOperationException) {
            Files.move(source.toPath(), target.toPath())
        }
    }

    override fun delete(file: File) {
        if (file.exists()) {
            Files.delete(file.toPath())
        }
    }
}

internal class AtomicFileRestoreException(
    val backupFile: File,
    cause: Throwable
) : IOException("failed to restore atomic file; backup kept at ${backupFile.absolutePath}", cause)
