package com.aura.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aura.app.assistant.AssistantRunMode
import com.aura.app.assistant.AssistantRunProgress
import com.aura.app.assistant.AssistantRunScheduler
import com.aura.app.assistant.AssistantRunSurfaceRepository
import com.aura.app.assistant.ChatAction
import com.aura.app.assistant.ChatResponse
import com.aura.app.assistant.ChatWidgetAction
import com.aura.app.assistant.ChatWidgetProposal
import com.aura.app.widgets.AuraWidgetActionDecision
import com.aura.app.widgets.AuraWidgetDatabase
import com.aura.app.widgets.AuraWidgetKind
import com.aura.app.widgets.AuraWidgetRepository
import com.aura.app.widgets.AuraWidgetStatus
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantRunSurfaceDatabaseTest {
    private lateinit var database: AuraWidgetDatabase
    private lateinit var widgets: AuraWidgetRepository
    private lateinit var runs: AssistantRunSurfaceRepository
    private lateinit var scheduler: RecordingScheduler

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AuraWidgetDatabase::class.java
        ).build()
        widgets = AuraWidgetRepository(database.auraWidgetDao(), clock = { 1_000_000L })
        scheduler = RecordingScheduler()
        runs = AssistantRunSurfaceRepository(
            dao = database.assistantRunDao(),
            auraWidgetRepository = widgets,
            workScheduler = scheduler,
            currentServiceMode = { "managed" },
            clock = { 1_000_000L }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun managedProgressBecomesDurableReportAndSchedulesOneSync() = runBlocking {
        runs.recordProgress(
            AssistantRunProgress("run-1", "queued", "admitted", 0, AssistantRunMode.Managed)
        )
        runs.recordProgress(
            AssistantRunProgress("run-1", "running", "delegating", 2, AssistantRunMode.Managed)
        )
        runs.complete(
            "run-1",
            ChatResponse(
                reply = "{neutral} Finished the research.",
                session_id = "session-1"
            )
        )

        val surface = database.assistantRunDao().surface("run-1")
        val widget = widgets.visibleWidgets.first().single()
        assertEquals("completed", surface?.state)
        assertEquals(AuraWidgetKind.Report, widget.kind)
        assertEquals(AuraWidgetStatus.Visible, widget.status)
        assertTrue(widget.content.orEmpty().contains("Finished the research"))
        assertEquals(listOf("run-1"), scheduler.enqueued.distinct())
    }

    @Test
    fun completedApprovalSurfaceStillRequiresConfirmation() = runBlocking {
        runs.recordProgress(
            AssistantRunProgress("run-2", "running", "planning", 0, AssistantRunMode.Managed)
        )
        runs.complete(
            "run-2",
            ChatResponse(
                reply = "{neutral} I prepared the next step.",
                session_id = "session-2",
                actions = listOf(
                    ChatAction(
                        type = "present_widget",
                        widget = ChatWidgetProposal(
                            kind = "confirmation",
                            title = "Next step",
                            message = "Review before continuing",
                            risk = "low",
                            actions = listOf(
                                ChatWidgetAction(
                                    id = "continue",
                                    label = "Continue",
                                    type = "assistant_message",
                                    payload = mapOf("message" to "Continue the prepared step"),
                                    requires_confirmation = false
                                )
                            )
                        )
                    )
                )
            )
        )

        val widget = widgets.visibleWidgets.first().single()
        assertTrue(widget.actions.single().requiresConfirmation)
        assertTrue(
            widgets.requestAction(widget.id, widget.actions.single().id)
                is AuraWidgetActionDecision.NeedsConfirmation
        )
    }

    @Test
    fun lateProgressCannotOverwriteACompletedSurface() = runBlocking {
        runs.recordProgress(
            AssistantRunProgress("run-3", "running", "planning", 0, AssistantRunMode.Managed)
        )
        runs.complete(
            "run-3",
            ChatResponse(reply = "{neutral} Complete", session_id = "session-3")
        )
        runs.recordProgress(
            AssistantRunProgress("run-3", "running", "delegating", 3, AssistantRunMode.Managed)
        )

        val widget = widgets.visibleWidgets.first().single()
        assertEquals(AuraWidgetKind.Report, widget.kind)
        assertEquals("completed", database.assistantRunDao().surface("run-3")?.state)
    }

    @Test
    fun localRunNeverSchedulesNetworkWorkAndRestartMarksItInterrupted() = runBlocking {
        runs.recordProgress(
            AssistantRunProgress("local-1", "running", "planning", 0, AssistantRunMode.Local)
        )
        runs.reconcileStartup()

        val surface = database.assistantRunDao().surface("local-1")
        val widget = widgets.visibleWidgets.first().single()
        assertEquals("interrupted", surface?.state)
        assertEquals(AuraWidgetKind.Report, widget.kind)
        assertTrue(widget.title.contains("interrupted", ignoreCase = true))
        assertTrue(scheduler.enqueued.isEmpty())
    }

    private class RecordingScheduler : AssistantRunScheduler {
        val enqueued = CopyOnWriteArrayList<String>()

        override fun enqueue(runId: String) {
            enqueued += runId
        }

        override fun cancel(runId: String) = Unit
    }
}
