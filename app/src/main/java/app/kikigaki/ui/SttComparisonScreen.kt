package app.kikigaki.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kikigaki.recording.RecordingViewModel
import app.kikigaki.stt.ModelDownloadManager
import app.kikigaki.stt.SttEngineType

@Composable
fun SttComparisonScreen(vm: RecordingViewModel = viewModel()) {
    val engineStates by vm.engineStates.collectAsState()
    val selectedEngine by vm.selectedEngine.collectAsState()
    val context = LocalContext.current

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "STT エンジン比較",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "各エンジンのライブ認識結果を比較できます",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Spacer(Modifier.height(16.dp))

            // エンジンカード一覧
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(SttEngineType.entries.toList()) { type ->
                    EngineCard(
                        type = type,
                        state = engineStates[type] ?: RecordingViewModel.EngineUiState(),
                        isSelected = type == selectedEngine,
                        onSelect = { vm.selectEngine(type) },
                        onDownload = { vm.downloadVoskModel() }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 操作ボタン
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { vm.startAllEngines() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("全エンジン開始")
                }
                Button(
                    onClick = { vm.stopAllEngines() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("全エンジン停止")
                }
            }
        }
    }
}

@Composable
private fun EngineCard(
    type: SttEngineType,
    state: RecordingViewModel.EngineUiState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                if (state.initialized) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "初期化済み",
                        tint = Color(0xFF4CAF50)
                    )
                } else if (state.errorMessage != null) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = "エラー",
                        tint = Color(0xFFE53935)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ダウンロード状態
            when (val dl = state.downloadProgress) {
                is ModelDownloadManager.DownloadState.Downloading -> {
                    Column {
                        Text("ダウンロード中... ${dl.progress}%")
                        LinearProgressIndicator(
                            progress = { dl.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is ModelDownloadManager.DownloadState.Failed -> {
                    Text("DL失敗: ${dl.message}", color = Color(0xFFE53935))
                    Button(onClick = onDownload) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("再試行")
                    }
                }
                is ModelDownloadManager.DownloadState.Completed -> {
                    Text("モデル準備完了", color = Color(0xFF4CAF50))
                }
                is ModelDownloadManager.DownloadState.Idle -> {
                    if (type == SttEngineType.VOSK && !state.initialized) {
                        Button(onClick = onDownload) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("モデルをダウンロード (~47MB)")
                        }
                    }
                }
            }

            // 認識結果
            if (state.partialText.isNotBlank() || state.finalText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Column {
                        if (state.finalText.isNotBlank()) {
                            Text(
                                text = state.finalText.trim(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (state.partialText.isNotBlank()) {
                            Text(
                                text = state.partialText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // レイテンシ
            if (state.latencyMs > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "レイテンシ: ${state.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            // エラーメッセージ
            if (state.errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE53935)
                )
            }
        }
    }
}
