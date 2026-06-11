package com.aura.app.miniapps

import java.util.Calendar
import java.util.Locale

object MiniAppEvolutionEngine {
    private val healthWords = setOf(
        "gym",
        "workout",
        "fitness",
        "habit",
        "wellness",
        "run",
        "running",
        "lift",
        "yoga",
        "training"
    )
    private val moodWords = setOf("mood", "energy", "readiness", "feeling", "fatigue", "tired", "sore")
    private val spendWords = setOf("spend", "expense", "money", "finance", "ledger", "purchase", "merchant", "amount")
    private val genericNoiseFields = setOf("id", "title", "status", "note", "notes", "created_at", "updated_at")

    fun suggest(bundle: MiniAppBundle, records: List<MiniAppRecord>): MiniAppEvolutionSuggestion? =
        healthMoodSuggestion(bundle, records)
            ?: lateNightSpendSuggestion(bundle, records)
            ?: repeatedFieldSuggestion(bundle, records)

    private fun healthMoodSuggestion(
        bundle: MiniAppBundle,
        records: List<MiniAppRecord>
    ): MiniAppEvolutionSuggestion? {
        if (records.size < 3) return null
        val existingFields = bundle.fieldNames()
        if ("pre_workout_energy" in existingFields) return null
        if (!bundle.matchesDomain(healthWords)) return null

        val moodRecordCount = records.count { record ->
            record.values.any { (key, value) ->
                key.matchesAny(moodWords) || value.matchesAny(moodWords)
            }
        }
        if (moodRecordCount < 3) return null

        val field = MiniAppField("pre_workout_energy", "number", defaultValue = "5")
        return MiniAppEvolutionSuggestion(
            id = "${bundle.id}:v${bundle.version}:pre_workout_energy",
            title = "Grow an energy check-in",
            reason = "Aura noticed $moodRecordCount recent check-ins mention mood or energy. This mini app is ready to track pre-workout energy as a first-class field.",
            revisionInstruction = """
                Evolve this mini app because recent records repeatedly mention mood or energy.
                Add a number field named pre_workout_energy with a default of 5.
                Update every record form, React UI if present, dashboard/timeline labels, and assistant intents so the user can log pre-workout energy naturally.
                Preserve all existing local records and keep the same mini app id.
            """.trimIndent(),
            proposedFields = listOf(field),
            confidence = 0.86f
        )
    }

    private fun lateNightSpendSuggestion(
        bundle: MiniAppBundle,
        records: List<MiniAppRecord>
    ): MiniAppEvolutionSuggestion? {
        if (records.size < 2) return null
        val existingFields = bundle.fieldNames()
        if ("why_did_this_happen" in existingFields) return null
        if (!bundle.matchesDomain(spendWords)) return null

        val lateNightCount = records.count { it.createdAt.isLateNight() }
        if (lateNightCount < 2) return null

        val field = MiniAppField("why_did_this_happen", "text")
        return MiniAppEvolutionSuggestion(
            id = "${bundle.id}:v${bundle.version}:why_did_this_happen",
            title = "Add a late-night reflection",
            reason = "Aura noticed $lateNightCount expenses logged late at night. This mini app can grow a small reflection field for context, not judgment.",
            revisionInstruction = """
                Evolve this spending mini app because multiple expenses were logged late at night.
                Add a text field named why_did_this_happen for optional late-night spending context.
                Update every record form, React UI if present, dashboard/timeline labels, and assistant intents so the user can capture the reason naturally.
                Preserve all existing local records and keep the same mini app id.
            """.trimIndent(),
            proposedFields = listOf(field),
            confidence = 0.82f
        )
    }

    private fun repeatedFieldSuggestion(
        bundle: MiniAppBundle,
        records: List<MiniAppRecord>
    ): MiniAppEvolutionSuggestion? {
        if (records.size < 3) return null
        val existingFields = bundle.fieldNames()
        val candidates = records
            .flatMap { record -> record.values.keys.map { it.normalizedFieldName() } }
            .filter { it.isNotBlank() && it !in existingFields && it !in genericNoiseFields }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= 3 }

        val fieldName = candidates.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.key ?: return null
        val values = records.mapNotNull { record -> record.values.entries.firstOrNull { it.key.normalizedFieldName() == fieldName }?.value }
        val field = MiniAppField(fieldName, values.inferFieldType())
        val label = fieldName.toReadableLabel()
        return MiniAppEvolutionSuggestion(
            id = "${bundle.id}:v${bundle.version}:$fieldName",
            title = "Make $label official",
            reason = "Aura noticed $label appearing across ${candidates[fieldName]} records. This mini app can promote it from repeated detail to a real field.",
            revisionInstruction = """
                Evolve this mini app because users repeatedly log a value named $fieldName.
                Add a ${field.type} field named $fieldName to the primary record schema.
                Update every record form, React UI if present, dashboard/timeline labels, and assistant intents so the user can log $label naturally.
                Preserve all existing local records and keep the same mini app id.
            """.trimIndent(),
            proposedFields = listOf(field),
            confidence = 0.74f
        )
    }

    private fun MiniAppBundle.fieldNames(): Set<String> =
        dataSchema.fields.map { it.name.normalizedFieldName() }.toSet()

    private fun MiniAppBundle.matchesDomain(words: Set<String>): Boolean {
        val text = buildString {
            append(metadata.name).append(' ')
            append(metadata.description).append(' ')
            append(metadata.category).append(' ')
            dataSchema.fields.forEach { append(it.name).append(' ') }
            actions.forEach { action ->
                append(action.id).append(' ')
                action.values.values.forEach { append(it).append(' ') }
            }
            assistantIntents.forEach { intent ->
                append(intent.name).append(' ')
                intent.utterances.forEach { append(it).append(' ') }
            }
        }.lowercase(Locale.US)
        return words.any { word -> text.contains(word) }
    }

    private fun String.matchesAny(words: Set<String>): Boolean {
        val text = lowercase(Locale.US)
        return words.any { word -> text.contains(word) }
    }

    private fun String.normalizedFieldName(): String =
        trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')

    private fun String.toReadableLabel(): String =
        replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase(Locale.US) } }
            .ifBlank { "Field" }

    private fun List<String>.inferFieldType(): String =
        when {
            isNotEmpty() && all { it.equals("true", true) || it.equals("false", true) } -> "boolean"
            isNotEmpty() && all { it.toDoubleOrNull() != null } -> "number"
            else -> "text"
        }

    private fun Long.isLateNight(): Boolean {
        val calendar = Calendar.getInstance().apply { timeInMillis = this@isLateNight }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 5
    }
}
