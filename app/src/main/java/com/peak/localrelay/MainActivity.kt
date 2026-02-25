package com.peak.localrelay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    var targetUrl by rememberSaveable { mutableStateOf(state.targetBaseUrl) }
    var localPortText by rememberSaveable { mutableStateOf(state.localPort.toString()) }
    var bindAll by rememberSaveable { mutableStateOf(state.bindAllInterfaces) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }

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

    LaunchedEffect(Unit) {
        checkingUpdate = true
        updateInfo = UpdateChecker.checkForUpdate(context, BuildConfig.UPDATE_INFO_URL)
        checkingUpdate = false
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
                text = "本地中继",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "独立本地路由应用。监听本机端口并将流量转发到你的远程 Happy 服务。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = targetUrl,
                onValueChange = {
                    targetUrl = it
                    RelayController.clearError()
                },
                label = { Text("目标基础 URL") },
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
                label = { Text("本地监听端口") },
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
                    Text("监听所有网卡 (0.0.0.0)")
                    Text(
                        text = "关闭时仅监听 localhost（更安全）。",
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
                            RelayController.setStatus(RelayStatus.ERROR, "端口必须在 1 到 65535 之间")
                            return@Button
                        }

                        if (targetUrl.isBlank()) {
                            RelayController.setStatus(RelayStatus.ERROR, "目标 URL 不能为空")
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
                    Text("启动")
                }

                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        RelayService.stop(context)
                    },
                ) {
                    Text("停止")
                }

                TextButton(
                    modifier = Modifier.weight(1f),
                    enabled = !checkingUpdate,
                    onClick = {
                        scope.launch {
                            checkingUpdate = true
                            val latest = UpdateChecker.checkForUpdate(context, BuildConfig.UPDATE_INFO_URL)
                            checkingUpdate = false
                            if (latest != null) {
                                updateInfo = latest
                            } else {
                                Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) {
                    Text(if (checkingUpdate) "检查中..." else "检查更新")
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
                    Text("日志", fontWeight = FontWeight.SemiBold)

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

        updateInfo?.let { info ->
            AlertDialog(
                onDismissRequest = {
                    if (!info.forceUpdate) {
                        updateInfo = null
                    }
                },
                title = {
                    Text("发现新版本：${info.latestVersionName}")
                },
                text = {
                    val notes = if (info.changelog.isBlank()) {
                        "检测到可用的新 APK。"
                    } else {
                        info.changelog
                    }
                    Text(
                        "当前版本：${BuildConfig.VERSION_NAME}\n最新版本：${info.latestVersionName}\n\n$notes",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            openUpdateLink(context, info.apkUrl)
                            if (!info.forceUpdate) {
                                updateInfo = null
                            }
                        },
                    ) {
                        Text("下载")
                    }
                },
                dismissButton = if (!info.forceUpdate) {
                    {
                        TextButton(
                            onClick = {
                                updateInfo = null
                            },
                        ) {
                            Text("稍后")
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun RelayStatusCard(state: RelayState) {
    val statusText = when (state.status) {
        RelayStatus.STOPPED -> "已停止"
        RelayStatus.STARTING -> "启动中"
        RelayStatus.RUNNING -> "运行中"
        RelayStatus.ERROR -> "异常"
    }

    val detail = when {
        state.lastError != null -> state.lastError
        state.status == RelayStatus.RUNNING -> "正在将本地流量转发到 ${state.targetBaseUrl}"
        else -> "就绪"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("状态：$statusText", fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun openUpdateLink(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
