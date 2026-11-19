package com.zuxing.markmap

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.zuxing.markmap.data.entity.LineEntity
import com.zuxing.markmap.databinding.ActivityTripDetailBinding
import com.zuxing.markmap.databinding.ItemTripSegmentBinding
import com.zuxing.markmap.domain.TrackStats
import com.zuxing.markmap.domain.TransportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailBinding
    private lateinit var app: MarkMapApplication
    private var groupId: Long = -1L
    private var groupName: String = ""

    private data class SegmentInfo(
        val line: LineEntity,
        val distanceMeters: Double,
        val startTime: Long?,
        val mode: TransportMode
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        groupId = intent.getLongExtra("groupId", -1L)

        binding.navBar.applyTopInsetPadding(8f)
        binding.dock.applyBottomInsetPadding()
        val behavior = BottomSheetBehavior.from(binding.detailSheet)
        behavior.peekHeight = resources.displayMetrics.heightPixels -
            (272 * resources.displayMetrics.density).toInt()
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        behavior.isHideable = false

        binding.btnBack.setOnClickListener { finish() }
        binding.btnMore.setOnClickListener { showMoreMenu(it) }
        binding.tvAllSegments.setOnClickListener { openSegmentList() }
        binding.btnShare.setOnClickListener { openShare() }
        binding.btnMerge.setOnClickListener { openMerge() }

        loadData()
    }

    private fun loadData() {
        if (groupId == -1L) {
            finish()
            return
        }
        lifecycleScope.launch {
            val group = app.repository.getGroupById(groupId)
            groupName = group?.name ?: ""
            binding.tvName.text = groupName

            val lines = app.repository.getLinesByGroupIdOnce(groupId)
            val routeCoords = mutableListOf<Pair<Double, Double>>()
            val segments = withContext(Dispatchers.IO) {
                lines.map { line ->
                    val points = app.repository.getPointsByLineIdOnce(line.id)
                    points.forEach { routeCoords.add(it.latitude to it.longitude) }
                    val stats = TrackStats.fromPoints(points)
                    SegmentInfo(
                        line = line,
                        distanceMeters = stats.distanceMeters,
                        startTime = line.startTime ?: stats.startTime,
                        mode = TransportMode.from(line.transportMode)
                    )
                }
            }

            binding.ivHero.setRoute(
                sampleRoute(routeCoords),
                ContextCompat.getColor(this@TripDetailActivity, R.color.primary),
                ContextCompat.getColor(this@TripDetailActivity, R.color.accent)
            )
            renderSummary(lines.size, segments)
            renderSegments(segments)
        }
    }

    private fun sampleRoute(coords: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (coords.size <= 400) return coords
        val step = coords.size / 400 + 1
        return coords.filterIndexed { index, _ -> index % step == 0 }
    }

    private suspend fun renderSummary(lineCount: Int, segments: List<SegmentInfo>) {
        val totalDistance = segments.sumOf { it.distanceMeters }
        val totalDuration = segments.sumOf {
            val end = it.line.endTime
            val start = it.startTime
            if (start != null && end != null && end > start) end - start else 0L
        }

        binding.tvStatDistance.text = formatDistanceValue(totalDistance)
        binding.tvStatDuration.text = formatDuration(totalDuration)

        val cities = collectCities(segments.map { it.line.id })
        binding.tvStatCount.text = if (cities.isNotEmpty()) cities.size.toString() else lineCount.toString()

        binding.tvDate.text = buildDateText(segments)

        bindChips(segments.map { it.mode })
    }

    private suspend fun collectCities(lineIds: List<Long>): List<String> {
        val cities = linkedSetOf<String>()
        lineIds.forEach { lineId ->
            app.repository.getPointsByLineIdOnce(lineId).forEach { point ->
                point.city?.takeIf { it.isNotBlank() }?.let { cities.add(it) }
            }
        }
        return cities.toList()
    }

    private fun bindChips(modes: List<TransportMode>) {
        binding.chipContainer.removeAllViews()
        val counts = modes.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }
        counts.take(2).forEachIndexed { index, entry ->
            val chip = TextView(this).apply {
                text = entry.key.label
                textSize = 11f
                setTextColor(
                    ContextCompat.getColor(
                        this@TripDetailActivity,
                        if (entry.key == TransportMode.TRAIN) R.color.accent else R.color.primary
                    )
                )
                setBackgroundResource(
                    if (entry.key == TransportMode.TRAIN) R.drawable.bg_chip_clay else R.drawable.bg_chip
                )
                val h = (6 * resources.displayMetrics.density).toInt()
                val w = (11 * resources.displayMetrics.density).toInt()
                setPadding(w, h, w, h)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) params.marginStart = (7 * resources.displayMetrics.density).toInt()
            chip.layoutParams = params
            binding.chipContainer.addView(chip)
        }
    }

    private fun renderSegments(segments: List<SegmentInfo>) {
        binding.segmentContainer.removeAllViews()
        if (segments.isEmpty()) {
            binding.tvNoSegments.visibility = View.VISIBLE
            return
        }
        binding.tvNoSegments.visibility = View.GONE
        val inflater = LayoutInflater.from(this)
        segments.forEach { info ->
            val itemBinding = ItemTripSegmentBinding.inflate(inflater, binding.segmentContainer, false)
            val mode = info.mode
            itemBinding.tvModeDot.text = modeSymbol(mode)
            val isClay = mode == TransportMode.TRAIN
            itemBinding.tvModeDot.setBackgroundResource(
                if (isClay) R.drawable.bg_mode_clay else R.drawable.bg_mode_dai
            )
            itemBinding.tvModeDot.setTextColor(
                ContextCompat.getColor(this, if (isClay) R.color.accent else R.color.primary)
            )
            itemBinding.tvTitle.text = info.line.name
            itemBinding.tvMeta.text = buildSegmentMeta(info)
            itemBinding.root.setOnClickListener {
                startActivity(Intent(this, LineMapActivity::class.java).apply {
                    putExtra("lineId", info.line.id)
                    putExtra("groupId", groupId)
                })
            }
            binding.segmentContainer.addView(itemBinding.root)
        }
    }

    private fun buildSegmentMeta(info: SegmentInfo): String {
        val distance = formatDistanceValue(info.distanceMeters) + " km"
        val time = info.startTime?.let {
            SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date(it))
        }
        return if (time != null) "$time　·　$distance" else distance
    }

    private fun buildDateText(segments: List<SegmentInfo>): String {
        val times = segments.mapNotNull { it.startTime }.filter { it > 0 }
        val range = if (times.isEmpty()) {
            ""
        } else {
            val format = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
            val start = format.format(Date(times.min()))
            val end = format.format(Date(times.max()))
            if (start == end) start else "$start – ${end.substring(5)}"
        }
        val base = "${segments.size} 段"
        return if (range.isBlank()) base else "$range　·　$base"
    }

    private fun modeSymbol(mode: TransportMode): String = when (mode) {
        TransportMode.FLIGHT -> "飞"
        TransportMode.TRAIN -> "铁"
        TransportMode.DRIVE -> "车"
        TransportMode.WALK -> "步"
        TransportMode.FERRY -> "船"
        TransportMode.OTHER -> "迹"
    }

    private fun formatDistanceValue(meters: Double): String {
        return if (meters >= 1000) {
            String.format(Locale.getDefault(), "%,.0f", meters / 1000)
        } else {
            String.format(Locale.getDefault(), "%.0f", meters)
        }
    }

    private fun formatDuration(millis: Long): String {
        if (millis <= 0) return "—"
        val hours = millis / 3_600_000
        val minutes = (millis % 3_600_000) / 60_000
        return if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
    }

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "剖面")
            menu.add(0, 2, 1, "轨迹回放")
            menu.add(0, 3, 2, "编辑行程")
            menu.add(0, 4, 3, "删除行程")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { startActivity(Intent(this@TripDetailActivity, ProfileActivity::class.java).apply { putExtra("groupId", groupId) }); true }
                    2 -> { startActivity(Intent(this@TripDetailActivity, PlaybackActivity::class.java).apply { putExtra("groupId", groupId) }); true }
                    3 -> {
                        startActivity(Intent(this@TripDetailActivity, GroupEditActivity::class.java).apply {
                            putExtra("groupId", groupId)
                        })
                        true
                    }
                    4 -> { confirmDelete(); true }
                    else -> false
                }
            }
            show()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("删除行程")
            .setMessage("确定要删除行程 \"$groupName\" 吗？删除后可在回收站恢复。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    app.repository.softDeleteGroup(groupId)
                    Toast.makeText(this@TripDetailActivity, "行程已删除", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openSegmentList() {
        startActivity(Intent(this, LineListActivity::class.java).apply {
            putExtra("groupId", groupId)
        })
    }

    private fun openShare() {
        startActivity(Intent(this, ShareActivity::class.java).apply {
            putExtra("groupId", groupId)
        })
    }

    private fun openMerge() {
        startActivity(Intent(this, MergeTrackActivity::class.java).apply {
            putExtra("groupId", groupId)
        })
    }
}
