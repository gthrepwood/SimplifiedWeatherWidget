package com.gthrepwood.simplifiedweather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it, "Loading weather…", "--°", "Today") }
        loadWeather(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, WidgetProvider::class.java))
            ids.forEach { render(context, manager, it, "Updating…", "--°", "Today") }
            loadWeather(context, manager, ids)
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int, condition: String, temp: String, highLow: String) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val isLight = prefs.getString("widget_theme", "dark") == "light"
            val opacity = prefs.getInt("opacity_pct", 100)

            val alphaInt = ((opacity * 255) / 100).coerceIn(0, 255)
            val baseBgColor = if (isLight) 0x0017243A else 0x00F7FAFF
            val backgroundColor = (alphaInt shl 24) or baseBgColor
            views.setInt(R.id.widget_root, "setBackgroundColor", backgroundColor)

            val primaryTextColor = if (isLight) 0xFFFFFFFF.toInt() else 0xFF17243A.toInt()
            val secondaryTextColor = if (isLight) 0xFFD0D7E2.toInt() else 0xFF68778B.toInt()

            views.setTextColor(R.id.time_clock, primaryTextColor)
            views.setTextColor(R.id.date_clock, secondaryTextColor)
            views.setTextColor(R.id.temperature, primaryTextColor)
            views.setTextColor(R.id.battery_value, primaryTextColor)
            views.setTextColor(R.id.storage_value, primaryTextColor)
            views.setTextColor(R.id.weather_condition, secondaryTextColor)
            views.setTextColor(R.id.high_low, secondaryTextColor)

            val battery = try {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } catch (_: Exception) { null }
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (level >= 0 && scale > 0) (100f * level / scale).roundToInt() else 0
            views.setTextViewText(R.id.battery_value, "Bat: $pct%")

            val usedPct = try {
                val stat = StatFs(Environment.getDataDirectory().path)
                val total = stat.totalBytes
                val available = stat.availableBytes
                val used = (total - available).coerceAtLeast(0)
                if (total > 0) (100f * used / total).roundToInt().coerceIn(0, 100) else 0
            } catch (_: Exception) {
                0
            }
            views.setTextViewText(R.id.storage_value, "Disk: $usedPct%")

            views.setTextViewText(R.id.weather_condition, condition)
            views.setTextViewText(R.id.temperature, temp)
            views.setTextViewText(R.id.high_low, highLow)

            val refreshIntent = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, WidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.weather_panel, refreshIntent)

            val menuIntent = PendingIntent.getActivity(
                context,
                id,
                Intent(context, WidgetMenuActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, menuIntent)
            manager.updateAppWidget(id, views)
        } catch (_: Exception) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                views.setTextViewText(R.id.weather_condition, "Tap to retry")
                views.setTextViewText(R.id.temperature, "--°")
                views.setTextViewText(R.id.high_low, "Error loading widget")
                manager.updateAppWidget(id, views)
            } catch (_: Exception) {}
        }
    }

    private fun loadWeather(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat = prefs.getString("lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("lon", null)?.toDoubleOrNull()
        if (lat == null || lon == null) {
            ids.forEach { render(context, manager, it, "Open app to set location", "--°", "Tap to retry") }
            return
        }
        EXECUTOR.execute {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                val data = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(data)
                val current = json.getJSONObject("current")
                val daily = json.getJSONObject("daily")
                val temp = "${current.getDouble("temperature_2m").roundToInt()}°"
                val hi = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()
                val lo = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
                val code = current.getInt("weather_code")
                val (label, glyph) = describe(code)
                val condition = "$glyph  Today · $label"
                val highLow = "H $hi°  ·  L $lo°"
                android.os.Handler(context.mainLooper).post {
                    ids.forEach { render(context, manager, it, condition, temp, highLow) }
                }
            } catch (_: Exception) {
                android.os.Handler(context.mainLooper).post {
                    ids.forEach { render(context, manager, it, "Weather unavailable", "--°", "Tap to retry") }
                }
            }
        }
    }

    private fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "Clear" to "☀"
        1, 2 -> "Partly cloudy" to "⛅"
        3 -> "Cloudy" to "☁"
        45, 48 -> "Fog" to "〰"
        51, 53, 55, 56, 57 -> "Drizzle" to "🌦"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain" to "🌧"
        71, 73, 75, 77, 85, 86 -> "Snow" to "❄"
        95, 96, 99 -> "Thunderstorm" to "⛈"
        else -> "Current weather" to "☁"
    }

    companion object {
        const val ACTION_REFRESH = "com.gthrepwood.simplifiedweather.REFRESH"
        const val PREFS = "widget_settings"
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
