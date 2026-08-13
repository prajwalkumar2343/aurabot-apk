package com.aura.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aura.app.widgets.AuraWidgetAction
import com.aura.app.widgets.AuraWidgetActionDecision
import com.aura.app.widgets.AuraWidgetActionType
import com.aura.app.widgets.AuraWidgetDatabase
import com.aura.app.widgets.AuraWidgetEntity
import com.aura.app.widgets.AuraWidgetKind
import com.aura.app.widgets.AuraWidgetProposal
import com.aura.app.widgets.AuraWidgetRepository
import com.aura.app.widgets.AuraWidgetRisk
import com.aura.app.widgets.AuraWidgetStatus
import com.aura.app.widgets.AuraWidgetValidationException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuraWidgetDatabaseTest {
    private lateinit var database: AuraWidgetDatabase
    private var now = 1_000_000L
    private lateinit var repository: AuraWidgetRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AuraWidgetDatabase::class.java
        ).build()
        repository = AuraWidgetRepository(database.auraWidgetDao(), clock = { now })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun repeatedInitialTapCannotConfirmAProtectedAction() = runBlocking {
        val widget = repository.admit(proposal(risk = AuraWidgetRisk.High))

        assertTrue(
            repository.requestAction(widget.id, ACTION_ID) is
                AuraWidgetActionDecision.NeedsConfirmation
        )
        assertEquals(
            AuraWidgetActionDecision.Ignored,
            repository.requestAction(widget.id, ACTION_ID)
        )
        assertTrue(
            repository.confirmAction(widget.id, ACTION_ID) is
                AuraWidgetActionDecision.Execute
        )
    }

    @Test
    fun staleCallbacksCannotOverwriteExecutingState() = runBlocking {
        val widget = repository.admit(proposal())
        startExecution(widget.id)

        assertFalse(repository.cancelConfirmation(widget.id))
        assertFalse(repository.dismiss(widget.id))
        assertFalse(repository.completeAction(widget.id, "wrong-action"))
        assertTrue(repository.completeAction(widget.id, ACTION_ID))
        assertEquals(
            AuraWidgetStatus.Succeeded.wireValue,
            database.auraWidgetDao().widget(widget.id)?.status
        )
    }

    @Test
    fun startupRecoveryNeverReplaysInterruptedExecution() = runBlocking {
        val widget = repository.admit(proposal())
        startExecution(widget.id)

        repository.reconcileStartup()

        val recovered = database.auraWidgetDao().widget(widget.id)
        assertEquals(AuraWidgetStatus.Failed.wireValue, recovered?.status)
        assertTrue(recovered?.lastError.orEmpty().contains("Verify the result"))
    }

    @Test
    fun staleInProcessExecutionTimesOutWithoutReplay() = runBlocking {
        val widget = repository.admit(proposal())
        startExecution(widget.id)
        now += TimeUnit.MINUTES.toMillis(6)

        repository.expireWidgets()

        val recovered = database.auraWidgetDao().widget(widget.id)
        assertEquals(AuraWidgetStatus.Failed.wireValue, recovered?.status)
        assertTrue(recovered?.lastError.orEmpty().contains("timed out"))
    }

    @Test
    fun expiryAndSuccessfulCompletionBecomeTerminal() = runBlocking {
        val expiring = repository.admit(proposal(dedupeKey = "expires"))
        now += TimeUnit.MINUTES.toMillis(31)
        repository.expireWidgets()
        assertEquals(
            AuraWidgetStatus.Expired.wireValue,
            database.auraWidgetDao().widget(expiring.id)?.status
        )

        val succeeding = repository.admit(proposal(dedupeKey = "succeeds"))
        startExecution(succeeding.id)
        repository.completeAction(succeeding.id, ACTION_ID)
        now += 1_501
        repository.expireWidgets()
        assertEquals(
            AuraWidgetStatus.Dismissed.wireValue,
            database.auraWidgetDao().widget(succeeding.id)?.status
        )
    }

    @Test
    fun widgetAndAdmissionEventRollbackTogether() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_aura_widget_test_events
            BEFORE INSERT ON aura_widget_events
            BEGIN
                SELECT RAISE(ABORT, 'forced event failure');
            END
            """.trimIndent()
        )

        try {
            repository.admit(proposal())
            fail("Expected the forced event failure")
        } catch (_: Exception) {
            // The Room transaction must leave neither the widget nor an orphaned event.
        }

        assertTrue(database.auraWidgetDao().activeWidgets().isEmpty())
    }

    @Test
    fun admissionIsDeduplicatedAndActiveWidgetCountIsBounded() = runBlocking {
        val first = repository.admit(proposal(dedupeKey = "same"))
        val duplicate = repository.admit(proposal(dedupeKey = "same"))
        assertEquals(first.id, duplicate.id)

        repeat(AuraWidgetRepository.MAX_ACTIVE_WIDGETS - 1) { index ->
            repository.admit(proposal(dedupeKey = "widget-$index"))
        }
        try {
            repository.admit(proposal(dedupeKey = "overflow"))
            fail("Expected active widget admission to be bounded")
        } catch (_: AuraWidgetValidationException) {
        }
    }

    @Test
    fun legacyStoredAssistantActionCannotBypassCurrentConfirmationPolicy() = runBlocking {
        database.auraWidgetDao().upsertWidget(
            AuraWidgetEntity(
                id = "legacy",
                kind = AuraWidgetKind.Message.wireValue,
                title = "Legacy",
                message = "Stored before confirmation hardening",
                detailsJson = "[]",
                actionsJson = """
                    [{
                      "id":"ask",
                      "label":"Ask",
                      "type":"assistant_message",
                      "payload":{"message":"Perform the model-authored request"},
                      "requiresConfirmation":false
                    }]
                """.trimIndent(),
                status = AuraWidgetStatus.Visible.wireValue,
                risk = AuraWidgetRisk.Low.wireValue,
                priority = 10,
                source = "assistant",
                dedupeKey = "legacy",
                pendingActionId = null,
                createdAt = now,
                updatedAt = now,
                expiresAt = now + TimeUnit.MINUTES.toMillis(30),
                lastError = null
            )
        )

        assertTrue(
            repository.requestAction("legacy", "ask") is
                AuraWidgetActionDecision.NeedsConfirmation
        )
    }

    private fun proposal(
        risk: AuraWidgetRisk = AuraWidgetRisk.Low,
        dedupeKey: String = "lunch"
    ) = AuraWidgetProposal(
        kind = AuraWidgetKind.FoodOrder,
        title = "Lunch",
        message = "Review your order",
        actions = listOf(
            AuraWidgetAction(
                id = ACTION_ID,
                label = "Review",
                type = AuraWidgetActionType.AssistantMessage,
                payload = mapOf("message" to "Review my lunch order")
            )
        ),
        risk = risk,
        expiresInMinutes = 30,
        dedupeKey = dedupeKey
    )

    private suspend fun startExecution(widgetId: String) {
        assertTrue(
            repository.requestAction(widgetId, ACTION_ID) is
                AuraWidgetActionDecision.NeedsConfirmation
        )
        assertTrue(
            repository.confirmAction(widgetId, ACTION_ID) is
                AuraWidgetActionDecision.Execute
        )
    }

    companion object {
        private const val ACTION_ID = "review"
    }
}
