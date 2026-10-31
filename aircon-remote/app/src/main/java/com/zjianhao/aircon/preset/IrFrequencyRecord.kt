package com.zjianhao.aircon.preset

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一条「测试成功」的红外载波频率记录。
 *
 * 只有在用户确认本次发射成功时才会写入，失败不入库。
 */
data class IrFrequencyRecord(
    val id: Long = 0L,
    /** 载波频率，单位 Hz，例如 38000 */
    val freqHz: Int,
    /** 记录时间戳（毫秒） */
    val recordedAt: Long
) {
    val khzLabel: String
        get() = if (freqHz % 1000 == 0) {
            "${freqHz / 1000}kHz"
        } else {
            "${freqHz / 1000.0}kHz"
        }

    val timeLabel: String
        get() = TIME_FORMAT.format(Date(recordedAt))

    /** 应用为预设时的默认名称 */
    val presetName: String
        get() = "$khzLabel 预设"

    private companion object {
        val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    }
}
