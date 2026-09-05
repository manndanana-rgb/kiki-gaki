package app.kikigaki.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private var pcmListener: ((ByteArray, Int, Int) -> Unit)? = null

    fun setPcmListener(listener: ((ByteArray, Int, Int) -> Unit)?) {
        pcmListener = listener
    }

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private lateinit var recordingsDir: File

    inner class LocalBinder : Binder() {
        val service: RecordingService get() = this@RecordingService
        fun setPcmListener(l: ((ByteArray, Int, Int) -> Unit)?) = service.setPcmListener(l)
        fun getPcmListener(): ((ByteArray, Int, Int) -> Unit)? = service.pcmListener
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
        if (RecordingStateManager.state.value.isRecording) return

        val file = File(recordingsDir, "rec_${System.currentTimeMillis()}.wav")
        wavWriter = WavWriter(file, sampleRate)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, channelConfig, audioFormat, bufferSize * 2
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
            RecordingStateManager.update {
                it.copy(currentRecordingId = id, isRecording = true, isPaused = false, elapsedMs = 0, filePath = file.absolutePath)
            }
        }

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KikiGaki:recording").apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L) // 1時間上限
        }

        // 読み取りループ: 一時停止中(read<=0)は待機して再開後に自動復帰する
        recordJob = serviceScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    wavWriter?.write(buffer, 0, read)
                    // PCM リスナーにチャンクを渡す(ライブSTT用)
                    pcmListener?.invoke(buffer, 0, read)
                    var peak = 0
                    for (i in 0 until read step 2) {
                        val v = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xff)
                        val a = abs(v)
                        if (a > peak) peak = a
                    }
                    RecordingStateManager.update { it.copy(peakAmplitude = peak) }
                } else {
                    // 一時停止(stop)中または一時的エラー: 待機してリトライ
                    delay(50)
                }
            }
        }

        val startMs = SystemClock.elapsedRealtime()
        tickJob = serviceScope.launch {
            while (isActive) {
                delay(200)
                val elapsed = SystemClock.elapsedRealtime() - startMs
                RecordingStateManager.update { it.copy(elapsedMs = elapsed) }
                updateNotification(elapsed)
            }
        }
    }

    fun pauseRecording() {
        if (!RecordingStateManager.state.value.isRecording) return
        if (RecordingStateManager.state.value.isPaused) return
        audioRecord?.stop() // read() は即座にエラーを返す → ループは delay(50) で待機
        tickJob?.cancel()
        tickJob = null
        RecordingStateManager.update { it.copy(isPaused = true, peakAmplitude = 0) }
        updateNotification(RecordingStateManager.state.value.elapsedMs, paused = true)
    }

    fun resumeRecording() {
        val s = RecordingStateManager.state.value
        if (!s.isRecording || !s.isPaused) return
        audioRecord?.startRecording()
        val startMs = SystemClock.elapsedRealtime() - s.elapsedMs
        tickJob = serviceScope.launch {
            while (isActive) {
                delay(200)
                val elapsed = SystemClock.elapsedRealtime() - startMs
                RecordingStateManager.update { it.copy(elapsedMs = elapsed, isPaused = false) }
                updateNotification(elapsed)
            }
        }
    }

    fun stopRecording() {
        val s = RecordingStateManager.state.value
        if (!s.isRecording) return

        tickJob?.cancel()
        tickJob = null
        audioRecord?.apply { stop(); release() }
        audioRecord = null

        serviceScope.launch {
            // 読み取りループの終了を待ってからWAVをクローズ(書き込み競合防止)
            recordJob?.cancelAndJoin()
            recordJob = null
            withContext(Dispatchers.IO) {
                wavWriter?.close()
                wavWriter = null
            }
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null

            val id = s.currentRecordingId
            if (id != null) {
                val dao = AppDatabase.get(this@RecordingService).recordingDao()
                dao.getById(id)?.let { r ->
                    dao.update(r.copy(durationMs = s.elapsedMs, status = "processing"))
                }
            }
            RecordingStateManager.reset()
            // 録音停止後は前台通知を消してサービスも終了(UIがバインド中は破棄されない)
            mainExecutor.execute {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun updateNotification(elapsedMs: Long = 0, paused: Boolean = false, idle: Boolean = false) {
        val text = when {
            idle -> "停止中"
            paused -> "一時停止中 ${formatTime(elapsedMs)}"
            else -> "録音中 ${formatTime(elapsedMs)}"
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    private fun startForegroundCompat() {
        val notif = buildNotification("準備完了")
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
        val stopIntent = Intent(this, RecordingService::class.java).apply { action = "STOP" }
        val stopPi = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPi = openIntent?.let {
            PendingIntent.getActivity(this, 3, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ききがき")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .apply { if (openPi != null) setContentIntent(openPi) }
            .addAction(0, "停止", stopPi)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        audioRecord?.release()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    companion object {
        const val CHANNEL_ID = "kiki_gaki_recording"
        const val NOTIF_ID = 1001
    }
}
