package app.kikigaki.recording

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class RecordingViewModel(app: Application) : AndroidViewModel(app) {
    val uiState: StateFlow<RecordingUiState> = RecordingStateManager.state
}
