package com.example.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallRecord
import com.example.data.model.WhatsAppTemplate
import com.example.data.repository.CallRepository
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryGreen
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.TertiaryAmber
import com.example.ui.theme.WhatsAppGreen
import com.example.websocket.CallWebSocketClient
import com.example.websocket.WsConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallOverlayDialogContent(
    phoneNumber: String,
    durationSeconds: Int,
    callType: String,
    startTime: Long,
    endTime: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CallRepository.getInstance(context) }
    val wsClient = remember { CallWebSocketClient.getInstance() }

    val settings by repository.settings.collectAsState()
    val templates by repository.templates.collectAsState()
    val wsState by wsClient.connectionState.collectAsState()

    var editablePhoneNumber by remember(phoneNumber) { mutableStateOf(phoneNumber) }
    var resolvedCallerName by remember(phoneNumber) {
        mutableStateOf(com.example.util.CallLogHelper.lookupContactName(context, phoneNumber))
    }

    var notesText by remember { mutableStateOf("") }
    val selectedTags = remember { mutableStateListOf<String>() }
    val actionsTaken = remember { mutableStateListOf<String>() }

    var isSaving by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }
    var showCustomTagDialog by remember { mutableStateOf(false) }
    var showAllTemplatesDialog by remember { mutableStateOf(false) }
    var showEditNumberDialog by remember { mutableStateOf(false) }
    var customTagInput by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    val formattedDuration = remember(durationSeconds) {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    val callTypeIcon = when (callType.uppercase()) {
        "OUTGOING" -> Icons.Default.CallMade
        "MISSED" -> Icons.Default.CallMissed
        else -> Icons.Default.CallReceived
    }

    val callTypeColor = when (callType.uppercase()) {
        "OUTGOING" -> PrimaryBlue
        "MISSED" -> Color(0xFFEF4444)
        else -> SecondaryGreen
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .heightIn(max = 680.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(
                1.5.dp,
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                ),
                RoundedCornerShape(24.dp)
            )
            .testTag("call_overlay_dialog"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(scrollState)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(callTypeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = callTypeIcon,
                            contentDescription = callType,
                            tint = callTypeColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        if (!resolvedCallerName.isNullOrBlank()) {
                            Text(
                                text = resolvedCallerName!!,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = editablePhoneNumber,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = editablePhoneNumber,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = callType.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = callTypeColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = " • $formattedDuration",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$editablePhoneNumber")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open dialer", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call back",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(14.dp))

            // Section 1: Call Tags
            Text(
                text = "Call Tags",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                settings.customTags.forEach { tag ->
                    val isSelected = selectedTags.contains(tag)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) selectedTags.remove(tag) else selectedTags.add(tag)
                        },
                        label = {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                // Add Custom Tag Chip
                FilterChip(
                    selected = false,
                    onClick = { showCustomTagDialog = true },
                    label = { Text("+ Custom Tag", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add custom tag",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 2: Notes & Quick Snippets
            Text(
                text = "Notes & Customer Requests",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                placeholder = {
                    Text(
                        "Type notes, follow-up items, customer budget, showroom preference...",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp)
                    .testTag("note_input_field"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Note Snippets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "📍 Sent Location",
                    "🗓️ Visiting Tomorrow",
                    "💰 Budget Discussed",
                    "📋 Quote Needed",
                    "📞 Call Back Later"
                ).forEach { snippet ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                notesText = if (notesText.isBlank()) snippet else "$notesText • $snippet"
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = snippet,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: WhatsApp Quick Actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = WhatsAppGreen.copy(alpha = 0.08f)
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        listOf(WhatsAppGreen.copy(alpha = 0.4f), WhatsAppGreen.copy(alpha = 0.1f))
                    )
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Message,
                                    contentDescription = "WhatsApp",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "WhatsApp Quick Share",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F5132)
                                )
                            )
                        }

                        TextButton(onClick = { showAllTemplatesDialog = true }) {
                            Text("All Templates", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Primary Showroom Location WhatsApp & SMS Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val template = templates.find { it.iconType == "LOCATION" } ?: templates.firstOrNull()
                                val textToSend = template?.formatMessage(
                                    phoneNumber = phoneNumber,
                                    showroomName = settings.showroomName,
                                    showroomAddress = settings.showroomAddress,
                                    showroomMapsUrl = settings.showroomMapsUrl
                                ) ?: "Hello! Here is our showroom location: ${settings.showroomMapsUrl}\nAddress: ${settings.showroomAddress}"

                                com.example.util.ShareHelper.shareViaWhatsApp(context, phoneNumber, textToSend)
                                if (!actionsTaken.contains("SENT_SHOWROOM_LOCATION_WHATSAPP")) {
                                    actionsTaken.add("SENT_SHOWROOM_LOCATION_WHATSAPP")
                                }
                                if (!selectedTags.contains("📍 Showroom Visit")) {
                                    selectedTags.add("📍 Showroom Visit")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_whatsapp_location_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhatsAppGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = "Location Pin",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WhatsApp Location",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // SMS Direct Button
                        OutlinedButton(
                            onClick = {
                                val template = templates.find { it.iconType == "LOCATION" } ?: templates.firstOrNull()
                                val textToSend = template?.formatMessage(
                                    phoneNumber = phoneNumber,
                                    showroomName = settings.showroomName,
                                    showroomAddress = settings.showroomAddress,
                                    showroomMapsUrl = settings.showroomMapsUrl
                                ) ?: "Hello! Here is our showroom location: ${settings.showroomMapsUrl}\nAddress: ${settings.showroomAddress}"

                                com.example.util.ShareHelper.shareViaSms(context, phoneNumber, textToSend)
                                if (!actionsTaken.contains("SENT_LOCATION_SMS")) {
                                    actionsTaken.add("SENT_LOCATION_SMS")
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "SMS", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SMS", fontSize = 12.sp)
                        }

                        // Copy Link Button
                        IconButton(
                            onClick = {
                                val textToCopy = "Showroom: ${settings.showroomName}\nAddress: ${settings.showroomAddress}\nLocation Map: ${settings.showroomMapsUrl}"
                                com.example.util.ShareHelper.copyToClipboard(context, textToCopy, "Showroom Location")
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Copy text", tint = WhatsAppGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary Quick Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val template = templates.find { it.iconType == "CATALOG" }
                                val text = template?.formatMessage(phoneNumber, settings.showroomName, settings.showroomAddress, settings.showroomMapsUrl)
                                    ?: "Hello! Here is our product catalog: https://example.com/catalog"
                                sendWhatsAppMessage(context, phoneNumber, text)
                                if (!actionsTaken.contains("SENT_CATALOG_WHATSAPP")) {
                                    actionsTaken.add("SENT_CATALOG_WHATSAPP")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Catalog", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val template = templates.find { it.iconType == "APPOINTMENT" }
                                val text = template?.formatMessage(phoneNumber, settings.showroomName, settings.showroomAddress, settings.showroomMapsUrl)
                                    ?: "Hi! Let's schedule your visit to ${settings.showroomName}."
                                sendWhatsAppMessage(context, phoneNumber, text)
                                if (!actionsTaken.contains("SENT_APPOINTMENT_WHATSAPP")) {
                                    actionsTaken.add("SENT_APPOINTMENT_WHATSAPP")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Book Visit", fontSize = 12.sp)
                        }
                    }

                    if (actionsTaken.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Sent",
                                tint = SecondaryGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "WhatsApp launched (${actionsTaken.size} action)",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // WebSocket Status Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (statusColor, statusText) = when (wsState) {
                        WsConnectionState.CONNECTED -> Pair(SecondaryGreen, "WebSocket Connected")
                        WsConnectionState.CONNECTING -> Pair(TertiaryAmber, "Connecting to Backend...")
                        WsConnectionState.RECONNECTING -> Pair(TertiaryAmber, "Reconnecting WebSocket...")
                        WsConnectionState.ERROR -> Pair(Color(0xFFEF4444), "WebSocket Offline (Queueing)")
                        WsConnectionState.DISCONNECTED -> Pair(Color.Gray, "WebSocket Disconnected")
                    }

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = settings.wsServerUrl.substringAfter("://").take(18) + "...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Save & Sync Primary Button
            Button(
                onClick = {
                    if (isSaving || saveSuccess) return@Button
                    isSaving = true

                    scope.launch {
                        val record = CallRecord(
                            phoneNumber = editablePhoneNumber,
                            callerName = resolvedCallerName ?: "Caller ($editablePhoneNumber)",
                            callType = callType,
                            startTimeMillis = startTime,
                            endTimeMillis = endTime,
                            durationSeconds = durationSeconds,
                            notes = notesText.trim(),
                            tags = selectedTags.toList(),
                            actionsTaken = actionsTaken.toList(),
                            syncStatus = if (wsState == WsConnectionState.CONNECTED) "SYNCED" else "PENDING"
                        )

                        repository.saveCallRecordAndSync(record)
                        isSaving = false
                        saveSuccess = true
                        Toast.makeText(context, "Note saved and queued for WebSocket sync!", Toast.LENGTH_SHORT).show()

                        delay(1200)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_and_sync_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (saveSuccess) SecondaryGreen else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Syncing with Backend...")
                } else if (saveSuccess) {
                    Icon(Icons.Default.CloudDone, contentDescription = "Synced", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saved & Dispatched!", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = "Save and sync", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Dispatch WebSocket", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    // Custom Tag Dialog
    if (showCustomTagDialog) {
        AlertDialog(
            onDismissRequest = { showCustomTagDialog = false },
            title = { Text("Add Custom Tag") },
            text = {
                OutlinedTextField(
                    value = customTagInput,
                    onValueChange = { customTagInput = it },
                    placeholder = { Text("e.g. ⭐ VIP Wholesale") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customTagInput.isNotBlank()) {
                            val newTag = customTagInput.trim()
                            repository.addCustomTag(newTag)
                            if (!selectedTags.contains(newTag)) {
                                selectedTags.add(newTag)
                            }
                            customTagInput = ""
                            showCustomTagDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTagDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // All Templates Dialog
    if (showAllTemplatesDialog) {
        AlertDialog(
            onDismissRequest = { showAllTemplatesDialog = false },
            title = { Text("Select WhatsApp Template") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.forEach { template ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val text = template.formatMessage(
                                        phoneNumber = phoneNumber,
                                        showroomName = settings.showroomName,
                                        showroomAddress = settings.showroomAddress,
                                        showroomMapsUrl = settings.showroomMapsUrl
                                    )
                                    sendWhatsAppMessage(context, phoneNumber, text)
                                    actionsTaken.add("SENT_${template.id.uppercase()}")
                                    showAllTemplatesDialog = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = template.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = template.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllTemplatesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

fun sendWhatsAppMessage(context: Context, phoneNumber: String, messageText: String) {
    com.example.util.ShareHelper.shareViaWhatsApp(context, phoneNumber, messageText)
}
