package com.example.websocket

import android.util.Log
import com.example.data.model.AppSettings
import com.example.data.model.CallMetadataPayload
import com.example.data.model.CallRecord
import com.example.data.model.DeviceInfoPayload
import com.example.data.model.ShowroomInfoPayload
import com.example.data.model.UserInputPayload
import com.example.data.model.WebSocketLogEvent
import com.example.data.model.WebSocketOutboundPacket
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class WsConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

class CallWebSocketClient private constructor() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val packetAdapter = moshi.adapter(WebSocketOutboundPacket::class.java)

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var activeWebSocket: WebSocket? = null
    private var currentUrl: String = ""
    private var currentToken: String = ""
    private var reconnectJob: Job? = null
    private var isUserDisconnected = false

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private val _logEvents = MutableStateFlow<List<WebSocketLogEvent>>(emptyList())
    val logEvents: StateFlow<List<WebSocketLogEvent>> = _logEvents.asStateFlow()

    private val _lastPingLatency = MutableStateFlow<Long?>(null)
    val lastPingLatency: StateFlow<Long?> = _lastPingLatency.asStateFlow()

    private var pingStartTime: Long = 0L

    companion object {
        private const val TAG = "CallWebSocketClient"
        private const val MAX_LOGS = 60

        @Volatile
        private var INSTANCE: CallWebSocketClient? = null

        fun getInstance(): CallWebSocketClient {
            return INSTANCE ?: synchronized(this) {
                val instance = CallWebSocketClient()
                INSTANCE = instance
                instance
            }
        }
    }

    fun connect(serverUrl: String, authToken: String = "") {
        if (serverUrl.isBlank()) return
        currentUrl = serverUrl.trim()
        currentToken = authToken.trim()
        isUserDisconnected = false

        reconnectJob?.cancel()
        activeWebSocket?.close(1000, "Reconnecting")
        activeWebSocket = null

        _connectionState.value = WsConnectionState.CONNECTING
        addLog(WebSocketLogEvent.Direction.SYSTEM, "Connecting to WebSocket", "Target URL: $currentUrl")

        try {
            val requestBuilder = Request.Builder().url(currentUrl)
            if (currentToken.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $currentToken")
            }
            requestBuilder.addHeader("X-Client-App", "CallNotesQuickShare-Android")

            val request = requestBuilder.build()
            activeWebSocket = okHttpClient.newWebSocket(request, createListener())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate WebSocket connection", e)
            _connectionState.value = WsConnectionState.ERROR
            addLog(WebSocketLogEvent.Direction.SYSTEM, "Connection Initiation Error", e.message ?: "Unknown error", false)
            scheduleReconnect()
        }
    }

    fun disconnect() {
        isUserDisconnected = true
        reconnectJob?.cancel()
        activeWebSocket?.close(1000, "User disconnected")
        activeWebSocket = null
        _connectionState.value = WsConnectionState.DISCONNECTED
        addLog(WebSocketLogEvent.Direction.SYSTEM, "Disconnected", "WebSocket disconnected by user")
    }

    fun sendCallPayload(record: CallRecord, settings: AppSettings): Boolean {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val startTimeIso = isoFormat.format(Date(record.startTimeMillis))
        val endTimeIso = isoFormat.format(Date(record.endTimeMillis))

        val packet = WebSocketOutboundPacket(
            event = "call_note_captured",
            message_id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            timestamp_iso = isoFormat.format(Date()),
            record_id = record.id,
            call_metadata = CallMetadataPayload(
                phone_number = record.phoneNumber,
                caller_name = record.callerName,
                call_type = record.callType,
                duration_seconds = record.durationSeconds,
                start_time_iso = startTimeIso,
                end_time_iso = endTimeIso
            ),
            user_input = UserInputPayload(
                notes = record.notes,
                tags = record.tags,
                actions_taken = record.actionsTaken,
                vehicle_ids = record.selectedVehicleIds
            ),
            showroom_info = ShowroomInfoPayload(
                showroom_name = settings.showroomName,
                showroom_address = settings.showroomAddress,
                maps_url = settings.showroomMapsUrl
            ),
            device_info = DeviceInfoPayload()
        )

        val jsonString = try {
            packetAdapter.indent("  ").toJson(packet)
        } catch (e: Exception) {
            Log.e(TAG, "JSON serialization error", e)
            return false
        }

        return sendRawMessage(jsonString, "Call Metadata & Notes (#${record.id})")
    }

    fun sendRawMessage(text: String, title: String = "Outbound Message"): Boolean {
        val ws = activeWebSocket
        if (ws != null && _connectionState.value == WsConnectionState.CONNECTED) {
            val sent = ws.send(text)
            if (sent) {
                addLog(WebSocketLogEvent.Direction.OUTBOUND, title, text, true)
                return true
            } else {
                addLog(WebSocketLogEvent.Direction.OUTBOUND, "$title (Failed to send)", text, false)
                return false
            }
        } else {
            addLog(
                WebSocketLogEvent.Direction.SYSTEM,
                "Send Failed (Not Connected)",
                "Payload queued in offline records:\n$text",
                false
            )
            // Try reconnecting if not connected
            if (!isUserDisconnected && currentUrl.isNotBlank()) {
                connect(currentUrl, currentToken)
            }
            return false
        }
    }

    fun sendPing() {
        val ws = activeWebSocket
        if (ws != null && _connectionState.value == WsConnectionState.CONNECTED) {
            pingStartTime = System.currentTimeMillis()
            val pingPayload = """{"type":"ping","timestamp":$pingStartTime}"""
            ws.send(pingPayload)
            addLog(WebSocketLogEvent.Direction.OUTBOUND, "Ping Sent", pingPayload, true)
        } else {
            addLog(WebSocketLogEvent.Direction.SYSTEM, "Ping Failed", "WebSocket is not connected", false)
        }
    }

    fun clearLogs() {
        _logEvents.value = emptyList()
    }

    private fun scheduleReconnect() {
        if (isUserDisconnected || currentUrl.isBlank()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionState.value = WsConnectionState.RECONNECTING
            addLog(WebSocketLogEvent.Direction.SYSTEM, "Auto-Reconnect", "Attempting reconnect in 4 seconds...")
            delay(4000)
            if (!isUserDisconnected && currentUrl.isNotBlank()) {
                connect(currentUrl, currentToken)
            }
        }
    }

    private fun addLog(direction: WebSocketLogEvent.Direction, title: String, content: String, isSuccess: Boolean = true) {
        val event = WebSocketLogEvent(
            direction = direction,
            title = title,
            content = content,
            isSuccess = isSuccess
        )
        _logEvents.update { current ->
            (listOf(event) + current).take(MAX_LOGS)
        }
    }

    private fun createListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = WsConnectionState.CONNECTED
                addLog(
                    WebSocketLogEvent.Direction.SYSTEM,
                    "WebSocket Handshake Established",
                    "Connected with code: ${response.code} ${response.message}",
                    true
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (pingStartTime > 0) {
                    val latency = System.currentTimeMillis() - pingStartTime
                    _lastPingLatency.value = latency
                    pingStartTime = 0L
                }
                addLog(WebSocketLogEvent.Direction.INBOUND, "Server Frame Received", text, true)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                addLog(WebSocketLogEvent.Direction.INBOUND, "Binary Frame", "${bytes.size} bytes received", true)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                addLog(WebSocketLogEvent.Direction.SYSTEM, "Server Closing", "Code: $code, Reason: $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = WsConnectionState.DISCONNECTED
                addLog(WebSocketLogEvent.Direction.SYSTEM, "Connection Closed", "Code: $code, Reason: $reason")
                if (!isUserDisconnected) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = WsConnectionState.ERROR
                val detail = "Error: ${t.message ?: "Unknown socket failure"}${response?.let { " (HTTP ${it.code})" } ?: ""}"
                addLog(WebSocketLogEvent.Direction.SYSTEM, "WebSocket Failure", detail, false)
                if (!isUserDisconnected) {
                    scheduleReconnect()
                }
            }
        }
    }
}
