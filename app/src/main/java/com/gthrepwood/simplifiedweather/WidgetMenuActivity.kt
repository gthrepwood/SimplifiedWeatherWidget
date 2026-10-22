package com.gthrepwood.simplifiedweather

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.net.toUri

class WidgetMenuActivity : Activity() {
    private val requestCode = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showMenu()
    }

    private fun showMenu() {
        val options = arrayOf(
            "Enable Local Weather (Set Location)",
            "Add Widget to Home Screen",
            "Set Theme (Font Color)",
            "Set Widget Opacity",
            "Update Weather",
            "Open Calendar",
            "Open Időkép (idokep.hu)",
            "About"
        )
        AlertDialog.Builder(this)
            .setTitle("Simplified Weather Widget Menu")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestLocation()
                    1 -> addWidgetToHomeScreen()
                    2 -> showThemeDialog()
                    3 -> showOpacityDialog()
                    4 -> updateWeather()
                    5 -> openCalendar()
                    6 -> openUrl("https://idokep.hu")
                    7 -> showAboutDialog()
                }
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showThemeDialog() {
        val themeLabels = arrayOf("Set Dark (Dark Text)", "Set Light (White Text)")
        val prefs = getSharedPreferences(WidgetProvider.PREFS, MODE_PRIVATE)

        AlertDialog.Builder(this)
            .setTitle("Set Theme (Font Color)")
            .setItems(themeLabels) { _, which ->
                val newTheme = if (which == 0) "dark" else "light"
                prefs.edit { putString("widget_theme", newTheme) }
                val label = if (which == 0) "Dark Text" else "Light Text"
                Toast.makeText(this, "Theme set to $label", Toast.LENGTH_SHORT).show()
                updateWeather()
            }
            .setOnCancelListener { finish() }
            .setOnDismissListener { finish() }
            .show()
    }

    private fun showOpacityDialog() {
        val opacityValues = intArrayOf(0, 30, 50, 70, 100)
        val opacityLabels = arrayOf("0% (Transparent)", "30%", "50%", "70%", "100% (Opaque)")
        val prefs = getSharedPreferences(WidgetProvider.PREFS, MODE_PRIVATE)

        AlertDialog.Builder(this)
            .setTitle("Set Widget Opacity")
            .setItems(opacityLabels) { _, which ->
                val newOpacity = opacityValues[which]
                prefs.edit { putInt("opacity_pct", newOpacity) }
                Toast.makeText(this, "Widget opacity set to $newOpacity%", Toast.LENGTH_SHORT).show()
                updateWeather()
            }
            .setOnCancelListener { finish() }
            .setOnDismissListener { finish() }
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About Simplified Weather Widget")
            .setMessage(
                "Simplified Weather Widget v1.0\n\n" +
                        "© 2026 All Rights Reserved.\n" +
                        "Author: Tamas Gervai (gthrepwood@gmail.com)\n" +
                        "GitHub:\ngithub.com/gthrepwood/SimplifiedWeatherWidget"
            )
            .setNeutralButton("GitHub Repo") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, "https://github.com/gthrepwood/SimplifiedWeatherWidget".toUri()).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, "No browser found.", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
            .setNegativeButton("Close") { _, _ -> finish() }
            .setOnDismissListener { finish() }
            .show()
    }

    private fun requestLocation() {
        val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                requestCode
            )
            return
        }
        findLocation()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == this.requestCode && results.any { it == PackageManager.PERMISSION_GRANTED }) {
            findLocation()
        } else {
            Toast.makeText(this, "Location permission not granted.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun findLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .asSequence()
                .mapNotNull { provider ->
                    try {
                        lm.getLastKnownLocation(provider)
                    } catch (_: Exception) {
                        null
                    }
                }
                .maxByOrNull { it.time }

            if (last != null) {
                saveLocation(last)
                return
            }

            val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                LocationManager.NETWORK_PROVIDER
            } else {
                LocationManager.GPS_PROVIDER
            }

            val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) return

            lm.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(location: Location) = saveLocation(location)
                @Deprecated("Deprecated by Android")
                override fun onStatusChanged(provider: String?, statusCode: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }, mainLooper)
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't read location. Ensure GPS/Location is ON.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveLocation(location: Location) {
        getSharedPreferences(WidgetProvider.PREFS, MODE_PRIVATE).edit {
            putString("lat", location.latitude.toString())
            putString("lon", location.longitude.toString())
        }
        Toast.makeText(this, "Location saved! Updating weather...", Toast.LENGTH_SHORT).show()
        updateWeather()
    }

    private fun addWidgetToHomeScreen() {
        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
        val myProvider = ComponentName(this, WidgetProvider::class.java)
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            appWidgetManager.requestPinAppWidget(myProvider, null, null)
        } else {
            Toast.makeText(this, "Pinning widget is not supported by your launcher.", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun updateWeather() {
        val intent = Intent(this, WidgetProvider::class.java).setAction(WidgetProvider.ACTION_REFRESH)
        sendBroadcast(intent)
        finish()
    }

    private fun openCalendar() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, "content://com.android.calendar/time/${System.currentTimeMillis()}".toUri()).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }
        finish()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {}
        finish()
    }
}
