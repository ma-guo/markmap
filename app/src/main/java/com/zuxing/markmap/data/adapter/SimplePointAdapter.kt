package com.zuxing.markmap.data.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.databinding.ItemPointSimpleBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SimplePointAdapter(
    private val onItemClick: (PointEntity) -> Unit,
    private val onItemLongClick: (PointEntity) -> Unit
) : ListAdapter<PointEntity, SimplePointAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPointSimpleBinding.inflate(
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
        private val binding: ItemPointSimpleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemLongClick(getItem(position))
                    true
                } else {
                    false
                }
            }
        }

        fun bind(point: PointEntity) {
            binding.tvPointDescription.text = "${point.sortOrder}. ${point.description ?: "未命名"} (${dateFormat.format(Date(point.createTime))})"
            binding.tvDistance.text = ""
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
