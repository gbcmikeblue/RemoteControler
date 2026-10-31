package com.zjianhao.aircon.ir.electra

/**
 * 帧内一个字段的位域描述。
 *
 * @param byteIndex 字节下标（0 起）
 * @param bitOffset 该字节内的起始位，0 表示最低位
 * @param bitWidth  字段宽度
 * @param reversed  写值前是否按位反转（对应 IRremoteESP8266 里的 reverseBits 用法，
 *                  部分空调协议的数值字段是"低位在前"的）
 */
data class Field(
    val byteIndex: Int,
    val bitOffset: Int,
    val bitWidth: Int,
    val reversed: Boolean = false
) {
    val mask: Int get() = (1 shl bitWidth) - 1
}

/**
 * Electra（ELECTRA_AC）协议的全部可调参数。
 *
 * 时序常量对齐 IRremoteESP8266 `ir_Electra.h` 的 kElectraAc* 系列：
 * 引导码 9166/4470us、位载波 646us、逻辑 1 间隔 1647us、逻辑 0 间隔 547us、分帧间隔 30ms。
 *
 * [M2 校准点]
 * 下列量与真机强相关，必须在实测中逐项确认，全部集中在本文件，逻辑层不出现裸数字：
 * 1. [SECTION_BIT_LENGTHS] 分帧长度（合计必须等于 [FRAME_BITS]）
 * 2. [POWER] / [MODE] / [TEMPERATURE] / [FAN] / [SLEEP] / [TIMER_OFF_HOURS] 的字节与位偏移
 * 3. 校验字节的计算方式（见 ElectraCodec.checksum）
 * 4. 若实测发现奥克斯某型号并非 Electra 协议，只需替换本文件与 ElectraCodec，
 *    上层预设与发射逻辑不受影响。
 */
object ElectraSpec {

    /** 需求 4.3：38kHz 载波 */
    const val CARRIER_FREQ_HZ = 38_000

    /** 需求 4.4：Electra 帧结构 13 字节 */
    const val FRAME_BYTES = 13
    const val FRAME_BITS = FRAME_BYTES * 8

    /** 帧首固定标识字节 */
    const val MAGIC_BYTE_0 = 0xC3

    // ---- 时序（微秒）----
    const val HDR_MARK_US = 9166
    const val HDR_SPACE_US = 4470
    const val BIT_MARK_US = 646
    const val ONE_SPACE_US = 1647
    const val ZERO_SPACE_US = 547
    const val SECTION_GAP_US = 30_000

    /**
     * 一帧被拆成 3 个分段发送，段间插入 [SECTION_GAP_US]。
     * 默认 34 + 34 + 36 = 104 = [FRAME_BITS]。
     */
    val SECTION_BIT_LENGTHS = intArrayOf(34, 34, 36)

    // ---- 温度 ----
    const val MIN_TEMP = 16
    const val MAX_TEMP = 30

    // ---- 定时关机 ----
    const val MAX_TIMER_HOURS = 12

    // ---- 位域 ----
    val POWER = Field(byteIndex = 1, bitOffset = 6, bitWidth = 1)
    val MODE = Field(byteIndex = 1, bitOffset = 0, bitWidth = 3)
    val TEMPERATURE = Field(byteIndex = 2, bitOffset = 0, bitWidth = 4, reversed = true)
    val FAN = Field(byteIndex = 2, bitOffset = 4, bitWidth = 2)
    val SLEEP = Field(byteIndex = 3, bitOffset = 0, bitWidth = 1)
    val SWING_VERTICAL = Field(byteIndex = 3, bitOffset = 1, bitWidth = 1)
    val TIMER_OFF_HOURS = Field(byteIndex = 4, bitOffset = 0, bitWidth = 5)

    const val CHECKSUM_BYTE_INDEX = 12

    init {
        val total = SECTION_BIT_LENGTHS.sum()
        require(total == FRAME_BITS) {
            "分帧长度合计 $total 必须等于帧位数 $FRAME_BITS"
        }
    }
}
