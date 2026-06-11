package com.aura.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSpeakerTest {
    @Test
    fun cleanTextForSpeechStripsEmotionTagsAndTrims() {
        val cleaned = VoiceSpeaker.cleanTextForSpeech("  {happy} Hello {excited_2} world  ")

        assertEquals("Hello world", cleaned)
    }

    @Test
    fun cleanTextForSpeechLeavesPlainRepliesUntouched() {
        assertEquals("Open calendar", VoiceSpeaker.cleanTextForSpeech("Open calendar"))
    }
}
