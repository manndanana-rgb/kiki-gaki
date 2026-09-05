package app.kikigaki.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class VoskSttEngine(
    private val context: Context,
    private val modelAssetPath: String? = null,
    private val modelDir: File = File(context.filesDir, "models/vosk-small-ja")
) : LiveSttEngine {

    override val name: String = "Vosk"
    override val isInitialized: Boolean get() = _initialized
    private var _initialized = false
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var listener: LiveSttEngine.Listener? = null

    override suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true) {
                model = Model(modelDir.absolutePath)
                _initialized = true
                return@withContext true
            }
            modelAssetPath?.let { assetPath ->
                context.assets.open(assetPath).use { input ->
                    extractAsset(input, modelDir)
                }
                model = Model(modelDir.absolutePath)
                _initialized = true
                return@withContext true
            }
            Log.w(TAG, "Vosk モデル未配置: $modelDir も assets($modelAssetPath) もない")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Vosk init 失敗", e)
            listener?.onError("Vosk初期化失敗: ${e.message}")
            false
        }
    }

    private fun extractAsset(input: InputStream, dir: File) {
        dir.mkdirs()
        val out = File(dir, "model.zip")
        FileOutputStream(out).use { input.copyTo(it) }
        java.util.zip.ZipInputStream(FileInputStream(out)).use { zis ->
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
        out.delete()
    }

    override fun start() {
        val m = model ?: return
        recognizer = Recognizer(m, 16000f).apply {
            setWords(false)
            setPartialWords(false)
        }
        _initialized = true
    }

    override fun feedChunk(pcm: ByteArray, offset: Int, length: Int) {
        val rec = recognizer ?: return
        // PCM16 リトルエンディアン → short[] (vosk-android 0.3.x API)
        val samples = length / 2
        val shorts = ShortArray(samples)
        for (i in 0 until samples) {
            val lo = pcm[offset + i * 2].toInt() and 0xff
            val hi = pcm[offset + i * 2 + 1].toInt()
            shorts[i] = (hi shl 8 or lo).toShort()
        }
        when {
            rec.acceptWaveform(shorts, samples) -> {
                val text = rec.result
                    .let { Regex("\"text\"\\s*:\\s*\"(.*?)\"").find(it)?.groupValues?.get(1) ?: "" }
                if (text.isNotBlank()) listener?.onFinal(SttResult(text, true))
            }
            else -> {
                val partial = rec.partialResult
                    .let { Regex("\"partial\"\\s*:\\s*\"(.*?)\"").find(it)?.groupValues?.get(1) ?: "" }
                if (partial.isNotBlank()) listener?.onPartial(SttResult(partial, false))
            }
        }
    }

    override fun stop(): String? {
        val text = recognizer?.finalResult
            ?.let { Regex("\"text\"\\s*:\\s*\"(.*?)\"").find(it)?.groupValues?.get(1) ?: "" }
        recognizer = null
        return text?.takeIf { it.isNotBlank() }
    }

    override fun release() {
        recognizer = null
        model = null
        _initialized = false
    }

    override fun setListener(listener: LiveSttEngine.Listener?) {
        this.listener = listener
    }

    fun getProgressModelDir(): File = modelDir

    companion object {
        private const val TAG = "VoskSttEngine"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip"
        const val MODEL_SIZE_BYTES = 47_000_000L // ~47MB
    }
}
