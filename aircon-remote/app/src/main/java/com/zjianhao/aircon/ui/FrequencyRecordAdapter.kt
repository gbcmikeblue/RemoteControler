package com.zjianhao.aircon.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zjianhao.aircon.databinding.ItemFrequencyRecordBinding
import com.zjianhao.aircon.preset.IrFrequencyRecord

class FrequencyRecordAdapter(
    private val onApply: (IrFrequencyRecord) -> Unit,
    private val onLongPress: (IrFrequencyRecord) -> Unit
) : RecyclerView.Adapter<FrequencyRecordAdapter.RecordViewHolder>() {

    private val items = mutableListOf<IrFrequencyRecord>()

    fun submit(list: List<IrFrequencyRecord>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding = ItemFrequencyRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecordViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class RecordViewHolder(
        private val binding: ItemFrequencyRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: IrFrequencyRecord) {
            binding.freqLabel.text = record.khzLabel
            binding.freqTime.text = record.timeLabel
            binding.applyButton.setOnClickListener { onApply(record) }
            binding.root.setOnLongClickListener {
                onLongPress(record)
                true
            }
        }
    }
}
