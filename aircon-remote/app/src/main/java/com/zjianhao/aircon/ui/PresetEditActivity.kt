package com.zjianhao.aircon.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.zjianhao.aircon.R
import com.zjianhao.aircon.databinding.ActivityPresetEditBinding
import com.zjianhao.aircon.ir.electra.AcMode
import com.zjianhao.aircon.ir.electra.ElectraSpec
import com.zjianhao.aircon.ir.electra.ElectraState
import com.zjianhao.aircon.ir.electra.FanSpeed
import com.zjianhao.aircon.preset.Preset
import com.zjianhao.aircon.preset.PresetStore

/**
 * 预设编辑：温度、定时、模式、风速、睡眠（对应需求 F3 与「预设编辑」迭代项）。
 */
class PresetEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPresetEditBinding
    private lateinit var store: PresetStore

    private var editingId = 0L
    private var original: Preset? = null

    private val modes = AcMode.entries
    private val fans = FanSpeed.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPresetEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsHelper.fitToolbarToStatusBar(binding.toolbar)
        WindowInsetsHelper.fitBottomInset(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        store = PresetStore(this)
        editingId = intent.getLongExtra(EXTRA_ID, 0L)
        original = if (editingId > 0) store.find(editingId) else null

        setupSpinners()
        setupSliders()
        fillForm(original)
    }

    private fun setupSpinners() {
        binding.modeSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, modes.map { it.label }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.fanSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, fans.map { it.label }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun setupSliders() {
        binding.tempSeek.max = ElectraSpec.MAX_TEMP - ElectraSpec.MIN_TEMP
        binding.timerSeek.max = ElectraSpec.MAX_AUTO_OFF_HOURS

        binding.tempSeek.setOnSeekBarChangeListener(
            SimpleSeekListener { progress ->
                binding.tempLabel.text =
                    getString(R.string.temp_value, ElectraSpec.MIN_TEMP + progress)
            }
        )
        binding.timerSeek.setOnSeekBarChangeListener(
            SimpleSeekListener { progress ->
                binding.timerLabel.text = if (progress == 0) {
                    getString(R.string.timer_none)
                } else {
                    getString(R.string.timer_value, progress)
                }
            }
        )

        binding.saveButton.setOnClickListener { save() }
    }

    private fun fillForm(preset: Preset?) {
        val state = preset?.state ?: ElectraState(
            power = true,
            mode = AcMode.COOL,
            temperature = 26,
            fan = FanSpeed.AUTO,
            quiet = true,
            autoOffHours = 2
        )

        binding.nameInput.setText(preset?.name ?: getString(R.string.preset_default_name))
        binding.modeSpinner.setSelection(modes.indexOfFirst { it == state.mode }.coerceAtLeast(0))
        binding.fanSpinner.setSelection(fans.indexOfFirst { it == state.fan }.coerceAtLeast(0))
        binding.tempSeek.progress = state.temperature - ElectraSpec.MIN_TEMP
        binding.timerSeek.progress = state.autoOffHours
        binding.sleepSwitch.isChecked = state.quiet

        binding.tempLabel.text = getString(R.string.temp_value, state.temperature)
        binding.timerLabel.text = if (state.autoOffHours == 0) {
            getString(R.string.timer_none)
        } else {
            getString(R.string.timer_value, state.autoOffHours)
        }
    }

    private fun save() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            snack(getString(R.string.preset_name_required))
            return
        }

        val state = ElectraState(
            power = true,
            mode = modes[binding.modeSpinner.selectedItemPosition],
            temperature = ElectraSpec.MIN_TEMP + binding.tempSeek.progress,
            fan = fans[binding.fanSpinner.selectedItemPosition],
            quiet = binding.sleepSwitch.isChecked,
            swingVertical = original?.state?.swingVertical ?: false,
            swingHorizontal = original?.state?.swingHorizontal ?: false,
            iFeel = original?.state?.iFeel ?: false,
            lightToggle = original?.state?.lightToggle ?: ElectraSpec.LIGHT_TOGGLE_OFF,
            autoOffHours = binding.timerSeek.progress
        ).sanitized()

        val preset = Preset(
            id = editingId,
            name = name,
            state = state,
            carrierFreqHz = original?.carrierFreqHz ?: ElectraSpec.CARRIER_FREQ_HZ,
            // 编辑表单不修改实测波形，保留原值避免误丢校准数据
            rawCommands = original?.rawCommands ?: emptyList()
        )
        store.upsert(preset)
        finish()
    }

    private fun snack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private class SimpleSeekListener(
        private val onProgress: (Int) -> Unit
    ) : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
            onProgress(progress)
        }

        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit

        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
    }

    companion object {
        const val EXTRA_ID = "extra_preset_id"
    }
}
