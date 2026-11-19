package com.zuxing.markmap.data.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zuxing.markmap.databinding.ItemMergeSegmentBinding
import com.zuxing.markmap.domain.TransportMode
import java.util.Locale

data class MergeRow(
    val segmentId: Long,
    val name: String,
    val pointCount: Int,
    val distanceMeters: Double,
    val transportMode: String,
    val selected: Boolean,
    val reversed: Boolean
) {
    val isMergeable: Boolean get() = pointCount >= 2
}

class MergeSegmentAdapter(
    private val onCheckedChange: (MergeRow, Boolean) -> Unit,
    private val onMoveUp: (MergeRow) -> Unit,
    private val onMoveDown: (MergeRow) -> Unit,
    private val onReverse: (MergeRow) -> Unit
) : ListAdapter<MergeRow, MergeSegmentAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMergeSegmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount)
    }

    inner class ViewHolder(
        private val binding: ItemMergeSegmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: MergeRow, position: Int, total: Int) {
            binding.tvName.text = row.name
            val mode = TransportMode.from(row.transportMode)
            binding.tvMeta.text = String.format(
                Locale.getDefault(),
                "%s · %d 个地点 · %s",
                mode.label,
                row.pointCount,
                formatDistance(row.distanceMeters)
            )
            binding.cbSelect.isChecked = row.selected
            binding.cbSelect.isEnabled = row.isMergeable
            binding.root.alpha = if (row.isMergeable) 1f else 0.5f
            binding.btnReverse.isEnabled = row.selected
            binding.btnReverse.alpha = if (row.selected) 1f else 0.35f
            binding.btnUp.isEnabled = position > 0
            binding.btnUp.alpha = if (position > 0) 1f else 0.3f
            binding.btnDown.isEnabled = position < total - 1
            binding.btnDown.alpha = if (position < total - 1) 1f else 0.3f

            binding.cbSelect.setOnClickListener {
                onCheckedChange(row, binding.cbSelect.isChecked)
            }
            binding.btnUp.setOnClickListener { onMoveUp(row) }
            binding.btnDown.setOnClickListener { onMoveDown(row) }
            binding.btnReverse.setOnClickListener { onReverse(row) }
        }
    }

    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format(Locale.getDefault(), "%.1f 公里", meters / 1000)
        } else {
            String.format(Locale.getDefault(), "%.0f 米", meters)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<MergeRow>() {
        override fun areItemsTheSame(oldItem: MergeRow, newItem: MergeRow): Boolean =
            oldItem.segmentId == newItem.segmentId

        override fun areContentsTheSame(oldItem: MergeRow, newItem: MergeRow): Boolean =
            oldItem == newItem
    }
}
