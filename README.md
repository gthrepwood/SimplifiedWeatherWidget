# HyperOS-inspired 4×2 Android widget

A native Android home-screen widget for Android 8.0+ (including Xiaomi HyperOS). It displays a live digital clock/date, current-day weather, battery level and storage usage.

## Build and install

1. Open this folder in Android Studio (Giraffe or newer) and allow Gradle sync.
2. Build and run the `app` configuration on your Xiaomi/Android phone.
3. Open **HyperOS 3 Widget** once and grant location permission for local weather.
4. Return to the launcher, long-press the home screen, choose **Widgets**, find **HyperOS 3 Widget**, and place the 4×2 widget.

Weather is fetched from Open-Meteo (no API key required) using the saved device coordinates. Refresh by tapping the weather area; the widget also refreshes periodically (Android launcher controls the exact interval). Battery and storage values update when Android delivers a widget refresh. Storage reports used space on the app's primary shared storage volume. The clock uses Android's live TextClock.

The generated concept image is only a visual reference; this project implements a real native widget and does not install itself automatically. HyperOS launchers may vary in widget resizing and styling.
