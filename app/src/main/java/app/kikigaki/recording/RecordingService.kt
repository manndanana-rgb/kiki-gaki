package app.kikigaki.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import app.kikigaki.data.AppDatabase
import app.kikigaki.data.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class RecordingService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var audioRecord: AudioRecord? = null
    private var wavWriter: WavWriter? = null
    private var recordJob: Job? = null
    private var tickJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private lateinit var recordingsDir: File
    private var currentFile: File? = null

    inner class LocalBinder : Binder() {
        val service: RecordingService get() = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        when (intent?.action) {
            "START" -> startRecording()
            "PAUSE" -> pauseRecording()
            "RESUME" -> resumeRecording()
            "STOP" -> stopRecording()
        }
        return START_STICKY
    }

    fun startRecording() {
        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) return

        val file = File(recordingsDir, "rec_${System.currentTimeMillis()}.wav")
        currentFile = file
        wavWriter = WavWriter(file, sampleRate)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, channelConfig, audioFormat, bufferSize
        )
        audioRecord = recorder
        recorder.startRecording()

        val dao = AppDatabase.get(this).recordingDao()
        serviceScope.launch {
            val id = dao.insert(
                Recording(
                    title = "録音 ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.JAPAN).format(java.util.Date())}",
                    createdAt = System.currentTimeMillis(),
                    durationMs = 0,
                    sampleRate = sampleRate,
                    filePath = file.absolutePath,
                    status = "recording"
                )
            )
            RecordingStateManager.update { it.copy(currentRecordingId = id, isRecording = true, isPaused = false, filePath = file.absolutePath) }
        }

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KikiGaki:recording").apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L) // 1時間
        }

        recordJob = serviceScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (true) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    wavWriter?.write(buffer, 0, read)
                    var peak = 0
                    for (i in 0 until read step 2) {
                        val v = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xff)
                        val a = abs(v)
                        if (a > peak) peak = a
                    }
                    RecordingStateManager.update { it.copy(peakAmplitude = peak) }
                } else if (read < 0) break
            }
        }

        val startMs = SystemClock.elapsedRealtime()
        tickJob = serviceScope.launch {
            while (true) {
                delay(200)
                RecordingStateManager.update {
                    it.copy(elapsedMs = SystemClock.elapsedRealtime() - startMs)
                }
            }
        }
    }

    fun pauseRecording() {
        audioRecord?.stop()
        tickJob?.cancel()
        RecordingStateManager.update { it.copy(isPaused = true) }
    }

    fun resumeRecording() {
        audioRecord?.startRecording()
        val startMs = SystemClock.elapsedRealtime() - RecordingStateManager.state.value.elapsedMs
        tickJob = serviceScope.launch {
            while (true) {
                delay(200)
                RecordingStateManager.update {
                    it.copy(elapsedMs = SystemClock.elapsedRealtime() - startMs, isPaused = false)
                }
            }
        }
    }

    fun stopRecording() {
        recordJob?.cancel()
        tickJob?.cancel()
        audioRecord?.apply { stop(); release() }
        audioRecord = null
        wavWriter?.close()
        wavWriter = null
        wakeLock?.release()

        val id = RecordingStateManager.state.value.currentRecordingId
        if (id != null) {
            serviceScope.launch {
                val dao = AppDatabase.get(this@RecordingService).recordingDao()
                val r = dao.getById(id)
                if (r != null) {
                    dao.update(
                        r.copy(
                            durationMs = RecordingStateManager.state.value.elapsedMs,
                            status = "processing"
                        )
                    )
                }
                RecordingStateManager.reset()
            }
        } else {
            RecordingStateManager.reset()
        }
    }

    private fun startForegroundCompat() {
        val notif = buildNotification("録音準備中")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, "録音", NotificationManager.IMPORTANCE_LOW)
        ch.description = "ききがき 録音中の通知"
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ききがき")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        audioRecord?.release()
        wakeLock?.release()
    }

    companion object {
        const val CHANNEL_ID = "kiki_gaki_recording"
        const val NOTIF_ID = 1001
    }
}
