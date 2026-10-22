package com.gthrepwood.simplifiedweather

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.edit

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val requestCode = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            setBackgroundColor(0xFFF3F6FA.toInt())
        }
        root.addView(
            TextView(this).apply {
                text = "Simplified Weather Widget"
                textSize = 26f
                setTextColor(0xFF1D2838.toInt())
            },
        )
        root.addView(
            TextView(this).apply {
                text = "Add the 4×2 widget from your home screen’s Widgets menu. Allow location to show local weather."
                textSize = 16f
                setPadding(0, 24, 0, 24)
                setTextColor(0xFF526174.toInt())
            },
        )
        status = TextView(this).apply {
            text = "Location is needed for local weather."
            textSize = 14f
            setTextColor(0xFF526174.toInt())
        }
        root.addView(status)
        root.addView(
            Button(this).apply {
                text = "Enable local weather"
                setOnClickListener { requestLocation() }
            },
        )
        root.addView(
            Button(this).apply {
                text = "Add Widget to Home Screen"
                setOnClickListener {
                    val appWidgetManager = getSystemService(AppWidgetManager::class.java)
                    val myProvider = ComponentName(this@MainActivity, WidgetProvider::class.java)
                    if (appWidgetManager.isRequestPinAppWidgetSupported) {
                        appWidgetManager.requestPinAppWidget(myProvider, null, null)
                    }
                }
            },
        )
        setContentView(root)
        val prefs = getSharedPreferences(WidgetProvider.PREFS, MODE_PRIVATE)
        if (prefs.contains("lat")) {
            status.text = "Location is set. Add the widget from the home-screen Widgets menu."
        }
        refreshWidgets()
    }

    private fun requestLocation() {
        val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasCoarse) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                requestCode,
            )
            return
        }
        findLocation()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if ((requestCode == this.requestCode) && results.any { it == PackageManager.PERMISSION_GRANTED }) {
            findLocation()
        } else {
            status.text = "Location permission wasn’t granted. Weather will be unavailable; you can enable it later."
        }
    }

    @Suppress("DEPRECATION")
    private fun findLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            val last = providers
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

            status.text = "Getting location… make sure Location is on."
            val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                LocationManager.NETWORK_PROVIDER
            } else {
                LocationManager.PASSIVE_PROVIDER
            }

            val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasCoarse) return

            lm.requestSingleUpdate(
                provider,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) = saveLocation(location)
                    @Deprecated("Deprecated by Android")
                    override fun onStatusChanged(provider: String?, statusCode: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {
                        status.text = "Turn on Location and try again."
                    }
                },
                mainLooper,
            )
        } catch (_: Exception) {
            status.text = "Couldn’t read location. Turn on Location and try again."
        }
    }

    private fun saveLocation(location: Location) {
        getSharedPreferences(WidgetProvider.PREFS, MODE_PRIVATE).edit {
            putString("lat", location.latitude.toString())
            putString("lon", location.longitude.toString())
        }
        status.text = "Location saved. Add the widget from the home-screen Widgets menu."
        refreshWidgets()
    }

    private fun refreshWidgets() {
        val intent = Intent(this, WidgetProvider::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        intent.putExtra(
            AppWidgetManager.EXTRA_APPWIDGET_IDS,
            AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, WidgetProvider::class.java)),
        )
        sendBroadcast(intent)
    }
}
