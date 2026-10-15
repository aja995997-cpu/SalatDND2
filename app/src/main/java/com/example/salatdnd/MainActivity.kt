package com.example.salatdnd

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.Typeface

class MainActivity : Activity() {
    private lateinit var notificationManager: NotificationManager
    private lateinit var locationManager: LocationManager
    private lateinit var statusText: TextView
    private lateinit var prayerTimesText: TextView
    private lateinit var locationText: TextView
    private lateinit var durationInput: EditText
    private lateinit var toggleButton: Button
    private lateinit var permissionButton: Button
    private lateinit var autoSwitch: Switch
    private val prefs by lazy { getSharedPreferences("salat_dnd", MODE_PRIVATE) }
    private var locationListener: LocationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationManager = getSystemService(NotificationManager::class.java)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        buildUi()
        refreshStatus()
        val savedLat = prefs.getString("latitude", null)
        val savedLon = prefs.getString("longitude", null)
        if (savedLat != null && savedLon != null) loadPrayerTimes(savedLat.toDouble(), savedLon.toDouble(), false)
        else prayerTimesText.text = "Tekan ‘Muat Ulang Jadwal’ untuk mengambil waktu salat berdasarkan lokasi HP."
    }

    private fun buildUi() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 28)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val title = TextView(this).apply {
            text = "Salat DND"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 6)
        }
        val subtitle = TextView(this).apply {
            text = "DND otomatis saat waktu salat"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 18)
        }
        statusText = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }
        permissionButton = Button(this).apply {
            text = "Beri Izin DND"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        }
        toggleButton = Button(this).apply {
            text = "Aktifkan DND"
            setOnClickListener { toggleDnd() }
        }
        val scheduleTitle = TextView(this).apply {
            text = "Jadwal Waktu Salat"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 20, 0, 8)
        }
        locationText = TextView(this).apply {
            text = "Lokasi: belum dimuat"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 8)
        }
        prayerTimesText = TextView(this).apply {
            text = "Jadwal salat belum dimuat."
            textSize = 16f
            setPadding(14, 12, 14, 12)
            setBackgroundColor(Color.rgb(245, 245, 245))
        }
        val reloadButton = Button(this).apply {
            text = "Muat Ulang Jadwal"
            setOnClickListener { requestLocationAndLoad() }
        }
        val durationTitle = TextView(this).apply {
            text = "Durasi DND setelah waktu salat (menit)"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 18, 0, 4)
        }
        durationInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Contoh: 15"
            setSingleLine(true)
            setText(prefs.getInt("duration_minutes", 15).toString())
        }
        val durationHelp = TextView(this).apply {
            text = "Contoh 15 berarti DND aktif selama 15 menit setelah Subuh, Zuhur, Asar, Magrib, dan Isya. Isi 1–180 menit."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 2, 0, 6)
        }
        val saveDurationButton = Button(this).apply {
            text = "Simpan Durasi & Jadwalkan"
            setOnClickListener { saveDurationAndSchedule() }
        }
        autoSwitch = Switch(this).apply {
            text = "Aktifkan jadwal DND otomatis"
            textSize = 15f
            isChecked = prefs.getBoolean("auto_enabled", true)
            setPadding(0, 8, 0, 8)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("auto_enabled", checked).apply()
                if (checked) {
                    Toast.makeText(this@MainActivity, "Jadwal otomatis diaktifkan", Toast.LENGTH_SHORT).show()
                    requestLocationAndLoad()
                } else {
                    Toast.makeText(this@MainActivity, "Jadwal otomatis dinonaktifkan", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val note = TextView(this).apply {
            text = "Catatan: izinkan akses lokasi dan DND. Agar jadwal tetap aktif, jangan masukkan aplikasi ke daftar aplikasi tidur Samsung."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, 10, 0, 8)
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(statusText)
        content.addView(permissionButton)
        content.addView(toggleButton)
        content.addView(scheduleTitle)
        content.addView(locationText)
        content.addView(prayerTimesText)
        content.addView(reloadButton)
        content.addView(durationTitle)
        content.addView(durationInput)
        content.addView(durationHelp)
        content.addView(saveDurationButton)
        content.addView(autoSwitch)
        content.addView(note)
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (::notificationManager.isInitialized) refreshStatus()
    }

    private fun requestLocationAndLoad() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 101)
            return
        }
        try {
            val providers = locationManager.getProviders(true)
            var best: Location? = null
            for (provider in providers) {
                val candidate = try { locationManager.getLastKnownLocation(provider) } catch (_: Exception) { null }
                if (candidate != null && (best == null || candidate.time > best!!.time)) best = candidate
            }
            if (best != null) {
                loadPrayerTimes(best!!.latitude, best!!.longitude, true)
                return
            }
            locationText.text = "Lokasi: mencari lokasi perangkat…"
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                    locationListener = null
                    runOnUiThread { loadPrayerTimes(location.latitude, location.longitude, true) }
                }
                @Deprecated("Deprecated in Android API") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationListener = listener
            var requested = false
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener)
                requested = true
            }
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
                requested = true
            }
            if (!requested) {
                locationText.text = "Lokasi belum tersedia. Aktifkan Lokasi/GPS lalu coba lagi."
            } else {
                Toast.makeText(this, "Sedang mencari lokasi…", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Izin lokasi belum diberikan", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            locationText.text = "Gagal membaca lokasi: ${e.message}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) requestLocationAndLoad()
            else Toast.makeText(this, "Izin lokasi diperlukan untuk mengambil jadwal salat", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadPrayerTimes(latitude: Double, longitude: Double, showLocation: Boolean) {
        prefs.edit().putString("latitude", latitude.toString()).putString("longitude", longitude.toString()).apply()
        if (showLocation) locationText.text = "Lokasi: ${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
        prayerTimesText.text = "Mengambil jadwal salat…"
        Thread {
            try {
                val entries = PrayerScheduler.fetchMonth(latitude, longitude)
                PrayerScheduler.schedulePrayerAlarms(this, entries)
                runOnUiThread {
                    val today = PrayerScheduler.todayEntries(entries)
                    prayerTimesText.text = if (today.isEmpty()) "Jadwal hari ini tidak ditemukan. Tekan Muat Ulang Jadwal." else today.joinToString("\n") { "${it.name}: ${it.displayTime}" }
                    if (!showLocation) locationText.text = "Lokasi tersimpan: ${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
                    Toast.makeText(this, "Jadwal salat berhasil dimuat", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    prayerTimesText.text = "Gagal mengambil jadwal. Periksa internet dan lokasi, lalu coba lagi.\n(${e.message ?: "kesalahan jaringan"})"
                }
            }
        }.start()
    }

    private fun saveDurationAndSchedule() {
        val minutes = durationInput.text.toString().toIntOrNull()
        if (minutes == null || minutes !in 1..180) {
            durationInput.error = "Masukkan angka 1 sampai 180"
            return
        }
        prefs.edit().putInt("duration_minutes", minutes).putBoolean("auto_enabled", autoSwitch.isChecked).apply()
        val lat = prefs.getString("latitude", null)?.toDoubleOrNull()
        val lon = prefs.getString("longitude", null)?.toDoubleOrNull()
        if (lat != null && lon != null) {
            loadPrayerTimes(lat, lon, false)
        } else {
            requestLocationAndLoad()
        }
        Toast.makeText(this, "Durasi disimpan: $minutes menit", Toast.LENGTH_SHORT).show()
    }

    private fun toggleDnd() {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, "Berikan izin DND terlebih dahulu", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        try {
            val current = notificationManager.currentInterruptionFilter
            val currentlyOn = current != NotificationManager.INTERRUPTION_FILTER_ALL && current != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            val target = if (currentlyOn) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_NONE
            notificationManager.setInterruptionFilter(target)
            refreshStatus()
            Toast.makeText(this, if (target == NotificationManager.INTERRUPTION_FILTER_NONE) "DND diaktifkan" else "DND dimatikan", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal mengubah DND: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshStatus() {
        if (!::notificationManager.isInitialized) return
        val granted = notificationManager.isNotificationPolicyAccessGranted
        permissionButton.isEnabled = !granted
        permissionButton.text = if (granted) "Izin DND sudah diberikan" else "Beri Izin DND"
        if (!granted) {
            statusText.text = "Izin DND: belum diberikan"
            toggleButton.text = "Aktifkan DND"
            return
        }
        val filter = notificationManager.currentInterruptionFilter
        val enabled = filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        statusText.text = "Izin DND: diberikan\nStatus DND: ${if (enabled) "AKTIF" else "NONAKTIF"}"
        toggleButton.text = if (enabled) "Matikan DND" else "Aktifkan DND"
    }
}
