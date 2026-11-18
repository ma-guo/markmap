package com.zuxing.markmap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private var isSegmentMode = false
    private var segmentFromTelecode = ""
    private var segmentToTelecode = ""
    private var segmentFromName = ""
    private var segmentToName = ""

    private data class CandidateSegment(
        val trainCode: String,
        val fromTelecode: String,
        val toTelecode: String,
        val fromName: String,
        val toName: String,
    )

    private data class CandidateMatch(
        val candidate: CandidateSegment,
        val info: TrainInfoData,
        val stops: List<TrainStopTime>,
        val range: Pair<Int, Int>,
    )

    private companion object {
        private const val MAX_STATION_TO_LINE_DISTANCE = 50_000.0
        private const val MAX_CANDIDATE_CHECKS = 24
        private const val MAX_REVERSE_CANDIDATE_CHECKS = 12
        private val HIGH_SPEED_PREFIXES = setOf('G', 'D', 'C')
    }

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

        segmentFromTelecode = intent.getStringExtra("fromTelecode") ?: ""
        segmentToTelecode = intent.getStringExtra("toTelecode") ?: ""
        segmentFromName = intent.getStringExtra("fromName") ?: ""
        segmentToName = intent.getStringExtra("toName") ?: ""

        val trainCodes = (intent.getStringArrayExtra("trainCodes")?.toList() ?: emptyList())
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf(trainCode).filter { it.isNotBlank() } }
        val fromCodes = intent.getStringArrayExtra("candidateFromTelecodes")?.toList() ?: emptyList()
        val toCodes = intent.getStringArrayExtra("candidateToTelecodes")?.toList() ?: emptyList()
        val fromNames = intent.getStringArrayExtra("candidateFromNames")?.toList() ?: emptyList()
        val toNames = intent.getStringArrayExtra("candidateToNames")?.toList() ?: emptyList()

        val candidates = trainCodes.mapIndexed { index, code ->
            CandidateSegment(
                trainCode = code,
                fromTelecode = fromCodes.getOrElse(index) { segmentFromTelecode },
                toTelecode = toCodes.getOrElse(index) { segmentToTelecode },
                fromName = fromNames.getOrElse(index) { segmentFromName },
                toName = toNames.getOrElse(index) { segmentToName },
            )
        }

        isSegmentMode = segmentFromTelecode.isNotBlank() ||
                segmentToTelecode.isNotBlank() ||
                segmentFromName.isNotBlank() ||
                segmentToName.isNotBlank() ||
                candidates.any { it.fromTelecode.isNotBlank() || it.fromName.isNotBlank() }

        setupToolbar()
        setupMap()
        setupStationPanel()
        initService()

        if (candidates.isNotEmpty() && date.isNotEmpty()) {
            loadTrainRoute(candidates, date)
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

    private data class LoadedRoute(
        val trainCode: String,
        val parts: List<TrainRouteStitcher.RoutePart>,
        val stations: List<TrainStopTime>,
        val exactSegment: Boolean,
        val reversed: Boolean = false,
    )

    private fun loadTrainRoute(candidates: List<CandidateSegment>, date: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStationCount.text =
            if (isSegmentMode) "正在查找包含该区间的车次…" else "正在查询列车信息…"

        lifecycleScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    resolveTrainRoute(candidates, date)
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (loaded == null) {
                        binding.tvStationCount.text = ""
                        Toast.makeText(
                            this@TrainMapActivity,
                            if (isSegmentMode) "未找到包含该区间的车次" else "未获取到运行图数据",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        currentTrainCode = loaded.trainCode
                        displayTrainRoute(loaded.parts, loaded.stations)
                        if (isSegmentMode && loaded.reversed) {
                            Toast.makeText(
                                this@TrainMapActivity,
                                "当前方向暂无走向，已使用反向本站线路",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (isSegmentMode && loaded.exactSegment) {
                            val actualFrom = loaded.stations.firstOrNull()?.stationName ?: ""
                            val actualTo = loaded.stations.lastOrNull()?.stationName ?: ""
                            if (actualFrom.isNotBlank() && actualTo.isNotBlank() &&
                                (normalizeStationName(actualFrom) != normalizeStationName(segmentFromName) ||
                                        normalizeStationName(actualTo) != normalizeStationName(segmentToName))
                            ) {
                                Toast.makeText(
                                    this@TrainMapActivity,
                                    "已使用同城站点：$actualFrom → $actualTo",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        if (isSegmentMode && !loaded.exactSegment) {
                            Toast.makeText(
                                this@TrainMapActivity,
                                "未找到包含该区间的车次，已展示完整运行图",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
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

    /**
     * 依次尝试候选车次，优先选出经停站按顺序包含目标区间、且自身有真实走向的车次；
     * 找到后只截取目标区间，避免把整条运行图都画出来。
     */
    private suspend fun resolveTrainRoute(
        candidates: List<CandidateSegment>,
        date: String,
    ): LoadedRoute? {
        val limited = candidates.take(MAX_CANDIDATE_CHECKS)
        if (limited.isEmpty()) return null

        // 非区间模式：沿用原来的逻辑，展示第一个能加载的车次
        if (!isSegmentMode) {
            for (candidate in limited) {
                val code = candidate.trainCode
                val info = fetchTrainInfo(code, date) ?: continue
                val stops = sortedStops(info)
                val parts = buildFullRoute(info, stops, date, code) ?: continue
                if (parts.isNotEmpty()) {
                    return LoadedRoute(code, parts, stops, exactSegment = false)
                }
            }
            return null
        }

        // 候选顺序已按「本站优先 -> 同城站 -> G/D/C 优先」排好
        val exactCandidates = mutableListOf<CandidateSegment>()
        val sameCityCandidates = mutableListOf<CandidateSegment>()
        for (candidate in limited) {
            if (isExactSegment(candidate)) {
                exactCandidates.add(candidate)
            } else {
                sameCityCandidates.add(candidate)
            }
        }

        // 1. 本站正向：优先使用用户所选站点本身的走向
        val exactMatches = mutableListOf<CandidateMatch>()
        for (candidate in exactCandidates) {
            val match = matchCandidate(candidate, date) ?: continue
            exactMatches.add(match)
            tryMapLine(match)?.let { return it }
        }

        // 2. 本站反向：正向没有走向时，交换始发站再获取一次
        tryReverse(date, exactMatches.firstOrNull())?.let { return it }

        // 3. 本站两个方向都没有走向时，再使用同城站点
        val sameCityMatches = mutableListOf<CandidateMatch>()
        for (candidate in sameCityCandidates) {
            val match = matchCandidate(candidate, date) ?: continue
            sameCityMatches.add(match)
            tryMapLine(match)?.let { return it }
        }

        // 4. 仍然没有真实走向：借用其他车次的走向拼接目标区间
        val firstMatched = (exactMatches + sameCityMatches).firstOrNull()
        if (firstMatched != null) {
            val range = firstMatched.range
            val fromAnchor = toAnchor(firstMatched.stops[range.first])
            val toAnchor = toAnchor(firstMatched.stops[range.second])
            if (fromAnchor?.latLng != null && toAnchor?.latLng != null) {
                val stitcher = createStitcher(
                    date = date,
                    trainCode = firstMatched.candidate.trainCode,
                    trainNo = firstMatched.info.trainNo.orEmpty(),
                )
                val parts = stitcher.resolveSegment(fromAnchor, toAnchor)
                if (parts.isNotEmpty()) {
                    return LoadedRoute(
                        trainCode = firstMatched.candidate.trainCode,
                        parts = parts,
                        stations = firstMatched.stops.subList(range.first, range.second + 1),
                        exactSegment = true,
                    )
                }
            }
        }

        // 4. 兜底：没有车次能确认该区间时，展示第一个车次的完整走向
        val fallbackCode = limited.first().trainCode
        val fallbackInfo = fetchTrainInfo(fallbackCode, date) ?: return null
        val fallbackStops = sortedStops(fallbackInfo)
        val fallbackParts = buildFullRoute(
            fallbackInfo, fallbackStops, date, fallbackCode
        ) ?: return null
        return LoadedRoute(fallbackCode, fallbackParts, fallbackStops, exactSegment = false)
    }

    private fun fetchTrainInfo(trainCode: String, date: String): TrainInfoData? {
        return try {
            val response = trainService.getTrainInfo(trainCode, date).execute()
            if (response.isSuccessful) response.body()?.data else null
        } catch (e: Exception) {
            Logger.e("获取列车信息失败: $trainCode ${e.localizedMessage}", e)
            null
        }
    }

    private fun fetchMapLine(trainNo: String): Map<String, TrainLineSegment>? {
        return try {
            val response = trainService.getTrainMapLine("v2", trainNo).execute()
            if (response.isSuccessful && response.body()?.status == true) {
                response.body()?.data
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e("获取车次走向失败: $trainNo ${e.localizedMessage}", e)
            null
        }
    }

    private fun sortedStops(info: TrainInfoData): List<TrainStopTime> {
        return info.trainDetail?.stopTime
            ?.sortedBy { it.stationNo?.toIntOrNull() ?: Int.MAX_VALUE }
            ?: emptyList()
    }

    private suspend fun buildFullRoute(
        info: TrainInfoData,
        stops: List<TrainStopTime>,
        date: String,
        trainCode: String,
    ): List<TrainRouteStitcher.RoutePart>? {
        val trainNo = info.trainNo ?: return null
        val segments = fetchMapLine(trainNo)
        val points = segments?.let { buildPointsFromSegments(it) }
        if (points != null) {
            return listOf(TrainRouteStitcher.RoutePart(points, approximate = false))
        }

        val anchors = stops.mapNotNull { toAnchor(it) }
        if (anchors.size < 2) return null
        return createStitcher(date, trainCode, trainNo).resolve(anchors)
    }

    private fun isExactSegment(candidate: CandidateSegment): Boolean {
        return isSameStation(
            candidate.fromTelecode, candidate.fromName,
            segmentFromTelecode, segmentFromName
        ) && isSameStation(
            candidate.toTelecode, candidate.toName,
            segmentToTelecode, segmentToName
        )
    }

    private fun isSameStation(
        telecode: String,
        name: String,
        otherTelecode: String,
        otherName: String,
    ): Boolean {
        if (telecode.isNotBlank() && otherTelecode.isNotBlank()) {
            return telecode.equals(otherTelecode, ignoreCase = true)
        }
        return name.isNotBlank() &&
                normalizeStationName(name) == normalizeStationName(otherName)
    }

    private fun matchCandidate(candidate: CandidateSegment, date: String): CandidateMatch? {
        val info = fetchTrainInfo(candidate.trainCode, date) ?: return null
        val stops = sortedStops(info)
        val range = findStopRange(stops, candidate) ?: return null
        return CandidateMatch(candidate, info, stops, range)
    }

    private fun tryMapLine(match: CandidateMatch): LoadedRoute? {
        val trainNo = match.info.trainNo ?: return null
        val fromLatLng = stopLatLng(match.stops[match.range.first]) ?: return null
        val toLatLng = stopLatLng(match.stops[match.range.second]) ?: return null
        val segments = fetchMapLine(trainNo) ?: return null
        val points = buildPointsFromSegments(segments) ?: return null
        val sliced = slicePointsBetween(points, fromLatLng, toLatLng) ?: return null

        return LoadedRoute(
            trainCode = match.candidate.trainCode,
            parts = listOf(TrainRouteStitcher.RoutePart(sliced, approximate = false)),
            stations = match.stops.subList(match.range.first, match.range.second + 1),
            exactSegment = true,
        )
    }

    /**
     * 当前方向没有走向数据时，只交换本站点再查一次。
     * 例如「贵阳 -> 凯里」没有数据时，只尝试「凯里 -> 贵阳」本站车次，
     * 不会把反向同城站也纳入逆向尝试；同城站仍放在后续步骤处理。
     */
    private fun tryReverse(date: String, displayMatch: CandidateMatch?): LoadedRoute? {
        if (segmentFromTelecode.isBlank() || segmentToTelecode.isBlank()) return null

        val candidates = loadReverseCandidates(date)
            .filter { isReverseExact(it) }
            .take(MAX_REVERSE_CANDIDATE_CHECKS)

        return tryReverseCandidates(candidates, date, displayMatch)
    }

    private fun loadReverseCandidates(date: String): List<CandidateSegment> {
        val response = try {
            ticketService.queryTicketsAuto(date, segmentToTelecode, segmentFromTelecode)
        } catch (e: Exception) {
            Logger.e(
                "逆向余票查询失败: $segmentToTelecode-$segmentFromTelecode ${e.localizedMessage}",
                e
            )
            null
        } ?: return emptyList()

        val stationMap = response.data?.map ?: emptyMap()
        return (response.data?.result ?: emptyList())
            .mapNotNull { item ->
                val parts = item.split("|")
                if (parts.size < 8) return@mapNotNull null
                val trainCode = extractTrainCode(parts[3])
                if (trainCode.isBlank()) return@mapNotNull null
                val fromCode = parts[6]
                val toCode = parts[7]
                CandidateSegment(
                    trainCode = trainCode,
                    fromTelecode = fromCode,
                    toTelecode = toCode,
                    fromName = stationMap[fromCode] ?: fromCode,
                    toName = stationMap[toCode] ?: toCode,
                )
            }
            .distinctBy { it.trainCode }
            .sortedWith(
                compareByDescending<CandidateSegment> {
                    it.trainCode.firstOrNull()?.uppercaseChar() in HIGH_SPEED_PREFIXES
                }
            )
    }

    private fun tryReverseCandidates(
        candidates: List<CandidateSegment>,
        date: String,
        displayMatch: CandidateMatch?,
    ): LoadedRoute? {
        for (candidate in candidates) {
            val match = matchCandidate(candidate, date) ?: continue
            val trainNo = match.info.trainNo ?: continue
            val reverseFrom = stopLatLng(match.stops[match.range.first]) ?: continue
            val reverseTo = stopLatLng(match.stops[match.range.second]) ?: continue

            // 原方向：segmentFrom -> segmentTo；反向车次：segmentTo -> segmentFrom
            val targetFrom = displayMatch
                ?.let { stopLatLng(it.stops[it.range.first]) }
                ?: reverseTo
            val targetTo = displayMatch
                ?.let { stopLatLng(it.stops[it.range.second]) }
                ?: reverseFrom
            if (targetFrom == null || targetTo == null) continue

            val segments = fetchMapLine(trainNo) ?: continue
            val points = buildPointsFromSegments(segments) ?: continue
            val sliced = slicePointsBetween(points, targetFrom, targetTo) ?: continue

            val stations = displayMatch
                ?.stops
                ?.subList(displayMatch.range.first, displayMatch.range.second + 1)
                ?: match.stops
                    .subList(match.range.first, match.range.second + 1)
                    .reversed()

            return LoadedRoute(
                trainCode = displayMatch?.candidate?.trainCode ?: candidate.trainCode,
                parts = listOf(TrainRouteStitcher.RoutePart(sliced, approximate = false)),
                stations = stations.toList(),
                exactSegment = true,
                reversed = true,
            )
        }
        return null
    }

    private fun isReverseExact(candidate: CandidateSegment): Boolean {
        return isSameStation(
            candidate.fromTelecode, candidate.fromName,
            segmentToTelecode, segmentToName
        ) && isSameStation(
            candidate.toTelecode, candidate.toName,
            segmentFromTelecode, segmentFromName
        )
    }

    private fun extractTrainCode(stationTrainCode: String): String {
        val raw = stationTrainCode.trim()
        val match = Regex("^([A-Z]+\\d+)").find(raw)
        return match?.groupValues?.getOrNull(1) ?: raw
    }

    private fun createStitcher(
        date: String,
        trainCode: String,
        trainNo: String,
    ): TrainRouteStitcher {
        return TrainRouteStitcher(
            trainService = trainService,
            ticketService = ticketService,
            httpClient = ticketHttpClient,
            date = date,
            targetTrainCode = trainCode,
            targetTrainNo = trainNo,
        )
    }

    private fun findStopRange(
        stops: List<TrainStopTime>,
        candidate: CandidateSegment,
    ): Pair<Int, Int>? {
        if (stops.isEmpty()) return null
        val fromIndex = stops.indexOfFirst {
            matchesStation(it, candidate.fromTelecode, candidate.fromName)
        }
        if (fromIndex < 0) return null

        // 从出发站之后继续找到达站，避免环线/重复停站时取到出发站之前的同名站
        val toIndex = (fromIndex + 1 until stops.size).firstOrNull {
            matchesStation(stops[it], candidate.toTelecode, candidate.toName)
        } ?: return null
        return fromIndex to toIndex
    }

    private fun matchesStation(stop: TrainStopTime, telecode: String, name: String): Boolean {
        val stopCode = stop.stationTelecode?.trim().orEmpty()
        if (telecode.isNotBlank() && stopCode.equals(telecode, ignoreCase = true)) {
            return true
        }
        if (name.isBlank()) return false
        return normalizeStationName(stop.stationName) == normalizeStationName(name)
    }

    private fun normalizeStationName(raw: String?): String {
        return raw?.trim().orEmpty().removeSuffix("站")
    }

    private fun stopLatLng(stop: TrainStopTime): LatLng? {
        val lon = stop.lon?.toDoubleOrNull() ?: return null
        val lat = stop.lat?.toDoubleOrNull() ?: return null
        return Utils.gcj02ToBd09(lat, lon)
    }

    /**
     * 在完整走向上截取离出发站/到达站最近的两个点之间的折线。
     */
    private fun slicePointsBetween(
        points: List<LatLng>,
        from: LatLng,
        to: LatLng,
    ): List<LatLng>? {
        if (points.size < 2) return null

        fun nearest(target: LatLng): Pair<Int, Double> {
            var index = -1
            var minDistance = Double.MAX_VALUE
            points.forEachIndexed { i, point ->
                val distance = Utils.calculateDistance(
                    point.latitude, point.longitude,
                    target.latitude, target.longitude
                )
                if (distance < minDistance) {
                    minDistance = distance
                    index = i
                }
            }
            return index to minDistance
        }

        val (fromIndex, fromDistance) = nearest(from)
        val (toIndex, toDistance) = nearest(to)
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return null
        if (fromDistance > MAX_STATION_TO_LINE_DISTANCE ||
            toDistance > MAX_STATION_TO_LINE_DISTANCE
        ) {
            return null
        }

        return if (fromIndex < toIndex) {
            points.subList(fromIndex, toIndex + 1).toList()
        } else {
            points.subList(toIndex, fromIndex + 1).toList().reversed()
        }
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
            append("车站: ${apiStations.size}  ")
            append(if (isSegmentMode) "区间距离: " else "总距离: ")
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
                    .color(
                        ContextCompat.getColor(
                            this@TrainMapActivity,
                            if (part.approximate) R.color.ink_tertiary else R.color.primary
                        )
                    )
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
