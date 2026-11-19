package com.zuxing.markmap

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.widget.PopupMenu
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Overlay
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.databinding.ActivityLineMapBinding
import com.zuxing.markmap.databinding.ItemPointSimpleBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LineMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLineMapBinding
    private lateinit var mapView: MapView
    private lateinit var app: MarkMapApplication
    private var mapPointList: List<MapPointEntity> = emptyList()
    private var markerList: List<Overlay> = emptyList()
    private var polylineOverlay: Overlay? = null
    private var showMarkers = true
    private var showInfo = true
    private var currentLineColor = Color.parseColor("#2E5D5C")
    private var selectedPointId: Long? = null
    private var lineId: Long = -1L
    private var groupId: Long = -1L

    data class MapPointEntity(
        val lineSort: Int,
        val point: PointEntity,
        val pointSort: Int,
        val distanceFromStart: Double = 0.0
    )

    data class ColorData(val name: String, val color: Int)

    private val lineColors = listOf(
        ColorData("黛青", Color.parseColor("#2E5D5C")),
        ColorData("陶土", Color.parseColor("#B96A4B")),
        ColorData("苔绿", Color.parseColor("#70897A")),
        ColorData("墨灰", Color.parseColor("#5F6F6D")),
        ColorData("雾蓝", Color.parseColor("#7E9AA1")),
        ColorData("金色", Color.parseColor("#BE9F66"))
    )

    private class PointAdapter(
        private val onPointClick: (PointEntity) -> Unit
    ) : ListAdapter<MapPointEntity, PointAdapter.PointViewHolder>(PointDiffCallback()) {

        private var selectedPointId: Long? = null

        fun setSelectedPoint(pointId: Long?) {
            val oldId = selectedPointId
            selectedPointId = pointId
            currentList.forEachIndexed { index, mapPoint ->
                if (mapPoint.point.id == oldId || mapPoint.point.id == pointId) {
                    notifyItemChanged(index)

                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PointViewHolder {
            val binding = ItemPointSimpleBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return PointViewHolder(binding)
        }

        override fun onBindViewHolder(holder: PointViewHolder, position: Int) {
            holder.bind(getItem(position), position, itemCount)
        }

        inner class PointViewHolder(
            private val binding: ItemPointSimpleBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(mapPoint: MapPointEntity, position: Int, total: Int) {
                val point = mapPoint.point
                binding.tvPointDescription.text = when {
                    !point.description.isNullOrBlank() -> point.description
                    !point.address.isNullOrBlank() -> point.address
                    else -> "${point.latitude}, ${point.longitude}"
                }
                binding.tvDistance.text = formatDistance(mapPoint.distanceFromStart)
                binding.tvStartTime.text = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault())
                    .format(Date(point.createTime))
                binding.node.setBackgroundResource(
                    when {
                        position == 0 -> R.drawable.bg_node_start
                        position == total - 1 -> R.drawable.bg_node_end
                        else -> R.drawable.bg_node_mid
                    }
                )
                binding.root.isSelected = point.id == selectedPointId
                binding.root.setOnClickListener {
                    selectedPointId = point.id
                    notifyDataSetChanged()
                    onPointClick(point)
                }
            }

            private fun formatDistance(distanceMeters: Double): String {
                return if (distanceMeters < 1000) {
                    "${distanceMeters.toInt()}m"
                } else {
                    String.format("%.1fkm", distanceMeters / 1000)
                }
            }
        }

        private class PointDiffCallback : DiffUtil.ItemCallback<MapPointEntity>() {
            override fun areItemsTheSame(oldItem: MapPointEntity, newItem: MapPointEntity): Boolean {
                return oldItem.point.id == newItem.point.id
            }

            override fun areContentsTheSame(oldItem: MapPointEntity, newItem: MapPointEntity): Boolean {
                return oldItem == newItem
            }
        }
    }

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
        binding = ActivityLineMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lineId = intent.getLongExtra("lineId", -1L)
        groupId = intent.getLongExtra("groupId", -1L)
        app = application as MarkMapApplication

        setupToolbar()
        setupMap()
        setupPointList()
        setupSheet()
        loadPoints()
    }

    private fun setupToolbar() {
        binding.navBar.applyTopInsetPadding(8f)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnMore.setOnClickListener { showOverflow(it) }
        binding.btnRecenter.setOnClickListener { recenter() }
    }

    private fun showOverflow(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "回到全览")
            menu.add(0, 2, 1, "剖面")
            menu.add(0, 3, 2, "轨迹回放")
            menu.add(0, 4, 3, if (showMarkers) "隐藏点标记" else "显示点标记")
            menu.add(0, 5, 4, if (showInfo) "隐藏点信息" else "显示点信息")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { recenter(); true }
                    2 -> {
                        startActivity(Intent(this@LineMapActivity, ProfileActivity::class.java).apply {
                            if (lineId != -1L) putExtra("lineId", lineId) else putExtra("groupId", groupId)
                        }); true
                    }
                    3 -> {
                        startActivity(Intent(this@LineMapActivity, PlaybackActivity::class.java).apply {
                            if (lineId != -1L) putExtra("lineId", lineId) else putExtra("groupId", groupId)
                        }); true
                    }
                    4 -> { showMarkers = !showMarkers; updateToggleStyles(); updateMapDisplay(); true }
                    5 -> { showInfo = !showInfo; updateToggleStyles(); updateMarkers(); true }
                    else -> false
                }
            }
            show()
        }
    }

    private fun setupMap() {
        mapView = binding.mapView
    }

    private fun setupPointList() {
        binding.rvPoints.layoutManager = LinearLayoutManager(this)
        binding.rvPoints.adapter = PointAdapter { point ->
            (binding.rvPoints.adapter as? PointAdapter)?.setSelectedPoint(point.id)
            centerOnPoint(point)
        }
    }

    private fun centerOnPoint(point: PointEntity) {
        val latLng = LatLng(point.latitude, point.longitude)
        selectedPointId = point.id
        mapView.map.animateMapStatus(MapStatusUpdateFactory.newLatLng(latLng))
        updateMarkers()
    }

    private fun setupSheet() {
        binding.tvTglMarkers.setOnClickListener {
            showMarkers = !showMarkers
            updateToggleStyles()
            updateMapDisplay()
        }
        binding.tvTglInfo.setOnClickListener {
            showInfo = !showInfo
            updateToggleStyles()
            updateMarkers()
        }
        binding.tvColor.setOnClickListener { showColorDialog() }
        binding.tvColor.text = lineColors.first().name
        binding.tvColor.setTextColor(lineColors.first().color)
        updateToggleStyles()
    }

    private fun updateToggleStyles() {
        styleToggle(binding.tvTglMarkers, showMarkers)
        styleToggle(binding.tvTglInfo, showInfo)
    }

    private fun styleToggle(view: TextView, on: Boolean) {
        view.setBackgroundResource(if (on) R.drawable.bg_seg_on else android.R.color.transparent)
        view.setTextColor(
            ContextCompat.getColor(this, if (on) R.color.primary else R.color.ink_tertiary)
        )
    }

    private fun showColorDialog() {
        val names = lineColors.map { it.name }.toTypedArray()
        val current = lineColors.indexOfFirst { it.color == currentLineColor }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("连线颜色")
            .setSingleChoiceItems(names, current) { dialog, which ->
                currentLineColor = lineColors[which].color
                binding.tvColor.text = lineColors[which].name
                binding.tvColor.setTextColor(lineColors[which].color)
                updatePolylineColor()
                dialog.dismiss()
            }
            .show()
    }

    private fun recenter() {
        if (mapPointList.isEmpty()) return
        val builder = LatLngBounds.Builder()
        mapPointList.forEach { builder.include(LatLng(it.point.latitude, it.point.longitude)) }
        mapView.map.animateMapStatus(
            MapStatusUpdateFactory.newLatLngBounds(builder.build(), 80, 80)
        )
    }

    private fun loadPoints() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val rawPointList = when {
                    lineId != -1L -> {
                        val points = app.repository.getPointsByLineId(lineId).first()
                        points.map { point ->
                            MapPointEntity(lineSort = 0, point = point, pointSort = point.sortOrder)
                        }
                    }

                    groupId != -1L -> {
                        val lines = app.repository.getLinesByGroupId(groupId).first()
                        lines.flatMap { line ->
                            val points = app.repository.getPointsByLineId(line.id).first()
                            points.map { point ->
                                MapPointEntity(lineSort = line.sortOrder, point = point, pointSort = point.sortOrder)
                            }
                        }
                    }

                    else -> emptyList()
                }

                val sortedList = rawPointList.sortedWith(compareBy({ it.lineSort }, { it.pointSort }))
                val pointEntities = sortedList.map { it.point }

                val distances = calculateDistances(pointEntities)

                mapPointList = sortedList.mapIndexed { index, mapPoint ->
                    mapPoint.copy(distanceFromStart = distances.getOrElse(index) { 0.0 })
                }

                if (lineId != -1L) {
                    val line = app.repository.getLineById(lineId)
                    line?.let {
                        binding.tvName.text = it.name
                        binding.tvTitle.text = it.name
                    }
                } else {
                    binding.tvName.text = "行程地图"
                }

                if (mapPointList.isEmpty()) {
                    binding.tvCount.text = "共 0 个点位"
                    binding.progressBar.visibility = View.GONE
                    return@launch
                }

                val totalDistance = distances.lastOrNull() ?: 0.0
                binding.tvStat.text = formatDistance(totalDistance)
                binding.tvCount.text = "共 ${mapPointList.size} 个点位"
                (binding.rvPoints.adapter as? PointAdapter)?.submitList(mapPointList)
                displayPointsOnMap()

                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                Logger.e("加载点失败: ${e.message}")
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun calculateDistances(points: List<PointEntity>): List<Double> {
        if (points.size < 2) return listOf(0.0)

        val distances = mutableListOf(0.0)
        var cumulativeDistance = 0.0

        for (i in 1 until points.size) {
            val distance = Utils.calculateDistance(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
            cumulativeDistance += distance
            distances.add(cumulativeDistance)
        }

        return distances
    }

    private fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters < 1000) {
            "${distanceMeters.toInt()}m"
        } else {
            String.format("%.1fkm", distanceMeters / 1000)
        }
    }

    private fun shouldShowMarker(mapPoint: MapPointEntity, sortedPoints: List<MapPointEntity>): Boolean {
        val pointId = mapPoint.point.id
        val isStartPoint = pointId == sortedPoints.firstOrNull()?.point?.id
        val isEndPoint = pointId == sortedPoints.lastOrNull()?.point?.id
        val isSelectedPoint = pointId == selectedPointId
        return isStartPoint || isEndPoint || isSelectedPoint
    }

    private fun displayPointsOnMap() {
        if (mapPointList.isEmpty()) return

        clearMapOverlays()

        val sortedPoints = mapPointList.sortedWith(compareBy({ it.lineSort }, { it.pointSort }))
        val latLngList = mutableListOf<LatLng>()

        sortedPoints.forEach { mapPoint ->
            latLngList.add(LatLng(mapPoint.point.latitude, mapPoint.point.longitude))
        }

        if (showMarkers) {
            val markers = mutableListOf<Overlay>()
            sortedPoints.forEachIndexed { index, mapPoint ->
                if (shouldShowMarker(mapPoint, sortedPoints)) {
                    val point = mapPoint.point
                    val latLng = LatLng(point.latitude, point.longitude)

                    val title = if (showInfo) "#${index + 1} ${point.description ?: ""}" else ""

                    val iconRes = when {
                        point.id == sortedPoints.firstOrNull()?.point?.id -> R.drawable.location_on_48dp_start
                        point.id == sortedPoints.lastOrNull()?.point?.id -> R.drawable.location_on_48dp_end
                        point.id == selectedPointId -> R.drawable.flag_48dp_opsz48
                        else -> R.drawable.location_on_48dp_opsz48
                    }

                    val markerOptions = MarkerOptions()
                        .position(latLng)
                        .icon(BitmapDescriptorFactory.fromResource(iconRes))
                        .title(title)

                    val marker = mapView.map.addOverlay(markerOptions) as Marker
                    markers.add(marker)
                }
            }
            markerList = markers
        }

        if (latLngList.size >= 2) {
            val polylineOptions = PolylineOptions()
                .points(latLngList)
                .width(8)
                .color(currentLineColor)

            polylineOverlay = mapView.map.addOverlay(polylineOptions)
        }

        if (latLngList.isNotEmpty()) {
            val builder = LatLngBounds.Builder()
            latLngList.forEach { builder.include(it) }
            val bounds = builder.build()

            val update = MapStatusUpdateFactory.newLatLngBounds(bounds)
            mapView.map.animateMapStatus(update)
        }
    }

    private fun updateMapDisplay() {
        if (mapPointList.isEmpty()) return

        val sortedPoints = mapPointList.sortedWith(compareBy({ it.lineSort }, { it.pointSort }))
        val latLngList = mutableListOf<LatLng>()

        sortedPoints.forEach { mapPoint ->
            latLngList.add(LatLng(mapPoint.point.latitude, mapPoint.point.longitude))
        }

        clearMapOverlays()

        if (showMarkers) {
            val markers = mutableListOf<Overlay>()
            sortedPoints.forEachIndexed { index, mapPoint ->
                if (shouldShowMarker(mapPoint, sortedPoints)) {
                    val point = mapPoint.point
                    val latLng = LatLng(point.latitude, point.longitude)

                    val title = if (showInfo) "#${index + 1} ${point.description ?: ""}" else ""

                    val iconRes = when {
                        point.id == sortedPoints.firstOrNull()?.point?.id -> R.drawable.location_on_48dp_start
                        point.id == sortedPoints.lastOrNull()?.point?.id -> R.drawable.location_on_48dp_end
                        point.id == selectedPointId -> R.drawable.flag_48dp_opsz48
                        else -> R.drawable.location_on_48dp_opsz48
                    }

                    val markerOptions = MarkerOptions()
                        .position(latLng)
                        .icon(BitmapDescriptorFactory.fromResource(iconRes))
                        .title(title)

                    val marker = mapView.map.addOverlay(markerOptions) as Marker
                    markers.add(marker)
                }
            }
            markerList = markers
        }

        if (latLngList.size >= 2) {
            val polylineOptions = PolylineOptions()
                .points(latLngList)
                .width(8)
                .color(currentLineColor)

            polylineOverlay = mapView.map.addOverlay(polylineOptions)
        }

        if (latLngList.isNotEmpty()) {
            val builder = LatLngBounds.Builder()
            latLngList.forEach { builder.include(it) }
            val bounds = builder.build()

            val update = MapStatusUpdateFactory.newLatLngBounds(bounds)
            mapView.map.animateMapStatus(update)
        }
    }

    private fun updatePolylineColor() {
        polylineOverlay?.let { overlay ->
            overlay.remove()
        }

        if (mapPointList.size >= 2) {
            val sortedPoints = mapPointList.sortedWith(compareBy({ it.lineSort }, { it.pointSort }))
            val latLngList = sortedPoints.map { LatLng(it.point.latitude, it.point.longitude) }

            val polylineOptions = PolylineOptions()
                .points(latLngList)
                .width(8)
                .color(currentLineColor)

            polylineOverlay = mapView.map.addOverlay(polylineOptions)
        }
    }

    private fun updateMarkers() {
        if (mapPointList.isEmpty()) return

        val sortedPoints = mapPointList.sortedWith(compareBy({ it.lineSort }, { it.pointSort }))

        markerList.forEach { it.remove() }
        markerList = emptyList()

        if (showMarkers) {
            val markers = mutableListOf<Overlay>()
            sortedPoints.forEachIndexed { index, mapPoint ->
                if (shouldShowMarker(mapPoint, sortedPoints)) {
                    val point = mapPoint.point
                    val latLng = LatLng(point.latitude, point.longitude)

                    val title = if (showInfo) "#${index + 1} ${point.description ?: ""}" else ""

                    val iconRes = when {
                        point.id == sortedPoints.firstOrNull()?.point?.id -> R.drawable.location_on_48dp_start
                        point.id == sortedPoints.lastOrNull()?.point?.id -> R.drawable.location_on_48dp_end
                        point.id == selectedPointId -> R.drawable.flag_48dp_opsz48
                        else -> R.drawable.location_on_48dp_opsz48
                    }

                    val markerOptions = MarkerOptions()
                        .position(latLng)
                        .icon(BitmapDescriptorFactory.fromResource(iconRes))
                        .title(title)

                    val marker = mapView.map.addOverlay(markerOptions) as Marker
                    markers.add(marker)
                }
            }
            markerList = markers
        }
    }

    private fun clearMapOverlays() {
        markerList.forEach { it.remove() }
        polylineOverlay?.remove()
        markerList = emptyList()
        polylineOverlay = null
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
