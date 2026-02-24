package com.peak.localrelay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val loaded = RelayPrefs.load(this)
        RelayController.setConfig(loaded)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RelayScreen()
                }
            }
        }
    }
}

@Composable
private fun RelayScreen() {
    val context = LocalContext.current
    val state by RelayController.state.collectAsState()

    var targetUrl by rememberSaveable { mutableStateOf(state.targetBaseUrl) }
    var localPortText by rememberSaveable { mutableStateOf(state.localPort.toString()) }
    var bindAll by rememberSaveable { mutableStateOf(state.bindAllInterfaces) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    LaunchedEffect(state.targetBaseUrl, state.localPort, state.bindAllInterfaces) {
        if (state.status == RelayStatus.STOPPED) {
            targetUrl = state.targetBaseUrl
            localPortText = state.localPort.toString()
            bindAll = state.bindAllInterfaces
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Local Relay",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Independent local routing app. It listens on localhost and forwards traffic to your remote Happy server.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = targetUrl,
                onValueChange = {
                    targetUrl = it
                    RelayController.clearError()
                },
                label = { Text("Target base URL") },
                placeholder = { Text("http://118.196.100.121:3005") },
                singleLine = true,
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = localPortText,
                onValueChange = {
                    localPortText = it.filter { ch -> ch.isDigit() }
                    RelayController.clearError()
                },
                label = { Text("Local listen port") },
                placeholder = { Text("3005") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = bindAll,
                    onCheckedChange = { bindAll = it },
                )
                Column {
                    Text("Bind all interfaces (0.0.0.0)")
                    Text(
                        text = "Off = localhost only (safer).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            RelayStatusCard(state = state)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val parsedPort = localPortText.toIntOrNull()
                        if (parsedPort == null || parsedPort !in 1..65535) {
                            RelayController.setStatus(RelayStatus.ERROR, "Port must be between 1 and 65535")
                            return@Button
                        }

                        if (targetUrl.isBlank()) {
                            RelayController.setStatus(RelayStatus.ERROR, "Target URL cannot be empty")
                            return@Button
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }

                        val config = RelayConfig(
                            targetBaseUrl = targetUrl.trim(),
                            localPort = parsedPort,
                            bindAllInterfaces = bindAll,
                        )
                        RelayService.start(context, config)
                    },
                ) {
                    Text("Start")
                }

                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        RelayService.stop(context)
                    },
                ) {
                    Text("Stop")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 360.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Logs", fontWeight = FontWeight.SemiBold)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.logs) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayStatusCard(state: RelayState) {
    val statusText = when (state.status) {
        RelayStatus.STOPPED -> "Stopped"
        RelayStatus.STARTING -> "Starting"
        RelayStatus.RUNNING -> "Running"
        RelayStatus.ERROR -> "Error"
    }

    val detail = when {
        state.lastError != null -> state.lastError
        state.status == RelayStatus.RUNNING -> "Forwarding local traffic to ${state.targetBaseUrl}"
        else -> "Ready"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Status: $statusText", fontWeight = FontWeight.SemiBold)
            Text(detail ?: "", style = MaterialTheme.typography.bodySmall)
        }
    }
}
