package app.kikigaki.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadManager(private val context: Context) {

    val downloadState: StateFlow<DownloadState> get() = _downloadState
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Int, val bytesDone: Long, val totalBytes: Long) : DownloadState()
        object Completed : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    fun voskModelDir(): File = File(context.filesDir, "models/vosk-small-ja")

    fun isVoskModelReady(): Boolean {
        val dir = voskModelDir()
        return dir.exists() && (dir.listFiles()?.any { it.isDirectory && it.name == "am" } == true)
    }

    suspend fun downloadVoskJaSmall(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val dir = voskModelDir()
        if (!force && isVoskModelReady()) {
            _downloadState.value = DownloadState.Completed
            return@withContext true
        }
        try {
            if (force) dir.deleteRecursively()
            dir.mkdirs()
            _downloadState.value = DownloadState.Downloading(0, 0, VoskSttEngine.MODEL_SIZE_BYTES)
            val url = URL(VoskSttEngine.MODEL_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.connect()
            val total = conn.contentLength.toLong().coerceAtLeast(1)
            val tmpZip = File(context.cacheDir, "vosk-ja.zip")
            FileOutputStream(tmpZip).use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = ((done * 100) / total).toInt()
                        _downloadState.value = DownloadState.Downloading(pct, done, total)
                    }
                }
            }
            // 解凍
            java.util.zip.ZipInputStream(FileInputStream(tmpZip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val target = File(dir, entry.name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            tmpZip.delete()
            _downloadState.value = DownloadState.Completed
            true
        } catch (e: Exception) {
            Log.e(TAG, "Vosk モデルDL失敗", e)
            _downloadState.value = DownloadState.Failed(e.message ?: "不明なエラー")
            false
        }
    }

    companion object {
        private const val TAG = "ModelDownloadManager"
    }
}
