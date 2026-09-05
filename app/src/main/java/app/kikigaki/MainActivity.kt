package app.kikigaki

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.kikigaki.ui.RecordingScreen
import app.kikigaki.ui.theme.KikiGakiTheme

class MainActivity : ComponentActivity() {

    private var granted by mutableStateOf(false)
    private var showRationale by mutableStateOf(false)

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        granted = results.values.all { it }
        showRationale = !granted
    }

    private fun requiredPermissions(): List<String> {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list
    }

    private fun allGranted(): Boolean {
        val perms = requiredPermissions()
        return perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (allGranted()) {
            granted = true
        } else {
            permLauncher.launch(requiredPermissions().toTypedArray())
        }

        setContent {
            KikiGakiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        granted -> RecordingScreen()
                        showRationale -> PermissionRationale { permLauncher.launch(requiredPermissions().toTypedArray()) }
                        else -> Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("マイクの許可が必要です", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "ききがきは録音するためにマイク権限が必要です。通知権限は録音中表示のために使います。",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("権限を許可する") }
    }
}
