package com.aura.app.voice

import java.io.ByteArrayOutputStream

internal object VoiceAudio {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    const val MIN_TRANSCRIBABLE_PCM_MS = 300L

    fun appendPcm16LittleEndian(output: ByteArrayOutputStream, buffer: ShortArray, samplesRead: Int) {
        for (i in 0 until samplesRead) {
            val sample = buffer[i]
            output.write(sample.toInt() and 0xFF)
            output.write((sample.toInt() shr 8) and 0xFF)
        }
    }

    fun pcmDurationMs(
        pcmByteCount: Int,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = CHANNELS,
        bitsPerSample: Int = BITS_PER_SAMPLE
    ): Long {
        val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)
        if (bytesPerSecond <= 0) return 0
        return (pcmByteCount * 1_000L) / bytesPerSecond
    }

    fun isLongEnoughForTranscription(pcmBytes: ByteArray): Boolean =
        pcmDurationMs(pcmBytes.size) >= MIN_TRANSCRIBABLE_PCM_MS

    fun pcm16ToWav(
        pcmBytes: ByteArray,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = CHANNELS,
        bitsPerSample: Int = BITS_PER_SAMPLE
    ): ByteArray {
        val totalAudioLen = pcmBytes.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val longSampleRate = sampleRate.toLong()
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val wavBytes = ByteArray(44 + pcmBytes.size)
        System.arraycopy(header, 0, wavBytes, 0, 44)
        System.arraycopy(pcmBytes, 0, wavBytes, 44, pcmBytes.size)
        return wavBytes
    }
}
