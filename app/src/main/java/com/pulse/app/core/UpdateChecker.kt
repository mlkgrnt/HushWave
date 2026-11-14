package com.pulse.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val version: String,
    val pageUrl: String,
    val notes: String
)

/** 只查 GitHub 的 latest release，不发任何设备信息 */
object UpdateChecker {

    const val REPO = "https://github.com/mlkgrnt/HushWave"
    const val ISSUES = "$REPO/issues"
    private const val API = "https://api.github.com/repos/mlkgrnt/HushWave/releases/latest"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun latest(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "HushWave")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("GitHub 返回 ${resp.code}")
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) error("响应为空，稍后再试")
                val o = JSONObject(body)
                UpdateInfo(
                    version = o.optString("tag_name").removePrefix("v").removePrefix("V").trim(),
                    pageUrl = o.optString("html_url").ifBlank { "$REPO/releases" },
                    notes = o.optString("body").trim()
                )
            }
        }
    }

    /** 逐段比较，缺位按 0 处理：1.0.1 > 1.0 */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parse(latest)
        val b = parse(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parse(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+', ' ')
            .mapNotNull { it.toIntOrNull() }
}
