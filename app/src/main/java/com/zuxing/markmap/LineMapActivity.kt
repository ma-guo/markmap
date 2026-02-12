package com.zuxing.markmap

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatActivity
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
import com.zuxing.markmap.databinding.PopupLineMapControlBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LineMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLineMapBinding
    private lateinit var mapView: MapView
    private lateinit var app: MarkMapApplication
    private lateinit var controlPopupWindow: PopupWindow
    private lateinit var controlBinding: PopupLineMapControlBinding
    private var mapPointList: List<MapPointEntity> = emptyList()
    private var markerList: List<Overlay> = emptyList()
    private var polylineOverlay: Overlay? = null
    private var isPanelExpanded = false
    private var isListExpanded = false
    private var showMarkers = true
    private var showInfo = true
    private var currentLineColor = Color.BLUE
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
        ColorData("蓝色", Color.BLUE),
        ColorData("红色", Color.RED),
        ColorData("绿色", Color.GREEN),
        ColorData("紫色", -65281),
        ColorData("橙色", -26624),
        ColorData("青色", -10580465)
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
            holder.bind(getItem(position))
        }

        inner class PointViewHolder(
            private val binding: ItemPointSimpleBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(mapPoint: MapPointEntity) {
                val point = mapPoint.point
                binding.tvPointDescription.text = when {
                    !point.description.isNullOrBlank() -> point.description
                    !point.address.isNullOrBlank() -> point.address
                    else -> "${point.latitude}, ${point.longitude}"
                }
                binding.tvDistance.text = formatDistance(mapPoint.distanceFromStart)
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
        binding = ActivityLineMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lineId = intent.getLongExtra("lineId", -1L)
        groupId = intent.getLongExtra("groupId", -1L)
        app = application as MarkMapApplication

        setupToolbar()
        setupMap()
        setupPointList()
        setupToggleList()
        setupControlPanel()
        loadPoints()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_line_map, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                toggleControlPanel()
                true
            }
            else -> super.onOptionsItemSelected(item)
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
        val update = MapStatusUpdateFactory.newLatLng(latLng)
        selectedPointId = point.id
        mapView.map.animateMapStatus(update)
        updateMarkers()
    }

    private fun setupToggleList() {
        binding.cardToggleList.setOnClickListener {
            toggleList()
        }
    }

    private fun toggleList() {
        if (isListExpanded) {
            collapseList()
        } else {
            expandList()
        }
    }

    private fun expandList() {
        isListExpanded = true
        binding.cardPointList.visibility = View.VISIBLE
        binding.cardPointList.translationY = binding.cardPointList.height.toFloat()
        binding.cardPointList.animate()
            .translationY(0f)
            .setDuration(200)
            .start()
        binding.ivToggleIcon.setImageResource(R.drawable.arrow_drop_down_24px)
    }

    private fun collapseList() {
        isListExpanded = false
        binding.cardPointList.animate()
            .translationY(binding.cardPointList.height.toFloat())
            .setDuration(200)
            .withEndAction {
                binding.cardPointList.visibility = View.GONE
            }
            .start()
        binding.ivToggleIcon.setImageResource(R.drawable.arrow_drop_up_24px)
    }


    private fun setupControlPanel() {
        controlBinding = PopupLineMapControlBinding.inflate(LayoutInflater.from(this))

        val popupWidth = (160 * resources.displayMetrics.density).toInt()
        controlPopupWindow = PopupWindow(
            controlBinding.root,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            setOnDismissListener {
                isPanelExpanded = false
            }
        }

        controlBinding.switchShowMarkers.setOnCheckedChangeListener { _, isChecked ->
            showMarkers = isChecked
            updateMapDisplay()
        }

        controlBinding.switchShowInfo.setOnCheckedChangeListener { _, isChecked ->
            showInfo = isChecked
            updateMarkers()
        }

        setupColorDropdown()
    }

    private fun setupColorDropdown() {
        val colorNames = lineColors.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, colorNames)
        controlBinding.actvColor.setAdapter(adapter)
        controlBinding.actvColor.setText(lineColors[0].name, false)
        controlBinding.actvColor.setOnItemClickListener { _, _, position, _ ->
            currentLineColor = lineColors[position].color
            updatePolylineColor()
        }
    }

    private fun toggleControlPanel() {
        if (isPanelExpanded) {
            controlPopupWindow.dismiss()
            isPanelExpanded = false
        } else {
            showControlPanel()
        }
    }

    private fun showControlPanel() {
        val marginRight = (10 * resources.displayMetrics.density).toInt()
        val xOffset = binding.toolbar.width - controlPopupWindow.width - marginRight
        controlPopupWindow.showAsDropDown(binding.toolbar, xOffset, 8)
        isPanelExpanded = true
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

                if (mapPointList.isEmpty()) {
                    binding.tvPointCountList.text = "点数量: 0"
                    binding.progressBar.visibility = View.GONE
                    return@launch
                }

                val totalDistance = distances.lastOrNull() ?: 0.0
                binding.tvPointCountList.text = "点数量: ${mapPointList.size}  总距离: ${formatDistance(totalDistance)}"
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
            sortedPoints.forEachIndexed { _, mapPoint ->
                if (shouldShowMarker(mapPoint, sortedPoints)) {
                    val point = mapPoint.point
                    val latLng = LatLng(point.latitude, point.longitude)

                    val title = if (showInfo) {
                        point.description ?: ""
                    } else {
                        ""
                    }

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
            sortedPoints.forEachIndexed { _, mapPoint ->
                if (shouldShowMarker(mapPoint, sortedPoints)) {
                    val point = mapPoint.point
                    val latLng = LatLng(point.latitude, point.longitude)

                    val title = if (showInfo) {
                        point.description ?: ""
                    } else {
                        ""
                    }

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
            sortedPoints.forEachIndexed { _, mapPoint ->
                if (shouldShowMarker(mapPoint, sortedPoints)) {
                    val point = mapPoint.point
                    val latLng = LatLng(point.latitude, point.longitude)

                    val title = if (showInfo) {
                        point.description ?: ""
                    } else {
                        ""
                    }

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
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }
}
