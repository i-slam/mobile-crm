package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

object ShareHelper {

    fun copyToClipboard(context: Context, text: String, label: String = "CRM Message"): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "📋 Copied to clipboard!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun shareViaWhatsApp(context: Context, phoneNumber: String, messageText: String) {
        copyToClipboard(context, messageText, "WhatsApp CRM Message")

        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "").removePrefix("+")
        val encodedMessage = try {
            URLEncoder.encode(messageText, "UTF-8")
        } catch (e: Exception) {
            messageText
        }

        // Try direct WhatsApp URI scheme
        val whatsappUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMessage")
        val whatsappIntent = Intent(Intent.ACTION_VIEW, whatsappUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(whatsappIntent)
        } catch (e: Exception) {
            // Fallback: Open system chooser / Share sheet
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageText)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val chooser = Intent.createChooser(shareIntent, "Share Showroom Details via...").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(chooser)
            } catch (e2: Exception) {
                Toast.makeText(context, "Message copied to clipboard! (No messaging apps found)", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun shareViaSms(context: Context, phoneNumber: String, messageText: String) {
        try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            val smsUri = Uri.parse("smsto:$cleanNumber")
            val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                putExtra("sms_body", messageText)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            copyToClipboard(context, messageText, "SMS Message")
            Toast.makeText(context, "Copied SMS text to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareViaSystemSheet(context: Context, subject: String, messageText: String) {
        copyToClipboard(context, messageText, subject)
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, messageText)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val chooser = Intent.createChooser(shareIntent, "Share with customer via...").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Copied text to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }
}
