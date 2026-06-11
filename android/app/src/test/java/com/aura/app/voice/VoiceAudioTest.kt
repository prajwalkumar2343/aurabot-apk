package com.aura.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAudioTest {
    @Test
    fun pcm16ToWavWritesExpectedHeaderAndDataSize() {
        val pcm = ByteArray(VoiceAudio.SAMPLE_RATE * VoiceAudio.BYTES_PER_SAMPLE / 2) { 1 }

        val wav = VoiceAudio.pcm16ToWav(pcm)

        assertEquals("RIFF", wav.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", wav.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals("data", wav.copyOfRange(36, 40).toString(Charsets.US_ASCII))
        assertEquals(pcm.size, wav.copyOfRange(40, 44).toLittleEndianInt())
        assertEquals(pcm.size + 44, wav.size)
    }

    @Test
    fun isLongEnoughForTranscriptionRequiresMinimumDuration() {
        val tooShort = ByteArray(VoiceAudio.SAMPLE_RATE * VoiceAudio.BYTES_PER_SAMPLE / 10)
        val longEnough = ByteArray(VoiceAudio.SAMPLE_RATE * VoiceAudio.BYTES_PER_SAMPLE / 2)

        assertFalse(VoiceAudio.isLongEnoughForTranscription(tooShort))
        assertTrue(VoiceAudio.isLongEnoughForTranscription(longEnough))
    }

    private fun ByteArray.toLittleEndianInt(): Int =
        (this[0].toInt() and 0xff) or
            ((this[1].toInt() and 0xff) shl 8) or
            ((this[2].toInt() and 0xff) shl 16) or
            ((this[3].toInt() and 0xff) shl 24)
}
