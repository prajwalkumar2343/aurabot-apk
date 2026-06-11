package com.aura.app.miniapps

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniAppEvolutionEngineTest {
    @Test
    fun healthMiniAppWithRepeatedMoodSignalsSuggestsPreWorkoutEnergy() {
        val records = listOf(
            record(values = mapOf("habit" to "Workout", "mood" to "low")),
            record(values = mapOf("habit" to "Workout", "note" to "energy was high")),
            record(values = mapOf("habit" to "Gym", "readiness" to "medium"))
        )

        val suggestion = MiniAppEvolutionEngine.suggest(BuiltInMiniApps.habitTracker, records)

        assertNotNull(suggestion)
        assertEquals("pre_workout_energy", suggestion?.proposedFields?.single()?.name)
        assertEquals("number", suggestion?.proposedFields?.single()?.type)
        assertTrue(suggestion?.revisionInstruction?.contains("pre_workout_energy") == true)
    }

    @Test
    fun spendMiniAppWithLateNightRecordsSuggestsReflectionField() {
        val records = listOf(
            record(createdAt = localTime(hour = 23), values = mapOf("merchant" to "Snacks", "amount" to "14", "category" to "Food")),
            record(createdAt = localTime(hour = 1), values = mapOf("merchant" to "Delivery", "amount" to "21", "category" to "Food")),
            record(createdAt = localTime(hour = 12), values = mapOf("merchant" to "Books", "amount" to "18", "category" to "Learning"))
        )

        val suggestion = MiniAppEvolutionEngine.suggest(BuiltInMiniApps.spendTracker, records)

        assertNotNull(suggestion)
        assertEquals("why_did_this_happen", suggestion?.proposedFields?.single()?.name)
        assertEquals("text", suggestion?.proposedFields?.single()?.type)
        assertTrue(suggestion?.reason?.contains("late at night") == true)
    }

    @Test
    fun repeatedAdHocFieldIsPromotedForGeneratedApps() {
        val bundle = MiniAppBundle(
            id = "generated.garden",
            metadata = MiniAppMetadata("Garden Log", "Track plant care", "Home"),
            dataSchema = MiniAppDataSchema(
                recordType = "plant_note",
                fields = listOf(MiniAppField("plant", "text", required = true))
            ),
            screens = listOf(MiniAppScreen("main", "Garden", listOf(MiniAppComponent("form", "New note"))))
        )
        val records = listOf(
            record("generated.garden", values = mapOf("plant" to "Basil", "sunlight" to "6")),
            record("generated.garden", values = mapOf("plant" to "Mint", "sunlight" to "4")),
            record("generated.garden", values = mapOf("plant" to "Rose", "sunlight" to "8"))
        )

        val suggestion = MiniAppEvolutionEngine.suggest(bundle, records)

        assertNotNull(suggestion)
        assertEquals("sunlight", suggestion?.proposedFields?.single()?.name)
        assertEquals("number", suggestion?.proposedFields?.single()?.type)
    }

    private fun record(
        miniAppId: String = "builtin.habit_tracker",
        createdAt: Long = localTime(hour = 9),
        values: Map<String, String>
    ) = MiniAppRecord(
        id = "record-${createdAt}-${values.hashCode()}",
        miniAppId = miniAppId,
        recordType = "record",
        values = values,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun localTime(hour: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 12)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
