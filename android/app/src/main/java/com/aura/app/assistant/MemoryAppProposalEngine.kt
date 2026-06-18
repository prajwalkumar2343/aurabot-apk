package com.aura.app.assistant

import java.util.Locale
import kotlin.math.min

data class MemoryAppProposal(
    val id: String,
    val topicKey: String,
    val appName: String,
    val category: String,
    val reason: String,
    val buildPrompt: String,
    val suggestedFields: List<String>,
    val suggestedActions: List<String>,
    val evidence: List<MemoryAppEvidence>,
    val confidence: Float
)

data class MemoryAppEvidence(
    val memoryId: String,
    val title: String,
    val excerpt: String
)

object MemoryAppProposalEngine {
    private val stopWords = setOf(
        "about",
        "after",
        "again",
        "also",
        "and",
        "are",
        "because",
        "been",
        "before",
        "but",
        "can",
        "class",
        "did",
        "for",
        "from",
        "had",
        "has",
        "have",
        "her",
        "him",
        "his",
        "how",
        "into",
        "just",
        "like",
        "make",
        "more",
        "need",
        "note",
        "not",
        "now",
        "our",
        "out",
        "she",
        "should",
        "status",
        "that",
        "the",
        "their",
        "them",
        "then",
        "there",
        "this",
        "todo",
        "track",
        "use",
        "want",
        "was",
        "when",
        "with",
        "work",
        "you",
        "your"
    )

    private val domainProfiles = listOf(
        DomainProfile(
            topicKey = "interview_prep",
            appName = "Interview Prep Tracker",
            category = "Career",
            keywords = setOf("interview", "resume", "recruiter", "coding", "system", "design", "leetcode", "offer", "hiring"),
            fields = listOf("company", "role", "round", "question", "answer_notes", "confidence", "follow_up_date"),
            actions = listOf("log_question", "practice_round", "save_follow_up", "mark_ready"),
            promptFocus = "interview preparation, company/role tracking, practice questions, confidence, follow-ups, and readiness"
        ),
        DomainProfile(
            topicKey = "learning_plan",
            appName = "Learning Sprint Tracker",
            category = "Education",
            keywords = setOf("learn", "study", "course", "lesson", "practice", "book", "reading", "skill", "tutorial"),
            fields = listOf("topic", "resource", "progress", "next_step", "difficulty", "notes"),
            actions = listOf("log_study", "save_resource", "mark_practiced", "plan_next_step"),
            promptFocus = "learning plans, resources, practice sessions, progress, difficulty, and next steps"
        ),
        DomainProfile(
            topicKey = "project_tracker",
            appName = "Project Momentum Tracker",
            category = "Productivity",
            keywords = setOf("project", "feature", "bug", "ship", "build", "release", "design", "prototype", "implement"),
            fields = listOf("project", "milestone", "status", "blocker", "next_action", "notes"),
            actions = listOf("log_progress", "save_blocker", "mark_milestone", "plan_next_action"),
            promptFocus = "project momentum, milestones, blockers, next actions, and shipping progress"
        ),
        DomainProfile(
            topicKey = "health_routine",
            appName = "Health Pattern Tracker",
            category = "Wellness",
            keywords = setOf("gym", "workout", "sleep", "health", "energy", "mood", "meal", "run", "recovery"),
            fields = listOf("activity", "energy", "mood", "sleep_quality", "recovery_note", "next_adjustment"),
            actions = listOf("log_check_in", "save_recovery_note", "mark_workout", "review_pattern"),
            promptFocus = "health routines, energy, mood, sleep, workouts, recovery, and pattern review"
        ),
        DomainProfile(
            topicKey = "money_watch",
            appName = "Money Pattern Tracker",
            category = "Finance",
            keywords = setOf("money", "expense", "spend", "budget", "bill", "saving", "purchase", "price"),
            fields = listOf("item", "amount", "category", "need_or_want", "reason", "notes"),
            actions = listOf("log_spend", "save_bill", "mark_need", "review_pattern"),
            promptFocus = "spending patterns, bills, budget notes, purchase reasons, and review"
        )
    )
    private val domainKeywords = domainProfiles.flatMap { it.keywords }.toSet()

    fun propose(memories: List<MemoryResponse>, limit: Int = 3): List<MemoryAppProposal> {
        if (memories.size < 3 || limit <= 0) return emptyList()
        val candidates = domainProfiles.mapNotNull { profile -> profileProposal(profile, memories) } +
            keywordProposals(memories)

        return candidates
            .distinctBy { it.topicKey }
            .sortedWith(compareByDescending<MemoryAppProposal> { it.confidence }.thenBy { it.appName })
            .take(limit)
    }

