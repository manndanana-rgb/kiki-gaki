package app.kikigaki.stt

enum class SttEngineType(val displayName: String, val description: String) {
    VOSK("Vosk", "オフライン・日本語smallモデル(~48MB)"),
    SYSTEM("Android SpeechRecognizer", "内蔵・Googleサービス依存"),
    SHERPA_ONNX("sherpa-onnx", "オフライン・ストリーミングZipformer(日本語)")
}
