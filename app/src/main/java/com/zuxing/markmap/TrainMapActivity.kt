package com.zuxing.markmap

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.baidu.mapapi.utils.CoordinateConverter
import com.zuxing.markmap.databinding.ActivityTrainMapBinding
import com.zuxing.markmap.databinding.ItemPointSimpleBinding

import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TrainMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrainMapBinding
    private lateinit var mapView: MapView
    private var polylineOverlay: Overlay? = null
    private val markerList = mutableListOf<Overlay>()
    private lateinit var trainService: TrainService
    private var isListExpanded = false
    private var stationInfoList = listOf<StationInfo>()
    private var currentTrainCode = ""

    data class StationInfo(
        val name: String,
        val latLng: LatLng,
        val distanceFromStart: Double = 0.0,
        val startTime: String = ""
    )

    private class StationAdapter(
        private val onItemClick: (StationInfo) -> Unit
    ) : ListAdapter<StationInfo, StationAdapter.ViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPointSimpleBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(
            private val binding: ItemPointSimpleBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: StationInfo) {
                binding.tvPointDescription.text = item.name.ifEmpty { "车站" }
                binding.tvStartTime.text = formatTime(item.startTime)
                binding.tvDistance.text = formatDistance(item.distanceFromStart)
                binding.root.setOnClickListener {
                    onItemClick(item)
                }
            }

            private fun formatTime(time: String): String {
                if (time.isBlank() || time.length < 4) return ""
                return "${time.substring(0, 2)}:${time.substring(2, 4)}"
            }

            private fun formatDistance(dist: Double): String {
                return if (dist < 1000) {
                    "${dist.toInt()}m"
                } else {
                    String.format("%.1fkm", dist / 1000)
                }
            }
        }

        private class DiffCallback : DiffUtil.ItemCallback<StationInfo>() {
            override fun areItemsTheSame(a: StationInfo, b: StationInfo): Boolean = a.name == b.name
            override fun areContentsTheSame(a: StationInfo, b: StationInfo): Boolean = a == b
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val trainCode = intent.getStringExtra("trainCode") ?: ""
        currentTrainCode = trainCode
        val date = intent.getStringExtra("date") ?: ""

        setupToolbar()
        setupMap()
        setupStationPanel()
        initService()

        if (trainCode.isNotEmpty() && date.isNotEmpty()) {
            loadTrainRoute(trainCode, date)
        } else {
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupMap() {
        mapView = binding.mapView
        mapView.map.clear()

        val center = LatLng(39.915, 116.404)
        mapView.map.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(center, 5f))
    }

    private fun setupStationPanel() {
        binding.rvStations.layoutManager = LinearLayoutManager(this)
        binding.rvStations.adapter = StationAdapter { station ->
            val update = MapStatusUpdateFactory.newLatLng(station.latLng)
            mapView.map.animateMapStatus(update)
        }

        binding.cardToggleList.setOnClickListener { toggleStationList() }
    }

    private fun toggleStationList() {
        if (isListExpanded) collapseStationList() else expandStationList()
    }

    private fun expandStationList() {
        isListExpanded = true
        binding.cardStationList.visibility = View.VISIBLE
        binding.cardStationList.translationY = binding.cardStationList.height.toFloat()
        binding.cardStationList.animate()
            .translationY(0f)
            .setDuration(200)
            .start()
        binding.ivToggleIcon.setImageResource(R.drawable.arrow_drop_down_24px)
    }

    private fun collapseStationList() {
        isListExpanded = false
        binding.cardStationList.animate()
            .translationY(binding.cardStationList.height.toFloat())
            .setDuration(200)
            .withEndAction {
                binding.cardStationList.visibility = View.GONE
            }
            .start()
        binding.ivToggleIcon.setImageResource(R.drawable.arrow_drop_up_24px)
    }

    private fun initService() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://mobile.12306.cn/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        trainService = retrofit.create(TrainService::class.java)
    }

    private fun loadTrainRoute(trainCode: String, date: String) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val trainInfoResponse = withContext(Dispatchers.IO) {
                    trainService.getTrainInfo(trainCode, date).execute()
                }

                if (!trainInfoResponse.isSuccessful || trainInfoResponse.body() == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@TrainMapActivity, "未找到列车信息，请检查车次和日期", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val trainInfoData = trainInfoResponse.body()!!.data
                val trainNo = trainInfoData?.trainNo

                if (trainNo.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@TrainMapActivity, "未找到列车信息，请检查车次和日期", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val apiStations = trainInfoData.trainDetail?.stopTime
                    ?.sortedBy { it.stationNo?.toIntOrNull() ?: Int.MAX_VALUE }
                    ?: emptyList()

                val mapResponse = withContext(Dispatchers.IO) {
                    trainService.getTrainMapLine("v2", trainNo).execute()
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    val segments = if (mapResponse.isSuccessful && mapResponse.body()?.status == true) {
                        mapResponse.body()?.data
                    } else {
                        null
                    }

                    if (segments.isNullOrEmpty() && apiStations.isEmpty()) {
                        val msg = mapResponse.body()?.errorMsg
                        Toast.makeText(this@TrainMapActivity, msg ?: "未获取到运行图数据", Toast.LENGTH_SHORT).show()
                    } else {
                        displayTrainRoute(segments, apiStations)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@TrainMapActivity, "请求失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun gcj02ToBd09(lat: Double, lng: Double): LatLng {
        val source = LatLng(lat, lng)
        val converter = CoordinateConverter()
        converter.from(CoordinateConverter.CoordType.COMMON)
        converter.coord(source)
        return converter.convert()
    }

    private fun displayTrainRoute(
        segments: Map<String, TrainLineSegment>?,
        apiStations: List<TrainStopTime>
    ) {
        val allLatLngs = mutableListOf<LatLng>()
        val cumulativeDistances = mutableListOf<Double>()

        if (!segments.isNullOrEmpty()) {
            var lastPoint: LatLng? = null
            var accumulatedDist = 0.0
            val sortedEntries = segments.entries.sortedBy { it.value.index }

            sortedEntries.forEach { (_, segment) ->
                val line = segment.line ?: return@forEach
                if (line.size < 2) return@forEach
                val points = line.map { gcj02ToBd09(it[1], it[0]) }

                points.forEach { pt ->
                    if (lastPoint == null || lastPoint != pt) {
                        if (lastPoint != null) {
                            accumulatedDist += Utils.calculateDistance(
                                lastPoint!!.latitude, lastPoint!!.longitude,
                                pt.latitude, pt.longitude
                            )
                        }
                        allLatLngs.add(pt)
                        cumulativeDistances.add(accumulatedDist)
                        lastPoint = pt
                    }
                }
            }
        }

        stationInfoList = apiStations.mapNotNull { apiStation ->
            val name = apiStation.stationName ?: return@mapNotNull null
            val lon = apiStation.lon?.toDoubleOrNull() ?: return@mapNotNull null
            val lat = apiStation.lat?.toDoubleOrNull() ?: return@mapNotNull null
            val bd09LatLng = gcj02ToBd09(lat, lon)
            val startTime = apiStation.startTime ?: ""

            var dist = 0.0
            if (allLatLngs.isNotEmpty()) {
                var minDist = Double.MAX_VALUE
                var closestIdx = -1
                allLatLngs.forEachIndexed { idx, pt ->
                    val d = Utils.calculateDistance(
                        bd09LatLng.latitude, bd09LatLng.longitude,
                        pt.latitude, pt.longitude
                    )
                    if (d < minDist) {
                        minDist = d
                        closestIdx = idx
                    }
                }
                dist = if (closestIdx >= 0) cumulativeDistances[closestIdx] else 0.0
            }
            StationInfo(name, bd09LatLng, dist, startTime)
        }

        val stationSize = stationInfoList.size
        val totalDistance = stationInfoList.lastOrNull()?.distanceFromStart ?: 0.0
        binding.tvStationCount.text = "车站: $stationSize  总距离: ${formatDistance(totalDistance)}"

        (binding.rvStations.adapter as? StationAdapter)?.submitList(stationInfoList)

        stationInfoList.forEachIndexed { index, info ->
            val iconRes = when (index) {
                0 -> R.drawable.location_on_48dp_start
                stationInfoList.size - 1 -> R.drawable.location_on_48dp_end
                else -> R.drawable.location_on_48dp_opsz48
            }
            val marker = mapView.map.addOverlay(
                MarkerOptions()
                    .position(info.latLng)
                    .icon(BitmapDescriptorFactory.fromResource(iconRes))
                    .title(info.name.ifEmpty { "车站" })
            ) as Marker
            markerList.add(marker)
        }

        if (allLatLngs.size >= 2) {
            polylineOverlay = mapView.map.addOverlay(
                PolylineOptions()
                    .points(allLatLngs)
                    .width(8)
                    .color(Color.parseColor("#4CAF50"))
            )
        }

        val allPoints = if (allLatLngs.isNotEmpty()) {
            allLatLngs
        } else {
            stationInfoList.map { it.latLng }
        }
        if (allPoints.isNotEmpty()) {
            val builder = LatLngBounds.Builder()
            allPoints.forEach { builder.include(it) }
            mapView.map.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(builder.build()))
        }

        val startName = stationInfoList.firstOrNull()?.name ?: ""
        val endName = stationInfoList.lastOrNull()?.name ?: ""
        binding.toolbar.title = "$currentTrainCode $startName-$endName"
    }

    private fun formatDistance(dist: Double): String {
        return if (dist < 1000) {
            "${dist.toInt()}m"
        } else {
            String.format("%.1fkm", dist / 1000)
        }
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
        markerList.forEach { it.remove() }
        polylineOverlay?.remove()
        mapView.onDestroy()
    }
}
