package com.zjianhao.aircon.ir.electra

/** 运行模式。code 为协议内的模式编码。 */
enum class AcMode(val code: Int, val label: String) {
    AUTO(0, "自动"),
    COOL(1, "制冷"),
    DRY(2, "除湿"),
    FAN(3, "送风"),
    HEAT(4, "制热");

    companion object {
        fun fromName(name: String?): AcMode = entries.firstOrNull { it.name == name } ?: COOL
    }
}

/** 风速。 */
enum class FanSpeed(val code: Int, val label: String) {
    AUTO(0, "自动"),
    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高");

    companion object {
        fun fromName(name: String?): FanSpeed = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * 一次要下发的空调状态。
 *
 * 注意：这是"意图状态"而非"空调当前状态"——需求 3.3 已明确红外单向通信，
 * App 无法读取空调的真实状态，这里只是用来生成状态码。
 */
data class ElectraState(
    val power: Boolean = true,
    val mode: AcMode = AcMode.COOL,
    val temperature: Int = 26,
    val fan: FanSpeed = FanSpeed.AUTO,
    val sleep: Boolean = false,
    val swing: Boolean = false,
    /** 定时自动关机小时数，0 表示不定时 */
    val timerOffHours: Int = 0
) {
    fun sanitized(): ElectraState = copy(
        temperature = temperature.coerceIn(ElectraSpec.MIN_TEMP, ElectraSpec.MAX_TEMP),
        timerOffHours = timerOffHours.coerceIn(0, ElectraSpec.MAX_TIMER_HOURS)
    )

    /** 供列表展示的一行摘要，例如「制冷 · 26℃ · 睡眠 · 2 小时后关」。 */
    fun summary(): String {
        val parts = mutableListOf<String>()
        parts += mode.label
        parts += "${temperature}℃"
        if (sleep) parts += "睡眠"
        if (swing) parts += "扫风"
        parts += if (timerOffHours > 0) "${timerOffHours} 小时后关" else "不定时"
        return parts.joinToString(" · ")
    }
}
