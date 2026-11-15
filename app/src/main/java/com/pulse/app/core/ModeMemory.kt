package com.pulse.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 每个玩具一套「哪种模式作用于哪些通道」的记忆。
 * 键是蓝牙地址：下次连上同一台玩具，脉冲 / 摇一摇 / 声控 / 音乐 / 波形都按上次的选择来。
 *
 * 空列表是有效值，表示「用户把它们全关了」，不能用空列表当成没记录。
 */
data class ModeTargets(
    val quick: List<String> = emptyList(),
    val voice: List<String> = emptyList(),
    val song: List<String> = emptyList(),
    val pattern: List<String> = emptyList(),
    val patternSync: Boolean = true
)

class ModeMemory(context: Context) {

    private val sp = context.getSharedPreferences("mode_targets_v1", Context.MODE_PRIVATE)
    private val cache: MutableMap<String, ModeTargets> = load()

    private fun load(): MutableMap<String, ModeTargets> {
        val text = sp.getString("items", null) ?: return HashMap()
        return try {
            val root = JSONObject(text)
            val map = HashMap<String, ModeTargets>()
            root.keys().forEach { address ->
                val o = root.optJSONObject(address) ?: return@forEach
                map[address.uppercase()] = ModeTargets(
                    quick = readList(o, "quick"),
                    voice = readList(o, "voice"),
                    song = readList(o, "song"),
                    pattern = readList(o, "pattern"),
                    patternSync = o.optBoolean("sync", true)
                )
            }
            map
        } catch (e: Exception) {
            HashMap()
        }
    }

    private fun readList(o: JSONObject, key: String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val v = arr.optString(i)
                if (v.isNotBlank()) add(v)
            }
        }
    }

    fun get(address: String): ModeTargets? = cache[address.uppercase()]

    fun put(address: String, targets: ModeTargets) {
        cache[address.uppercase()] = targets
        persist()
    }

    fun forget(address: String) {
        cache.remove(address.uppercase())
        persist()
    }

    fun clear() {
        cache.clear()
        persist()
    }

    private fun persist() {
        try {
            persistInternal()
        } catch (_: Throwable) {
            // 记忆写失败不该影响控制
        }
    }

    private fun persistInternal() {
        val root = JSONObject()
        cache.forEach { (address, t) ->
            val o = JSONObject()
                .put("quick", JSONArray(t.quick))
                .put("voice", JSONArray(t.voice))
                .put("song", JSONArray(t.song))
                .put("pattern", JSONArray(t.pattern))
                .put("sync", t.patternSync)
            root.put(address, o)
        }
        sp.edit().putString("items", root.toString()).apply()
    }
}
