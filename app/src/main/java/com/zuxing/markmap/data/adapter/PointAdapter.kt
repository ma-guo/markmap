package com.zuxing.markmap.data.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.databinding.ItemPointBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PointAdapter(
    private val onItemClick: (PointEntity) -> Unit
) : ListAdapter<PointEntity, PointAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPointBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPointBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(point: PointEntity) {
            binding.tvName.text = point.description ?: "未命名点"
            binding.tvAddress.text = point.address ?: ""
            binding.tvCoords.text = "经度: ${String.format("%.6f", point.longitude)}, 纬度: ${String.format("%.6f", point.latitude)}"
            binding.tvCreateTime.text = dateFormat.format(Date(point.createTime))
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<PointEntity>() {
        override fun areItemsTheSame(oldItem: PointEntity, newItem: PointEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PointEntity, newItem: PointEntity): Boolean {
            return oldItem == newItem
        }
    }
}