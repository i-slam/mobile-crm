package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
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
import com.example.data.model.CallRecord
import com.example.data.repository.CallRepository
import com.example.overlay.sendWhatsAppMessage
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryGreen
import com.example.ui.theme.TertiaryAmber
import com.example.ui.theme.WhatsAppGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CallRepository.getInstance(context) }

    val allRecords by repository.allRecords.collectAsState(initial = emptyList())
    val settings by repository.settings.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var selectedRecordForDetail by remember { mutableStateOf<CallRecord?>(null) }
    var recordToDelete by remember { mutableStateOf<CallRecord?>(null) }
    var isSyncingAll by remember { mutableStateOf(false) }

    val filteredRecords = remember(allRecords, searchQuery, selectedTagFilter) {
        allRecords.filter { record ->
            val matchesQuery = searchQuery.isBlank() ||
                    record.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                    record.notes.contains(searchQuery, ignoreCase = true) ||
                    record.callerName.contains(searchQuery, ignoreCase = true)

            val matchesTag = selectedTagFilter == null ||
                    (selectedTagFilter == "PENDING_SYNC" && record.syncStatus == "PENDING") ||
                    record.tags.contains(selectedTagFilter)

            matchesQuery && matchesTag
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar & Sync All Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by number, notes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                } else null,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("history_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            IconButton(
                onClick = {
                    if (isSyncingAll) return@IconButton
                    isSyncingAll = true
                    scope.launch {
                        val count = repository.flushPendingQueue()
                        isSyncingAll = false
                        Toast.makeText(
                            context,
                            if (count > 0) "Synced $count records to WebSocket!" else "All records already synced",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .testTag("sync_all_button")
            ) {
                if (isSyncingAll) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync all pending",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedTagFilter == null,
                onClick = { selectedTagFilter = null },
                label = { Text("All (${allRecords.size})") }
            )

            val pendingCount = allRecords.count { it.syncStatus == "PENDING" }
            if (pendingCount > 0) {
                FilterChip(
                    selected = selectedTagFilter == "PENDING_SYNC",
                    onClick = {
                        selectedTagFilter = if (selectedTagFilter == "PENDING_SYNC") null else "PENDING_SYNC"
                    },
                    label = { Text("Pending Sync ($pendingCount)") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = TertiaryAmber,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }

            settings.customTags.forEach { tag ->
                val count = allRecords.count { it.tags.contains(tag) }
                if (count > 0) {
                    FilterChip(
                        selected = selectedTagFilter == tag,
                        onClick = {
                            selectedTagFilter = if (selectedTagFilter == tag) null else tag
                        },
                        label = { Text("$tag ($count)") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Call Records List
        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty() || selectedTagFilter != null)
                            "No calls match the current filters"
                        else
                            "No call notes recorded yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Use the live tester on Dashboard or complete phone calls to populate history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                repository.populateDemoData()
                                Toast.makeText(context, "Loaded sample call records!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Populate Sample CRM Records")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    CallRecordItemCard(
                        record = record,
                        onClick = { selectedRecordForDetail = record },
                        onQuickWhatsApp = {
                            val text = "Hello! 📍 Here is our showroom location: ${settings.showroomMapsUrl}\nAddress: ${settings.showroomAddress}\nShowroom: ${settings.showroomName}"
                            sendWhatsAppMessage(context, record.phoneNumber, text)
                        },
                        onRetrySync = {
                            scope.launch {
                                val success = repository.retrySyncRecord(record)
                                Toast.makeText(
                                    context,
                                    if (success) "Dispatched to WebSocket successfully!" else "WebSocket is offline. Saved in queue.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }

    // Detail Bottom Dialog
    selectedRecordForDetail?.let { record ->
        CallRecordDetailDialog(
            record = record,
            settings = settings,
            onDismiss = { selectedRecordForDetail = null },
            onDelete = {
                recordToDelete = record
                selectedRecordForDetail = null
            },
            onRetrySync = {
                scope.launch {
                    val success = repository.retrySyncRecord(record)
                    Toast.makeText(
                        context,
                        if (success) "Dispatched to WebSocket!" else "Offline. Queued for auto-dispatch.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // Delete Confirmation Dialog
    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Delete Call Note?") },
            text = { Text("Are you sure you want to delete note for ${record.phoneNumber}? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteRecord(record)
                            recordToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallRecordItemCard(
    record: CallRecord,
    onClick: () -> Unit,
    onQuickWhatsApp: () -> Unit,
    onRetrySync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(record.endTimeMillis) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(record.endTimeMillis))
    }

    val formattedDuration = remember(record.durationSeconds) {
        val min = record.durationSeconds / 60
        val sec = record.durationSeconds % 60
        if (min > 0) "${min}m ${sec}s" else "${sec}s"
    }

    val (callTypeIcon, callTypeColor) = when (record.callType.uppercase()) {
        "OUTGOING" -> Pair(Icons.Default.CallMade, PrimaryBlue)
        "MISSED" -> Pair(Icons.Default.CallMissed, Color(0xFFEF4444))
        else -> Pair(Icons.Default.CallReceived, SecondaryGreen)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("record_card_${record.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(callTypeColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = callTypeIcon,
                            contentDescription = record.callType,
                            tint = callTypeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = record.phoneNumber,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$formattedTime • $formattedDuration",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sync Status Pill
                if (record.syncStatus == "SYNCED") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SecondaryGreen.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = "Synced", tint = SecondaryGreen, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SYNCED", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = SecondaryGreen))
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.clickable { onRetrySync() },
                        shape = RoundedCornerShape(12.dp),
                        color = TertiaryAmber.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = "Pending", tint = TertiaryAmber, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PENDING", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TertiaryAmber))
                        }
                    }
                }
            }

            if (record.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    record.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (record.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = record.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3
                )
            }

            if (record.actionsTaken.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = WhatsAppGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "WhatsApp showroom link sent",
                        style = MaterialTheme.typography.labelSmall,
                        color = WhatsAppGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallRecordDetailDialog(
    record: CallRecord,
    settings: com.example.data.model.AppSettings,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onRetrySync: () -> Unit
) {
    val context = LocalContext.current

    val jsonPayload = remember(record) {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        """{
  "event": "call_note_captured",
  "record_id": ${record.id},
  "timestamp_iso": "${isoFormat.format(Date(record.endTimeMillis))}",
  "call_metadata": {
    "phone_number": "${record.phoneNumber}",
    "call_type": "${record.callType}",
    "duration_seconds": ${record.durationSeconds}
  },
  "user_input": {
    "notes": "${record.notes.replace("\n", "\\n")}",
    "tags": [${record.tags.joinToString(",") { "\"$it\"" }}],
    "actions_taken": [${record.actionsTaken.joinToString(",") { "\"$it\"" }}]
  },
  "showroom_info": {
    "showroom_name": "${settings.showroomName}",
    "maps_url": "${settings.showroomMapsUrl}"
  }
}"""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Call Note #${record.id}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Phone Number", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = record.phoneNumber, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Call Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${record.durationSeconds} seconds (${record.callType})", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (record.tags.isNotEmpty()) {
                    Text(text = "Tags", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        record.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (record.notes.isNotBlank()) {
                    Text(text = "Notes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = record.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // WebSocket Dispatched JSON Payload Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "WebSocket JSON Payload", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("WebSocket Payload", jsonPayload)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied JSON payload to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy JSON", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A)
                ) {
                    Text(
                        text = jsonPayload,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8)
                        ),
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Quick Messaging & Sharing Triggers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val text = "Hello! 📍 Here is our showroom location: ${settings.showroomMapsUrl}\nAddress: ${settings.showroomAddress}\nShowroom: ${settings.showroomName}"
                            com.example.util.ShareHelper.shareViaWhatsApp(context, record.phoneNumber, text)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val text = "Hello! 📍 Here is our showroom location: ${settings.showroomMapsUrl}\nAddress: ${settings.showroomAddress}\nShowroom: ${settings.showroomName}"
                            com.example.util.ShareHelper.shareViaSms(context, record.phoneNumber, text)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("SMS", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val text = "Showroom: ${settings.showroomName}\nAddress: ${settings.showroomAddress}\nLocation Map: ${settings.showroomMapsUrl}"
                            com.example.util.ShareHelper.shareViaSystemSheet(context, "Showroom Location", text)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetrySync) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Re-sync WS")
                }
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    )
}
