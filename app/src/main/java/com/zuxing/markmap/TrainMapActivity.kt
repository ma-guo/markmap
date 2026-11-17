package com.zuxing.markmap

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.google.gson.GsonBuilder
import com.zuxing.markmap.databinding.ActivityTrainMapBinding
import com.zuxing.markmap.databinding.ItemPointSimpleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TrainMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrainMapBinding
    private lateinit var mapView: MapView
    private val polylineOverlays = mutableListOf<Overlay>()
    private val markerList = mutableListOf<Overlay>()
    private lateinit var trainService: TrainService
    private lateinit var ticketService: TicketService
    private lateinit var ticketHttpClient: OkHttpClient
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

        // 拼接区间路线需要余票查询，12306 需要先拿会话 Cookie
        ticketHttpClient = createTicketHttpClient()
        val gson = GsonBuilder().setLenient().create()
        ticketService = Retrofit.Builder()
            .baseUrl("https://kyfw.12306.cn/")
            .client(ticketHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(TicketService::class.java)
    }

    private fun loadTrainRoute(trainCode: String, date: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStationCount.text = "正在查询列车信息…"

        lifecycleScope.launch {
            try {
                val trainInfoResponse = withContext(Dispatchers.IO) {
                    trainService.getTrainInfo(trainCode, date).execute()
                }

                val trainInfoData = trainInfoResponse.body()?.data
                val trainNo = trainInfoData?.trainNo
                if (!trainInfoResponse.isSuccessful || trainInfoData == null || trainNo.isNullOrEmpty()) {
                    showLoadError("未找到列车信息，请检查车次和日期")
                    return@launch
                }

                val apiStations = trainInfoData.trainDetail?.stopTime
                    ?.sortedBy { it.stationNo?.toIntOrNull() ?: Int.MAX_VALUE }
                    ?: emptyList()

                val mapResponse = withContext(Dispatchers.IO) {
                    trainService.getTrainMapLine("v2", trainNo).execute()
                }
                val segments = if (mapResponse.isSuccessful && mapResponse.body()?.status == true) {
                    mapResponse.body()?.data
                } else {
                    null
                }

                if (segments.isNullOrEmpty() && apiStations.isEmpty()) {
                    showLoadError(mapResponse.body()?.errorMsg ?: "未获取到运行图数据")
                    return@launch
                }

                if (segments.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.tvStationCount.text = "正在拼接分段路线…"
                    }
                }

                val parts = withContext(Dispatchers.IO) {
                    buildRouteParts(segments, apiStations, date, trainCode, trainNo)
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (parts.isEmpty()) {
                        binding.tvStationCount.text = ""
                        Toast.makeText(
                            this@TrainMapActivity,
                            "未获取到运行图数据",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        displayTrainRoute(parts, apiStations)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStationCount.text = ""
                    Toast.makeText(
                        this@TrainMapActivity,
                        "请求失败: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun showLoadError(message: String) {
        withContext(Dispatchers.Main) {
            binding.progressBar.visibility = View.GONE
            binding.tvStationCount.text = ""
            Toast.makeText(this@TrainMapActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 本车走向为空时，按经停站区间从其他车次拼接；实在找不到的区间用直线兜底。
     */
    private suspend fun buildRouteParts(
        segments: Map<String, TrainLineSegment>?,
        apiStations: List<TrainStopTime>,
        date: String,
        trainCode: String,
        trainNo: String,
    ): List<TrainRouteStitcher.RoutePart> {
        if (!segments.isNullOrEmpty()) {
            val points = buildPointsFromSegments(segments)
            if (points != null) {
                return listOf(TrainRouteStitcher.RoutePart(points, approximate = false))
            }
        }

        val anchors = apiStations.mapNotNull { toAnchor(it) }
        if (anchors.size < 2) return emptyList()

        val stitcher = TrainRouteStitcher(
            trainService = trainService,
            ticketService = ticketService,
            httpClient = ticketHttpClient,
            date = date,
            targetTrainCode = trainCode,
            targetTrainNo = trainNo,
        )
        return stitcher.resolve(anchors)
    }

    private fun buildPointsFromSegments(segments: Map<String, TrainLineSegment>): List<LatLng>? {
        val points = mutableListOf<LatLng>()
        segments.entries.sortedBy { it.value.index }.forEach { (_, segment) ->
            segment.line?.forEach { coordinate ->
                if (coordinate.size >= 2) {
                    val point = Utils.gcj02ToBd09(coordinate[1], coordinate[0])
                    if (points.lastOrNull() != point) {
                        points.add(point)
                    }
                }
            }
        }
        return points.takeIf { it.size >= 2 }
    }

    private fun toAnchor(stop: TrainStopTime): TrainRouteStitcher.Anchor? {
        val name = stop.stationName ?: return null
        val lon = stop.lon?.toDoubleOrNull()
        val lat = stop.lat?.toDoubleOrNull()
        val latLng = if (lon != null && lat != null) Utils.gcj02ToBd09(lat, lon) else null
        return TrainRouteStitcher.Anchor(
            name = name,
            telecode = stop.stationTelecode,
            latLng = latLng,
        )
    }

    private fun displayTrainRoute(
        parts: List<TrainRouteStitcher.RoutePart>,
        apiStations: List<TrainStopTime>
    ) {
        val allLatLngs = mutableListOf<LatLng>()
        val cumulativeDistances = mutableListOf<Double>()

        parts.forEach { part ->
            part.points.forEach { point ->
                if (allLatLngs.isEmpty() || allLatLngs.last() != point) {
                    if (allLatLngs.isNotEmpty()) {
                        val last = allLatLngs.last()
                        val distance = Utils.calculateDistance(
                            last.latitude, last.longitude,
                            point.latitude, point.longitude
                        )
                        cumulativeDistances.add(cumulativeDistances.last() + distance)
                    } else {
                        cumulativeDistances.add(0.0)
                    }
                    allLatLngs.add(point)
                }
            }
        }

        stationInfoList = apiStations.mapNotNull { apiStation ->
            val name = apiStation.stationName ?: return@mapNotNull null
            val lon = apiStation.lon?.toDoubleOrNull() ?: return@mapNotNull null
            val lat = apiStation.lat?.toDoubleOrNull() ?: return@mapNotNull null
            val bd09LatLng = Utils.gcj02ToBd09(lat, lon)
            val startTime = apiStation.startTime ?: ""

            var dist = 0.0
            if (allLatLngs.isNotEmpty()) {
                var minDist = Double.MAX_VALUE
                var closestIdx = -1
                allLatLngs.forEachIndexed { idx, point ->
                    val d = Utils.calculateDistance(
                        bd09LatLng.latitude, bd09LatLng.longitude,
                        point.latitude, point.longitude
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

        val approximateCount = parts.count { it.approximate }
        val missingCoordinateCount = apiStations.size - stationInfoList.size
        val totalDistance = stationInfoList.lastOrNull()?.distanceFromStart ?: 0.0

        binding.tvStationCount.text = buildString {
            append("车站: ${apiStations.size}  总距离: ")
            if (approximateCount > 0) append("约")
            append(formatDistance(totalDistance))
            if (approximateCount > 0) append("  (部分路段为示意)")
            if (missingCoordinateCount > 0) append("  ($missingCoordinateCount 个车站缺少坐标)")
        }

        (binding.rvStations.adapter as? StationAdapter)?.submitList(stationInfoList)

        val firstApiStation = apiStations.firstOrNull()?.stationName
        val lastApiStation = apiStations.lastOrNull()?.stationName

        markerList.forEach { it.remove() }
        markerList.clear()
        stationInfoList.forEach { info ->
            val iconRes = when (info.name) {
                firstApiStation -> R.drawable.location_on_48dp_start
                lastApiStation -> R.drawable.location_on_48dp_end
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

        polylineOverlays.forEach { it.remove() }
        polylineOverlays.clear()
        parts.forEach { part ->
            if (part.points.size >= 2) {
                val options = PolylineOptions()
                    .points(part.points)
                    .width(if (part.approximate) 6 else 8)
                    .color(Color.parseColor(if (part.approximate) "#9E9E9E" else "#4CAF50"))
                if (part.approximate) {
                    options.dottedLine(true)
                }
                polylineOverlays.add(mapView.map.addOverlay(options))
            }
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

        if (approximateCount > 0) {
            val message = if (approximateCount == parts.size) {
                "未找到真实走向，已按经停站直线示意"
            } else {
                "部分路段缺少真实走向，已用虚线示意"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        val startName = firstApiStation ?: stationInfoList.firstOrNull()?.name ?: ""
        val endName = lastApiStation ?: stationInfoList.lastOrNull()?.name ?: ""
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
        polylineOverlays.forEach { it.remove() }
        mapView.onDestroy()
    }
}
