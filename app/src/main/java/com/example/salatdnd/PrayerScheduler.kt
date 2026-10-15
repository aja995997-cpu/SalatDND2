package com.example.salatdnd

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

 data class PrayerTimeEntry(val name: String, val displayTime: String, val triggerAtMillis: Long)

object PrayerScheduler {
    private const val PREFS = "salat_dnd"
    private const val ACTION_PRAYER = "com.example.salatdnd.ACTION_PRAYER"
    private const val ACTION_REFRESH = "com.example.salatdnd.ACTION_REFRESH_CALENDAR"
    private const val REFRESH_REQUEST = 8091

    fun fetchMonth(latitude: Double, longitude: Double): List<PrayerTimeEntry> {
        val now = Calendar.getInstance()
        val urlText = "https://api.aladhan.com/v1/calendar/${now.get(Calendar.YEAR)}/${now.get(Calendar.MONTH) + 1}?latitude=$latitude&longitude=$longitude&method=20"
        val connection = (URL(urlText).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "SalatDND/1.0")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("API jadwal salat HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            if (root.optInt("code", 0) != 200) throw IllegalStateException("API jadwal salat tidak mengembalikan data")
            val data = root.getJSONArray("data")
            val output = mutableListOf<PrayerTimeEntry>()
            val names = listOf("Fajr" to "Subuh", "Dhuhr" to "Zuhur", "Asr" to "Asar", "Maghrib" to "Magrib", "Isha" to "Isya")
            val apiTimeZone = root.optJSONObject("meta")?.optString("timezone")
                ?: TimeZone.getDefault().id
            val parser = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(apiTimeZone); isLenient = false }
            val display = SimpleDateFormat("HH:mm", Locale.getDefault())
            for (i in 0 until data.length()) {
                val day = data.getJSONObject(i)
                val date = day.getJSONObject("date").getJSONObject("gregorian").getString("date")
                val timings = day.getJSONObject("timings")
                for ((apiName, label) in names) {
                    val time = timings.getString(apiName).substringBefore(" ").trim()
                    val parsed = parser.parse("$date $time") ?: continue
                    output.add(PrayerTimeEntry(label, display.format(parsed), parsed.time))
                }
            }
            return output.sortedBy { it.triggerAtMillis }
        } finally {
            connection.disconnect()
        }
    }

    fun todayEntries(entries: List<PrayerTimeEntry>): List<PrayerTimeEntry> {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val today = fmt.format(Date())
        return entries.filter { fmt.format(Date(it.triggerAtMillis)) == today }
    }

    fun schedulePrayerAlarms(context: Context, entries: List<PrayerTimeEntry>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        scheduleRefresh(context)
        if (!prefs.getBoolean("auto_enabled", true)) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val duration = prefs.getInt("duration_minutes", 15).coerceIn(1, 180)
        for (entry in entries) {
            if (entry.triggerAtMillis <= now) continue
            val intent = Intent(context, DndAlarmReceiver::class.java).apply {
                action = ACTION_PRAYER
                putExtra("session_id", entry.triggerAtMillis)
                putExtra("duration_minutes", duration)
                putExtra("prayer_name", entry.name)
            }
            val requestCode = (entry.triggerAtMillis / 60000L).toInt()
            val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.triggerAtMillis, pending)
        }
    }

    fun scheduleOff(context: Context, sessionId: Long, durationMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DndAlarmReceiver::class.java).apply {
            action = DndAlarmReceiver.ACTION_OFF
            putExtra("session_id", sessionId)
        }
        val requestCode = 500000 + (sessionId / 60000L).toInt().rem(1000000)
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + durationMinutes * 60_000L, pending)
    }

    fun scheduleRefresh(context: Context) {
        val next = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val intent = Intent(context, DndAlarmReceiver::class.java).apply { action = ACTION_REFRESH }
        val pending = PendingIntent.getBroadcast(context, REFRESH_REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
    }

    fun refreshInBackground(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return
        val lon = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return
        Thread {
            try { schedulePrayerAlarms(context, fetchMonth(lat, lon)) } catch (_: Exception) { scheduleRefresh(context) }
        }.start()
    }

    fun isRefreshAction(action: String?): Boolean = action == ACTION_REFRESH
}
