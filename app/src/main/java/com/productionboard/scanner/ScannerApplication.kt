package com.productionboard.scanner

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

class ScannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Modern OpenCV Android artifacts bundle their native libraries
        // directly in the AAR (no separate "OpenCV Manager" APK needed),
        // but initLocal() is still the documented way to force/confirm the
        // native library is loaded before any org.opencv.* call.
        val loaded = OpenCVLoader.initLocal()
        if (!loaded) {
            Log.e("ScannerApplication", "OpenCV native library failed to load - vision features will not work.")
        }
    }
}
