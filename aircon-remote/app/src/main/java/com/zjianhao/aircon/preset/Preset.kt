package com.zjianhao.aircon.preset

import com.zjianhao.aircon.ir.electra.ElectraSpec
import com.zjianhao.aircon.ir.electra.ElectraState

/**
 * 一条预设。对应需求 F3「多预设管理」。
 *
 * Electra 是状态码协议：一帧就包含开关/模式/温度/风速的完整状态，
 * 所以执行时只发一帧，不再拆成多步。
 *
 * [carrierFreqHz] 是该预设使用的红外载波频率，默认 38kHz；
 * 频率测试记录「应用为预设」时会把它覆盖成实测可用的频率。
 *
 * [rawCommands] 是"实测波形覆盖"：拿到原装遥控器的实测微秒数组后填进来，
 * 执行器会优先按实测波形下发，完全绕开协议编码。
 */
data class Preset(
    val id: Long = 0L,
    val name: String,
    val state: ElectraState,
    val carrierFreqHz: Int = ElectraSpec.CARRIER_FREQ_HZ,
    val rawCommands: List<IntArray> = emptyList()
) {
    val usesCalibratedRaw: Boolean get() = rawCommands.isNotEmpty()

    val carrierLabel: String
        get() = if (carrierFreqHz % 1000 == 0) {
            "${carrierFreqHz / 1000}kHz"
        } else {
            "${carrierFreqHz / 1000.0}kHz"
        }

    val usesCustomCarrier: Boolean get() = carrierFreqHz != ElectraSpec.CARRIER_FREQ_HZ
}
