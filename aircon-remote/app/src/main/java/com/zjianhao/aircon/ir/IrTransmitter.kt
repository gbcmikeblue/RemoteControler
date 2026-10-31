package com.zjianhao.aircon.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build

/** 红外硬件可用性（需求 F1）。 */
sealed interface IrAvailability {
    /** 可用。carrierRanges 为硬件支持的载波频率区间，单位 Hz。 */
    data class Available(val carrierRanges: List<IntRange>) : IrAvailability

    /** 系统版本低于 Android 4.4，根本没有 ConsumerIrManager。 */
    data object UnsupportedApi : IrAvailability

    /** 系统可用但本机无红外发射器（或红外服务不可达）。 */
    data object NoEmitter : IrAvailability
}

sealed interface TransmitResult {
    data object Success : TransmitResult
    data class Failure(val reason: String) : TransmitResult
}

/**
 * ConsumerIrManager 的薄封装。
 *
 * 与旧项目的 InfraredUtil 相比，这里显式暴露"是否支持红外"，
 * 并在发射前做脉冲序列校验，不再静默失败。
 */
class IrTransmitter(context: Context) {

    private val manager: ConsumerIrManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            context.applicationContext
                .getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        } else {
            null
        }

    /** 需求 F1：启动时检查 hasIrEmitter()。 */
    fun availability(): IrAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return IrAvailability.UnsupportedApi
        }
        val irManager = manager ?: return IrAvailability.NoEmitter
        if (!irManager.hasIrEmitter()) return IrAvailability.NoEmitter

        val ranges = try {
            irManager.carrierFrequencies
                ?.map { it.minFrequency..it.maxFrequency }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return IrAvailability.Available(ranges)
    }

    fun isSupported(): Boolean = availability() is IrAvailability.Available

    /**
     * 同步发射，调用方需自行切到后台线程。
     *
     * @param carrierFreqHz 载波频率，单位 Hz
     * @param pattern 交替的微秒数组
     */
    fun transmit(carrierFreqHz: Int, pattern: IntArray): TransmitResult {
        PulsePattern.validate(pattern)?.let { return TransmitResult.Failure(it) }

        if (availability() !is IrAvailability.Available) {
            return TransmitResult.Failure("本机不支持红外发射")
        }
        val irManager = manager ?: return TransmitResult.Failure("红外服务不可用")

        return try {
            irManager.transmit(carrierFreqHz, pattern)
            TransmitResult.Success
        } catch (e: Exception) {
            TransmitResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }
}
