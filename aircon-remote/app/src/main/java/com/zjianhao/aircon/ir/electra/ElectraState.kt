package com.zjianhao.aircon.ir.electra

/** 运行模式，code 为协议内编码（kElectraAc* 常量）。 */
enum class AcMode(val code: Int, val label: String) {
    AUTO(0b000, "自动"),
    COOL(0b001, "制冷"),
    DRY(0b010, "除湿"),
    HEAT(0b100, "制热"),
    FAN(0b110, "送风");

    companion object {
        fun fromName(name: String?): AcMode = entries.firstOrNull { it.name == name } ?: COOL
        fun fromCode(code: Int): AcMode = entries.firstOrNull { it.code == code } ?: AUTO
    }
}

/** 风速，注意编码不是自然序：Auto=5, Low=3, Med=2, High=1。 */
enum class FanSpeed(val code: Int, val label: String) {
    AUTO(0b101, "自动"),
    LOW(0b011, "低"),
    MEDIUM(0b010, "中"),
    HIGH(0b001, "高");

    companion object {
        fun fromName(name: String?): FanSpeed = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * 一次要下发的空调状态（意图状态，非空调真实状态——红外单向无回读）。
 *
 * 说明：
 * - [quiet] 对应 Electra 的 Quiet 位。Electra 协议**没有独立的睡眠字段**，
 *   界面上「睡眠模式」用 Quiet 近似；
 * - [autoOffHours] **不是协议字段**。Electra 没有定时关机码，
 *   只能由 App 本地倒计时到点再发一帧关机状态。
 */
data class ElectraState(
    val power: Boolean = true,
    val mode: AcMode = AcMode.COOL,
    val temperature: Int = 26,
    val fan: FanSpeed = FanSpeed.AUTO,
    val swingVertical: Boolean = false,
    val swingHorizontal: Boolean = false,
    val quiet: Boolean = false,
    val turbo: Boolean = false,
    val iFeel: Boolean = false,
    val clean: Boolean = false,
    val lightToggle: Int = ElectraSpec.LIGHT_TOGGLE_OFF,
    /** 传感器温度（℃）。null 表示不写入该字节 */
    val sensorTemperature: Int? = null,
    /** App 内倒计时关机小时数，0 表示不定时 */
    val autoOffHours: Int = 0
) {
    fun sanitized(): ElectraState = copy(
        temperature = temperature.coerceIn(ElectraSpec.MIN_TEMP, ElectraSpec.MAX_TEMP),
        autoOffHours = autoOffHours.coerceIn(0, ElectraSpec.MAX_AUTO_OFF_HOURS),
        sensorTemperature = sensorTemperature?.coerceIn(
            ElectraSpec.SENSOR_MIN_TEMP, ElectraSpec.SENSOR_MAX_TEMP
        )
    )

    /** 列表摘要，例如「制冷 · 26℃ · 静音 · 2 小时后关(App)」 */
    fun summary(): String {
        val parts = mutableListOf<String>()
        parts += mode.label
        parts += "${temperature}℃"
        if (quiet) parts += "静音"
        if (swingVertical || swingHorizontal) parts += "扫风"
        parts += if (autoOffHours > 0) "${autoOffHours} 小时后关（App 倒计时）" else "不定时"
        return parts.joinToString(" · ")
    }
}
