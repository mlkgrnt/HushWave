package com.pulse.app.core

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** 可操控模块的类型。前 6 种来自 buttplug 的设备定义，后 5 种是伽拉库私有帧特有。 */
enum class ModuleType(val cn: String) {
    VIBRATE("振动"),
    ROTATE("旋转"),
    OSCILLATE("摆动"),
    CONSTRICT("气阀"),
    SUCTION("吮吸"),
    THRUST("抽插"),
    PUMP("气泵"),
    ESTIM("电击"),
    ESTIM_FREQ("电击频率"),
    HEAT("加热"),
    LED("灯效")
}

data class ModuleSpec(
    val type: ModuleType,
    val index: Int,
    val label: String,
    val max: Int
) {
    val key: String get() = "${type.name}:$index"

    fun raw(value: Float): Int =
        (value.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)

    fun ratio(raw: Int): Float = if (max <= 0) 0f else raw.toFloat() / max

    fun toJson(): JSONObject = JSONObject()
        .put("t", type.name)
        .put("i", index)
        .put("l", label)
        .put("m", max)

    companion object {
        fun fromJson(o: JSONObject) = ModuleSpec(
            type = runCatching { ModuleType.valueOf(o.getString("t")) }.getOrDefault(ModuleType.VIBRATE),
            index = o.optInt("i", 0),
            label = o.optString("l").ifBlank { o.optString("label", "振动") },
            max = (if (o.has("m")) o.optInt("m", 100) else o.optInt("max", 100)).coerceAtLeast(1)
        )
    }
}

data class GattProfile(val service: String, val tx: String, val rx: String?)

data class CatalogModel(
    val name: String,
    val names: List<String>,
    val modules: List<ModuleSpec>,
    val brand: String = ""
) {
    /** 去掉品牌前缀，只留型号本身（不音译、不翻译） */
    val shortName: String
        get() {
            val prefix = listOf(brand, "Svakom", "Galaku", "Jissbon", "杰士邦", "Lovense", "WeVibe", "We-Vibe", "MagicMotion")
                .filter { it.isNotBlank() }
                .firstOrNull { name.startsWith("$it ", ignoreCase = true) }
            return if (prefix != null) name.substring(prefix.length).trim() else name
        }
}

data class ProtocolSpec(
    val id: String,
    val brand: String,
    val display: String,
    val profiles: List<GattProfile>,
    val names: List<String>,
    val services: List<String>,
    val models: MutableList<CatalogModel>,
    val defaults: List<ModuleSpec>,
    val keepAliveMs: Long,
    val writeResponse: Boolean
) {
    fun modelFor(deviceName: String?): CatalogModel? {
        if (deviceName.isNullOrBlank()) return null
        return models.firstOrNull { m -> m.names.any { it.equals(deviceName, true) } }
    }

    /** 该协议所有型号出现过的模块类型去重，用于“未知型号时手动挑模块” */
    val knownModuleTypes: List<ModuleType>
        get() = (models.flatMap { it.modules } + defaults).map { it.type }.distinct()
}

data class ToyMatch(
    val protocol: ProtocolSpec,
    val model: CatalogModel?,
    val confidence: Float,
    val reason: String
)

object ToyCatalog {

