package com.zuxing.markmap.data.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zuxing.markmap.data.entity.LineEntity
import com.zuxing.markmap.databinding.ItemLineBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LineAdapter(
    private val onItemClick: (LineEntity) -> Unit
) : ListAdapter<LineEntity, LineAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLineBinding.inflate(
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
        private val binding: ItemLineBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(line: LineEntity) {
            binding.tvName.text = line.name
            binding.tvCreateTime.text = dateFormat.format(Date(line.createTime))
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<LineEntity>() {
        override fun areItemsTheSame(oldItem: LineEntity, newItem: LineEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LineEntity, newItem: LineEntity): Boolean {
            return oldItem == newItem
        }
    }
}