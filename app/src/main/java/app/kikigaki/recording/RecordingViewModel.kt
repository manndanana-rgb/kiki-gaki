package app.kikigaki.recording

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.kikigaki.stt.LiveSttEngine
import app.kikigaki.stt.ModelDownloadManager
import app.kikigaki.stt.SttEngineFactory
import app.kikigaki.stt.SttEngineType
import app.kikigaki.stt.SttResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecordingViewModel(app: Application) : AndroidViewModel(app) {

    val uiState: StateFlow<RecordingUiState> = RecordingStateManager.state

    private val downloadMgr = ModelDownloadManager(app)

    private val _engineStates = MutableStateFlow<Map<SttEngineType, EngineUiState>>(emptyMap())
    val engineStates: StateFlow<Map<SttEngineType, EngineUiState>> = _engineStates.asStateFlow()

    private val _selectedEngine = MutableStateFlow(SttEngineType.VOSK)
    val selectedEngine: StateFlow<SttEngineType> = _selectedEngine.asStateFlow()

    private val engines = mutableMapOf<SttEngineType, LiveSttEngine>()
    private val engineResults = mutableMapOf<SttEngineType, MutableList<SttResult>>()

    data class EngineUiState(
        val initialized: Boolean = false,
        val partialText: String = "",
        val finalText: String = "",
        val downloadProgress: ModelDownloadManager.DownloadState = ModelDownloadManager.DownloadState.Idle,
        val latencyMs: Long = 0,
        val errorMessage: String? = null
    )

    init {
        // 全エンジン初期化を試みる(設定画面で使えるように)
        viewModelScope.launch {
            SttEngineType.entries.forEach { type ->
                val engine = SttEngineFactory.create(type, getApplication())
                engines[type] = engine
                engine.setListener(object : LiveSttEngine.Listener {
                    override fun onPartial(result: SttResult) {
                        val list = engineResults.getOrPut(type) { mutableListOf() }
                        list.removeAll { !it.isFinal }
                        list.add(result)
                        updateEngineState(type) {
                            it.copy(partialText = result.text, latencyMs = result.latencyMs)
                        }
                    }
                    override fun onFinal(result: SttResult) {
                        val list = engineResults.getOrPut(type) { mutableListOf() }
                        list.removeAll { !it.isFinal }
                        list.add(result)
                        updateEngineState(type) {
                            it.copy(finalText = it.finalText + " " + result.text, partialText = "")
                        }
                    }
                    override fun onError(message: String) {
                        updateEngineState(type) { it.copy(errorMessage = message) }
                    }
                })
                // Vosk はモデル事前DLが必要
                if (type == SttEngineType.VOSK && !downloadMgr.isVoskModelReady()) {
                    updateEngineState(type) {
                        it.copy(downloadProgress = ModelDownloadManager.DownloadState.Idle)
                    }
                } else {
                    val ok = engine.init()
                    updateEngineState(type) { it.copy(initialized = ok) }
                }
            }
        }
    }

    fun selectEngine(type: SttEngineType) {
        _selectedEngine.value = type
    }

    fun downloadVoskModel(force: Boolean = false) {
        viewModelScope.launch {
            val type = SttEngineType.VOSK
            updateEngineState(type) {
                it.copy(downloadProgress = ModelDownloadManager.DownloadState.Downloading(0, 0, 0))
            }
            downloadMgr.downloadState.collect { st ->
                updateEngineState(type) { it.copy(downloadProgress = st) }
                if (st is ModelDownloadManager.DownloadState.Completed) {
                    val engine = engines[type]!!
                    val ok = engine.init()
                    updateEngineState(type) { it.copy(initialized = ok, downloadProgress = st) }
                }
                if (st is ModelDownloadManager.DownloadState.Failed) {
                    updateEngineState(type) {
                        it.copy(downloadProgress = st, errorMessage = st.message)
                    }
                }
            }
        }
    }

    fun startAllEngines() {
        engines.forEach { (type, engine) ->
            if (engine.isInitialized) {
                engine.start()
            }
        }
    }

    fun stopAllEngines() {
        engines.forEach { (_, engine) ->
            engine.stop()
        }
    }

    fun onPcmFrame(pcm: ByteArray, offset: Int, length: Int) {
        engines.forEach { (_, engine) ->
            if (engine.isInitialized) {
                engine.feedChunk(pcm, offset, length)
            }
        }
    }

    private fun updateEngineState(type: SttEngineType, updater: (EngineUiState) -> EngineUiState) {
        _engineStates.value = _engineStates.value.toMutableMap().apply {
            put(type, updater(getOrDefault(type, EngineUiState())))
        }
    }

    override fun onCleared() {
        super.onCleared()
        engines.values.forEach { it.release() }
    }
}
