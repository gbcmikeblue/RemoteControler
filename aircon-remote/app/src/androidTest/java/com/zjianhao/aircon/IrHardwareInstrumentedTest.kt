package com.zjianhao.aircon

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zjianhao.aircon.ir.IrAvailability
import com.zjianhao.aircon.ir.IrTransmitter
import com.zjianhao.aircon.ir.PulsePattern
import com.zjianhao.aircon.ir.TransmitResult
import com.zjianhao.aircon.ir.electra.AcMode
import com.zjianhao.aircon.ir.electra.ElectraCodec
import com.zjianhao.aircon.ir.electra.ElectraSpec
import com.zjianhao.aircon.ir.electra.ElectraState
import com.zjianhao.aircon.ir.electra.FanSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机验证红外发射链路：硬件检测 → 编码 → ConsumerIrManager.transmit。
 * 注意：只验证"HAL 接受并发射"，无法验证空调是否收到（红外单向）。
 */
@RunWith(AndroidJUnit4::class)
class IrHardwareInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val transmitter = IrTransmitter(context)

    private val sample = ElectraState(
        power = true,
        mode = AcMode.COOL,
        temperature = 26,
        fan = FanSpeed.AUTO,
        quiet = true,
        autoOffMinutes = 120
    )

    @Test
    fun 红外硬件可用且支持38kHz() {
        val availability = transmitter.availability()
        Log.i(TAG, "availability = $availability")
        assertTrue("期望 Available，实际 $availability", availability is IrAvailability.Available)

        val ranges = (availability as IrAvailability.Available).carrierRanges
        Log.i(TAG, "carrierRanges = $ranges")
        assertTrue("硬件载波范围不含 38kHz：$ranges", ranges.any { 38000 in it })
    }

    @Test
    fun Electra帧被HAL接受并发射() {
        val frame = ElectraCodec.encode(sample)
        val pattern = ElectraCodec.toPulsePattern(frame)
        Log.i(TAG, "frame = ${ElectraCodec.frameToHex(frame)}")
        Log.i(
            TAG,
            "pattern: ${pattern.size} 段, 总时长 ${PulsePattern.totalDurationUs(pattern)}us"
        )

        assertNull("本层校验应通过", PulsePattern.validate(pattern))

        val result = transmitter.transmit(ElectraSpec.CARRIER_FREQ_HZ, pattern)
        Log.i(TAG, "transmit result = $result")
        assertEquals("HAL 拒绝发射", TransmitResult.Success, result)
    }

    @Test
    fun 超过两秒的序列在本地被拒绝() {
        // 3000 段 × 1000us = 3s，必须被 PulsePattern 拦下，不会走到 HAL
        val oversized = IntArray(3000) { 1000 }
        val result = transmitter.transmit(ElectraSpec.CARRIER_FREQ_HZ, oversized)
        Log.i(TAG, "oversized transmit result = $result")
        assertTrue(result is TransmitResult.Failure)
        assertTrue((result as TransmitResult.Failure).reason.contains("2 秒"))
    }

    @Test
    fun 空序列被拒绝() {
        val result = transmitter.transmit(ElectraSpec.CARRIER_FREQ_HZ, IntArray(0))
        assertTrue(result is TransmitResult.Failure)
    }

    private companion object {
        const val TAG = "IrHardwareTest"
    }
}
