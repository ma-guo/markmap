package com.zuxing.markmap.data.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zuxing.markmap.R
import com.zuxing.markmap.databinding.ItemGroupBinding
import com.zuxing.markmap.domain.TransportMode
import java.util.Locale

data class ModeCount(val mode: TransportMode, val count: Int)

data class TripCardItem(
    val groupId: Long,
    val name: String,
    val distanceMeters: Double,
    val segmentCount: Int,
    val durationMillis: Long,
    val dateText: String,
    val modes: List<ModeCount>,
    val mossVariant: Boolean,
    val route: List<Pair<Double, Double>>
)

class GroupAdapter(
    private val onItemClick: (TripCardItem) -> Unit,
    private val onItemLongClick: (TripCardItem) -> Unit = {}
) : ListAdapter<TripCardItem, GroupAdapter.ViewHolder>(DiffCallback()) {

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

        fun bind(item: TripCardItem) {
            binding.tvName.text = item.name
            val routeColor = ContextCompat.getColor(
                binding.root.context,
                if (item.mossVariant) R.color.moss else R.color.primary
            )
            binding.ivThumb.setRoute(
                item.route,
                routeColor,
                ContextCompat.getColor(binding.root.context, R.color.accent)
            )
            binding.tvDistance.text = formatDistance(item.distanceMeters)
            binding.tvDistance.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (item.mossVariant) R.color.moss else R.color.primary
                )
            )

            val meta = buildString {
                if (item.dateText.isNotBlank()) {
                    append(item.dateText)
                    append("　·　")
                }
                append("${item.segmentCount} 段")
                val duration = formatDuration(item.durationMillis)
                if (duration.isNotBlank()) {
                    append("　·　")
                    append(duration)
                }
            }
            binding.tvMeta.text = meta

            bindChips(binding.root.context, binding.chipContainer, item.modes)
        }
    }

    private fun bindChips(context: Context, container: LinearLayout, modes: List<ModeCount>) {
        container.removeAllViews()
        if (modes.isEmpty()) return
        modes.take(3).forEachIndexed { index, modeCount ->
            val chip = TextView(context).apply {
                text = buildChipText(modeCount)
                textSize = 11f
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (modeCount.mode == TransportMode.TRAIN) R.color.accent else R.color.primary
                    )
                )
                setBackgroundResource(
                    if (modeCount.mode == TransportMode.TRAIN) R.drawable.bg_chip_clay
                    else R.drawable.bg_chip
                )
                val h = (6 * context.resources.displayMetrics.density).toInt()
                val w = (11 * context.resources.displayMetrics.density).toInt()
                setPadding(w, h, w, h)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) params.marginStart = (7 * context.resources.displayMetrics.density).toInt()
            chip.layoutParams = params
            container.addView(chip)
        }
    }

    private fun buildChipText(modeCount: ModeCount): String {
        val symbol = when (modeCount.mode) {
            TransportMode.FLIGHT -> "✈ "
            TransportMode.TRAIN -> "铁 "
            TransportMode.DRIVE -> "车 "
            TransportMode.WALK -> "步 "
            TransportMode.FERRY -> "船 "
            TransportMode.OTHER -> ""
        }
        val suffix = if (modeCount.count > 1) " ×${modeCount.count}" else ""
        return "$symbol${modeCount.mode.label}$suffix"
    }

    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format(Locale.getDefault(), "%,.0f km", meters / 1000)
        } else {
            String.format(Locale.getDefault(), "%.0f m", meters)
        }
    }

    private fun formatDuration(millis: Long): String {
        if (millis <= 0) return ""
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours} 小时 ${minutes} 分" else "${minutes} 分"
    }

    private class DiffCallback : DiffUtil.ItemCallback<TripCardItem>() {
        override fun areItemsTheSame(oldItem: TripCardItem, newItem: TripCardItem): Boolean =
            oldItem.groupId == newItem.groupId

        override fun areContentsTheSame(oldItem: TripCardItem, newItem: TripCardItem): Boolean =
            oldItem == newItem
    }
}
