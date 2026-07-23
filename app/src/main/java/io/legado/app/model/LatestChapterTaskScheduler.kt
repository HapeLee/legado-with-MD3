package io.legado.app.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

internal class LatestChapterTaskScheduler<K>(
    private val scope: CoroutineScope,
    private val context: CoroutineContext = Dispatchers.IO,
    private val onError: (K, Throwable) -> Unit = { _, _ -> },
) {
    private val lock = Any()
    private val entries = mutableMapOf<K, Entry>()

    fun submit(
        key: K,
        block: suspend CoroutineScope.() -> Unit,
    ): Deferred<Unit> {
        val request = Request(block = block)
        synchronized(lock) {
            val entry = entries.getOrPut(key, ::Entry)
            if (entry.running == null) {
                startLocked(key, entry, request)
            } else {
                entry.pending?.result?.cancel(
                    CancellationException("Replaced by a newer chapter task")
                )
                entry.pending = request
            }
        }
        return request.result
    }

    fun cancel(key: K) {
        val running = synchronized(lock) {
            entries.remove(key)?.also { entry ->
                entry.pending?.result?.cancel()
            }?.running?.job
        }
        running?.cancel()
    }

    fun cancelAll() {
        cancelIf { true }
    }

    fun cancelIf(predicate: (K) -> Boolean) {
        val runningJobs = synchronized(lock) {
            entries.entries
                .filter { predicate(it.key) }
                .mapNotNull { (key, entry) ->
                    entries.remove(key)
                    entry.pending?.result?.cancel()
                    entry.running?.job
                }
        }
        runningJobs.forEach(Job::cancel)
    }

    internal fun stateOf(key: K): TaskState = synchronized(lock) {
        entries[key]?.let {
            TaskState(running = it.running != null, pending = it.pending != null)
        } ?: TaskState()
    }

    private fun startLocked(key: K, entry: Entry, request: Request) {
        val token = Any()
        val job = scope.launch(context, start = CoroutineStart.LAZY) {
            try {
                request.block(this)
                request.result.complete(Unit)
            } catch (error: CancellationException) {
                request.result.cancel(error)
                throw error
            } catch (error: Throwable) {
                request.result.completeExceptionally(error)
                onError(key, error)
            } finally {
                onTaskFinished(key, token)
            }
        }
        entry.running = Running(token = token, job = job)
        job.start()
    }

    private fun onTaskFinished(key: K, token: Any) {
        synchronized(lock) {
            val entry = entries[key] ?: return
            if (entry.running?.token !== token) return
            entry.running = null
            val next = entry.pending
            entry.pending = null
            if (next == null) {
                entries.remove(key)
            } else {
                startLocked(key, entry, next)
            }
        }
    }

    private class Entry {
        var running: Running? = null
        var pending: Request? = null
    }

    private class Running(
        val token: Any,
        val job: Job,
    )

    private class Request(
        val block: suspend CoroutineScope.() -> Unit,
        val result: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    internal data class TaskState(
        val running: Boolean = false,
        val pending: Boolean = false,
    )
}
