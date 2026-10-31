package com.zjianhao.aircon.ir.electra

import com.zjianhao.aircon.ir.PulsePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协议层验证。
 *
 * 关键用例「黄金帧」用的是 IRremoteESP8266 仓库中真实抓取的一帧 Electra 数据，
 * 逐字节比对，可直接证明编码器与权威实现一致。
 */
class ElectraCodecTest {

    private val base = ElectraState(
        power = true,
        mode = AcMode.COOL,
        temperature = 26,
        fan = FanSpeed.AUTO
    )

    @Test
    fun `帧长度与首字节固定`() {
        val frame = ElectraCodec.encode(base)
        assertEquals(ElectraSpec.FRAME_BYTES, frame.size)
        assertEquals(ElectraSpec.MAGIC_BYTE_0, frame[0].toInt() and 0xFF)
    }

    /**
     * 真实帧：C3 6F E0 00 A0 00 28 64 00 20 00 1E 7C
     *   byte1=0x6F → SwingV=7(关)、Temp=13 → 21℃
     *   byte2=0xE0 → SwingH=7(关)
     *   byte4=0xA0 → Fan=5(Auto)
     *   byte6=0x28 → Mode=1(制冷)、IFeel=1
     *   byte7=0x64 → 100-0x4A = 26℃
     *   byte9=0x20 → Power=1
     *   byte11=0x1E
     *   byte12=0x7C → 前 12 字节累加和
     */
    @Test
    fun `黄金帧与真实抓取数据逐字节一致`() {
        val state = ElectraState(
            power = true,
            mode = AcMode.COOL,
            temperature = 21,
            fan = FanSpeed.AUTO,
            swingVertical = false,
            swingHorizontal = false,
            iFeel = true,
            sensorTemperature = 26,
            lightToggle = 0x1E
        )
        val expected = "C3 6F E0 00 A0 00 28 64 00 20 00 1E 7C"
        assertEquals(expected, ElectraCodec.frameToHex(ElectraCodec.encode(state)))
    }

    @Test
    fun `温度按 -8 偏移写入 byte1 高 5 位`() {
        // 26℃ → 18 = 0b10010，位于 byte1 的 bit3..7
        val frame = ElectraCodec.encode(base.copy(temperature = 26))
        val tempField = (frame[1].toInt() shr 3) and 0x1F
        assertEquals(18, tempField)

        // 16℃ → 8
        val min = ElectraCodec.encode(base.copy(temperature = 16))
        assertEquals(8, (min[1].toInt() shr 3) and 0x1F)

        // 32℃ → 24
        val max = ElectraCodec.encode(base.copy(temperature = 32))
        assertEquals(24, (max[1].toInt() shr 3) and 0x1F)
    }

    @Test
    fun `温度越界被夹取`() {
        assertEquals(ElectraSpec.MAX_TEMP, ElectraState(temperature = 99).sanitized().temperature)
        assertEquals(ElectraSpec.MIN_TEMP, ElectraState(temperature = -5).sanitized().temperature)
    }

    @Test
    fun `模式与风速使用官方编码表`() {
        val cool = ElectraCodec.encode(base.copy(mode = AcMode.COOL, fan = FanSpeed.AUTO))
        assertEquals(AcMode.COOL.code, (cool[6].toInt() shr 5) and 0x07)
        assertEquals(FanSpeed.AUTO.code, (cool[4].toInt() shr 5) and 0x07)

        val heat = ElectraCodec.encode(base.copy(mode = AcMode.HEAT, fan = FanSpeed.HIGH))
        assertEquals(0b100, (heat[6].toInt() shr 5) and 0x07)
        assertEquals(0b001, (heat[4].toInt() shr 5) and 0x07)
    }

    @Test
    fun `电源位在 byte9 bit5`() {
        val on = ElectraCodec.encode(base.copy(power = true))
        assertEquals(1, (on[9].toInt() shr 5) and 1)

        val off = ElectraCodec.encode(base.copy(power = false))
        assertEquals(0, (off[9].toInt() shr 5) and 1)
    }

    @Test
    fun `校验字节是前 12 字节累加和而非异或`() {
        val frame = ElectraCodec.encode(base)
        var sum = 0
        for (i in 0 until 12) sum += frame[i].toInt() and 0xFF
        assertEquals(sum and 0xFF, frame[12].toInt() and 0xFF)
    }

    @Test
    fun `脉冲序列结构合法且总时长小于两秒`() {
        val pattern = ElectraCodec.stateToPattern(base)
        assertNull(PulsePattern.validate(pattern))
        assertEquals(0, pattern.size % 2)
        assertEquals(ElectraSpec.HDR_MARK_US, pattern[0])
        assertEquals(ElectraSpec.HDR_SPACE_US, pattern[1])
        // 1 个 header + 104 个 bit + 1 个 footer = 106 组 mark/space
        assertEquals(106 * 2, pattern.size)
        assertTrue(PulsePattern.totalDurationUs(pattern) < PulsePattern.MAX_TOTAL_DURATION_US)
    }

    @Test
    fun `不同温度产生不同序列`() {
        val a = ElectraCodec.stateToPattern(base.copy(temperature = 20))
        val b = ElectraCodec.stateToPattern(base.copy(temperature = 28))
        assertTrue(!a.contentEquals(b))
    }
}
