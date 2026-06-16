package com.aura.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAppProposalEngineTest {
    @Test
    fun repeatedInterviewMemoriesProposeInterviewPrepTracker() {
        val memories = listOf(
            memory("m1", "Interview loop", "Need to practice system design and behavioral answers for backend interviews."),
            memory("m2", "Recruiter prep", "Interview with Stripe recruiter next week, prep company notes and role questions."),
            memory("m3", "Coding round", "Practice interview coding questions and confidence notes after each attempt."),
            memory("m4", "Offer thoughts", "Track follow ups and offer notes after interview calls.")
        )

        val proposals = MemoryAppProposalEngine.propose(memories)

        assertEquals("Interview Prep Tracker", proposals.first().appName)
        assertEquals("interview_prep", proposals.first().topicKey)
        assertTrue(proposals.first().suggestedFields.contains("company"))
        assertTrue(proposals.first().buildPrompt.contains("runtime react"))
        assertEquals(4, proposals.first().evidence.size)
    }

    @Test
    fun genericRepeatedMemoryTopicBecomesCustomTracker() {
        val memories = listOf(
            memory("m1", "Garden basil", "Basil needs morning water and sunlight notes."),
            memory("m2", "Garden mint", "Mint sunlight seems low, remember to rotate the pot."),
            memory("m3", "Garden rose", "Rose sunlight is strong, track watering and bloom notes."),
            memory("m4", "Groceries", "Buy rice and coffee.")
        )

        val proposal = MemoryAppProposalEngine.propose(memories).first()

        assertEquals("Garden Tracker", proposal.appName)
        assertEquals("garden", proposal.topicKey)
        assertEquals("Custom", proposal.category)
        assertTrue(proposal.suggestedActions.contains("review_garden"))
        assertEquals(3, proposal.evidence.size)
    }

    @Test
    fun sparseMemoriesDoNotCreateProposal() {
        val memories = listOf(
            memory("m1", "Interview", "One interview reminder."),
            memory("m2", "Gym", "One workout thought.")
        )

        assertTrue(MemoryAppProposalEngine.propose(memories).isEmpty())
    }

    @Test
    fun genericUtilityWordsDoNotBecomeCustomTrackers() {
        val memories = listOf(
            memory("m1", "Status note", "Remember status notes for class planning."),
            memory("m2", "Status note", "Track status notes after class review."),
            memory("m3", "Status note", "Need status notes before class starts.")
        )

        assertTrue(MemoryAppProposalEngine.propose(memories).isEmpty())
    }

    @Test
    fun nonPositiveLimitReturnsNoProposals() {
        val memories = listOf(
            memory("m1", "Interview loop", "Practice interview system design."),
            memory("m2", "Recruiter prep", "Interview notes and recruiter follow up."),
            memory("m3", "Coding round", "Interview coding practice.")
        )

        assertTrue(MemoryAppProposalEngine.propose(memories, limit = 0).isEmpty())
        assertTrue(MemoryAppProposalEngine.propose(memories, limit = -1).isEmpty())
    }

    private fun memory(id: String, title: String, content: String) =
        MemoryResponse(id = id, title = title, content = content, created_at = "2026-01-12T09:30:00Z")
}
