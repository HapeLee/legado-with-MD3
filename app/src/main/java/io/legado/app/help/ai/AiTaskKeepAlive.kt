package io.legado.app.help.ai

import android.content.Intent
import io.legado.app.data.entities.AiAgentJob
import io.legado.app.service.AiTaskKeepAliveService
import io.legado.app.utils.startForegroundServiceCompat
import splitties.init.appCtx
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AiTaskKeepAlive {

    private const val NOTIFY_THROTTLE_MILLIS = 800L
    private const val MAX_CONTENT_LENGTH = 160

    data class TaskState(
        val id: String,
        val title: String,
        val content: String = "",
        val progressText: String = "",
        val kind: String = KIND_GENERIC,
        val updatedAt: Long = System.currentTimeMillis()
    ) {
        val displayText: String
            get() = listOf(content, progressText)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { title }
    }

    const val KIND_GENERIC = "generic"
    const val KIND_CHAT = "chat"
    const val KIND_READ_AI = "read_ai"
    const val KIND_SUMMARY = "summary"
    const val KIND_ROLE_ASSIGN = "role_assign"

    private val activeTasks = ConcurrentHashMap<String, TaskState>()
    private var lastNotifyAt = 0L

    val activeCount: Int
        get() = activeTaskSnapshot().size

    val title: String
        get() = latestTask()?.title ?: "AI任务处理中"

    val content: String
        get() = latestTask()?.displayText ?: title

    fun activeTaskSnapshot(): List<TaskState> {
        val memoryTasks = runCatching {
            activeTasks.entries
                .map { it.value }
                .sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
        if (memoryTasks.isNotEmpty()) return memoryTasks
        return runCatching {
            AiAgentStateStore.activeJobs().map { job ->
                TaskState(
                    id = job.jobId,
                    title = jobTitle(job),
                    content = job.type,
                    progressText = job.status,
                    kind = job.type,
                    updatedAt = job.updatedAt
                )
            }
        }.getOrDefault(emptyList())
    }

    fun retain(
        title: String,
        content: String = "",
        kind: String = KIND_GENERIC
    ): String {
        val taskId = UUID.randomUUID().toString()
        activeTasks[taskId] = TaskState(
            id = taskId,
            title = title.ifBlank { "AI任务处理中" },
            content = cleanContent(content),
            kind = kind
        )
        notifyService(AiTaskKeepAliveService.ACTION_REFRESH, force = true)
        return taskId
    }

    fun update(
        taskId: String?,
        title: String? = null,
        content: String? = null,
        progressText: String? = null,
        force: Boolean = false
    ) {
        if (taskId.isNullOrBlank()) return
        val current = activeTasks[taskId] ?: return
        activeTasks[taskId] = current.copy(
            title = title?.takeIf { it.isNotBlank() } ?: current.title,
            content = content?.let(::cleanContent) ?: current.content,
            progressText = progressText?.let(::cleanContent) ?: current.progressText,
            updatedAt = System.currentTimeMillis()
        )
        notifyService(AiTaskKeepAliveService.ACTION_REFRESH, force = force)
    }

    fun release(taskId: String?) {
        if (taskId.isNullOrBlank()) return
        activeTasks.remove(taskId)
        notifyService(
            if (activeTasks.isEmpty()) {
                AiTaskKeepAliveService.ACTION_STOP
            } else {
                AiTaskKeepAliveService.ACTION_REFRESH
            },
            force = true
        )
    }

    private fun latestTask(): TaskState? {
        return activeTaskSnapshot().maxByOrNull { it.updatedAt }
    }

    private fun jobTitle(job: AiAgentJob): String {
        return when (job.type) {
            AiAgentJob.TYPE_READ_AI -> "阅读页问AI"
            AiAgentJob.TYPE_ROLE_ASSIGN -> "角色分配中"
            AiAgentJob.TYPE_AUDIO_ASSIGN -> "智能音频处理中"
            else -> "AI回复生成中"
        }
    }

    private fun notifyService(action: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force &&
            action == AiTaskKeepAliveService.ACTION_REFRESH &&
            now - lastNotifyAt < NOTIFY_THROTTLE_MILLIS
        ) {
            return
        }
        lastNotifyAt = now
        val intent = Intent(appCtx, AiTaskKeepAliveService::class.java).apply {
            this.action = action
        }
        appCtx.startForegroundServiceCompat(intent)
    }

    private fun cleanContent(raw: String): String {
        return raw
            .replace(Regex("data:image/[^\\s\"')]+"), "data:image/<stored>")
            .replace(Regex("```legado-tool-events[\\s\\S]*?```"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_CONTENT_LENGTH)
    }
}
