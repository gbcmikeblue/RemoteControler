package com.zjianhao.aircon.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.zjianhao.aircon.R
import com.zjianhao.aircon.databinding.ItemProbeResultBinding
import com.zjianhao.aircon.probe.ProbeResult

class ProbeResultAdapter : RecyclerView.Adapter<ProbeResultAdapter.ResultViewHolder>() {

    private val items = mutableListOf<ProbeResult>()

    fun submit(list: List<ProbeResult>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ItemProbeResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ResultViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ResultViewHolder(
        private val binding: ItemProbeResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ProbeResult) {
            val context = binding.root.context
            binding.probeTitle.text = item.title
            binding.probeDetail.text = item.detail
            binding.probeBadge.text = if (item.passed) "✓" else "✗"
            binding.probeBadge.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.passed) R.color.probe_pass else R.color.probe_fail
                )
            )
        }
    }
}
