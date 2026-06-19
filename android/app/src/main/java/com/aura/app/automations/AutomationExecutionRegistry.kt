package com.aura.app.automations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AutomationExecutionRegistry {
    private val mutationMutexes = List(MutationMutexCount) { Mutex() }
    private val stateMutex = Mutex()
    private val generations = mutableMapOf<String, Long>()
    private val executions = mutableMapOf<String, MutableSet<TrackedExecution>>()

    suspend fun generation(automationId: String): Long =
        mutationMutex(automationId).withLock {
            stateMutex.withLock { generations[automationId] ?: 0L }
        }

    suspend fun <T> track(
        automationId: String,
        runId: String,
        expectedGeneration: Long,
        block: suspend () -> T
    ): T {
        val execution = TrackedExecution(runId, currentCoroutineContext().job)
        mutationMutex(automationId).withLock {
            stateMutex.withLock {
                if ((generations[automationId] ?: 0L) != expectedGeneration) {
                    throw AutomationConfigurationChangedException(
                        automationId,
                        runId,
                        AutomationRunStatus.Failed,
                        "Automation configuration changed before execution"
                    )
                }
                executions.getOrPut(automationId, ::mutableSetOf).add(execution)
            }
        }
        return try {
            block()
        } finally {
            withContext(NonCancellable) {
                stateMutex.withLock {
                    executions[automationId]?.let { active ->
                        active.remove(execution)
                        if (active.isEmpty()) executions.remove(automationId)
                    }
                }
            }
        }
    }

    suspend fun <T> mutate(
        automationId: String,
        terminalStatus: String,
        message: String,
        block: suspend () -> T
    ): T = mutationMutex(automationId).withLock {
        val mutationJob = currentCoroutineContext().job
        val active = stateMutex.withLock {
            check(executions[automationId].orEmpty().none { it.job === mutationJob }) {
                "Automation execution cannot mutate its own configuration"
            }
            generations[automationId] = (generations[automationId] ?: 0L) + 1L
            executions[automationId].orEmpty().toList()
        }
        active.forEach { execution ->
            execution.job.cancel(
                AutomationConfigurationChangedException(
                    automationId,
                    execution.runId,
                    terminalStatus,
                    message
                )
            )
        }
        active.map { it.job }.joinAll()
        block()
    }

    private fun mutationMutex(automationId: String): Mutex =
        mutationMutexes[Math.floorMod(automationId.hashCode(), mutationMutexes.size)]

    private data class TrackedExecution(val runId: String, val job: Job)

    private companion object {
        const val MutationMutexCount = 64
    }
}

internal class AutomationConfigurationChangedException(
    val automationId: String,
    val runId: String,
    val terminalStatus: String,
    message: String
) : CancellationException(message)
