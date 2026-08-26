package com.example.data.model

import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@JsonClass(generateAdapter = true)
data class CallMetadataPayload(
    val phone_number: String,
    val caller_name: String,
    val call_type: String,
    val duration_seconds: Int,
    val start_time_iso: String,
    val end_time_iso: String
)

@JsonClass(generateAdapter = true)
data class UserInputPayload(
    val notes: String,
    val tags: List<String>,
    val actions_taken: List<String>,
    val vehicle_ids: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ShowroomInfoPayload(
    val showroom_name: String,
    val showroom_address: String,
    val maps_url: String
)

@JsonClass(generateAdapter = true)
data class DeviceInfoPayload(
    val app_name: String = "Call Notes & Quick Share",
    val app_version: String = "1.0",
    val platform: String = "Android"
)

@JsonClass(generateAdapter = true)
data class WebSocketOutboundPacket(
    val event: String = "call_note_captured",
    val message_id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val timestamp_iso: String,
    val record_id: Long,
    val call_metadata: CallMetadataPayload,
    val user_input: UserInputPayload,
    val showroom_info: ShowroomInfoPayload,
    val device_info: DeviceInfoPayload = DeviceInfoPayload()
)

data class WebSocketLogEvent(
    val id: String = System.currentTimeMillis().toString() + "_" + (1000..9999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: Direction, // OUTBOUND, INBOUND, SYSTEM
    val title: String,
    val content: String,
    val isSuccess: Boolean = true
) {
    enum class Direction { OUTBOUND, INBOUND, SYSTEM }

    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}
