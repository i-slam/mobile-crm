package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

data class RecentCallInfo(
    val number: String,
    val name: String?,
    val durationSeconds: Int,
    val callType: String,
    val timestampMillis: Long
)

object CallLogHelper {

    private const val TAG = "CallLogHelper"

    fun getMostRecentCall(context: Context): RecentCallInfo? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG permission not granted")
            return null
        }

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            if (cursor != null && cursor.moveToFirst()) {
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)

                val number = if (numberIdx != -1) cursor.getString(numberIdx) ?: "" else ""
                var name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                val duration = if (durationIdx != -1) cursor.getInt(durationIdx) else 0
                val typeInt = if (typeIdx != -1) cursor.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                val date = if (dateIdx != -1) cursor.getLong(dateIdx) else System.currentTimeMillis()

                val callType = when (typeInt) {
                    CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                    CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                    CallLog.Calls.MISSED_TYPE -> "MISSED"
                    CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                    CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
                    else -> "INCOMING"
                }

                if (name.isNullOrBlank() && number.isNotBlank()) {
                    name = lookupContactName(context, number)
                }

                return RecentCallInfo(
                    number = number,
                    name = name,
                    durationSeconds = duration,
                    callType = callType,
                    timestampMillis = date
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying CallLog", e)
        } finally {
            cursor?.close()
        }
        return null
    }

    fun lookupContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        var cursor: Cursor? = null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIdx != -1) {
                    val name = cursor.getString(nameIdx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name for $phoneNumber", e)
        } finally {
            cursor?.close()
        }
        return null
    }
}
