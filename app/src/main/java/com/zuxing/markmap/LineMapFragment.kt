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
    private var showMarkers = true
    private var showInfo = true
    private var currentLineColor = Color.BLUE

    data class ColorData(val name: String, val color: Int)
    private val lineColors = listOf(
        ColorData("蓝色", Color.BLUE),
        ColorData("红色", Color.RED),
        ColorData("绿色", Color.GREEN),
        ColorData("紫色", -65281),
        ColorData("橙色", -26624),
        ColorData("青色", -10580465)
    )

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
        setupControlPanel()
        loadPoints()
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
                pointList = app.repository.getPointsByLineId(args.lineId).first()

                if (pointList.isEmpty()) {
                    binding.tvPointCount.text = "点数量: 0"
                    binding.progressBar.visibility = View.GONE
                    return@launch
                }

                binding.tvPointCount.text = "点数量: ${pointList.size}"
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
