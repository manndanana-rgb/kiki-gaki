package app.kikigaki.recording

import app.kikigaki.data.Recording
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecordingUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val currentRecordingId: Long? = null,
    val elapsedMs: Long = 0,
    val peakAmplitude: Int = 0,
    val filePath: String = ""
)

object RecordingStateManager {
    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    fun update(transform: (RecordingUiState) -> RecordingUiState) {
        _state.value = transform(_state.value)
    }

    fun reset() {
        _state.value = RecordingUiState()
    }
}
