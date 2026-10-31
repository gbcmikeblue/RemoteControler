package com.zjianhao.aircon.preset

import android.util.Log
import com.zjianhao.aircon.ir.IrTransmitter
import com.zjianhao.aircon.ir.PulsePattern
import com.zjianhao.aircon.ir.TransmitResult
import com.zjianhao.aircon.ir.electra.ElectraCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 预设执行。
 *
 * Electra 是状态码协议：一帧已包含开关/模式/温度/风速，因此默认只发一帧。
 * 只有预设带实测波形（[Preset.rawCommands]）时才按数组顺序逐条下发。
 */
class PresetExecutor(private val transmitter: IrTransmitter) {

    /** 多帧（实测波形）之间的间隔。 */
    var commandIntervalMs: Long = 300L

    private data class Command(val label: String, val pattern: IntArray)

    suspend fun execute(
        preset: Preset,
        onStep: suspend (String) -> Unit
    ): TransmitResult = withContext(Dispatchers.Default) {
        if (!transmitter.isSupported()) {
            return@withContext TransmitResult.Failure("本机不支持红外发射")
        }

        val commands = buildCommands(preset)
        if (commands.isEmpty()) {
            return@withContext TransmitResult.Failure("预设没有可发射的指令")
        }

        if (!preset.usesCalibratedRaw) {
            Log.i(TAG, "frame = ${ElectraCodec.frameToHex(ElectraCodec.encode(preset.state))}")
        }

        for ((index, command) in commands.withIndex()) {
            withContext(Dispatchers.Main) {
                onStep("正在发射 ${index + 1}/${commands.size}：${command.label}")
            }
            val result = transmitter.transmit(preset.carrierFreqHz, command.pattern)
            Log.i(
                TAG,
                "step ${index + 1}/${commands.size} [${command.label}] " +
                    "carrier=${preset.carrierFreqHz}Hz " +
                    "segments=${command.pattern.size} " +
                    "durationUs=${PulsePattern.totalDurationUs(command.pattern)} " +
                    "result=$result"
            )
            if (result is TransmitResult.Failure) return@withContext result
            if (index != commands.lastIndex) delay(commandIntervalMs)
        }
        TransmitResult.Success
    }

    /** 定时到点后发送关机状态帧（Electra 无定时码，只能本地倒计时）。 */
    suspend fun sendPowerOff(preset: Preset): TransmitResult = withContext(Dispatchers.Default) {
        val pattern = ElectraCodec.stateToPattern(
            preset.state.copy(power = false, autoOffHours = 0)
        )
        val result = transmitter.transmit(preset.carrierFreqHz, pattern)
        Log.i(TAG, "auto-off fired, result=$result")
        result
    }

    private fun buildCommands(preset: Preset): List<Command> =
        if (preset.usesCalibratedRaw) {
            preset.rawCommands.mapIndexed { index, pattern ->
                Command("实测波形 ${index + 1}", pattern)
            }
        } else {
            listOf(Command("完整状态", ElectraCodec.stateToPattern(preset.state)))
        }

    private companion object {
        const val TAG = "PresetExecutor"
    }
}
