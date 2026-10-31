package com.zjianhao.aircon.ir.electra

import com.zjianhao.aircon.ir.PulsePatternBuilder

/**
 * Electra 协议编码：13 字节状态帧 → 38kHz 脉冲序列。
 *
 * 与 IRremoteESP8266 `ir_Electra.cpp` 保持一致：
 * - raw[0] = 0xC3，raw[1..11] 为字段，raw[12] = 前 12 字节累加和
 * - 字节内 **LSB 先发**
 * - 单帧 104 bit 连续发送，不分段
 */
object ElectraCodec {

    /** 状态 -> 13 字节帧 */
    fun encode(state: ElectraState): ByteArray {
        val safe = state.sanitized()
        val frame = ByteArray(ElectraSpec.FRAME_BYTES)

        frame[0] = ElectraSpec.MAGIC_BYTE_0.toByte()

        setField(frame, ElectraSpec.SWING_VERTICAL, if (safe.swingVertical) ElectraSpec.SWING_ON else ElectraSpec.SWING_OFF)
        setField(frame, ElectraSpec.TEMPERATURE, safe.temperature - ElectraSpec.TEMP_DELTA)
        setField(frame, ElectraSpec.SWING_HORIZONTAL, if (safe.swingHorizontal) ElectraSpec.SWING_ON else ElectraSpec.SWING_OFF)
        setField(frame, ElectraSpec.FAN, safe.fan.code)
        setField(frame, ElectraSpec.TURBO, if (safe.turbo) 1 else 0)
        setField(frame, ElectraSpec.QUIET, if (safe.quiet) 1 else 0)
        setField(frame, ElectraSpec.I_FEEL, if (safe.iFeel) 1 else 0)
        setField(frame, ElectraSpec.MODE, safe.mode.code)
        safe.sensorTemperature?.let {
            setField(frame, ElectraSpec.SENSOR_TEMPERATURE, it + ElectraSpec.SENSOR_TEMP_DELTA)
        }
        setField(frame, ElectraSpec.CLEAN, if (safe.clean) 1 else 0)
        setField(frame, ElectraSpec.POWER, if (safe.power) 1 else 0)
        setField(frame, ElectraSpec.LIGHT_TOGGLE, safe.lightToggle)

        frame[ElectraSpec.CHECKSUM_BYTE_INDEX] = checksum(frame).toByte()
        return frame
    }

    fun stateToPattern(state: ElectraState): IntArray = toPulsePattern(encode(state))

    /**
     * 13 字节帧 -> 微秒数组。
     *
     * 结构：header(mark,space) + 104 个 bit(mark,space,LSB 先发) + footer(mark,gap)。
     */
    fun toPulsePattern(frame: ByteArray): IntArray {
        require(frame.size == ElectraSpec.FRAME_BYTES) {
            "帧长度必须是 ${ElectraSpec.FRAME_BYTES} 字节，实际 ${frame.size}"
        }

        val builder = PulsePatternBuilder(2 + ElectraSpec.FRAME_BITS * 2 + 2)
        builder.mark(ElectraSpec.HDR_MARK_US)
        builder.space(ElectraSpec.HDR_SPACE_US)

        for (byteIndex in 0 until ElectraSpec.FRAME_BYTES) {
            val value = frame[byteIndex].toInt() and 0xFF
            for (bit in 0 until 8) {           // LSB first
                val one = (value shr bit) and 1 == 1
                builder.bit(
                    ElectraSpec.BIT_MARK_US,
                    if (one) ElectraSpec.ONE_SPACE_US else ElectraSpec.ZERO_SPACE_US
                )
            }
        }

        // 收尾 mark + 间隔
        builder.bit(ElectraSpec.BIT_MARK_US, ElectraSpec.MESSAGE_GAP_US)

        return builder.build()
    }

    private fun setField(frame: ByteArray, field: Field, rawValue: Int) {
        val current = frame[field.byteIndex].toInt() and 0xFF
        val cleared = current and (field.mask shl field.bitOffset).inv()
        val updated = cleared or ((rawValue and field.mask) shl field.bitOffset)
        frame[field.byteIndex] = updated.toByte()
    }

    /**
     * 校验字节 = 前 12 字节算术累加和（低 8 位）。
     * 对应 IRremoteESP8266 的 `calcChecksum` → `sumBytes(state, length - 1)`。
     */
    fun checksum(frame: ByteArray): Int {
        var sum = 0
        for (i in 0 until ElectraSpec.CHECKSUM_BYTE_INDEX) {
            sum += frame[i].toInt() and 0xFF
        }
        return sum and 0xFF
    }

    /** 调试用：把帧格式化成 "C3 6F E0 ..." */
    fun frameToHex(frame: ByteArray): String =
        frame.joinToString(" ") { "%02X".format(it) }
}
