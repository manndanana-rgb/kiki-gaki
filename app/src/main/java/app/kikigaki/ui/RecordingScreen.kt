package app.kikigaki.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kikigaki.recording.RecordingService
import app.kikigaki.recording.RecordingViewModel

@Composable
fun RecordingScreen(vm: RecordingViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    DisposableEffect(Unit) {
        val intent = Intent(context, RecordingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose { context.unbindService(connection) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (state.isRecording && !state.isPaused) "録音中…" else if (state.isPaused) "一時停止中" else "準備完了",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = formatElapsed(state.elapsedMs),
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(24.dp))
            // 簡易レベルメーター
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(8.dp)
                    .background(Color.LightGray, CircleShape)
            ) {
                val frac = (state.peakAmplitude / 32768f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac)
                        .height(8.dp)
                        .background(if (state.isRecording && !state.isPaused) Color(0xFFE53935) else Color.Gray, CircleShape)
                )
            }
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (!state.isRecording) {
                    IconButton(
                        onClick = {
                            context.startService(Intent(context, RecordingService::class.java).apply {
                                action = "START"
                            })
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFFE53935), CircleShape)
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "録音開始", tint = Color.White)
                    }
                } else {
                    IconButton(
                        onClick = {
                            context.startService(Intent(context, RecordingService::class.java).apply {
                                action = if (state.isPaused) "RESUME" else "PAUSE"
                            })
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFFFFA000), CircleShape)
                    ) {
                        Icon(
                            if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = "一時停止/再開",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            context.startService(Intent(context, RecordingService::class.java).apply {
                                action = "STOP"
                            })
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFF43A047), CircleShape)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "停止", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
