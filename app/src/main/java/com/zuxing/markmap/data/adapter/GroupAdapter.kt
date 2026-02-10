package com.zuxing.markmap.data.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zuxing.markmap.data.entity.GroupEntity
import com.zuxing.markmap.databinding.ItemGroupBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupAdapter(
    private val onItemClick: (GroupEntity) -> Unit,
    private val onItemLongClick: (GroupEntity) -> Unit = {}
) : ListAdapter<GroupEntity, GroupAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupBinding.inflate(
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
        private val binding: ItemGroupBinding
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
                }
                true
            }
        }

        fun bind(group: GroupEntity) {
            binding.tvName.text = group.name
            binding.tvModifyTime.text = dateFormat.format(Date(group.modifyTime))
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<GroupEntity>() {
        override fun areItemsTheSame(oldItem: GroupEntity, newItem: GroupEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GroupEntity, newItem: GroupEntity): Boolean {
            return oldItem == newItem
        }
    }
}