    private var all: List<ProtocolSpec> = emptyList()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        all = try {
            val text = context.assets.open("toy_catalog.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONArray("protocols")
            val built = buildList {
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    val profiles = mutableListOf<GattProfile>()
                    val ps = p.optJSONArray("profiles") ?: JSONArray()
                    for (j in 0 until ps.length()) {
                        val o = ps.getJSONObject(j)
                        val tx = o.optString("tx")
                        if (tx.isBlank()) continue
                        profiles.add(
                            GattProfile(
                                service = o.optString("s"),
                                tx = tx,
                                rx = o.optString("rx").ifBlank { null }
                            )
                        )
                    }
                    val models = mutableListOf<CatalogModel>()
                    val ms = p.optJSONArray("models") ?: JSONArray()
                    for (j in 0 until ms.length()) {
                        val m = ms.getJSONObject(j)
                        val names = mutableListOf<String>()
                        val ns = m.optJSONArray("names") ?: JSONArray()
                        for (k in 0 until ns.length()) names.add(ns.getString(k))
                        models.add(
                            CatalogModel(
                                name = m.optString("name", "未知型号"),
                                names = names,
                                modules = parseModules(m.optJSONArray("modules")),
                                brand = p.getString("brand")
                            )
                        )
                    }
                    val names = mutableListOf<String>()
                    val pn = p.optJSONArray("names") ?: JSONArray()
                    for (j in 0 until pn.length()) names.add(pn.getString(j))
                    val services = mutableListOf<String>()
                    val sv = p.optJSONArray("services") ?: JSONArray()
                    for (j in 0 until sv.length()) services.add(sv.getString(j).lowercase())

                    add(
                        ProtocolSpec(
                            id = p.getString("id"),
                            brand = p.getString("brand"),
                            display = p.optString("display", p.getString("id")),
                            profiles = profiles,
                            names = names,
                            services = services,
                            models = models.toMutableList(),
                            defaults = parseModules(p.optJSONArray("defaults")),
                            keepAliveMs = p.optLong("keepAlive", 0L),
                            writeResponse = p.optBoolean("writeResp", false)
                        )
                    )
                }
            }
            applyOverrides(context, built)
            built
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun applyOverrides(context: Context, list: List<ProtocolSpec>) {
        val text = try {
            context.assets.open("model_overrides.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return
        }
        val arr = try {
            JSONObject(text).optJSONArray("models") ?: return
        } catch (e: Exception) {
            return
        }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val brand = o.optString("brand")
            val match = mutableListOf<String>()
            val m = o.optJSONArray("match") ?: continue
            for (j in 0 until m.length()) match.add(m.getString(j))
            val modules = parseModules(o.optJSONArray("modules"))
            if (modules.isEmpty()) continue
            list.filter { it.brand == brand }.forEach { p ->
                p.models.forEachIndexed { idx, model ->
                    val hit = model.names.any { n -> match.any { it.equals(n, true) } } ||
                        match.any { it.equals(model.name, true) }
                    if (hit) {
                        p.models[idx] = model.copy(modules = modules)
                        Log.d("PulseCatalog", "override ${model.name} -> ${modules.map { "${it.type}/${it.label}/${it.max}" }}")
                    }
                }
            }
        }
    }

    private fun parseModules(arr: JSONArray?): List<ModuleSpec> {
        if (arr == null) return emptyList()
        val out = mutableListOf<ModuleSpec>()
        for (i in 0 until arr.length()) out.add(ModuleSpec.fromJson(arr.getJSONObject(i)))
        return out
    }

    val protocols: List<ProtocolSpec> get() = all

    val brands: List<String>
        get() = all.map { it.brand }.distinct()

    fun find(id: String): ProtocolSpec? = all.firstOrNull { it.id == id }

    fun ofBrand(brand: String?): List<ProtocolSpec> =
        if (brand == null) all else all.filter { it.brand == brand }

    fun modelCount(): Int = all.sumOf { it.models.size }

    private val nameCache = HashMap<String, ToyMatch?>()

    /** 用广播名识别：先精确型号，再协议名通配（LVS-* 之类） */
    fun matchByName(name: String?): ToyMatch? {
        if (name.isNullOrBlank()) return null
        return nameCache.getOrPut(name) { matchByNameUncached(name) }
    }

    private fun matchByNameUncached(name: String): ToyMatch? {
        var best: ToyMatch? = null
        for (p in all) {
            val model = p.modelFor(name)
            if (model != null) {
                val m = ToyMatch(p, model, 0.95f, "型号匹配")
                if (best == null || m.confidence > best.confidence) best = m
            }
        }
        if (best != null) return best
        for (p in all) {
            if (p.names.any { globMatch(it, name) }) {
                val m = ToyMatch(p, null, 0.7f, "名称前缀")
                if (best == null || m.confidence > best.confidence) best = m
            }
        }
        return best
    }

    /** 用扫描响应里的 service UUID 识别（最可靠） */
    fun matchByServices(uuids: List<String>): ToyMatch? {
        if (uuids.isEmpty()) return null
        val set = uuids.map { it.lowercase() }
        for (p in all) {
            if (p.services.any { set.contains(it) }) {
                return ToyMatch(p, null, 0.8f, "服务 UUID")
            }
            if (p.profiles.any { set.contains(it.service.lowercase()) }) {
                return ToyMatch(p, null, 0.75f, "GATT 服务")
            }
        }
        return null
    }

    fun match(name: String?, advertised: List<String>): ToyMatch? =
        matchByName(name) ?: matchByServices(advertised)

    private fun globMatch(pattern: String, text: String): Boolean {
        val regex = "^" + Regex.escape(pattern).replace("\\*", ".*") + "$"
        return Regex(regex, RegexOption.IGNORE_CASE).matches(text)
    }
}
