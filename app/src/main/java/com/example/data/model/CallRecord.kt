package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "call_records")
data class CallRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val callerName: String = "Unknown Contact",
    val callType: String = "INCOMING", // "INCOMING", "OUTGOING", "MISSED", "REJECTED"
    val startTimeMillis: Long = System.currentTimeMillis(),
    val endTimeMillis: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val actionsTaken: List<String> = emptyList(), // e.g. "SENT_LOCATION_WHATSAPP", "SENT_CATALOG"
    val selectedVehicleIds: List<String> = emptyList(),
    val syncStatus: String = "PENDING", // "PENDING", "SYNCED", "FAILED"
    val syncedAtMillis: Long? = null,
    val webSocketResponse: String? = null
)

class StringListConverters {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, String::class.java)
    private val adapter = moshi.adapter<List<String>>(listType)

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return adapter.toJson(value ?: emptyList())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            adapter.fromJson(value) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
