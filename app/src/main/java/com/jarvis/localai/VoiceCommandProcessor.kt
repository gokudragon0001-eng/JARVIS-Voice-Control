package com.jarvis.localai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.app.ActivityCompat
import java.net.URLEncoder
import java.util.Locale

/** Fast, deterministic phone commands. AI is used only for general conversation. */
object VoiceCommandProcessor {
    fun execute(context: Context, raw: String): String? {
        val command = normalize(raw)
        if (command.isBlank()) return null
        if (containsAny(command, "open jarvis", "jarvis kholo", "jarvis open")) return "JARVIS is online. Tell me your command."
        if (containsAny(command, "open whatsapp", "whatsapp kholo", "whatsapp khol do", "whatsapp open", "launch whatsapp")) {
            return openWhatsApp(context)
        }
        val sendMatch = Regex("(?:search|find|open)\\s+(.+?)\\s+chat\\s+(?:and\\s+)?(?:send|bhej|bhejo)\\s+(?:a\\s+)?(?:message|msg)\\s+(.+)$", RegexOption.IGNORE_CASE).find(command)
            ?: Regex("(?:send|bhej|bhejo|message)\\s+(?:a\\s+)?(?:whatsapp\\s+)?(?:message\\s+)?(.+?)\\s+(?:message|msg)\\s+(.+)$", RegexOption.IGNORE_CASE).find(command)
            ?: Regex("(?:whatsapp\\s+)?(?:send|bhej|bhejo)\\s+(.+?)\\s+(?:ko\\s+)?(?:message|msg)\\s+(.+)$", RegexOption.IGNORE_CASE).find(command)
        if (sendMatch != null) {
            val name = sendMatch.groupValues[1].trim()
            val message = sendMatch.groupValues[2].trim()
            return openContact(context, name, message, false)
        }
        val callMatch = Regex("^(?:whatsapp\\s+)?(?:call|phone|milao|call\\s+lagao)\\s+(?:to\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(command)
            ?: Regex("(.+?)\\s+ko\\s+whatsapp\\s+(?:per\\s+)?(?:call|phone)\\s+(?:milao|milo|lagao)$", RegexOption.IGNORE_CASE).find(command)
        if (callMatch != null) {
            val name = callMatch.groupValues[1].trim().removePrefix("ko ").trim()
            return openContact(context, name, null, true)
        }
        val searchMatch = Regex("(?:search|find|open|kholo|khol do)\\s+(.+?)\\s+(?:chat|ki chat)$", RegexOption.IGNORE_CASE).find(command)
            ?: Regex("(?:search|find|open)\\s+(.+?)\\s+(?:on\\s+)?whatsapp$", RegexOption.IGNORE_CASE).find(command)
        if (searchMatch != null) return openContact(context, searchMatch.groupValues[1].trim(), null, false)
        return null
    }

    private fun openWhatsApp(context: Context): String {
        val launch = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        return try {
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                "WhatsApp opening."
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.whatsapp.com/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "WhatsApp is not installed. I opened its website."
            }
        } catch (_: Exception) { "I could not open WhatsApp on this phone." }
    }

    private fun openContact(context: Context, name: String, message: String?, call: Boolean): String {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            if (context is Activity) ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.READ_CONTACTS), 704)
            return "Please allow Contacts permission, then repeat your command."
        }
        val matches = findContacts(context, name)
        if (matches.isEmpty()) return "I could not find $name in your phone contacts. Save the person in Contacts first."
        if (matches.size > 1) return "I found more than one contact named $name. Please say the full name to identify the right person."
        val phone = matches.first().filter { it.isDigit() || it == '+' }
        if (phone.isBlank()) return "I could not find a phone number for $name."
        return try {
            val prefs = context.getSharedPreferences("jarvis_automation", Context.MODE_PRIVATE)
            if (call) prefs.edit().putString("pending_action", "call").apply()
            else if (!message.isNullOrBlank()) prefs.edit().putString("pending_action", "send").apply()
            val url = if (!message.isNullOrBlank()) {
                "https://wa.me/${phone.filter { it.isDigit() }}?text=${URLEncoder.encode(message, "UTF-8")}"
            } else {
                "https://wa.me/${phone.filter { it.isDigit() }}"
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            when {
                call -> "Your WhatsApp chat with $name is opening. I will try to start the voice call."
                !message.isNullOrBlank() -> "Opening $name's WhatsApp chat and preparing your message."
                else -> "Opening $name's WhatsApp chat."
            }
        } catch (_: Exception) { "I could not open WhatsApp for $name." }
    }

    private fun findContacts(context: Context, requested: String): List<String> {
        val result = mutableListOf<Pair<String, String>>()
        val wanted = requested.lowercase(Locale.ROOT).trim()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
            val ni = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val pi = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val display = c.getString(ni) ?: continue
                val phone = c.getString(pi) ?: continue
                val normalized = display.lowercase(Locale.ROOT)
                if (normalized == wanted || normalized.contains(wanted) || wanted.contains(normalized)) result.add(display to phone)
            }
        }
        val distinct = result.distinctBy { it.first.lowercase(Locale.ROOT) }
        return if (distinct.size == 1) listOf(distinct.first().second) else distinct.map { it.second }
    }

    private fun normalize(s: String) = s.lowercase(Locale.ROOT).replace(Regex("^(?:hey\\s+)?jarvis\\s+"), "").replace(Regex("[,.!?]"), " ").replace(Regex("\\s+"), " ").trim()
    private fun containsAny(s: String, vararg terms: String) = terms.any { s.contains(it) }
}
