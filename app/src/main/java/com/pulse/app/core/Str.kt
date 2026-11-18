package com.pulse.app.core

import android.content.Context
import androidx.annotation.StringRes

/**
 * 全局取词。
 *
 * 界面文案走 `values/`（英文默认）、`values-zh/`、`values-es/` 三套资源；
 * 这里把 Context 收在一处，让 ViewModel / 蓝牙控制器也能取到同一套语言。
 * 换语言时重新 init 再 recreate，所有界面会重建。
 *
 * 预设名、通道名这类「数据里的中文」不走资源 id，统一用 [data] 查表；
 * 用户自己起的名字查不到就原样返回。
 */
object Str {

    @Volatile
    private var ctx: Context? = null

    @Volatile
    private var lang: String = "zh"

    fun init(base: Context) {
        ctx = LocaleManager.wrap(base.applicationContext)
        lang = LocaleManager.language(base)
    }

    fun s(@StringRes id: Int, vararg args: Any): String =
        ctx?.getString(id, *args) ?: ""

    fun data(zh: String): String = when (lang) {
        "en" -> DATA_EN[zh] ?: zh
        "es" -> DATA_ES[zh] ?: zh
        else -> zh
    }

    private val DATA_EN: Map<String, String> = mapOf(
        "振动" to "Vibration", "旋转" to "Rotation", "摆动" to "Swing", "气阀" to "Air valve",
        "吮吸" to "Suction", "抽插" to "Thrusting", "气泵" to "Pump", "电击" to "E-stim",
        "电击频率" to "E-stim frequency", "加热" to "Heat", "灯效" to "Lights",
        "振动 A" to "Vibration A", "振动 B" to "Vibration B", "振动 C" to "Vibration C",
        "杰士邦" to "Jissbon", "小怪兽" to "MonsterPub", "网易春风" to "TryFun",
        "未知型号" to "Unknown model", "型号匹配" to "Model match", "名称前缀" to "Name prefix",
        "服务 UUID" to "Service UUID", "GATT 服务" to "GATT service",
        "通用串口服务" to "Generic serial service",
        "潮汐" to "Tide", "呼吸" to "Breath", "心跳" to "Heartbeat", "涌浪" to "Surge",
        "阶梯" to "Steps", "电音脉冲" to "Synth pulse", "蜂鸟" to "Hummingbird",
        "暴雨" to "Storm", "渐强" to "Fade in", "渐弱" to "Fade out",
        "舒缓" to "Calm", "律动" to "Rhythm", "节奏" to "Beat", "高频" to "High",
        "随机" to "Random", "自定义" to "Custom", "函数" to "Function",
        "正弦" to "Sine", "三角" to "Triangle", "锯齿" to "Saw", "方波" to "Square",
        "脉冲" to "Pulse", "衰减" to "Decay", "斜坡" to "Ramp",
        "基础" to "Base", "相加" to "Add", "相乘" to "Multiply", "取大" to "Max",
        "取小" to "Min", "混合" to "Mix",
        "跟随系统" to "Follow system", "浅色" to "Light", "深色" to "Dark",
        "糖果" to "Candy", "极光" to "Aurora", "薄荷" to "Mint", "晚霞" to "Sunset",
        "海盐" to "Sea salt", "葡萄" to "Grape", "素色" to "Mono",
        "壁纸取色" to "From wallpaper"
    )

    private val DATA_ES: Map<String, String> = mapOf(
        "振动" to "Vibración", "旋转" to "Rotación", "摆动" to "Balanceo",
        "气阀" to "Válvula de aire", "吮吸" to "Succión", "抽插" to "Empuje",
        "气泵" to "Bomba", "电击" to "Electroestimulación", "电击频率" to "Frecuencia de electro",
        "加热" to "Calor", "灯效" to "Luces",
        "振动 A" to "Vibración A", "振动 B" to "Vibración B", "振动 C" to "Vibración C",
        "杰士邦" to "Jissbon", "小怪兽" to "MonsterPub", "网易春风" to "TryFun",
        "未知型号" to "Modelo desconocido", "型号匹配" to "Coincidencia de modelo",
        "名称前缀" to "Prefijo de nombre", "服务 UUID" to "UUID de servicio",
        "GATT 服务" to "Servicio GATT", "通用串口服务" to "Servicio serie genérico",
        "潮汐" to "Marea", "呼吸" to "Respiración", "心跳" to "Latido", "涌浪" to "Oleaje",
        "阶梯" to "Escalera", "电音脉冲" to "Pulso synth", "蜂鸟" to "Colibrí",
        "暴雨" to "Tormenta", "渐强" to "Fundido de entrada", "渐弱" to "Fundido de salida",
        "舒缓" to "Suave", "律动" to "Ritmo", "节奏" to "Compás", "高频" to "Alta",
        "随机" to "Aleatorio", "自定义" to "Personalizado", "函数" to "Función",
        "正弦" to "Seno", "三角" to "Triangular", "锯齿" to "Sierra", "方波" to "Cuadrada",
        "脉冲" to "Pulso", "衰减" to "Decaimiento", "斜坡" to "Rampa",
        "基础" to "Base", "相加" to "Sumar", "相乘" to "Multiplicar", "取大" to "Máximo",
        "取小" to "Mínimo", "混合" to "Mezclar",
        "跟随系统" to "Seguir el sistema", "浅色" to "Claro", "深色" to "Oscuro",
        "糖果" to "Caramelo", "极光" to "Aurora", "薄荷" to "Menta", "晚霞" to "Atardecer",
        "海盐" to "Sal marina", "葡萄" to "Uva", "素色" to "Monocromo",
        "壁纸取色" to "Del fondo"
    )
}
