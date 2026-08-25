package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.repository.CallRepository
import com.example.overlay.CallOverlayActivity
import com.example.overlay.CallOverlayDialogContent
import com.example.service.CallMonitorService
import com.example.ui.navigation.Screen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WebSocketScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SecondaryGreen
import com.example.ui.theme.TertiaryAmber
import com.example.ui.theme.WhatsAppGreen
import com.example.util.PermissionUtils
import com.example.websocket.CallWebSocketClient
import com.example.websocket.WsConnectionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContainer()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val screens = listOf(Screen.Home, Screen.History, Screen.WebSocket, Screen.Settings)

    val wsClient = remember { CallWebSocketClient.getInstance() }
    val wsState by wsClient.connectionState.collectAsState()

    var showInAppDialogOverlay by remember { mutableStateOf(false) }
    var inAppOverlayNumber by remember { mutableStateOf("+1 (555) 489-3201") }
    var inAppOverlayDuration by remember { mutableStateOf(45) }
    var inAppOverlayType by remember { mutableStateOf("INCOMING") }

    // Permission tracking state with lifecycle auto-refresh
    var refreshCounter by remember { mutableIntStateOf(0) }
    var hasRuntimePerms by remember { mutableStateOf(PermissionUtils.areRuntimePermissionsGranted(context)) }
    var hasOverlayPerm by remember { mutableStateOf(PermissionUtils.isOverlayPermissionGranted(context)) }

    val runtimePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasRuntimePerms = PermissionUtils.areRuntimePermissionsGranted(context)
        if (hasRuntimePerms) {
            CallMonitorService.start(context)
        }
    }

    val overlayPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPerm = PermissionUtils.isOverlayPermissionGranted(context)
    }

    // Refresh permission statuses whenever the activity comes to the foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasRuntimePerms = PermissionUtils.areRuntimePermissionsGranted(context)
                hasOverlayPerm = PermissionUtils.isOverlayPermissionGranted(context)
                refreshCounter++
                if (hasRuntimePerms && !CallMonitorService.isRunning) {
                    val settings = CallRepository.getInstance(context).settings.value
                    if (settings.overlayEnabled) {
                        CallMonitorService.start(context)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Automatically fire runtime permission request on startup if any are missing
    LaunchedEffect(Unit) {
        val missing = PermissionUtils.getMissingRuntimePermissions(context)
        if (missing.isNotEmpty()) {
            runtimePermLauncher.launch(missing.toTypedArray())
        } else {
            CallMonitorService.start(context)
        }
    }

    val (wsPillColor, wsPillText) = when (wsState) {
        WsConnectionState.CONNECTED -> Pair(SecondaryGreen, "WS Live")
        WsConnectionState.CONNECTING, WsConnectionState.RECONNECTING -> Pair(TertiaryAmber, "WS Connecting")
        WsConnectionState.ERROR, WsConnectionState.DISCONNECTED -> Pair(Color(0xFFEF4444), "WS Offline")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "Call Notes & Share",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Post-Call Overlay & WS Sync",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        // Real-time WebSocket connection status badge in top app bar
                        Surface(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { currentScreen = Screen.WebSocket },
                            color = wsPillColor.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(wsPillColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = wsPillText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = wsPillColor
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    screens.forEach { screen ->
                        val isSelected = currentScreen == screen
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("nav_item_${screen.route}")
                        )
                    }
                }
            },
            floatingActionButton = {
                if (currentScreen == Screen.Home || currentScreen == Screen.History) {
                    FloatingActionButton(
                        onClick = {
                            showInAppDialogOverlay = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("fab_test_popup")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AddComment, contentDescription = "Test Popup")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Popup", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Global Permission Alert Header if any permission is missing
                if (!hasRuntimePerms || !hasOverlayPerm) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        color = TertiaryAmber.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TertiaryAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Permission Alert",
                                    tint = TertiaryAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (!hasRuntimePerms) "Call & Contact permissions required" else "Overlay permission required",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Tap to grant and enable auto-popups",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val missing = PermissionUtils.getMissingRuntimePermissions(context)
                                    if (missing.isNotEmpty()) {
                                        runtimePermLauncher.launch(missing.toTypedArray())
                                    } else if (!hasOverlayPerm) {
                                        PermissionUtils.requestOverlayPermission(context, overlayPermLauncher)
                                    }
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TertiaryAmber, contentColor = Color.Black)
                            ) {
                                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentScreen) {
                        Screen.Home -> HomeScreen(
                            onNavigateToHistory = { currentScreen = Screen.History },
                            onNavigateToWebSocket = { currentScreen = Screen.WebSocket },
                            onNavigateToSettings = { currentScreen = Screen.Settings },
                            onShowInAppOverlay = { num, dur, type ->
                                inAppOverlayNumber = num
                                inAppOverlayDuration = dur
                                inAppOverlayType = type
                                showInAppDialogOverlay = true
                            }
                        )

                        Screen.History -> HistoryScreen()

                        Screen.WebSocket -> WebSocketScreen()

                        Screen.Settings -> SettingsScreen()
                    }
                }
            }
        }

        // In-App Modal Dialog Overlay (Immediate testing without leaving app)
        AnimatedVisibility(
            visible = showInAppDialogOverlay,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CallOverlayDialogContent(
                    phoneNumber = inAppOverlayNumber,
                    durationSeconds = inAppOverlayDuration,
                    callType = inAppOverlayType,
                    startTime = System.currentTimeMillis() - (inAppOverlayDuration * 1000L),
                    endTime = System.currentTimeMillis(),
                    onDismiss = {
                        showInAppDialogOverlay = false
                    }
                )
            }
        }
    }
}