    private fun profileProposal(profile: DomainProfile, memories: List<MemoryResponse>): MemoryAppProposal? {
        val matched = memories.filter { memory ->
            memory.textTokens().any { it in profile.keywords }
        }
        if (matched.size < 3) return null
        val evidence = matched.take(5).map { it.evidence() }
        val reason = "Aura found ${matched.size} memories around ${profile.appName.removeSuffix(" Tracker").lowercase(Locale.US)}."
        val confidence = min(0.95f, 0.55f + matched.size * 0.08f)
        return MemoryAppProposal(
            id = "memory-app:${profile.topicKey}:${matched.map { it.id }.sorted().take(6).joinToString("-").hashCode()}",
            topicKey = profile.topicKey,
            appName = profile.appName,
            category = profile.category,
            reason = reason,
            buildPrompt = buildPrompt(profile, evidence),
            suggestedFields = profile.fields,
            suggestedActions = profile.actions,
            evidence = evidence,
            confidence = confidence
        )
    }

    private fun keywordProposals(memories: List<MemoryResponse>): List<MemoryAppProposal> {
        val tokenToMemories = linkedMapOf<String, MutableList<MemoryResponse>>()
        memories.forEach { memory ->
            memory.textTokens()
                .filter { it.length >= 5 && it !in stopWords }
                .filter { it !in domainKeywords }
                .distinct()
                .forEach { token -> tokenToMemories.getOrPut(token) { mutableListOf() }.add(memory) }
        }
        return tokenToMemories
            .filterValues { it.size >= 3 }
            .map { (token, matched) ->
                val label = token.readableLabel()
                val appName = "${label} Tracker"
                val evidence = matched.take(5).map { it.evidence() }
                val fields = listOf(token, "status", "next_step", "priority", "notes")
                val actions = listOf("log_$token", "save_next_step", "mark_priority", "review_$token")
                MemoryAppProposal(
                    id = "memory-app:$token:${matched.map { it.id }.sorted().take(6).joinToString("-").hashCode()}",
                    topicKey = token,
                    appName = appName,
                    category = "Custom",
                    reason = "Aura found ${matched.size} memories repeatedly mentioning $label.",
                    buildPrompt = buildPrompt(
                        DomainProfile(
                            topicKey = token,
                            appName = appName,
                            category = "Custom",
                            keywords = setOf(token),
                            fields = fields,
                            actions = actions,
                            promptFocus = "$label notes, statuses, priorities, next steps, and review"
                        ),
                        evidence
                    ),
                    suggestedFields = fields,
                    suggestedActions = actions,
                    evidence = evidence,
                    confidence = min(0.82f, 0.48f + matched.size * 0.07f)
                )
            }
    }

    private fun buildPrompt(profile: DomainProfile, evidence: List<MemoryAppEvidence>): String {
        val evidenceText = evidence.joinToString("\n") { "- ${it.title}: ${it.excerpt}" }
        return """
            Build a polished Aura mini app using runtime react named "${profile.appName}".
            It should turn repeated memories into an active tracker for ${profile.promptFocus}.
            Category: ${profile.category}.
            Use local records and preserve privacy by storing data locally.
            Suggested record fields: ${profile.fields.joinToString(", ")}.
            Suggested quick actions: ${profile.actions.joinToString(", ")}.
            Include a dashboard, timeline/history, useful charts or lists, a record form, assistant intents for logging and opening the app, and concise review guidance.
            Ground the first version in these memory examples:
            $evidenceText
        """.trimIndent()
    }

    private fun MemoryResponse.textTokens(): Set<String> =
        "${title.lowercase(Locale.US)} ${content.lowercase(Locale.US)}"
            .replace(Regex("[^a-z0-9\\s]+"), " ")
            .split(Regex("\\s+"))
            .mapNotNull { token ->
                val cleaned = token.trim().singularize()
                cleaned.takeIf { it.length >= 3 && it !in stopWords }
            }
            .toSet()

    private fun MemoryResponse.evidence() = MemoryAppEvidence(
        memoryId = id,
        title = title.ifBlank { "Memory" },
        excerpt = content.trim().replace(Regex("\\s+"), " ").let { if (it.length <= 120) it else it.take(117) + "..." }
    )

    private fun String.singularize(): String =
        when {
            length > 4 && endsWith("ies") -> dropLast(3) + "y"
            endsWith("ss") || endsWith("us") -> this
            length > 3 && endsWith("s") -> dropLast(1)
            else -> this
        }

    private fun String.readableLabel(): String =
        split('_', '-', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase(Locale.US) } }
            .ifBlank { "Memory" }

    private data class DomainProfile(
        val topicKey: String,
        val appName: String,
        val category: String,
        val keywords: Set<String>,
        val fields: List<String>,
        val actions: List<String>,
        val promptFocus: String
    )
}
