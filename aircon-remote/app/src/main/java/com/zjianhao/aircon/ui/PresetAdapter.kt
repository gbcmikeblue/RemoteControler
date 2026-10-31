package com.zjianhao.aircon.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zjianhao.aircon.databinding.ItemPresetBinding
import com.zjianhao.aircon.preset.Preset

class PresetAdapter(
    private val onEmit: (Preset) -> Unit,
    private val onLongPress: (Preset) -> Unit
) : RecyclerView.Adapter<PresetAdapter.PresetViewHolder>() {

    private val items = mutableListOf<Preset>()

    fun submit(list: List<Preset>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
        val binding = ItemPresetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PresetViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class PresetViewHolder(
        private val binding: ItemPresetBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(preset: Preset) {
            binding.presetName.text = preset.name
            binding.presetSummary.text = preset.state.summary()
            binding.presetRawHint.visibility =
                if (preset.usesCalibratedRaw) View.VISIBLE else View.GONE

            binding.emitButton.setOnClickListener { onEmit(preset) }
            binding.root.setOnClickListener { onEmit(preset) }
            binding.root.setOnLongClickListener {
                onLongPress(preset)
                true
            }
        }
    }
}
