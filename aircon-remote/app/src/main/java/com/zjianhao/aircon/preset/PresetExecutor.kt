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
 * 一键预设的指令编排与发射（需求 F2）。
 *
 * 需求 4.5 指出：空调协议下发的是状态码，因此有两种下发策略——
 * - [EmitMode.SEQUENTIAL]：按 开机 → 睡眠 → 温度 → 定时 逐条下发（默认，兼容性最好）
 * - [EmitMode.SINGLE_FRAME]：只发最终完整状态（一次成功）
 *
 * 若预设带实测波形（[Preset.rawCommands]），则完全绕开协议编码，按实测数组顺序下发。
 */
class PresetExecutor(private val transmitter: IrTransmitter) {

    /**
     * [M2 校准点] 多条指令之间的间隔。
     * 需求 5 指出"多条指令连发可能被空调忽略"，该值需实测调整。
     */
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

    private fun buildCommands(preset: Preset): List<Command> {
        if (preset.usesCalibratedRaw) {
            return preset.rawCommands.mapIndexed { index, pattern ->
                Command("实测波形 ${index + 1}", pattern)
            }
        }

        val target = preset.state.sanitized()

        if (preset.emitMode == EmitMode.SINGLE_FRAME) {
            return listOf(Command("完整状态", ElectraCodec.stateToPattern(target)))
        }

        val steps = mutableListOf<Command>()

        // 1. 开机：回到已知基线（默认 26℃，不定时、不睡眠）
        var current = target.copy(
            power = true,
            sleep = false,
            timerOffHours = 0,
            temperature = BASE_TEMPERATURE
        )
        steps += Command("开机", ElectraCodec.stateToPattern(current))

        // 2. 睡眠模式
        if (target.sleep) {
            current = current.copy(sleep = true)
            steps += Command("睡眠模式", ElectraCodec.stateToPattern(current))
        }

        // 3. 设定温度
        current = current.copy(temperature = target.temperature)
        steps += Command("温度 ${target.temperature}℃", ElectraCodec.stateToPattern(current))

        // 4. 定时关机
        if (target.timerOffHours > 0) {
            current = current.copy(timerOffHours = target.timerOffHours)
            steps += Command("定时 ${target.timerOffHours} 小时后关", ElectraCodec.stateToPattern(current))
        }

        return steps
    }

    private companion object {
        const val BASE_TEMPERATURE = 26
        const val TAG = "PresetExecutor"
    }
}
