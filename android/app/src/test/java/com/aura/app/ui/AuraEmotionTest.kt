package com.aura.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraEmotionTest {
    @Test
    fun vocabularyCoversCuteThroughAngryStates() {
        assertTrue(AuraEmotion.entries.size >= 30)
        assertEquals(AuraEmotion.Cute, AuraEmotion.fromWireValue("cute"))
        assertEquals(AuraEmotion.Angry, AuraEmotion.fromWireValue("angry"))
        assertTrue(AuraEmotion.entries.any { it == AuraEmotion.Adoring })
        assertTrue(AuraEmotion.entries.any { it == AuraEmotion.Enraged })
        assertTrue(AuraEmotion.Cute.profile.pupilScale > AuraEmotion.Angry.profile.pupilScale)
        assertTrue(AuraEmotion.Angry.profile.tiltDegrees > AuraEmotion.Cute.profile.tiltDegrees)
        assertTrue(AuraEmotion.Playful.profile.winkStrength > 0f)
        assertEquals(0f, AuraEmotion.Neutral.profile.winkStrength)
    }

    @Test
    fun unknownWireValueAndLegacyReplyTagUseNeutralOrKnownFallbacks() {
        assertEquals(AuraEmotion.Neutral, AuraEmotion.fromWireValue("unknown"))
        assertEquals(AuraEmotion.Happy, AuraEmotion.fromReplyTag("{happy} Welcome back"))
        assertEquals(AuraEmotion.Neutral, AuraEmotion.fromReplyTag("Welcome back"))
    }

    @Test
    fun createdEmotionBuildsAStableProfileWithoutEnumChanges() {
        val dynamic = AuraEmotion.resolve("neutral", "create dreamily curious")

        assertEquals("create dreamily curious", dynamic.createdEmotion)
        assertEquals("Dreamily Curious", dynamic.label)
        assertTrue(dynamic.wireValue.startsWith("custom:"))
        assertTrue(dynamic.profile.asymmetry > 0f)
        assertTrue(dynamic.profile.openness > AuraEmotion.Neutral.profile.openness)
        assertEquals(AuraEmotion.Neutral.wireValue, AuraEmotion.resolve("neutral", "dreamily curious").wireValue)
    }
}
