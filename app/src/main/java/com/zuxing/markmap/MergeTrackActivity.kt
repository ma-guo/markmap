package com.zuxing.markmap

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.baidu.mapapi.map.CircleOptions
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.map.Stroke
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.zuxing.markmap.data.adapter.MergeRow
import com.zuxing.markmap.data.adapter.MergeSegmentAdapter
import com.zuxing.markmap.data.entity.MergedTrackPlanEntity
import com.zuxing.markmap.databinding.ActivityMergeTrackBinding
import com.zuxing.markmap.domain.MergeConfig
import com.zuxing.markmap.domain.ShareCardGenerator
import com.zuxing.markmap.domain.TrackMerger
import com.zuxing.markmap.domain.TrackStats
import com.zuxing.markmap.domain.TransportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MergeTrackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMergeTrackBinding
    private lateinit var app: MarkMapApplication
    private lateinit var mapView: MapView
    private lateinit var adapter: MergeSegmentAdapter

    private var groupId: Long = -1L
    private var planId: Long = -1L
    private var groupName: String = ""
    private var existingPlan: MergedTrackPlanEntity? = null
    private var lastResult: TrackMerger.Result? = null

    private val inputsById = mutableMapOf<Long, TrackMerger.SegmentInput>()
    private val rows = mutableListOf<MergeRow>()
    private val originalRows = mutableListOf<MergeRow>()
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!(application as MarkMapApplication).baiduReady) {
            android.widget.Toast.makeText(
                this,
                "地图组件不可用：百度地图 SDK 在当前设备初始化失败",
                android.widget.Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        binding = ActivityMergeTrackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        groupId = intent.getLongExtra("groupId", -1L)
        planId = intent.getLongExtra("planId", -1L)
        mapView = binding.mapView

        setupToolbar()
        setupRecyclerView()
        setupActions()
        loadData()
    }

    private fun setupToolbar() {
        binding.navBar.applyTopInsetPadding(8f)
        binding.dock.applyBottomInsetPadding()
        binding.btnBack.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener { shareCard() }
    }

    private fun setupRecyclerView() {
        adapter = MergeSegmentAdapter(
            onCheckedChange = { row, checked ->
                updateRow(row.segmentId) { it.copy(selected = checked) }
                render()
            },
            onMoveUp = { row -> moveRow(row.segmentId, -1) },
            onMoveDown = { row -> moveRow(row.segmentId, 1) },
            onReverse = { row ->
                updateRow(row.segmentId) { it.copy(reversed = !it.reversed) }
                render()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupActions() {
        binding.switchConnector.setOnCheckedChangeListener { _, _ ->
            if (ready) render()
        }
        binding.tvReset.setOnClickListener { resetToOriginal() }
        binding.tvSelectAll.setOnClickListener { toggleSelectAll() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnGenerate.setOnClickListener { showSaveDialog() }
    }

    private fun loadData() {
        if (groupId == -1L) {
            Toast.makeText(this, "缺少行程信息", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        lifecycleScope.launch {
            val group = app.repository.getGroupById(groupId)
            groupName = group?.name ?: ""
            binding.tvTitle.text = if (groupName.isBlank()) "合并轨迹" else groupName

            val lines = app.repository.getLinesByGroupIdOnce(groupId)
            val loadedRows = lines.map { line ->
                val points = app.repository.getPointsByLineIdOnce(line.id)
                val distance = TrackStats.fromPoints(points).distanceMeters
                inputsById[line.id] = TrackMerger.SegmentInput(
                    id = line.id,
                    name = line.name,
                    transportMode = line.transportMode,
                    points = points
                )
                MergeRow(
                    segmentId = line.id,
                    name = line.name,
                    pointCount = points.size,
                    distanceMeters = distance,
                    transportMode = line.transportMode,
                    selected = points.size >= 2,
                    reversed = false
                )
            }
            rows.clear()
            rows.addAll(loadedRows)
            originalRows.clear()
            originalRows.addAll(loadedRows)

            val plan = if (planId != -1L) {
                app.repository.getMergedPlanById(planId)
            } else {
                app.repository.getMergedPlansByGroupId(groupId).first().firstOrNull()
            }
            if (plan != null) {
                existingPlan = plan
                applyConfig(MergeConfig.fromJson(plan.configJson))
            }

            ready = true
            submitRows()
            render()
        }
    }

    private fun applyConfig(config: MergeConfig?) {
        if (config == null) return
        binding.switchConnector.isChecked = config.includeConnectors
        val byId = rows.associateBy { it.segmentId }
        val configIds = config.segments.map { it.segmentId }.toSet()
        val reordered = config.segments.mapNotNull { seg ->
            byId[seg.segmentId]?.copy(selected = true, reversed = seg.reversed)
        }.toMutableList()
        rows.filter { it.segmentId !in configIds }.forEach { reordered.add(it.copy(selected = false)) }
        rows.clear()
        rows.addAll(reordered)
    }

    private fun toggleSelectAll() {
        val allSelected = rows.filter { it.isMergeable }.all { it.selected }
        rows.replaceAll { row ->
            if (row.isMergeable) row.copy(selected = !allSelected) else row
        }
        submitRows()
        render()
    }

    private fun resetToOriginal() {
        rows.clear()
        rows.addAll(originalRows.map { it.copy(selected = it.isMergeable, reversed = false) })
        binding.switchConnector.isChecked = true
        submitRows()
        render()
    }

    private fun updateRow(segmentId: Long, block: (MergeRow) -> MergeRow) {
        val index = rows.indexOfFirst { it.segmentId == segmentId }
        if (index == -1) return
        rows[index] = block(rows[index])
    }

    private fun moveRow(segmentId: Long, delta: Int) {
        val index = rows.indexOfFirst { it.segmentId == segmentId }
        val target = index + delta
        if (index == -1 || target !in rows.indices) return
        val row = rows.removeAt(index)
        rows.add(target, row)
        submitRows()
        render()
    }

    private fun submitRows() {
        adapter.submitList(rows.toList())
    }

    private fun currentInputs(): List<TrackMerger.SegmentInput> {
        return rows
            .filter { it.selected && it.isMergeable }
            .mapNotNull { row ->
                inputsById[row.segmentId]?.copy(reversed = row.reversed)
            }
    }

    private fun render() {
        if (!ready) return
        val result = TrackMerger.merge(currentInputs(), binding.switchConnector.isChecked)
        lastResult = result
        val selectedCount = rows.count { it.selected && it.isMergeable }

        if (selectedCount == 0) {
            binding.tvSummary.text = "尚未选择路段，勾选参与合并的路段。"
        } else {
            val connectorHint = if (binding.switchConnector.isChecked) "端点为虚线" else "不显示连接线"
            val skippedHint = if (result.skippedSegmentIds.isNotEmpty()) " · ${result.skippedSegmentIds.size} 段已跳过" else ""
            binding.tvSummary.text = String.format(
                Locale.getDefault(),
                "已选 %d 段 · %s · %s%s",
                selectedCount,
                formatDistance(result.totalDistanceMeters),
                connectorHint,
                skippedHint
            )
        }

        renderMap(result)
    }

    private fun renderMap(result: TrackMerger.Result) {
        val map = mapView.map
        map.clear()
        binding.tvMapEmpty.visibility = if (result.points.isEmpty()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        if (result.points.isEmpty()) return

        result.ranges.forEach { range ->
            val latLngs = result.points.subList(range.startIndex, range.endIndex + 1)
                .map { LatLng(it.latitude, it.longitude) }
            if (latLngs.size < 2) return@forEach
            val color = Color.parseColor(TransportMode.from(range.transportMode).colorHex)
            map.addOverlay(
                PolylineOptions()
                    .points(latLngs)
                    .width(10)
                    .color(color)
            )
        }

        result.connectors.forEach { connector ->
            map.addOverlay(
                PolylineOptions()
                    .points(
                        listOf(
                            LatLng(connector.from.latitude, connector.from.longitude),
                            LatLng(connector.to.latitude, connector.to.longitude)
                        )
                    )
                    .width(7)
                    .color(Color.parseColor("#B96A4B"))
                    .dottedLine(true)
            )
        }

        val first = result.points.first()
        val last = result.points.last()
        map.addOverlay(
            CircleOptions()
                .center(LatLng(first.latitude, first.longitude))
                .radius(80)
                .fillColor(Color.parseColor("#2E5D5C"))
                .stroke(Stroke(3, Color.WHITE))
        )
        map.addOverlay(
            CircleOptions()
                .center(LatLng(last.latitude, last.longitude))
                .radius(80)
                .fillColor(Color.parseColor("#B96A4B"))
                .stroke(Stroke(3, Color.WHITE))
        )

        if (result.points.size >= 2) {
            val builder = LatLngBounds.Builder()
            result.points.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
            map.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(builder.build(), 60, 60))
        } else {
            map.animateMapStatus(
                MapStatusUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 14f)
            )
        }
    }

    private fun showSaveDialog() {
        if (rows.none { it.selected && it.isMergeable }) {
            Toast.makeText(this, "请先选择至少一个路段", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(existingPlan?.name ?: buildDefaultName())
            setSelection(text.length)
            setPadding(48, 24, 48, 8)
        }
        AlertDialog.Builder(this)
            .setTitle("保存合并方案")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "请输入方案名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                savePlan(name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun buildDefaultName(): String {
        val base = groupName.ifBlank { "合并轨迹" }
        return "$base · 合并方案"
    }

    private fun savePlan(name: String) {
        val config = MergeConfig(
            segments = rows
                .filter { it.selected && it.isMergeable }
                .map { MergeConfig.MergeSegmentConfig(it.segmentId, it.reversed) },
            includeConnectors = binding.switchConnector.isChecked,
            showSegmentColors = true
        )
        val now = System.currentTimeMillis()
        val plan = existingPlan?.copy(
            name = name,
            configJson = config.toJson(),
            modifyTime = now
        ) ?: MergedTrackPlanEntity(
            groupId = groupId,
            name = name,
            configJson = config.toJson(),
            createTime = now,
            modifyTime = now
        )
        lifecycleScope.launch {
            val id = app.repository.saveMergedPlan(plan)
            existingPlan = plan.copy(id = if (plan.id == 0L) id else plan.id)
            Toast.makeText(this@MergeTrackActivity, "合并轨迹已生成", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun shareCard() {
        val result = lastResult
        if (result == null || result.points.isEmpty()) {
            Toast.makeText(this, "暂无可分享的轨迹", Toast.LENGTH_SHORT).show()
            return
        }
        val title = when {
            !existingPlan?.name.isNullOrBlank() -> existingPlan!!.name
            groupName.isNotBlank() -> groupName
            else -> "我的行程"
        }
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                ShareCardGenerator.generate(title, result)
            }
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "markmap_${System.currentTimeMillis()}.png")
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            val uri = FileProvider.getUriForFile(
                this@MergeTrackActivity,
                "$packageName.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享行程卡"))
        }
    }

    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format(Locale.getDefault(), "%.1f 公里", meters / 1000)
        } else {
            String.format(Locale.getDefault(), "%.0f 米", meters)
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::mapView.isInitialized) mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (this::mapView.isInitialized) mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::mapView.isInitialized) mapView.onDestroy()
    }
}
