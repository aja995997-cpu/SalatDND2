package com.example.salatdnd

import android.content.BroadcastReceiver
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class DndAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_OFF = "com.example.salatdnd.ACTION_DND_OFF"
        private const val ACTION_PRAYER = "com.example.salatdnd.ACTION_PRAYER"
        private const val PREFS = "salat_dnd"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when {
            PrayerScheduler.isRefreshAction(intent.action) -> PrayerScheduler.refreshInBackground(context)
            intent.action == ACTION_OFF -> turnOffIfCurrent(context, intent.getLongExtra("session_id", -1L))
            intent.action == ACTION_PRAYER -> turnOnForPrayer(context, intent)
        }
    }

    private fun turnOnForPrayer(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_enabled", true)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.isNotificationPolicyAccessGranted) return
        val sessionId = intent.getLongExtra("session_id", System.currentTimeMillis())
        val duration = intent.getIntExtra("duration_minutes", prefs.getInt("duration_minutes", 15)).coerceIn(1, 180)
        try {
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            prefs.edit().putLong("active_session_id", sessionId).apply()
            PrayerScheduler.scheduleOff(context, sessionId, duration)
        } catch (_: SecurityException) {
        }
    }

    private fun turnOffIfCurrent(context: Context, sessionId: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong("active_session_id", -2L) != sessionId) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.isNotificationPolicyAccessGranted) return
        try {
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            prefs.edit().remove("active_session_id").apply()
        } catch (_: SecurityException) {
        }
    }
}
