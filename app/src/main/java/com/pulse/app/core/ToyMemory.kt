package com.pulse.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 玩具记忆：以蓝牙地址为主键，记住品牌/协议/型号/可用模块/上次的通道值与波形。
 * 下次连上同一台设备时直接恢复，不再重新探测。
 */
data class ToyRecord(
    val address: String,
    val name: String,
    val brand: String,
    val protocolId: String,
    val modelName: String,
    val modules: List<ModuleSpec>,
    val values: Map<String, Float>,
    val patternId: String?,
    val scale: Float,
    val updatedAt: Long,
    val knownModel: Boolean = false
)

class ToyMemory(context: Context) {

    private val sp = context.getSharedPreferences("toy_memory_v1", Context.MODE_PRIVATE)
    private var cache: MutableList<ToyRecord> = load()

    private fun load(): MutableList<ToyRecord> {
        val text = sp.getString("toys", null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(text)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val mods = mutableListOf<ModuleSpec>()
                    val ma = o.optJSONArray("modules") ?: JSONArray()
                    for (j in 0 until ma.length()) mods.add(ModuleSpec.fromJson(ma.getJSONObject(j)))
                    val vals = mutableMapOf<String, Float>()
                    val vo = o.optJSONObject("values")
                    if (vo != null) {
                        vo.keys().forEach { k -> vals[k] = vo.getDouble(k).toFloat() }
                    }
                    add(
                        ToyRecord(
                            address = o.getString("address"),
                            name = o.optString("name"),
                            brand = o.optString("brand"),
                            protocolId = o.getString("protocolId"),
                            modelName = o.optString("modelName"),
                            modules = mods,
                            values = vals,
                            patternId = o.optString("patternId").ifBlank { null },
                            scale = o.optDouble("scale", 1.0).toFloat(),
                            updatedAt = o.optLong("updatedAt", 0L),
                            knownModel = o.optBoolean("knownModel", false)
                        )
                    )
                }
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun persist() {
        try {
            persistInternal()
        } catch (_: Throwable) {
            // 记忆写失败不应该让 App 崩溃
        }
    }

    private fun persistInternal() {
        val arr = JSONArray()
        cache.forEach { r ->
            val o = JSONObject()
                .put("address", r.address)
                .put("name", r.name)
                .put("brand", r.brand)
                .put("protocolId", r.protocolId)
                .put("modelName", r.modelName)
                .put("patternId", r.patternId ?: "")
                .put("scale", r.scale.toDouble())
                .put("updatedAt", r.updatedAt)
                .put("knownModel", r.knownModel)
            val ma = JSONArray()
            r.modules.forEach { ma.put(it.toJson()) }
            o.put("modules", ma)
            val vo = JSONObject()
            r.values.forEach { (k, v) -> if (v.isFinite()) vo.put(k, v.toDouble()) }
            o.put("values", vo)
            arr.put(o)
        }
        sp.edit().putString("toys", arr.toString()).apply()
    }

    fun all(): List<ToyRecord> = cache.sortedByDescending { it.updatedAt }

    fun find(address: String): ToyRecord? =
        cache.firstOrNull { it.address.equals(address, ignoreCase = true) }

    fun save(record: ToyRecord) {
        cache.removeAll { it.address.equals(record.address, ignoreCase = true) }
        cache.add(record)
        persist()
    }

    fun patchValues(address: String, values: Map<String, Float>, patternId: String?, scale: Float) {
        val idx = cache.indexOfFirst { it.address.equals(address, ignoreCase = true) }
        if (idx < 0) return
        cache[idx] = cache[idx].copy(
            values = values,
            patternId = patternId ?: cache[idx].patternId,
            scale = scale,
            updatedAt = System.currentTimeMillis()
        )
        persist()
    }

    fun forget(address: String) {
        cache.removeAll { it.address.equals(address, ignoreCase = true) }
        persist()
    }

    fun clear() {
        cache.clear()
        persist()
    }
}
