package com.zuxing.markmap.data.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zuxing.markmap.databinding.ItemRecycleBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RecycleType(val label: String) {
    GROUP("行程"),
    LINE("路段"),
    POINT("地点")
}

data class RecycleItem(
    val type: RecycleType,
    val id: Long,
    val name: String,
    val deleteTime: Long?
)

class RecycleAdapter(
    private val onRestore: (RecycleItem) -> Unit,
    private val onPurge: (RecycleItem) -> Unit
) : ListAdapter<RecycleItem, RecycleAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecycleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemRecycleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecycleItem) {
            binding.tvName.text = item.name
            val time = item.deleteTime?.let { dateFormat.format(Date(it)) } ?: ""
            binding.tvMeta.text = if (time.isBlank()) {
                "${item.type.label}"
            } else {
                "${item.type.label} · ${time} 删除"
            }
            binding.btnRestore.setOnClickListener { onRestore(item) }
            binding.btnPurge.setOnClickListener { onPurge(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<RecycleItem>() {
        override fun areItemsTheSame(oldItem: RecycleItem, newItem: RecycleItem): Boolean =
            oldItem.type == newItem.type && oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RecycleItem, newItem: RecycleItem): Boolean =
            oldItem == newItem
    }
}
