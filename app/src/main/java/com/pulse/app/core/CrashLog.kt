package com.pulse.app.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 把未捕获异常写到 files/last_crash.txt，便于真机出问题时回传 */
object CrashLog {

    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                file(context).writeText(
                    "$time  thread=${thread.name}\n${error.stackTraceToString()}"
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
