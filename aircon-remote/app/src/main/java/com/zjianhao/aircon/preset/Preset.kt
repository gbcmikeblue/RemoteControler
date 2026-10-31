package com.zjianhao.aircon.preset

import com.zjianhao.aircon.ir.electra.ElectraState

/** 指令下发方式。 */
enum class EmitMode {
    /** 需求 4.5：按 开机 → 睡眠 → 温度 → 定时 的顺序逐条下发 */
    SEQUENTIAL,

    /** 若状态码本身已包含全部信息，只发最终一帧 */
    SINGLE_FRAME;

    companion object {
        fun fromName(name: String?): EmitMode = entries.firstOrNull { it.name == name } ?: SEQUENTIAL
    }
}

/**
 * 一条预设。对应需求 F3「多预设管理」。
 *
 * [rawCommands] 是"实测波形覆盖"：当 M2 校准出的真实脉冲序列与 [state] 推导结果不一致时，
 * 把实测数组填进来即可直接下发，无需改动协议层。
 */
data class Preset(
    val id: Long = 0L,
    val name: String,
    val state: ElectraState,
    val emitMode: EmitMode = EmitMode.SEQUENTIAL,
    val rawCommands: List<IntArray> = emptyList()
) {
    val usesCalibratedRaw: Boolean get() = rawCommands.isNotEmpty()
}
