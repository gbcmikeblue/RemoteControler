package com.zjianhao.aircon.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.zjianhao.aircon.BuildConfig
import com.zjianhao.aircon.R
import com.zjianhao.aircon.databinding.ActivityFrequencyTestBinding
import com.zjianhao.aircon.ir.IrAvailability
import com.zjianhao.aircon.ir.IrTransmitter
import com.zjianhao.aircon.ir.TransmitResult
import com.zjianhao.aircon.ir.electra.AcMode
import com.zjianhao.aircon.ir.electra.ElectraCodec
import com.zjianhao.aircon.ir.electra.ElectraSpec
import com.zjianhao.aircon.ir.electra.ElectraState
import com.zjianhao.aircon.ir.electra.FanSpeed
import com.zjianhao.aircon.preset.EmitMode
import com.zjianhao.aircon.preset.FrequencyRecordStore
import com.zjianhao.aircon.preset.IrFrequencyRecord
import com.zjianhao.aircon.preset.Preset
import com.zjianhao.aircon.preset.PresetExecutor
import com.zjianhao.aircon.preset.PresetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 红外频率测试与记录。
 *
 * 界面按三个环节分区：
 * ① 测试：选择载波频率 → 发射测试信号 → 弹窗确认是否成功
 * ② 成功记录：仅展示用户确认成功的频率
 * ③ 预设应用：把选中的频率固化为预设，并可直接调用发射
 */
class FrequencyTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFrequencyTestBinding
    private lateinit var transmitter: IrTransmitter
    private lateinit var executor: PresetExecutor
    private lateinit var presetStore: PresetStore
    private lateinit var recordStore: FrequencyRecordStore
    private lateinit var recordAdapter: FrequencyRecordAdapter
    private lateinit var presetAdapter: PresetAdapter

    private var selectedFreqHz = ElectraSpec.CARRIER_FREQ_HZ
    private var supported = false
    private var emittingPreset = false

    /** 测试用状态：与默认预设一致，便于对照 */
    private val testState = ElectraState(
        power = true,
        mode = AcMode.COOL,
        temperature = 26,
        fan = FanSpeed.AUTO,
        sleep = true,
        timerOffHours = 2
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFrequencyTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        transmitter = IrTransmitter(this)
        executor = PresetExecutor(transmitter)
        presetStore = PresetStore(this)
        recordStore = FrequencyRecordStore(this)

        recordAdapter = FrequencyRecordAdapter(
            onApply = ::applyAsPreset,
            onLongPress = ::confirmDeleteRecord
        )
        binding.recordList.layoutManager = LinearLayoutManager(this)
        binding.recordList.adapter = recordAdapter

        presetAdapter = PresetAdapter(
            onEmit = ::emitPreset,
            onLongPress = { /* 该页面不提供删除，避免误操作 */ }
        )
        binding.presetList.layoutManager = LinearLayoutManager(this)
        binding.presetList.adapter = presetAdapter

        binding.btnTest.setOnClickListener { runTest() }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showSection(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        buildFrequencyChips()
        checkIrHardware()
        showSection(binding.tabs.selectedTabPosition.coerceAtLeast(0))

        if (debugAuto) {
            binding.root.postDelayed({ runTest() }, 400)
        } else if (BuildConfig.DEBUG) {
            intent.getIntExtra(EXTRA_DEBUG_TAB, -1)
                .takeIf { it in 0..2 }
                ?.let { binding.tabs.getTabAt(it)?.select() }
        }
    }

    /**
     * 仅 debug 包可用：`--ez debug_auto true` 时按真实代码路径自动走完
     * 测试 → 确认成功 → 应用为预设，用于无法注入点击时做端到端自检。
     */
    private val debugAuto: Boolean
        get() = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_AUTO, false)

    /** 三个环节互斥展示，避免互相干扰。 */
    private fun showSection(position: Int) {
        binding.sectionTest.visibility = if (position == 0) View.VISIBLE else View.GONE
        binding.sectionRecords.visibility = if (position == 1) View.VISIBLE else View.GONE
        binding.sectionPresets.visibility = if (position == 2) View.VISIBLE else View.GONE
        when (position) {
            1 -> refreshRecords()
            2 -> refreshPresets()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRecords()
        refreshPresets()
    }

    // ---- ① 测试 ----

    private fun checkIrHardware() {
        val availability = transmitter.availability()
        supported = availability is IrAvailability.Available
        if (!supported) {
            binding.testStatus.setText(R.string.ir_not_supported_block)
            binding.btnTest.isEnabled = false
        }
    }

    private fun buildFrequencyChips() {
        val candidates = candidateFrequencies()
        binding.freqChipGroup.removeAllViews()

        candidates.forEach { freq ->
            val chip = Chip(this).apply {
                text = labelOf(freq)
                isCheckable = true
                isChecked = freq == selectedFreqHz
                setOnClickListener { selectFrequency(freq) }
            }
            binding.freqChipGroup.addView(chip)
        }
        updateSelectedLabel()
    }

    /** 优先使用硬件实际支持的载波，取不到时退回 38kHz。 */
    private fun candidateFrequencies(): List<Int> {
        val availability = transmitter.availability()
        val fromHardware = if (availability is IrAvailability.Available) {
            availability.carrierRanges.map { it.first }.filter { it > 0 }
        } else {
            emptyList()
        }
        val merged = (fromHardware + ElectraSpec.CARRIER_FREQ_HZ).distinct().sorted()
        return if (merged.isEmpty()) listOf(ElectraSpec.CARRIER_FREQ_HZ) else merged
    }

    private fun selectFrequency(freq: Int) {
        selectedFreqHz = freq
        updateSelectedLabel()
    }

    private fun updateSelectedLabel() {
        binding.selectedFreq.text =
            getString(R.string.freq_selected, labelOf(selectedFreqHz))
    }

    private fun labelOf(freqHz: Int): String =
        if (freqHz % 1000 == 0) "${freqHz / 1000}kHz" else "${freqHz / 1000.0}kHz"

    private fun runTest() {
        if (!supported) {
            snack(getString(R.string.ir_not_supported_block))
            return
        }
        binding.btnTest.isEnabled = false
        binding.testStatus.text = getString(R.string.test_emitting, labelOf(selectedFreqHz))

        lifecycleScope.launch {
            val pattern = ElectraCodec.stateToPattern(testState)
            val result = withContext(Dispatchers.Default) {
                transmitter.transmit(selectedFreqHz, pattern)
            }
            binding.btnTest.isEnabled = true

            when (result) {
                TransmitResult.Success -> {
                    binding.testStatus.text = getString(R.string.test_emitted, labelOf(selectedFreqHz))
                    askTestResult()
                }

                is TransmitResult.Failure -> {
                    binding.testStatus.text = getString(R.string.emit_failed, result.reason)
                    snack(getString(R.string.emit_failed, result.reason))
                }
            }
        }
    }

    /** 每次测试完成后必须询问结果；只有确认成功才落库。 */
    private fun askTestResult() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.test_result_title)
            .setMessage(getString(R.string.test_result_message, labelOf(selectedFreqHz)))
            .setPositiveButton(R.string.test_result_success) { _, _ -> saveSuccess() }
            .setNegativeButton(R.string.test_result_failure) { d, _ ->
                d.dismiss()
                binding.testStatus.setText(R.string.test_result_discarded)
                snack(getString(R.string.test_record_discarded))
            }
            .setNeutralButton(R.string.cancel, null)
            .setCancelable(false)
            .create()

        dialog.show()

        if (debugAuto) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.postDelayed({
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
            }, 400)
        }
    }

    private fun saveSuccess() {
        val record = recordStore.append(selectedFreqHz)
        refreshRecords()
        binding.testStatus.text = getString(R.string.test_record_saved, record.khzLabel)
        snack(getString(R.string.test_record_saved, record.khzLabel))

        if (debugAuto) {
            binding.tabs.getTabAt(1)?.select()
            binding.root.postDelayed({
                recordStore.load().firstOrNull()?.let { applyAsPreset(it) }
                binding.tabs.getTabAt(2)?.select()
            }, 400)
        }
    }

    // ---- ② 成功记录 ----

    private fun refreshRecords() {
        val records = recordStore.load()
        recordAdapter.submit(records)
        binding.recordsEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDeleteRecord(record: IrFrequencyRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.record_delete_title)
            .setMessage(getString(R.string.record_delete_message, record.khzLabel))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                recordStore.delete(record.id)
                refreshRecords()
            }
            .show()
    }

    // ---- ③ 预设应用 ----

    private fun applyAsPreset(record: IrFrequencyRecord) {
        val preset = Preset(
            name = record.presetName,
            state = testState,
            emitMode = EmitMode.SEQUENTIAL,
            carrierFreqHz = record.freqHz
        )
        val saved = presetStore.upsert(preset)
        refreshPresets()
        snack(getString(R.string.preset_created, saved.name))
    }

    private fun refreshPresets() {
        val presets = presetStore.load()
        presetAdapter.submit(presets)
        binding.presetsEmpty.visibility = if (presets.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun emitPreset(preset: Preset) {
        if (!supported) {
            snack(getString(R.string.ir_not_supported_block))
            return
        }
        if (emittingPreset) {
            snack(getString(R.string.emit_busy))
            return
        }
        emittingPreset = true
        snack(getString(R.string.emit_starting, preset.name))

        lifecycleScope.launch {
            val result = executor.execute(preset) { }
            snack(
                when (result) {
                    TransmitResult.Success -> getString(R.string.emit_done)
                    is TransmitResult.Failure -> getString(R.string.emit_failed, result.reason)
                }
            )
            emittingPreset = false
        }
    }

    private fun snack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_DEBUG_AUTO = "debug_auto"
        const val EXTRA_DEBUG_TAB = "debug_tab"
    }
}
