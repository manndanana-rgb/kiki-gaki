package app.kikigaki.stt

data class SttResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 0f,
    val latencyMs: Long = 0
)

interface LiveSttEngine {

    val name: String
    val isInitialized: Boolean

    /** モデル読み込み(必要ならDLを含む)。成功で true */
    suspend fun init(): Boolean

    /** 新しい認識セッションを開始 */
    fun start()

    /** 16kHz/mono/PCM16 の音声チャンクを供給 */
    fun feedChunk(pcm: ByteArray, offset: Int = 0, length: Int = pcm.size)

    /** セッション終了。確定テキストを返す */
    fun stop(): String?

    /** リソース解放 */
    fun release()

    fun setListener(listener: Listener?)

    interface Listener {
        fun onPartial(result: SttResult)
        fun onFinal(result: SttResult)
        fun onError(message: String)
    }
}
