package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WebSocketLogEvent
import com.example.data.repository.CallRepository
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryGreen
import com.example.ui.theme.TertiaryAmber
import com.example.websocket.CallWebSocketClient
import com.example.websocket.WsConnectionState
import kotlinx.coroutines.launch

@Composable
fun WebSocketScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CallRepository.getInstance(context) }
    val wsClient = remember { CallWebSocketClient.getInstance() }

    val settings by repository.settings.collectAsState()
    val wsState by wsClient.connectionState.collectAsState()
    val logEvents by wsClient.logEvents.collectAsState()
    val lastPingLatency by wsClient.lastPingLatency.collectAsState()
    val pendingCount by repository.pendingCount.collectAsState(initial = 0)

    var urlInput by remember(settings.wsServerUrl) { mutableStateOf(settings.wsServerUrl) }
    var tokenInput by remember(settings.wsAuthToken) { mutableStateOf(settings.wsAuthToken) }
    var autoReconnect by remember(settings.autoReconnect) { mutableStateOf(settings.autoReconnect) }

    var testCustomMessage by remember { mutableStateOf("""{"type":"test_event","device":"android"}""") }
    var isFlushing by remember { mutableStateOf(false) }

    val (statusColor, statusText) = when (wsState) {
        WsConnectionState.CONNECTED -> Pair(SecondaryGreen, "Connected & Streaming")
        WsConnectionState.CONNECTING -> Pair(TertiaryAmber, "Connecting...")
        WsConnectionState.RECONNECTING -> Pair(TertiaryAmber, "Reconnecting...")
        WsConnectionState.ERROR -> Pair(Color(0xFFEF4444), "Connection Error")
        WsConnectionState.DISCONNECTED -> Pair(Color.Gray, "Disconnected")
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status & Connection Header Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ws_connection_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = statusColor
                            )
                        }

                        if (lastPingLatency != null && wsState == WsConnectionState.CONNECTED) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SecondaryGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${lastPingLatency}ms ping",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SecondaryGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("WebSocket Server URL (ws:// or wss://)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ws_url_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset URL Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = urlInput == "wss://echo.websocket.events",
                            onClick = { urlInput = "wss://echo.websocket.events" },
                            label = { Text("Echo Server", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = urlInput == "ws://10.0.2.2:8080/ws",
                            onClick = { urlInput = "ws://10.0.2.2:8080/ws" },
                            label = { Text("Localhost (10.0.2.2)", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Authorization Bearer Token (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val newSettings = settings.copy(
                                    wsServerUrl = urlInput.trim(),
                                    wsAuthToken = tokenInput.trim(),
                                    autoReconnect = autoReconnect
                                )
                                repository.updateSettings(newSettings)
                                wsClient.connect(newSettings.wsServerUrl, newSettings.wsAuthToken)
                                Toast.makeText(context, "Connecting to WebSocket...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (wsState == WsConnectionState.CONNECTED) "Re-connect" else "Connect Now")
                        }

                        if (wsState == WsConnectionState.CONNECTED) {
                            OutlinedButton(
                                onClick = {
                                    wsClient.disconnect()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
            }
        }

        // Live Actions: Ping, Flush Queue, Test Send
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Real-Time Commands & Queue Management",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { wsClient.sendPing() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            enabled = wsState == WsConnectionState.CONNECTED
                        ) {
                            Icon(Icons.Default.Cable, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Send Ping")
                        }

                        Button(
                            onClick = {
                                if (isFlushing) return@Button
                                isFlushing = true
                                scope.launch {
                                    val flushed = repository.flushPendingQueue()
                                    isFlushing = false
                                    Toast.makeText(
                                        context,
                                        if (flushed > 0) "Dispatched $flushed queued records to server!" else "No pending records in queue",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pendingCount > 0) TertiaryAmber else MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            if (isFlushing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Flush Queue ($pendingCount)")
                            }
                        }
                    }
                }
            }
        }

        // Live Packet & Frame Inspector Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Packet & Frame Inspector (${logEvents.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                if (logEvents.isNotEmpty()) {
                    TextButton(onClick = { wsClient.clearLogs() }) {
                        Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Logs", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Logs Stream List
        if (logEvents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No WebSocket frames recorded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Connect to your server and trigger test calls or pings to observe live traffic",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(logEvents, key = { it.id }) { log ->
                WebSocketLogItemCard(log = log)
            }
        }
    }
}

@Composable
fun WebSocketLogItemCard(
    log: WebSocketLogEvent,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val (badgeColor, directionText, directionIcon) = when (log.direction) {
        WebSocketLogEvent.Direction.OUTBOUND -> Triple(PrimaryBlue, "OUTBOUND", Icons.Default.CallMade)
        WebSocketLogEvent.Direction.INBOUND -> Triple(SecondaryGreen, "INBOUND", Icons.Default.CallReceived)
        WebSocketLogEvent.Direction.SYSTEM -> Triple(if (log.isSuccess) Color(0xFF64748B) else Color(0xFFEF4444), "SYSTEM", Icons.Default.Info)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ws_log_item"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = directionIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = directionText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = badgeColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = log.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("WebSocket Log", log.content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied log content!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F172A)
            ) {
                Text(
                    text = log.content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (log.direction == WebSocketLogEvent.Direction.OUTBOUND) Color(0xFF38BDF8) else if (log.direction == WebSocketLogEvent.Direction.INBOUND) Color(0xFF4ADE80) else Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
