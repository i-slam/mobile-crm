package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.dao.CallRecordDao
import com.example.data.db.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.CallRecord
import com.example.data.model.WhatsAppTemplate
import com.example.websocket.CallWebSocketClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallRepository(
    private val context: Context,
    private val callRecordDao: CallRecordDao = AppDatabase.getInstance(context).callRecordDao(),
    private val wsClient: CallWebSocketClient = CallWebSocketClient.getInstance()
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val prefs: SharedPreferences = context.getSharedPreferences("call_crm_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    val allRecords: Flow<List<CallRecord>> = callRecordDao.getAllRecords()
    val recordCount: Flow<Int> = callRecordDao.getRecordCount()
    val pendingCount: Flow<Int> = callRecordDao.getPendingCount()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _templates = MutableStateFlow(loadTemplates())
    val templates: StateFlow<List<WhatsAppTemplate>> = _templates.asStateFlow()

    init {
        // Automatically start WebSocket connection based on saved settings
        val current = _settings.value
        if (current.wsServerUrl.isNotBlank()) {
            wsClient.connect(current.wsServerUrl, current.wsAuthToken)
        }

        // Seed initial sample CRM calls if first launch so user can immediately test all functionality
        scope.launch {
            seedSampleDataIfEmpty()
        }
    }

    suspend fun populateDemoData() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sample1 = CallRecord(
            phoneNumber = "+1 (555) 382-9912",
            callerName = "Sarah Jenkins (High-End Interior)",
            callType = "INCOMING",
            startTimeMillis = now - 180000,
            endTimeMillis = now - 65000,
            durationSeconds = 115,
            notes = "Customer looking for Italian marble dining collection. Inquired about weekend showroom viewing with family.",
            tags = listOf("📍 Showroom Visit", "⭐ VIP Wholesale", "🔥 Hot Lead"),
            actionsTaken = listOf("SENT_SHOWROOM_LOCATION_WHATSAPP"),
            syncStatus = "SYNCED",
            syncedAtMillis = now - 60000
        )
        val sample2 = CallRecord(
            phoneNumber = "+1 (555) 874-2109",
            callerName = "David Chen",
            callType = "OUTGOING",
            startTimeMillis = now - 3600000,
            endTimeMillis = now - 3420000,
            durationSeconds = 180,
            notes = "Discussed modern velvet sectional sofa in Emerald Green. Sent price quotation and dimensions via WhatsApp.",
            tags = listOf("💰 Price Quote", "🛋️ Furniture Inquiry"),
            actionsTaken = listOf("SENT_CATALOG_WHATSAPP"),
            syncStatus = "SYNCED",
            syncedAtMillis = now - 3400000
        )
        val sample3 = CallRecord(
            phoneNumber = "+1 (555) 901-4432",
            callerName = "Apex Architecture Group",
            callType = "MISSED",
            startTimeMillis = now - 7200000,
            endTimeMillis = now - 7200000,
            durationSeconds = 0,
            notes = "Missed call during meeting. Follow-up SMS sent with showroom location link.",
            tags = listOf("📞 Call Back Later", "📍 Showroom Visit"),
            actionsTaken = listOf("SENT_LOCATION_SMS"),
            syncStatus = "PENDING"
        )

        callRecordDao.insertRecord(sample1)
        callRecordDao.insertRecord(sample2)
        callRecordDao.insertRecord(sample3)
    }

    private suspend fun seedSampleDataIfEmpty() {
        val count = callRecordDao.getRecordCountDirect()
        if (count == 0) {
            populateDemoData()
        }
    }

    suspend fun saveCallRecordAndSync(record: CallRecord): Long = withContext(Dispatchers.IO) {
        val insertedId = callRecordDao.insertRecord(record)
        val fullRecord = record.copy(id = insertedId)

        val success = wsClient.sendCallPayload(fullRecord, _settings.value)
        if (success) {
            val updated = fullRecord.copy(
                syncStatus = "SYNCED",
                syncedAtMillis = System.currentTimeMillis()
            )
            callRecordDao.updateRecord(updated)
        }
        insertedId
    }

    suspend fun retrySyncRecord(record: CallRecord): Boolean = withContext(Dispatchers.IO) {
        val success = wsClient.sendCallPayload(record, _settings.value)
        if (success) {
            val updated = record.copy(
                syncStatus = "SYNCED",
                syncedAtMillis = System.currentTimeMillis()
            )
            callRecordDao.updateRecord(updated)
            true
        } else {
            false
        }
    }

    suspend fun flushPendingQueue(): Int = withContext(Dispatchers.IO) {
        val pending = callRecordDao.getPendingSyncRecords()
        var syncedCount = 0
        for (rec in pending) {
            val sent = wsClient.sendCallPayload(rec, _settings.value)
            if (sent) {
                val updated = rec.copy(
                    syncStatus = "SYNCED",
                    syncedAtMillis = System.currentTimeMillis()
                )
                callRecordDao.updateRecord(updated)
                syncedCount++
            }
        }
        syncedCount
    }

    suspend fun updateRecord(record: CallRecord) = withContext(Dispatchers.IO) {
        callRecordDao.updateRecord(record)
    }

    suspend fun deleteRecord(record: CallRecord) = withContext(Dispatchers.IO) {
        callRecordDao.deleteRecord(record)
    }

    suspend fun deleteAllRecords() = withContext(Dispatchers.IO) {
        callRecordDao.deleteAllRecords()
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        saveSettingsToPrefs(newSettings)
        if (newSettings.wsServerUrl.isNotBlank()) {
            wsClient.connect(newSettings.wsServerUrl, newSettings.wsAuthToken)
        }
    }

    fun updateTemplates(newTemplates: List<WhatsAppTemplate>) {
        _templates.value = newTemplates
        saveTemplatesToPrefs(newTemplates)
    }

    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean("onboarding_complete", false)
    }

    fun setOnboardingComplete() {
        prefs.edit().putBoolean("onboarding_complete", true).apply()
    }

    fun addCustomTag(tag: String) {
        val currentTags = _settings.value.customTags
        if (!currentTags.contains(tag) && tag.isNotBlank()) {
            val updated = _settings.value.copy(customTags = currentTags + tag)
            updateSettings(updated)
        }
    }

    private fun loadSettings(): AppSettings {
        val url = prefs.getString("ws_server_url", "wss://echo.websocket.events") ?: "wss://echo.websocket.events"
        val token = prefs.getString("ws_auth_token", "") ?: ""
        val autoReconnect = prefs.getBoolean("ws_auto_reconnect", true)
        val showroomName = prefs.getString("showroom_name", "Downtown Luxury Showroom") ?: "Downtown Luxury Showroom"
        val showroomAddress = prefs.getString("showroom_address", "100 Grand Avenue, Financial District, New York, NY 10001") ?: "100 Grand Avenue, Financial District, New York, NY 10001"
        val showroomMapsUrl = prefs.getString("showroom_maps_url", "https://maps.google.com/?q=40.712776,-74.005974(Downtown+Luxury+Showroom)") ?: "https://maps.google.com/?q=40.712776,-74.005974(Downtown+Luxury+Showroom)"
        val overlayEnabled = prefs.getBoolean("overlay_enabled", true)
        val autoOpen = prefs.getBoolean("auto_open_overlay", true)

        val tagsJson = prefs.getString("custom_tags_json", null)
        val tags = if (!tagsJson.isNullOrEmpty()) {
            try {
                val listType = Types.newParameterizedType(List::class.java, String::class.java)
                moshi.adapter<List<String>>(listType).fromJson(tagsJson) ?: AppSettings().customTags
            } catch (e: Exception) {
                AppSettings().customTags
            }
        } else {
            AppSettings().customTags
        }

        return AppSettings(
            wsServerUrl = url,
            wsAuthToken = token,
            autoReconnect = autoReconnect,
            showroomName = showroomName,
            showroomAddress = showroomAddress,
            showroomMapsUrl = showroomMapsUrl,
            overlayEnabled = overlayEnabled,
            autoOpenOverlayOnCallEnd = autoOpen,
            customTags = tags
        )
    }

    private fun saveSettingsToPrefs(settings: AppSettings) {
        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        val tagsJson = moshi.adapter<List<String>>(listType).toJson(settings.customTags)

        prefs.edit()
            .putString("ws_server_url", settings.wsServerUrl)
            .putString("ws_auth_token", settings.wsAuthToken)
            .putBoolean("ws_auto_reconnect", settings.autoReconnect)
            .putString("showroom_name", settings.showroomName)
            .putString("showroom_address", settings.showroomAddress)
            .putString("showroom_maps_url", settings.showroomMapsUrl)
            .putBoolean("overlay_enabled", settings.overlayEnabled)
            .putBoolean("auto_open_overlay", settings.autoOpenOverlayOnCallEnd)
            .putString("custom_tags_json", tagsJson)
            .apply()
    }

    private fun loadTemplates(): List<WhatsAppTemplate> {
        val json = prefs.getString("whatsapp_templates_json", null)
        if (!json.isNullOrEmpty()) {
            try {
                val listType = Types.newParameterizedType(List::class.java, WhatsAppTemplate::class.java)
                val loaded = moshi.adapter<List<WhatsAppTemplate>>(listType).fromJson(json)
                if (!loaded.isNullOrEmpty()) return loaded
            } catch (e: Exception) {
                // fallback to default
            }
        }
        return WhatsAppTemplate.getDefaultTemplates()
    }

    private fun saveTemplatesToPrefs(templates: List<WhatsAppTemplate>) {
        val listType = Types.newParameterizedType(List::class.java, WhatsAppTemplate::class.java)
        val json = moshi.adapter<List<WhatsAppTemplate>>(listType).toJson(templates)
        prefs.edit().putString("whatsapp_templates_json", json).apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: CallRepository? = null

        fun getInstance(context: Context): CallRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = CallRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
