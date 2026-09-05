package app.kikigaki.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class SystemSttEngine(private val context: Context) : LiveSttEngine {

    override val name: String = "Android SpeechRecognizer"
    override val isInitialized: Boolean get() = _initialized
    private var _initialized = false
    private var recognizer: SpeechRecognizer? = null
    private var listener: LiveSttEngine.Listener? = null

    override suspend fun init(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener?.onError("音声認識が利用できません")
            return false
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        _initialized = true
        return true
    }

    override fun start() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) listener?.onPartial(SttResult(text, false))
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) listener?.onFinal(SttResult(text, true))
            }
            override fun onError(error: Int) {
                listener?.onError("SpeechRecognizer error: $error")
            }
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
        recognizer?.startListening(intent)
    }

    // SpeechRecognizer は PCM ストリームを直接扱えない(内部でマイクを取る)ため feedChunk は無視
    override fun feedChunk(pcm: ByteArray, offset: Int, length: Int) {}

    override fun stop(): String? {
        recognizer?.stopListening()
        recognizer?.cancel()
        recognizer = null
        return null
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
        _initialized = false
    }

    override fun setListener(listener: LiveSttEngine.Listener?) {
        this.listener = listener
    }
}
