package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen("home", "Dashboard", Icons.Filled.Home, Icons.Outlined.Home)
    object History : Screen("history", "Call Logs", Icons.Filled.History, Icons.Outlined.History)
    object WebSocket : Screen("websocket", "WebSocket", Icons.Filled.Cable, Icons.Outlined.Cable)
    object Settings : Screen("settings", "Showroom", Icons.Filled.Settings, Icons.Outlined.Settings)
}
