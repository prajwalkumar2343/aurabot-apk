package com.aura.app.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aura.app.LauncherActivity
import com.aura.app.R
import android.provider.Settings
import com.aura.app.AuraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AuraListeningService : Service() {
    private var recorder: AudioRecord? = null
    private var recorderThread: Thread? = null
    private var noiseFloor = 0.0
    private var speechFrames = 0
    private var silenceFrames = 0
    private val pcmOutputStream = java.io.ByteArrayOutputStream()
    private val preSpeechPcmOutputStream = java.io.ByteArrayOutputStream()

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    @Volatile
    private var cachedAppMode = "launcher"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            try {
                val container = (application as AuraApplication).container
                container.sessionStore.state.collect { sessionState ->
                    cachedAppMode = sessionState.appMode
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                stopForegroundCompat()
                stopSelf()
                START_NOT_STICKY
            }
            else -> {
                if (!hasRecordPermission()) {
                    stopSelf()
                    START_NOT_STICKY
                } else {
                    startAsForeground()
                    if (!startListening()) {
                        stopListening()
                        stopForegroundCompat()
                        stopSelf()
                        START_NOT_STICKY
                    } else {
                        START_STICKY
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        stopListening()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning.set(true)
        emitEvent()
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, LauncherActivity::class.java).let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val stopIntent = Intent(this, AuraListeningService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Aura is listening")
            .setContentText("Background listening is on.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(R.drawable.notification_icon, "Stop", stopPendingIntent)
            .build()
    }

    private fun startListening(): Boolean {
        if (recorderThread?.isAlive == true) return true

        val sampleRate = VoiceAudio.SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) return false

        val bufferSize = maxOf(minBufferSize * 4, 4096)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return false
        }

        resetVad()
        recorder = audioRecord
        recorderThread = thread(name = "AuraListeningService", isDaemon = true) {
            val buffer = ShortArray(bufferSize / 2)
            try {
                audioRecord.startRecording()
                while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                    val samplesRead = audioRecord.read(buffer, 0, buffer.size)
                    if (samplesRead > 0) {
                        processAudioFrame(buffer, samplesRead)
                    } else if (samplesRead < 0) {
                        Thread.sleep(100)
                    } else {
                        Thread.sleep(10)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop()
                    }
                } catch (_: Exception) {
                }
                audioRecord.release()
            }
        }

        return true
    }

    private fun stopListening() {
        finishRecordedAudioIfNeeded()
        isRunning.set(false)
        isSpeechDetected.set(false)
        emitEvent()
        recorderThread?.interrupt()
        if (recorderThread != null && recorderThread != Thread.currentThread()) {
            try {
                recorderThread?.join(500)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        recorderThread = null
        recorder = null
    }

    private fun resetVad() {
        noiseFloor = 0.0
        speechFrames = 0
        silenceFrames = 0
        lastRms.set(0)
        isSpeechDetected.set(false)
        synchronized(pcmOutputStream) {
            pcmOutputStream.reset()
            preSpeechPcmOutputStream.reset()
        }
    }

    private fun processAudioFrame(buffer: ShortArray, samplesRead: Int) {
        var sumSquares = 0L
        for (index in 0 until samplesRead) {
            val sample = buffer[index].toLong()
            sumSquares += sample * sample
        }

        val rawRms = sqrt(sumSquares.toDouble() / samplesRead)
        val rms = rawRms * RAW_RMS_MULTIPLIER
        lastRms.set((rawRms * RMS_TO_INT_MULTIPLIER).roundToInt())

        if (!isSpeechDetected.get()) {
            noiseFloor = if (noiseFloor == 0.0) rms else (noiseFloor * 0.96) + (rms * 0.04)
        }

        val speechThreshold = maxOf(MIN_SPEECH_RMS, noiseFloor * SPEECH_THRESHOLD_MULTIPLIER)
        val hasSpeech = rms >= speechThreshold

        if (hasSpeech) {
            speechFrames += 1
            silenceFrames = 0
        } else {
            silenceFrames += 1
            speechFrames = 0
        }

        if (!isSpeechDetected.get() && speechFrames >= SPEECH_START_FRAMES) {
            isSpeechDetected.set(true)
            speechEvents.incrementAndGet()
            lastSpeechAt.set(System.currentTimeMillis())
            synchronized(pcmOutputStream) {
                pcmOutputStream.reset()
                pcmOutputStream.write(preSpeechPcmOutputStream.toByteArray())
                preSpeechPcmOutputStream.reset()
            }
            emitEvent()

            try {
                if (cachedAppMode == "overlay" && Settings.canDrawOverlays(this@AuraListeningService)) {
                    val launchIntent = Intent(this@AuraListeningService, LauncherActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("voice_triggered", true)
                    }
                    startActivity(launchIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        synchronized(pcmOutputStream) {
            if (isSpeechDetected.get()) {
                VoiceAudio.appendPcm16LittleEndian(pcmOutputStream, buffer, samplesRead)
            } else {
                appendPreSpeechFrame(buffer, samplesRead)
            }
        }

        if (isSpeechDetected.get() && (
                silenceFrames >= SPEECH_END_FRAMES ||
                    synchronized(pcmOutputStream) { pcmOutputStream.size() >= MAX_RECORDED_PCM_BYTES }
                )
        ) {
            finishRecordedAudioIfNeeded()
            emitEvent()
        }
    }

    private fun appendPreSpeechFrame(buffer: ShortArray, samplesRead: Int) {
        VoiceAudio.appendPcm16LittleEndian(preSpeechPcmOutputStream, buffer, samplesRead)
        if (preSpeechPcmOutputStream.size() > PRE_SPEECH_PCM_BYTES) {
            val bytes = preSpeechPcmOutputStream.toByteArray()
            preSpeechPcmOutputStream.reset()
            preSpeechPcmOutputStream.write(bytes, bytes.size - PRE_SPEECH_PCM_BYTES, PRE_SPEECH_PCM_BYTES)
        }
    }

    private fun finishRecordedAudioIfNeeded() {
        if (!isSpeechDetected.getAndSet(false)) return
        val audioBytes = synchronized(pcmOutputStream) {
            val bytes = pcmOutputStream.toByteArray()
            pcmOutputStream.reset()
            preSpeechPcmOutputStream.reset()
            bytes
        }
        if (VoiceAudio.isLongEnoughForTranscription(audioBytes)) {
            val wavBytes = VoiceAudio.pcm16ToWav(audioBytes)
            val base64 = android.util.Base64.encodeToString(wavBytes, android.util.Base64.NO_WRAP)
            recordedAudioBase64.set(base64)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Aura listening",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when Aura is actively listening in the background."
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "aura_listening"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_STOP = "com.aura.app.action.STOP_LISTENING"
        private const val ACTION_START = "com.aura.app.action.START_LISTENING"
        private const val MIN_SPEECH_RMS = 0.018
        private const val SPEECH_THRESHOLD_MULTIPLIER = 3.2
        private const val SPEECH_START_FRAMES = 3
        private const val SPEECH_END_FRAMES = 10
        private const val PRE_SPEECH_PCM_BYTES =
            VoiceAudio.SAMPLE_RATE * VoiceAudio.CHANNELS * VoiceAudio.BYTES_PER_SAMPLE / 4
        private const val MAX_RECORDED_PCM_BYTES =
            VoiceAudio.SAMPLE_RATE * VoiceAudio.CHANNELS * VoiceAudio.BYTES_PER_SAMPLE * 15

        private const val RAW_RMS_MULTIPLIER = 1.0 / 32767.0
        private const val RMS_TO_INT_MULTIPLIER = 1000.0 / 32767.0

        private val isRunning = AtomicBoolean(false)
        private val isSpeechDetected = AtomicBoolean(false)
        private val lastRms = AtomicInteger(0)
        private val speechEvents = AtomicInteger(0)
        private val lastSpeechAt = AtomicLong(0)
        private val recordedAudioBase64 = java.util.concurrent.atomic.AtomicReference<String?>(null)

        @Volatile
        private var eventSink: (() -> Unit)? = null

        fun start(context: Context) {
            val intent = Intent(context, AuraListeningService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AuraListeningService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun clearLastRecordedAudio(expectedBase64: String? = null) {
            if (expectedBase64 == null) {
                recordedAudioBase64.set(null)
            } else {
                recordedAudioBase64.compareAndSet(expectedBase64, null)
            }
            emitEvent()
        }

        fun status(): ListeningStatus =
            ListeningStatus(
                running = isRunning.get(),
                speechDetected = isSpeechDetected.get(),
                rmsLevel = lastRms.get(),
                speechEvents = speechEvents.get(),
                lastSpeechAt = lastSpeechAt.get(),
                lastRecordedAudioBase64 = recordedAudioBase64.get()
            )

        fun setEventSink(sink: (() -> Unit)?) {
            eventSink = sink
        }

        private fun emitEvent() {
            eventSink?.invoke()
        }
    }
}
