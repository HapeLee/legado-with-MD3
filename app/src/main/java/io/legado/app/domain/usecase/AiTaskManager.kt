package io.legado.app.domain.usecase

import io.legado.app.data.entities.AiArtifact
import io.legado.app.domain.gateway.AiArtifactGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class AiTaskSnapshot(
    val id: String,
    val taskType: String,
    val bookUrl: String,
    val chapterIndex: Int? = null,
    val status: Int,
    val output: String? = null,
    val reasoning: String = "",
    val toolNames: List<String> = emptyList(),
    val errorMessage: String? = null,
)

interface AiTaskReporter {
    fun appendContent(text: String)
    fun appendReasoning(text: String)
    fun reportToolCall(name: String)
}

/**
 * Application-scoped runner for AI work. UI collectors may disappear without cancelling work.
 * Results and terminal errors are persisted as [AiArtifact] records.
 */
class AiTaskManager(
    private val aiArtifactGateway: AiArtifactGateway,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val snapshots = MutableStateFlow<Map<String, AiTaskSnapshot>>(emptyMap())

    fun observeTask(taskId: String): Flow<AiTaskSnapshot?> = snapshots.map { it[taskId] }

    fun observeBookTask(
        bookUrl: String,
        taskType: String,
        chapterIndex: Int? = null,
    ): Flow<AiTaskSnapshot?> = snapshots.map {
        it.values.lastOrNull { snapshot ->
            snapshot.bookUrl == bookUrl && snapshot.taskType == taskType &&
                    (chapterIndex == null || snapshot.chapterIndex == chapterIndex)
        }
    }

    fun submit(
        artifact: AiArtifact,
        work: suspend AiTaskReporter.() -> String,
    ): String {
        jobs[artifact.id]?.takeIf { it.isActive }?.let { return artifact.id }
        jobs.remove(artifact.id)?.cancel()
        publish(artifact.toSnapshot(status = AiArtifact.STATUS_RUNNING))
        jobs[artifact.id] = scope.launch {
            aiArtifactGateway.upsertArtifact(
                artifact.copy(
                    status = AiArtifact.STATUS_RUNNING,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            try {
                val output = work(Reporter(artifact.id))
                val success = artifact.copy(
                    status = AiArtifact.STATUS_SUCCESS,
                    output = output,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis(),
                )
                aiArtifactGateway.upsertArtifact(success)
                publish(success.toSnapshot().withProgress(taskId = artifact.id))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val failed = artifact.copy(
                    status = AiArtifact.STATUS_FAILED,
                    errorMessage = error.localizedMessage ?: error.javaClass.simpleName,
                    updatedAt = System.currentTimeMillis(),
                )
                aiArtifactGateway.upsertArtifact(failed)
                publish(failed.toSnapshot().withProgress(taskId = artifact.id))
            } finally {
                jobs.remove(artifact.id)
            }
        }
        return artifact.id
    }

    private fun publish(snapshot: AiTaskSnapshot) {
        snapshots.update { it + (snapshot.id to snapshot) }
    }

    private fun AiArtifact.toSnapshot(
        status: Int = this.status,
    ) = AiTaskSnapshot(
        id = id,
        taskType = taskType,
        bookUrl = bookUrl,
        chapterIndex = chapterIndex,
        status = status,
        output = output,
        errorMessage = errorMessage,
    )

    private fun AiTaskSnapshot.withProgress(taskId: String): AiTaskSnapshot {
        val active = snapshots.value[taskId] ?: return this
        return copy(reasoning = active.reasoning, toolNames = active.toolNames)
    }

    private inner class Reporter(
        private val taskId: String,
    ) : AiTaskReporter {
        override fun appendContent(text: String) {
            snapshots.update { current ->
                val snapshot = current[taskId] ?: return@update current
                current + (taskId to snapshot.copy(output = snapshot.output.orEmpty() + text))
            }
        }

        override fun appendReasoning(text: String) {
            snapshots.update { current ->
                val snapshot = current[taskId] ?: return@update current
                current + (taskId to snapshot.copy(reasoning = snapshot.reasoning + text))
            }
        }

        override fun reportToolCall(name: String) {
            snapshots.update { current ->
                val snapshot = current[taskId] ?: return@update current
                current + (taskId to snapshot.copy(toolNames = (snapshot.toolNames + name).distinct()))
            }
        }
    }
}
