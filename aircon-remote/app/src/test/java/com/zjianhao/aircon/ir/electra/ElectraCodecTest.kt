package com.zjianhao.aircon.ir.electra

import com.zjianhao.aircon.ir.PulsePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只验证"结构性正确"的部分：帧长度、位域写入、脉冲序列合法性与 2 秒上限。
 * 协议是否被真机接受属于 M2 校准范围，单测无法覆盖。
 */
class ElectraCodecTest {

    private val base = ElectraState(
        power = true,
        mode = AcMode.COOL,
        temperature = 26,
        fan = FanSpeed.AUTO,
        sleep = true,
        timerOffHours = 2
    )

    @Test
    fun `帧长度与首字节固定`() {
        val frame = ElectraCodec.encode(base)
        assertEquals(ElectraSpec.FRAME_BYTES, frame.size)
        assertEquals(ElectraSpec.MAGIC_BYTE_0, frame[0].toInt() and 0xFF)
    }

    @Test
    fun `温度字段写入相对偏移并按位反转`() {
        // 26℃ -> 偏移 10（1010b），4 位反转后为 0101b
        val frame = ElectraCodec.encode(base.copy(temperature = 26))
        val field = (frame[ElectraSpec.TEMPERATURE.byteIndex].toInt() shr ElectraSpec.TEMPERATURE.bitOffset) and 0x0F
        assertEquals(5, field)

        // 16℃ -> 偏移 0
        val minFrame = ElectraCodec.encode(base.copy(temperature = 16))
        val minField = (minFrame[ElectraSpec.TEMPERATURE.byteIndex].toInt() shr ElectraSpec.TEMPERATURE.bitOffset) and 0x0F
        assertEquals(0, minField)
    }

    @Test
    fun `开机位与睡眠位独立写入`() {
        val on = ElectraCodec.encode(base.copy(power = true, sleep = true))
        val powerBit = (on[ElectraSpec.POWER.byteIndex].toInt() shr ElectraSpec.POWER.bitOffset) and 1
        val sleepBit = (on[ElectraSpec.SLEEP.byteIndex].toInt() shr ElectraSpec.SLEEP.bitOffset) and 1
        assertEquals(1, powerBit)
        assertEquals(1, sleepBit)

        val off = ElectraCodec.encode(base.copy(power = false, sleep = false))
        val powerBit2 = (off[ElectraSpec.POWER.byteIndex].toInt() shr ElectraSpec.POWER.bitOffset) and 1
        assertEquals(0, powerBit2)
    }

    @Test
    fun `温度越界被夹取而不是抛异常`() {
        val tooHigh = ElectraState(temperature = 99).sanitized()
        val tooLow = ElectraState(temperature = -5).sanitized()
        assertEquals(ElectraSpec.MAX_TEMP, tooHigh.temperature)
        assertEquals(ElectraSpec.MIN_TEMP, tooLow.temperature)
    }

    @Test
    fun `脉冲序列结构合法且总时长小于两秒`() {
        val pattern = ElectraCodec.stateToPattern(base)

        assertNull(PulsePattern.validate(pattern))
        assertEquals(0, pattern.size % 2)
        assertEquals(ElectraSpec.HDR_MARK_US, pattern[0])
        assertEquals(ElectraSpec.HDR_SPACE_US, pattern[1])
        assertTrue(PulsePattern.totalDurationUs(pattern) < PulsePattern.MAX_TOTAL_DURATION_US)
    }

    @Test
    fun `不同状态产生不同脉冲序列`() {
        val a = ElectraCodec.stateToPattern(base.copy(temperature = 20))
        val b = ElectraCodec.stateToPattern(base.copy(temperature = 28))
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun `分帧长度合计等于帧位数`() {
        assertEquals(ElectraSpec.FRAME_BITS, ElectraSpec.SECTION_BIT_LENGTHS.sum())
    }
}
