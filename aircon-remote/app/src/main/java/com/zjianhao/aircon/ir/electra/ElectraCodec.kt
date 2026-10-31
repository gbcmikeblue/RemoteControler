package com.zjianhao.aircon.ir.electra

import com.zjianhao.aircon.ir.PulsePatternBuilder

/**
 * Electra 协议编码：把 [ElectraState] 编成 13 字节状态帧，再转成 38kHz 脉冲序列。
 *
 * 空调红外协议下发的是"完整状态码"而非"按键码"，所以同一温度/模式的不同组合
 * 就是不同的一帧。位域定义全部来自 [ElectraSpec]，本文件只负责拼接与脉冲化。
 */
object ElectraCodec {

    /** 状态 -> 13 字节帧 */
    fun encode(state: ElectraState): ByteArray {
        val safe = state.sanitized()
        val frame = ByteArray(ElectraSpec.FRAME_BYTES)
        frame[0] = ElectraSpec.MAGIC_BYTE_0.toByte()

        setField(frame, ElectraSpec.POWER, if (safe.power) 1 else 0)
        setField(frame, ElectraSpec.MODE, safe.mode.code)
        setField(frame, ElectraSpec.TEMPERATURE, safe.temperature - ElectraSpec.MIN_TEMP)
        setField(frame, ElectraSpec.FAN, safe.fan.code)
        setField(frame, ElectraSpec.SLEEP, if (safe.sleep) 1 else 0)
        setField(frame, ElectraSpec.SWING_VERTICAL, if (safe.swing) 1 else 0)
        setField(frame, ElectraSpec.TIMER_OFF_HOURS, safe.timerOffHours)

        frame[ElectraSpec.CHECKSUM_BYTE_INDEX] = checksum(frame).toByte()
        return frame
    }

    /** 状态 -> 可直接交给 ConsumerIrManager 的微秒数组 */
    fun stateToPattern(state: ElectraState): IntArray = toPulsePattern(encode(state))

    /**
     * 13 字节帧 -> 脉冲序列。
     *
     * 结构：引导码 + 104 个位（每段后按 [ElectraSpec.SECTION_GAP_US] 分帧）+ 段间间隔。
     * 位序为字节内高位在前。
     */
    fun toPulsePattern(frame: ByteArray): IntArray {
        require(frame.size == ElectraSpec.FRAME_BYTES) {
            "帧长度必须是 ${ElectraSpec.FRAME_BYTES} 字节，实际 ${frame.size}"
        }

        val sections = ElectraSpec.SECTION_BIT_LENGTHS
        val builder = PulsePatternBuilder(2 + ElectraSpec.FRAME_BITS * 2)
        builder.mark(ElectraSpec.HDR_MARK_US)
        builder.space(ElectraSpec.HDR_SPACE_US)

        var bitCursor = 0
        sections.forEachIndexed { sectionIndex, sectionBits ->
            val isLastSection = sectionIndex == sections.lastIndex
            for (i in 0 until sectionBits) {
                val bit = frameBit(frame, bitCursor)
                val space = if (bit == 1) ElectraSpec.ONE_SPACE_US else ElectraSpec.ZERO_SPACE_US
                val isLastBitOfSection = i == sectionBits - 1
                val extraGap = if (isLastBitOfSection && !isLastSection) ElectraSpec.SECTION_GAP_US else 0
                builder.bit(ElectraSpec.BIT_MARK_US, space + extraGap)
                bitCursor++
            }
        }
        return builder.build()
    }

    /** 字节内高位在前地取第 index 个 bit。 */
    private fun frameBit(frame: ByteArray, index: Int): Int {
        val byte = frame[index / 8].toInt() and 0xFF
        return (byte shr (7 - index % 8)) and 1
    }

    private fun setField(frame: ByteArray, field: Field, rawValue: Int) {
        val value = if (field.reversed) reverseBits(rawValue, field.bitWidth) else rawValue
        val current = frame[field.byteIndex].toInt() and 0xFF
        val cleared = current and (field.mask shl field.bitOffset).inv()
        val updated = cleared or ((value and field.mask) shl field.bitOffset)
        frame[field.byteIndex] = updated.toByte()
    }

    private fun reverseBits(value: Int, width: Int): Int {
        var result = 0
        for (i in 0 until width) {
            result = (result shl 1) or ((value shr i) and 1)
        }
        return result
    }

    /**
     * 校验字节：[ElectraSpec.CHECKSUM_BYTE_INDEX] 之前所有字节逐位异或。
     * [M2 校准点] 若实测不校验通过，在此处替换算法即可。
     */
    private fun checksum(frame: ByteArray): Int {
        var x = 0
        for (i in 0 until ElectraSpec.CHECKSUM_BYTE_INDEX) {
            x = x xor (frame[i].toInt() and 0xFF)
        }
        return x and 0xFF
    }

    /** 调试用：把帧格式化成 "C3 08 1A ..." */
    fun frameToHex(frame: ByteArray): String =
        frame.joinToString(" ") { "%02X".format(it) }
}
