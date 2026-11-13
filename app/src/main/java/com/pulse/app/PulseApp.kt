package com.pulse.app

import android.app.Application
import com.pulse.app.core.CrashLog
import com.pulse.app.core.ToyCatalog

class PulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        val t = System.currentTimeMillis()
        ToyCatalog.load(this)
        android.util.Log.d("PulseStartup", "catalog load ${System.currentTimeMillis() - t}ms")
    }
}
