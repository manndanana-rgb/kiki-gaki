package app.kikigaki.stt

import android.content.Context

object SttEngineFactory {
    fun create(type: SttEngineType, context: Context): LiveSttEngine {
        return when (type) {
            SttEngineType.VOSK -> VoskSttEngine(context)
            SttEngineType.SYSTEM -> SystemSttEngine(context)
            SttEngineType.SHERPA_ONNX -> SystemSttEngine(context) // 未実装フォールバック
        }
    }
}
