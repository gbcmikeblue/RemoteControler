package com.zjianhao.aircon.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zjianhao.aircon.BuildConfig
import com.zjianhao.aircon.R
import com.zjianhao.aircon.databinding.ActivityMainBinding
import com.zjianhao.aircon.ir.IrAvailability
import com.zjianhao.aircon.ir.IrTransmitter
import com.zjianhao.aircon.ir.TransmitResult
import com.zjianhao.aircon.preset.Preset
import com.zjianhao.aircon.preset.PresetExecutor
import com.zjianhao.aircon.preset.PresetStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var transmitter: IrTransmitter
    private lateinit var store: PresetStore
    private lateinit var executor: PresetExecutor
    private lateinit var adapter: PresetAdapter

    private var emitting = false
    private var supported = false
    private var autoOffJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsHelper.fitToolbarToStatusBar(binding.toolbar)
        WindowInsetsHelper.fitBottomInset(binding.root)

        transmitter = IrTransmitter(this)
        store = PresetStore(this)
        executor = PresetExecutor(transmitter)

        adapter = PresetAdapter(onEmit = ::emitPreset, onLongPress = ::confirmDelete)
        binding.presetList.layoutManager = LinearLayoutManager(this)
        binding.presetList.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, PresetEditActivity::class.java))
        }

        binding.btnFrequencyTest.setOnClickListener {
            startActivity(Intent(this, FrequencyTestActivity::class.java))
        }
        binding.btnConfigCheck.setOnClickListener {
            startActivity(Intent(this, RemoteConfigCheckActivity::class.java))
        }

        checkIrHardware()
        handleDebugNavigation()
        handleDebugEmit()
    }

    /** 仅 debug 包可用：`am start ... --es debug_open frequency_test` 直接跳转频率测试页。 */
    private fun handleDebugNavigation() {
        if (!BuildConfig.DEBUG) return
        if (intent.getStringExtra(EXTRA_DEBUG_OPEN) == "config_check") {
            startActivity(Intent(this, RemoteConfigCheckActivity::class.java))
            return
        }
        if (intent.getStringExtra(EXTRA_DEBUG_OPEN) == "frequency_test") {
            startActivity(
                Intent(this, FrequencyTestActivity::class.java)
                    .putExtra(
                        FrequencyTestActivity.EXTRA_DEBUG_AUTO,
                        intent.getBooleanExtra("debug_auto", false)
                    )
                    .putExtra(
                        FrequencyTestActivity.EXTRA_DEBUG_TAB,
                        intent.getIntExtra("debug_tab", -1)
                    )
            )
        }
    }

    /**
     * 仅 debug 包可用：通过 `am start ... --el debug_emit_preset_id <id>` 直接触发一次发射。
     * 用于真机自动化验证（HyperOS 会拦截 adb input 注入，无法点击）。
     */
    private fun handleDebugEmit() {
        if (!BuildConfig.DEBUG) return
        val presetId = intent.getLongExtra(EXTRA_DEBUG_EMIT_PRESET, -1L)
        if (presetId <= 0) return

        val preset = store.find(presetId)
        if (preset == null) {
            Log.w(TAG, "debug emit: preset $presetId 不存在")
            return
        }
        Log.i(TAG, "debug emit: 触发预设 ${preset.name}")
        binding.root.post { emitPreset(preset) }
    }

    override fun onResume() {
        super.onResume()
        refreshPresets()
    }

    /** 需求 F1：启动时检查红外发射器，无则明确提示不支持。 */
    private fun checkIrHardware() {
        when (val availability = transmitter.availability()) {
            is IrAvailability.Available -> {
                supported = true
                binding.irStatusTitle.setText(R.string.ir_ready)
                val ranges = availability.carrierRanges
                    .joinToString("、") { "${it.first / 1000}~${it.last / 1000}kHz" }
                binding.irStatusDetail.text = if (ranges.isEmpty()) {
                    getString(R.string.ir_ready_detail_no_range)
                } else {
                    getString(R.string.ir_ready_detail, ranges)
                }
                binding.fabAdd.isEnabled = true
            }

            IrAvailability.UnsupportedApi -> {
                supported = false
                binding.irStatusTitle.setText(R.string.ir_unsupported_api)
                binding.irStatusDetail.setText(R.string.ir_unsupported_api_detail)
                binding.fabAdd.isEnabled = false
            }

            IrAvailability.NoEmitter -> {
                supported = false
                binding.irStatusTitle.setText(R.string.ir_no_emitter)
                binding.irStatusDetail.setText(R.string.ir_no_emitter_detail)
                binding.fabAdd.isEnabled = false
            }
        }
    }

    private fun refreshPresets() {
        runCatching { store.load() }
            .onSuccess { adapter.submit(it) }
            .onFailure { snack(getString(R.string.preset_load_failed, it.message ?: "")) }
    }

    /** 需求 F2：一键发射。 */
    private fun emitPreset(preset: Preset) {
        if (!supported) {
            snack(getString(R.string.ir_not_supported_block))
            return
        }
        if (emitting) {
            snack(getString(R.string.emit_busy))
            return
        }
        emitting = true
        binding.emitStatus.visibility = View.VISIBLE
        binding.emitStatus.text = getString(R.string.emit_starting, preset.name)

        lifecycleScope.launch {
            val result = executor.execute(preset) { step ->
                binding.emitStatus.text = step
            }
            binding.emitStatus.text = when (result) {
                TransmitResult.Success -> getString(R.string.emit_done)
                is TransmitResult.Failure -> getString(R.string.emit_failed, result.reason)
            }
            snack(binding.emitStatus.text.toString())
            emitting = false

            if (result == TransmitResult.Success && preset.state.autoOffHours > 0) {
                scheduleAutoOff(preset)
            }
        }
    }

    /**
     * Electra 协议没有定时关机码，只能由 App 倒计时到点再发一帧关机状态。
     * 注意：倒计时跟随界面生命周期，退出 App 后会失效。
     */
    private fun scheduleAutoOff(preset: Preset) {
        val hours = preset.state.autoOffHours
        autoOffJob?.cancel()
        binding.emitStatus.text = getString(R.string.auto_off_scheduled, hours)
        snack(getString(R.string.auto_off_scheduled, hours))

        autoOffJob = lifecycleScope.launch {
            delay(hours * 60L * 60L * 1000L)
            val result = executor.sendPowerOff(preset)
            binding.emitStatus.text = when (result) {
                TransmitResult.Success -> getString(R.string.auto_off_fired, hours)
                is TransmitResult.Failure -> getString(R.string.emit_failed, result.reason)
            }
            snack(binding.emitStatus.text.toString())
        }
    }

    private fun confirmDelete(preset: Preset) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.preset_delete_title)
            .setMessage(getString(R.string.preset_delete_message, preset.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.delete(preset.id)
                refreshPresets()
            }
            .show()
    }

    private fun snack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private companion object {
        const val TAG = "MainActivity"
        const val EXTRA_DEBUG_EMIT_PRESET = "debug_emit_preset_id"
        const val EXTRA_DEBUG_OPEN = "debug_open"
    }
}
