package com.zuxing.markmap

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.Overlay
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.databinding.FragmentLineMapBinding
import com.zuxing.markmap.databinding.ItemPointSimpleBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LineMapFragment : Fragment() {

    private var _binding: FragmentLineMapBinding? = null
    private val binding get() = _binding!!

    private val args: LineMapFragmentArgs by navArgs()

    private lateinit var mapView: MapView
    private lateinit var app: MarkMapApplication
    private var pointList: List<PointEntity> = emptyList()
    private var markerList: List<Overlay> = emptyList()
    private var polylineOverlay: Overlay? = null
    private var isPanelExpanded = false
    private var isListExpanded = false
    private var showMarkers = true
    private var showInfo = true
    private var currentLineColor = Color.BLUE
    private var selectedPointId: Long? = null

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
    ) : ListAdapter<PointEntity, PointAdapter.PointViewHolder>(PointDiffCallback()) {

        private var selectedPointId: Long? = null

        fun setSelectedPoint(pointId: Long?) {
            val oldId = selectedPointId
            selectedPointId = pointId
            currentList.forEachIndexed { index, point ->
                if (point.id == oldId || point.id == pointId) {
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

            fun bind(point: PointEntity) {
                binding.tvPointDescription.text = when {
                    !point.description.isNullOrBlank() -> point.description
                    !point.address.isNullOrBlank() -> point.address
                    else -> "${point.latitude}, ${point.longitude}"
                }
                binding.root.isSelected = point.id == selectedPointId
                binding.root.setOnClickListener {
                    selectedPointId = point.id
                    notifyDataSetChanged()
                    onPointClick(point)
                }
            }
        }

        private class PointDiffCallback : DiffUtil.ItemCallback<PointEntity>() {
            override fun areItemsTheSame(oldItem: PointEntity, newItem: PointEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: PointEntity, newItem: PointEntity): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLineMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        app = requireActivity().application as MarkMapApplication

        setupMap()
        setupPointList()
        setupToggleList()
        setupControlPanel()
        loadPoints()
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
        binding.ivToggleIcon.setImageResource(android.R.drawable.arrow_down_float)
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
        binding.ivToggleIcon.setImageResource(android.R.drawable.arrow_up_float)
    }

    private fun setupPointList() {
        binding.rvPoints.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPoints.adapter = PointAdapter { point ->
            (binding.rvPoints.adapter as? PointAdapter)?.setSelectedPoint(point.id)
            centerOnPoint(point)
        }
    }

    private fun centerOnPoint(point: PointEntity) {
        val latLng = LatLng(point.latitude, point.longitude)
        val update = MapStatusUpdateFactory.newLatLng(latLng)
        mapView.map.animateMapStatus(update)
    }


    private fun setupMap() {
        mapView = binding.mapView
    }

    private fun setupControlPanel() {
        binding.expandCard.setOnClickListener {
            togglePanel()
        }

        binding.switchShowMarkers.setOnCheckedChangeListener { _, isChecked ->
            showMarkers = isChecked
            updateMapDisplay()
        }

        binding.switchShowInfo.setOnCheckedChangeListener { _, isChecked ->
            showInfo = isChecked
            updateMarkers()
        }

        setupColorDropdown()
    }

    private fun setupColorDropdown() {
        val colorNames = lineColors.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, colorNames)
        binding.actvColor.setAdapter(adapter)
        binding.actvColor.setText(lineColors[0].name, false)
        binding.actvColor.setOnItemClickListener { _, _, position, _ ->
            currentLineColor = lineColors[position].color
            updatePolylineColor()
        }
    }

    private fun togglePanel() {
        if (isPanelExpanded) {
            collapsePanel()
        } else {
            expandPanel()
        }
    }

    private fun expandPanel() {
        isPanelExpanded = true
        binding.panelContent.visibility = View.VISIBLE
        binding.panelContent.alpha = 0f
        binding.panelContent.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        binding.ivExpandIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
    }

    private fun collapsePanel() {
        isPanelExpanded = false
        binding.panelContent.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.panelContent.visibility = View.GONE
            }
            .start()
        binding.ivExpandIcon.setImageResource(android.R.drawable.ic_menu_manage)
    }

    private fun loadPoints() {
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pointList = when {
                    args.lineId != -1L -> {
                        app.repository.getPointsByLineId(args.lineId).first()
                    }
                    args.groupId != -1L -> {
                        val lines = app.repository.getLinesByGroupId(args.groupId).first()
                        lines.flatMap { line ->
                            app.repository.getPointsByLineId(line.id).first()
                        }
                    }
                    else -> emptyList()
                }

                if (pointList.isEmpty()) {
                    binding.tvPointCountList.text = "点数量: 0"
                    binding.tvPointCount.text = "点数量: 0"
                    binding.progressBar.visibility = View.GONE
                    return@launch
                }

                binding.tvPointCountList.text = "点数量: ${pointList.size}"
                binding.tvPointCount.text = "点数量: ${pointList.size}"
                val sortedPoints = pointList.sortedBy { it.sortOrder }
                (binding.rvPoints.adapter as? PointAdapter)?.submitList(sortedPoints)
                displayPointsOnMap()

                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                Logger.e("加载点失败: ${e.message}")
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun displayPointsOnMap() {
        if (pointList.isEmpty()) return

        clearMapOverlays()

        val sortedPoints = pointList.sortedBy { it.sortOrder }
        val latLngList = mutableListOf<LatLng>()

        sortedPoints.forEach { point ->
            latLngList.add(LatLng(point.latitude, point.longitude))
        }

        if (showMarkers) {
            val markers = mutableListOf<Overlay>()
            sortedPoints.forEachIndexed { _, point ->
                val latLng = LatLng(point.latitude, point.longitude)

                val title = if (showInfo) {
                    point.description ?: ""
                } else {
                    ""
                }

                val markerOptions = com.baidu.mapapi.map.MarkerOptions()
                    .position(latLng)
                    .icon(BitmapDescriptorFactory.fromResource(android.R.drawable.ic_menu_compass))
                    .title(title)

                val marker = mapView.map.addOverlay(markerOptions) as Marker
                markers.add(marker)
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
        if (pointList.isEmpty()) return

        val sortedPoints = pointList.sortedBy { it.sortOrder }
        val latLngList = mutableListOf<LatLng>()

        sortedPoints.forEach { point ->
            latLngList.add(LatLng(point.latitude, point.longitude))
        }

        clearMapOverlays()

        if (showMarkers) {
            val markers = mutableListOf<Overlay>()
            sortedPoints.forEachIndexed { _, point ->
                val latLng = LatLng(point.latitude, point.longitude)

                val title = if (showInfo) {
                    point.description ?: ""
                } else {
                    ""
                }

                val markerOptions = com.baidu.mapapi.map.MarkerOptions()
                    .position(latLng)
                    .icon(BitmapDescriptorFactory.fromResource(android.R.drawable.ic_menu_compass))
                    .title(title)

                val marker = mapView.map.addOverlay(markerOptions) as Marker
                markers.add(marker)
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

        if (pointList.size >= 2) {
            val sortedPoints = pointList.sortedBy { it.sortOrder }
            val latLngList = sortedPoints.map { LatLng(it.latitude, it.longitude) }

            val polylineOptions = PolylineOptions()
                .points(latLngList)
                .width(8)
                .color(currentLineColor)

            polylineOverlay = mapView.map.addOverlay(polylineOptions)
        }
    }

    private fun updateMarkers() {
        if (pointList.isEmpty()) return

        val sortedPoints = pointList.sortedBy { it.sortOrder }

        markerList.forEach { it.remove() }
        markerList = emptyList()

        if (showMarkers) {
            val markers = mutableListOf<Overlay>()
            sortedPoints.forEachIndexed { _, point ->
                val latLng = LatLng(point.latitude, point.longitude)

                val title = if (showInfo) {
                    point.description ?: ""
                } else {
                    ""
                }

                val markerOptions = com.baidu.mapapi.map.MarkerOptions()
                    .position(latLng)
                    .icon(BitmapDescriptorFactory.fromResource(android.R.drawable.ic_menu_compass))
                    .title(title)

                val marker = mapView.map.addOverlay(markerOptions) as Marker
                markers.add(marker)
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

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
        _binding = null
    }
}
