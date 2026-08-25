package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.repository.CallRepository
import com.example.overlay.CallOverlayActivity
import com.example.overlay.sendWhatsAppMessage
import com.example.service.CallMonitorService
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryGreen
import com.example.ui.theme.TertiaryAmber
import com.example.ui.theme.WhatsAppGreen
import com.example.util.PermissionUtils
import com.example.websocket.CallWebSocketClient
import com.example.websocket.WsConnectionState
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToWebSocket: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowInAppOverlay: (String, Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember { CallRepository.getInstance(context) }
    val wsClient = remember { CallWebSocketClient.getInstance() }

    val settings by repository.settings.collectAsState()
    val wsState by wsClient.connectionState.collectAsState()
    val pendingCount by repository.pendingCount.collectAsState(initial = 0)
    val totalCount by repository.recordCount.collectAsState(initial = 0)

    var isServiceActive by remember { mutableStateOf(CallMonitorService.isRunning) }

    // Dynamic Permission tracking
    var hasRuntimePerms by remember { mutableStateOf(PermissionUtils.areRuntimePermissionsGranted(context)) }
    var hasOverlayPerm by remember { mutableStateOf(PermissionUtils.isOverlayPermissionGranted(context)) }

    // Simulation Test State
    var testNumber by remember { mutableStateOf("+1 (555) 489-3201") }
    var testDuration by remember { mutableIntStateOf(58) }
    var testCallType by remember { mutableStateOf("INCOMING") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasRuntimePerms = PermissionUtils.areRuntimePermissionsGranted(context)
        if (hasRuntimePerms && !CallMonitorService.isRunning) {
            CallMonitorService.start(context)
            isServiceActive = true
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPerm = PermissionUtils.isOverlayPermissionGranted(context)
    }

    // Refresh permission statuses on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasRuntimePerms = PermissionUtils.areRuntimePermissionsGranted(context)
                hasOverlayPerm = PermissionUtils.isOverlayPermissionGranted(context)
                isServiceActive = CallMonitorService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card: Live Simulation Trigger
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .testTag("simulator_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddComment,
                                    contentDescription = "Test Call",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Test Post-Call Popup",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Preview note dialog & WhatsApp sharing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "LIVE TEST",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = testNumber,
                        onValueChange = { testNumber = it },
                        label = { Text("Simulated Phone Number") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sim_phone_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Call Type Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("INCOMING", "OUTGOING", "MISSED").forEach { type ->
                            FilterChip(
                                selected = testCallType == type,
                                onClick = { testCallType = type },
                                label = { Text(type, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Action: Open Interactive CRM Popup
                    Button(
                        onClick = {
                            onShowInAppOverlay(testNumber, testDuration, testCallType)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("launch_overlay_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = "Popup", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Interactive CRM Popup", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary Action: Test background Activity launch
                    OutlinedButton(
                        onClick = {
                            CallOverlayActivity.launch(
                                context = context,
                                phoneNumber = testNumber,
                                durationSeconds = testDuration,
                                callType = testCallType,
                                startTime = System.currentTimeMillis() - (testDuration * 1000L),
                                endTime = System.currentTimeMillis()
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Floating Window Activity", fontSize = 13.sp)
                    }
                }
            }
        }

        // WebSocket Stream Status Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToWebSocket() }
                    .testTag("ws_status_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (statusColor, statusIcon) = when (wsState) {
                            WsConnectionState.CONNECTED -> Pair(SecondaryGreen, Icons.Default.CloudDone)
                            WsConnectionState.CONNECTING, WsConnectionState.RECONNECTING -> Pair(TertiaryAmber, Icons.Default.Refresh)
                            WsConnectionState.ERROR, WsConnectionState.DISCONNECTED -> Pair(Color(0xFFEF4444), Icons.Default.CloudOff)
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = "WebSocket",
                                tint = statusColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "WebSocket Server",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                            }
                            Text(
                                text = when (wsState) {
                                    WsConnectionState.CONNECTED -> "Connected & Ready"
                                    WsConnectionState.CONNECTING -> "Connecting to server..."
                                    WsConnectionState.RECONNECTING -> "Reconnecting..."
                                    WsConnectionState.ERROR -> "Offline (Queueing records)"
                                    WsConnectionState.DISCONNECTED -> "Disconnected"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = settings.wsServerUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Inspect",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Showroom Quick Info Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("showroom_info_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = WhatsAppGreen.copy(alpha = 0.07f)
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        listOf(WhatsAppGreen.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Showroom",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = settings.showroomName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Configured Showroom Location",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(onClick = { onNavigateToSettings() }) {
                            Text("Edit", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = settings.showroomAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(settings.showroomMapsUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open maps URL", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Maps Link", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val message = "Hello! 📍 Here is our official showroom location: ${settings.showroomMapsUrl}\nAddress: ${settings.showroomAddress}\nWe look forward to seeing you at ${settings.showroomName}!"
                                com.example.util.ShareHelper.shareViaWhatsApp(context, testNumber, message)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhatsAppGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WhatsApp", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val message = "Showroom: ${settings.showroomName}\nAddress: ${settings.showroomAddress}\nLocation Map: ${settings.showroomMapsUrl}"
                                com.example.util.ShareHelper.shareViaSms(context, testNumber, message)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("SMS", fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = {
                                val message = "Showroom: ${settings.showroomName}\nAddress: ${settings.showroomAddress}\nLocation Map: ${settings.showroomMapsUrl}"
                                com.example.util.ShareHelper.copyToClipboard(context, message, "Showroom Location")
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy text", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Permissions & Background Service Setup Card
        item {
            val allGranted = hasRuntimePerms && hasOverlayPerm
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (allGranted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = if (!allGranted) CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(TertiaryAmber, Color.Transparent))
                ) else null
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Phone Call Detection & Permissions",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (allGranted) "All permissions granted. Ready for real phone calls." else "Permissions required to detect calls and display overlay.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (allGranted) SecondaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!allGranted) {
                            Button(
                                onClick = {
                                    val missing = PermissionUtils.getMissingRuntimePermissions(context)
                                    if (missing.isNotEmpty()) {
                                        permissionLauncher.launch(missing.toTypedArray())
                                    } else if (!hasOverlayPerm) {
                                        PermissionUtils.requestOverlayPermission(context, overlayPermissionLauncher)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Grant All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Permission 1: Overlay Permission
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasOverlayPerm) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasOverlayPerm) SecondaryGreen else TertiaryAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Display Over Other Apps",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Allows CRM popup to appear after call ends",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!hasOverlayPerm) {
                            FilledTonalButton(
                                onClick = {
                                    PermissionUtils.requestOverlayPermission(context, overlayPermissionLauncher)
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Allow", fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )

                    // Permission 2: Read Phone State & Call Log
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasRuntimePerms) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasRuntimePerms) SecondaryGreen else TertiaryAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Phone Call & Contacts Access",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Captures caller number, duration, and name",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!hasRuntimePerms) {
                            FilledTonalButton(
                                onClick = {
                                    permissionLauncher.launch(PermissionUtils.getRequiredRuntimePermissions())
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Allow", fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )

                    // Foreground Service Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Background Call Monitor Service",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isServiceActive) "Actively listening for completed calls" else "Tap toggle to activate background listener",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = isServiceActive,
                            onCheckedChange = { enable ->
                                isServiceActive = enable
                                if (enable) {
                                    CallMonitorService.start(context)
                                    Toast.makeText(context, "Call monitor service started", Toast.LENGTH_SHORT).show()
                                } else {
                                    CallMonitorService.stop(context)
                                    Toast.makeText(context, "Call monitor service stopped", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    if (!allGranted) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = { PermissionUtils.openAppSettings(context) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Open App System Settings", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Summary Stats Quick Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToHistory() },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "Total Call Notes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "$totalCount", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToWebSocket() },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "Pending WS Sync", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$pendingCount",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (pendingCount > 0) TertiaryAmber else SecondaryGreen
                            )
                        )
                    }
                }
            }
        }
    }
}
