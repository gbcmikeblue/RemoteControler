package com.zjianhao.aircon.ir.electra

/**
 * 帧内一个字段的位域描述（bitOffset=0 表示该字节最低位）。
 */
data class Field(
    val byteIndex: Int,
    val bitOffset: Int,
    val bitWidth: Int
) {
    val mask: Int get() = (1 shl bitWidth) - 1
}

/**
 * Electra（ELECTRA_AC）协议参数。
 *
 * 全部数值来自 IRremoteESP8266 v2.9.0 的 `ir_Electra.h` / `ir_Electra.cpp`，
 * 其头文件明确列出的支持机型包含 **AUX KFR-35GW/BpNFW=3 + YKR-T/011**，
 * 即奥克斯 YKR 系列遥控器。
 *
 * 三条容易搞错、但在源码里写死的事实：
 * 1. 校验是**前 12 字节的算术累加和**（`sumBytes`），不是异或；
 * 2. `sendGeneric(..., MSBfirst = false)` → **每字节 LSB 先发**；
 * 3. **单帧 104 bit 连续发送，不分段**，没有段间 gap。
 */
object ElectraSpec {

    const val CARRIER_FREQ_HZ = 38_000

    const val FRAME_BYTES = 13
    const val FRAME_BITS = FRAME_BYTES * 8

    /** raw[0] 固定标识 */
    const val MAGIC_BYTE_0 = 0xC3

    // ---- 时序（微秒，来自 kElectraAc*）----
    const val HDR_MARK_US = 9166
    const val HDR_SPACE_US = 4470
    const val BIT_MARK_US = 646
    const val ONE_SPACE_US = 1647
    const val ZERO_SPACE_US = 547

    /**
     * 帧尾间隔。原库用 kDefaultMessageGap（100ms），手机端单帧连发时
     * 100ms 纯空转没有意义，这里取 10ms 仅用于让末位收到一个收尾 mark。
     */
    const val MESSAGE_GAP_US = 10_000

    // ---- 温度：编码值 = 温度 - 8，5 位 ----
    const val MIN_TEMP = 16
    const val MAX_TEMP = 32
    const val TEMP_DELTA = 8

    // ---- 传感器温度：编码值 = 温度 + 0x4A ----
    const val SENSOR_TEMP_DELTA = 0x4A
    const val SENSOR_MIN_TEMP = 0
    const val SENSOR_MAX_TEMP = 50

    /** App 内倒计时上限（非协议字段） */
    const val MAX_AUTO_OFF_HOURS = 12

    // ---- 位域 ----
    val SWING_VERTICAL = Field(byteIndex = 1, bitOffset = 0, bitWidth = 3)
    val TEMPERATURE = Field(byteIndex = 1, bitOffset = 3, bitWidth = 5)
    val SWING_HORIZONTAL = Field(byteIndex = 2, bitOffset = 5, bitWidth = 3)
    val SENSOR_UPDATE = Field(byteIndex = 3, bitOffset = 6, bitWidth = 1)
    val FAN = Field(byteIndex = 4, bitOffset = 5, bitWidth = 3)
    val TURBO = Field(byteIndex = 5, bitOffset = 6, bitWidth = 1)
    val QUIET = Field(byteIndex = 5, bitOffset = 7, bitWidth = 1)
    val I_FEEL = Field(byteIndex = 6, bitOffset = 3, bitWidth = 1)
    val MODE = Field(byteIndex = 6, bitOffset = 5, bitWidth = 3)
    val SENSOR_TEMPERATURE = Field(byteIndex = 7, bitOffset = 0, bitWidth = 8)
    val CLEAN = Field(byteIndex = 9, bitOffset = 2, bitWidth = 1)
    val POWER = Field(byteIndex = 9, bitOffset = 5, bitWidth = 1)
    val LIGHT_TOGGLE = Field(byteIndex = 11, bitOffset = 0, bitWidth = 8)

    const val CHECKSUM_BYTE_INDEX = 12

    // ---- 枚举常量 ----
    const val SWING_ON = 0b000
    const val SWING_OFF = 0b111

    const val LIGHT_TOGGLE_OFF = 0x08
}
